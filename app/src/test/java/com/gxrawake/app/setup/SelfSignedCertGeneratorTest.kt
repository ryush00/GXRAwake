package com.gxrawake.app.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.interfaces.RSAPrivateKey

class SelfSignedCertGeneratorTest {

    @Test
    fun generatesValidSelfSignedX509Certificate() {
        val creds = SelfSignedCertGenerator.generate("GXRAwakeTest")

        assertTrue(creds.privateKey is RSAPrivateKey)
        assertEquals(2048, (creds.privateKey as RSAPrivateKey).modulus.bitLength())
        assertEquals("X.509", creds.certificate.type)
        assertEquals(3, creds.certificate.version)
        assertTrue(creds.certificate.subjectX500Principal.name.contains("CN=GXRAwakeTest"))

        // Verifies signature and validity window
        creds.certificate.checkValidity()
        creds.certificate.verify(creds.certificate.publicKey)
    }

    @Test
    fun persistsAndReloadsSameCredentials() {
        val dir: File = Files.createTempDirectory("gxrawake_cert_test").toFile()
        try {
            val first = SelfSignedCertGenerator.loadOrCreate(dir, "GXRAwake")
            val second = SelfSignedCertGenerator.loadOrCreate(dir, "GXRAwake")

            assertEquals(first.certificate.serialNumber, second.certificate.serialNumber)
            assertTrue(first.privateKey.encoded.contentEquals(second.privateKey.encoded))
            assertTrue(first.certificate.encoded.contentEquals(second.certificate.encoded))
        } finally {
            dir.deleteRecursively()
        }
    }
}
