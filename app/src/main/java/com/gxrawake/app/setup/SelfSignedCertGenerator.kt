package com.gxrawake.app.setup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates and persists a standard RSA-2048 private key and self-signed X.509 v3
 * certificate in pure DER without requiring external BouncyCastle dependencies.
 */
object SelfSignedCertGenerator {
    private const val PRIVATE_KEY_FILE = "gxrawake_adb_key.pk8"
    private const val CERTIFICATE_FILE = "gxrawake_adb_cert.der"

    // OIDs in ASN.1 DER encoded content bytes
    private val OID_SHA256_WITH_RSA = byteArrayOf(
        0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B
    )
    private val OID_COMMON_NAME = byteArrayOf(0x55, 0x04, 0x03)

    data class AdbCredentials(
        val privateKey: PrivateKey,
        val certificate: X509Certificate
    )

    fun loadOrCreate(storageDir: File, commonName: String = "GXRAwake"): AdbCredentials {
        val keyFile = File(storageDir, PRIVATE_KEY_FILE)
        val certFile = File(storageDir, CERTIFICATE_FILE)
        if (keyFile.isFile && certFile.isFile) {
            try {
                val keyBytes = keyFile.readBytes()
                val certBytes = certFile.readBytes()
                val privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                cert.checkValidity()
                return AdbCredentials(privateKey, cert)
            } catch (_: Exception) {
                // Regenerate below if corrupted or expired
            }
        }

        val generated = generate(commonName)
        storageDir.mkdirs()
        keyFile.writeBytes(generated.privateKey.encoded)
        certFile.writeBytes(generated.certificate.encoded)
        return generated
    }

    fun generate(commonName: String = "GXRAwake"): AdbCredentials {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        val keyPair: KeyPair = keyGen.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 60L * 60L * 1000L) // -1 day clock skew margin
        val notAfter = Date(now + 10L * 365L * 24L * 60L * 60L * 1000L) // +10 years

        val certDer = buildSelfSignedX509Der(keyPair, commonName, notBefore, notAfter)
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
        return AdbCredentials(keyPair.private, cert)
    }

    private fun buildSelfSignedX509Der(
        keyPair: KeyPair,
        commonName: String,
        notBefore: Date,
        notAfter: Date
    ): ByteArray {
        val serial = BigInteger(63, SecureRandom()).abs().max(BigInteger.ONE)
        val sigAlgId = derSequence(derOid(OID_SHA256_WITH_RSA), derNull())
        val subjectDn = derSequence(
            derSet(
                derSequence(
                    derOid(OID_COMMON_NAME),
                    derUtf8String(commonName)
                )
            )
        )
        val validity = derSequence(derGeneralizedTime(notBefore), derGeneralizedTime(notAfter))
        val version = derExplicitTag(0, derInteger(BigInteger.valueOf(2))) // v3

        val tbsCertificate = derSequence(
            version,
            derInteger(serial),
            sigAlgId,
            subjectDn, // issuer == subject (self-signed)
            validity,
            subjectDn,
            keyPair.public.encoded // SubjectPublicKeyInfo is already ASN.1 DER
        )

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(tbsCertificate)
        val signatureBytes = signer.sign()

        return derSequence(
            tbsCertificate,
            sigAlgId,
            derBitString(signatureBytes)
        )
    }

    private fun derSequence(vararg elements: ByteArray): ByteArray =
        derTag(0x30, concat(*elements))

    private fun derSet(vararg elements: ByteArray): ByteArray =
        derTag(0x31, concat(*elements))

    private fun derExplicitTag(tagNumber: Int, content: ByteArray): ByteArray =
        derTag(0xA0 or tagNumber, content)

    private fun derInteger(value: BigInteger): ByteArray =
        derTag(0x02, value.toByteArray())

    private fun derOid(encodedOidBytes: ByteArray): ByteArray =
        derTag(0x06, encodedOidBytes)

    private fun derNull(): ByteArray = byteArrayOf(0x05, 0x00)

    private fun derUtf8String(text: String): ByteArray =
        derTag(0x0C, text.toByteArray(StandardCharsets.UTF_8))

    private fun derGeneralizedTime(date: Date): ByteArray {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return derTag(0x18, fmt.format(date).toByteArray(StandardCharsets.US_ASCII))
    }

    private fun derBitString(bytes: ByteArray): ByteArray {
        val prefixed = ByteArray(bytes.size + 1)
        prefixed[0] = 0x00 // 0 unused bits
        System.arraycopy(bytes, 0, prefixed, 1, bytes.size)
        return derTag(0x03, prefixed)
    }

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        writeDerLength(out, content.size)
        out.write(content)
        return out.toByteArray()
    }

    private fun writeDerLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 0x80) {
            out.write(length)
            return
        }
        val lengthBytes = mutableListOf<Byte>()
        var remaining = length
        while (remaining > 0) {
            lengthBytes.add(0, (remaining and 0xFF).toByte())
            remaining = remaining ushr 8
        }
        out.write(0x80 or lengthBytes.size)
        for (b in lengthBytes) {
            out.write(b.toInt() and 0xFF)
        }
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val result = ByteArray(total)
        var offset = 0
        for (part in parts) {
            System.arraycopy(part, 0, result, offset, part.size)
            offset += part.size
        }
        return result
    }
}
