package com.html2apk.converter.core

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Firma el APK parcheado en el propio movil usando apksig (v1+v2).
 * Genera un keystore de debug persistente en files/keystore/debug.keystore.
 * Con BouncyCastle se auto-genera un certificado autofirmado.
 */
class ApkSignerHelper(private val context: Context) {

    fun sign(unsignedApk: File, signedApk: File) {
        val (key, certChain) = getOrCreateDebugKey()
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "debug", key, certChain.toList()
        ).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .build()
            .sign()
    }

    private fun getOrCreateDebugKey(): Pair<java.security.PrivateKey, Array<X509Certificate>> {
        val ksFile = File(context.filesDir, "keystore/debug.keystore").apply {
            parentFile?.mkdirs()
        }
        val password = "html2apk-debug".toCharArray()
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        if (ksFile.exists()) {
            ksFile.inputStream().use { ks.load(it, password) }
            val key = ks.getKey("debug", password) as java.security.PrivateKey
            val chain = ks.getCertificateChain("debug").map { it as X509Certificate }.toTypedArray()
            return key to chain
        }
        // Generar RSA 2048 + certificado autofirmado (BC)
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()
        val cert = generateSelfSigned(kp)
        ks.load(null, password)
        ks.setKeyEntry("debug", kp.private, password, arrayOf(cert))
        ksFile.outputStream().use { ks.store(it, password) }
        return kp.private to arrayOf(cert)
    }

    @Suppress("DEPRECATION")
    private fun generateSelfSigned(kp: java.security.KeyPair): X509Certificate {
        // Usa BouncyCastle para no depender de sun.security (no disponible en Android).
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 1000L * 60 * 60 * 24)
        val notAfter = Date(now + 1000L * 60 * 60 * 24 * 365 * 30) // 30 anos
        val serial = BigInteger.valueOf(now)
        val issuer = org.bouncycastle.asn1.x500.X500Name("CN=HTML2APK Debug, O=HTML2APK")
        val builder = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, issuer, kp.public
        )
        val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
            .build(kp.private)
        val holder = builder.build(signer)
        return org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(holder)
    }
}
