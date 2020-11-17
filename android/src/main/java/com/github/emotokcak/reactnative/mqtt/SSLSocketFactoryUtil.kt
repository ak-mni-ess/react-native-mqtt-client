package com.github.emotokcak.reactnative.mqtt

import android.util.Log
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
    private const val SSL_PROTOCOL: String = "TLSv1.2"

    private const val PASSWORD: String = ""

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
     * @param caCertAlias
     *
     *   Alias for a root certificate.
     *
     * @param keyAlias
     *
     *   Alias for a private key.
     *
     * @return
     *
     *   `SSLSocketFactory` created with `caCertPem`, `certPem` and `keyPem`.
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
            keyPem: String,
            caCertAlias: String,
            keyAlias: String
    ): SSLSocketFactory {
        // Reference: https://gist.github.com/sharonbn/4104301
        val rootCaCert = PEMLoader.loadX509CertificateFromString(caCertPem)
        val clientCert = PEMLoader.loadX509CertificateFromString(certPem)
        val clientKey = PEMLoader.loadPrivateKeyFromString(keyPem)
        // certificates and a key saved in the AndroidKeyStore are persisted.
        // please refer to the following section for AndroidKeyStore,
        // https://developer.android.com/training/articles/keystore#UsingAndroidKeyStore
        val androidKeyStore = KeyStore.getInstance("AndroidKeyStore")
        androidKeyStore.load(null)
        Log.d(
            "SSLSocketFactoryUtil",
            "aliases: ${androidKeyStore.aliases().toList()}"
        )
        androidKeyStore.setKeyEntry(
            keyAlias,
            clientKey,
            PASSWORD.toCharArray(),
            arrayOf(clientCert)
        )
        androidKeyStore.setCertificateEntry(caCertAlias, rootCaCert)
        return this.createSocketFactoryFromKeyStore(androidKeyStore)
    }

    /**
     * Creates an `SSLSocketFactory` from the Android key store.
     *
     * @return
     *
     *   `SSLSocketFactory` created from the Android key store.
     *
     * @throws CertificateException
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
    fun createSocketFactoryFromAndroidKeyStore(): SSLSocketFactory {
        val androidKeyStore = KeyStore.getInstance("AndroidKeyStore")
        androidKeyStore.load(null)
        return this.createSocketFactoryFromKeyStore(androidKeyStore)
    }

    /**
     * Creates an `SSLSocketFactory` from a given key store.
     *
     * @param keyStore
     *
     *   `KeyStore` containing a necessary CA certificate, certificate and
     *   private key.
     *
     * @return
     *
     *   `SSLSocketFactory` created from `keyStore`.
     *
     * @throws KeyManagementException
     *
     * @throws KeyStoreException
     *
     * @throws NoSuchAlgorithmException
     *
     * @throws UnrecoverableKeyException
     */
    private fun createSocketFactoryFromKeyStore(keyStore: KeyStore):
            SSLSocketFactory
    {
        val sslContext = SSLContext.getInstance(SSL_PROTOCOL)
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(keyStore)
        val keyManagerFactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        )
        keyManagerFactory.init(keyStore, PASSWORD.toCharArray())
        sslContext.init(
            keyManagerFactory.getKeyManagers(),
            trustManagerFactory.getTrustManagers(),
            null // default SecureRandom
        )
        return sslContext.getSocketFactory()
    }

    /**
     * Clears a root certificate and private key the Android key store.
     *
     * @param caCertAlias
     *
     *   Alias for the root certificate to be cleared.
     *
     * @param keyAlias
     *
     *   Alias for the private key to be cleared.
     *
     * @throws CertificateException
     *
     * @throws IOException
     *
     * @throws KeyStoreException
     *
     * @throws NoSuchAlgorithmException
     */
    fun resetAndroidKeyStore(caCertAlias: String, keyAlias: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry(caCertAlias)
        keyStore.deleteEntry(keyAlias)
    }
}
