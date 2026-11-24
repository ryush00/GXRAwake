package com.gxrawake.app.setup

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

class LocalAdbConnectionManager private constructor(
    private val credentials: SelfSignedCertGenerator.AdbCredentials
) : AbsAdbConnectionManager() {

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(10L, TimeUnit.SECONDS)
    }

    override fun getPrivateKey(): PrivateKey = credentials.privateKey

    override fun getCertificate(): Certificate = credentials.certificate

    override fun getDeviceName(): String = "GXRAwake"

    companion object {
        @Volatile
        private var instance: LocalAdbConnectionManager? = null

        fun getInstance(context: Context): LocalAdbConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val creds = SelfSignedCertGenerator.loadOrCreate(context.applicationContext.filesDir)
                    LocalAdbConnectionManager(creds).also { instance = it }
                }
            }
        }
    }
}
