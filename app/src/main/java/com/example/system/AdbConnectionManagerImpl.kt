package com.example.system

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

class AdbConnectionManagerImpl private constructor(context: Context) : AbsAdbConnectionManager() {

    private val appContext: Context = context.applicationContext
    private val keyFile = File(appContext.filesDir, "ins_adb_key.pk8")
    private val certFile = File(appContext.filesDir, "ins_adb_cert.der")

    private var privateKeyRef: PrivateKey? = null
    private var certificateRef: Certificate? = null

    companion object {
        @Volatile
        private var instance: AdbConnectionManagerImpl? = null

        fun getInstance(context: Context): AdbConnectionManagerImpl =
            instance ?: synchronized(this) {
                instance ?: AdbConnectionManagerImpl(context).also { instance = it }
            }
    }

    init {
        setApi(Build.VERSION.SDK_INT)
        setHostAddress("127.0.0.1")
        loadOrGenerate()
    }

    override fun getPrivateKey(): PrivateKey =
        privateKeyRef ?: throw IllegalStateException("Kunci ADB belum siap")

    override fun getCertificate(): Certificate =
        certificateRef ?: throw IllegalStateException("Sertifikat ADB belum siap")

    override fun getDeviceName(): String = "INSTerminal"

    fun hasKeys(): Boolean = keyFile.exists() && certFile.exists()

    fun regenerateKeys() {
        runCatching { keyFile.delete() }
        runCatching { certFile.delete() }
        privateKeyRef = null
        certificateRef = null
        generate()
    }

    private fun loadOrGenerate() {
        if (keyFile.exists() && certFile.exists()) {
            val loaded = runCatching {
                val spec = PKCS8EncodedKeySpec(keyFile.readBytes())
                val key = KeyFactory.getInstance("RSA").generatePrivate(spec)
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(certFile.inputStream())
                key to cert
            }.getOrNull()
            if (loaded != null) {
                privateKeyRef = loaded.first
                certificateRef = loaded.second
                return
            }
        }
        generate()
    }

    private fun generate() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()

        val now = System.currentTimeMillis()
        val subject = X500Name("CN=INS Terminal, O=INS, C=ID")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            Date(now - 86_400_000L),
            Date(now + 3_650L * 86_400_000L),
            subject,
            pair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
        val x509 = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        privateKeyRef = pair.private
        certificateRef = x509

        runCatching { keyFile.writeBytes(pair.private.encoded) }
        runCatching { certFile.writeBytes(x509.encoded) }
    }
}
