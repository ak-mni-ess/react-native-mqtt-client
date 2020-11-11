package com.github.emotokcak.reactnative.mqtt;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** Utility to configures an `SSLSocketFactory`. */
public final class SSLSocketFactoryUtil {
	private static final String SSL_PROTOCOL = "TLSv1.2";

	private static final String PASSWORD = "";

	/** Creates an `SSLSocketFactory` with given certificates. */
	public static SSLSocketFactory createSocketFactory(
			String caCertPem,
			String certPem,
			String keyPem
	) throws CertificateException,
			InvalidKeySpecException,
			IOException,
			KeyManagementException,
			KeyStoreException,
			NoSuchAlgorithmException,
			UnrecoverableKeyException
	{
		// Reference: https://gist.github.com/sharonbn/4104301
		SSLContext sslContext = SSLContext.getInstance(SSL_PROTOCOL);
		final X509Certificate rootCaCert =
			PEMLoader.loadX509CertificateFromString(caCertPem);
		final X509Certificate clientCert =
			PEMLoader.loadX509CertificateFromString(certPem);
		final PrivateKey clientKey =
			PEMLoader.loadPrivateKeyFromString(keyPem);
		final KeyStore rootCaKeyStore =
			KeyStore.getInstance(KeyStore.getDefaultType());
		rootCaKeyStore.load(null, null);
		rootCaKeyStore.setCertificateEntry("ca-certificate", rootCaCert);
		final KeyStore clientKeyStore =
			KeyStore.getInstance(KeyStore.getDefaultType());
		clientKeyStore.load(null, null);
		clientKeyStore.setCertificateEntry("certificate", clientCert);
		clientKeyStore.setKeyEntry(
			"private-key",
			clientKey,
			PASSWORD.toCharArray(),
			new Certificate[]{clientCert}
		);
		final TrustManagerFactory trustManagerFactory =
			TrustManagerFactory.getInstance(	
				TrustManagerFactory.getDefaultAlgorithm()
			);
		trustManagerFactory.init(rootCaKeyStore);
		final KeyManagerFactory keyManagerFactory =
			KeyManagerFactory.getInstance(
				KeyManagerFactory.getDefaultAlgorithm()
			);
		keyManagerFactory.init(clientKeyStore, PASSWORD.toCharArray());
		sslContext.init(
			keyManagerFactory.getKeyManagers(),
			trustManagerFactory.getTrustManagers(),
			null // default SecureRandom
		);
		return sslContext.getSocketFactory();
	}
}
