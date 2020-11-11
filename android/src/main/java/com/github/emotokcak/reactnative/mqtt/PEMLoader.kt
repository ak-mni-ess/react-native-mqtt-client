package com.github.emotokcak.reactnative.mqtt

import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** Utility to read a certificate and key from a PEM text. */
object PEMLoader {
    /**
     * Loads an X.509 certificate from a given PEM text.
     *
     * `pem` has to start with "-----BEGIN CERTIFICATE-----"
     * and end with "-----END CERTIFICATE-----".
     *
     * @param pem
     *
     *   PEM representation of a certificate.
     *
     * @throws CertificateException
     *
     *   If `pem` is invalid.
     */
    @JvmStatic
    fun loadX509CertificateFromString(pem: String): X509Certificate {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        return certificateFactory.generateCertificate(pem.byteInputStream())
            as X509Certificate
    }

    /**
     * Loads an RSA private key from a given PEM text.
     *
     * `pem` has to start with "-----BEGIN RSA PRIVATE KEY-----"
     * and end with "-----END RSA PRIVATE KEY-----".
     *
     * @param pem
     *
     *   PEM representation of an RSA private key.
     *
     * @throws InvalidKeySpecException
     *
     *   If `pem` is invalid.
     *
     * @throws NoSuchAlgorithmException
     *
     *   If the algorithm "RSA" is not supported.
     */
    @JvmStatic
    fun loadPrivateKeyFromString(pem: String): PrivateKey {
        val pemContents = pem
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
        val data = Base64.getMimeDecoder().decode(pemContents)
        val keySpec = PKCS8EncodedKeySpec(data)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }
}
