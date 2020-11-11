package com.github.emotokcak.reactnative.mqtt

import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/** Utility to configures an `SSLSocketFactory`. */
object SSLSocketFactoryUtil {
    private val SSL_PROTOCOL: String = "TLSv1.2"

    private val PASSWORD: String = ""

    /**
     * Creates an `SSLSocketFactory` with given certificates.
     *
     * @param caCertPem
     *
     *   PEM representation of a root certificate.
     *
     * @param certPem
     *
     *   PEM representation of a certificate.
     *
     * @param keyPem
     *
     *   PEM representation of a private key.
     *
     * @throws CertificateException
     *
     * @throws InvalidKeySpecException
     *
     * @throws IOException
     *
     * @throws KeyManagementException
     *
     * @throws KeyStoreException
     *
     * @throws NoSuchAlgorithmException
     *
     * @throws UnrecoverableKeyException
     */
    @JvmStatic
    fun createSocketFactory(
            caCertPem: String,
            certPem: String,
            keyPem: String
    ): SSLSocketFactory {
        // Reference: https://gist.github.com/sharonbn/4104301
        val sslContext = SSLContext.getInstance(SSL_PROTOCOL)
        val rootCaCert = PEMLoader.loadX509CertificateFromString(caCertPem)
        val clientCert = PEMLoader.loadX509CertificateFromString(certPem)
        val clientKey = PEMLoader.loadPrivateKeyFromString(keyPem)
        val rootCaKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        rootCaKeyStore.load(null, null)
        rootCaKeyStore.setCertificateEntry("ca-certificate", rootCaCert)
        val clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        clientKeyStore.load(null, null)
        clientKeyStore.setCertificateEntry("certificate", clientCert)
        clientKeyStore.setKeyEntry(
            "private-key",
            clientKey,
            PASSWORD.toCharArray(),
            arrayOf(clientCert)
        )
        val trustManagerFactory = TrustManagerFactory.getInstance(  
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(rootCaKeyStore)
        val keyManagerFactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        )
        keyManagerFactory.init(clientKeyStore, PASSWORD.toCharArray())
        sslContext.init(
            keyManagerFactory.getKeyManagers(),
            trustManagerFactory.getTrustManagers(),
            null // default SecureRandom
        )
        return sslContext.getSocketFactory()
    }
}
