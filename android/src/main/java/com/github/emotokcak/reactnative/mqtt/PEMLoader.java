package com.github.emotokcak.reactnative.mqtt;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Utility to read a certificate and key from a PEM text. */
public final class PEMLoader {
	/**
	 * Loads an X.509 certificate from a given PEM text.
	 *
	 * `pem` has to start with "-----BEGIN CERTIFICATE-----"
	 * and end with "-----END CERTIFICATE-----".
	 */
	public static X509Certificate loadX509CertificateFromString(String pem)
			throws CertificateException
	{
		final CertificateFactory certificateFactory =
			CertificateFactory.getInstance("X.509");
		return (X509Certificate)certificateFactory.generateCertificate(
			new ByteArrayInputStream(pem.getBytes())
		);
	}

	/**
	 * Loads an RSA private key from a given PEM text.
	 *
	 * `pem` has to start with "-----BEGIN RSA PRIVATE KEY-----"
	 * and end with "-----END RSA PRIVATE KEY-----".
	 */
	public static PrivateKey loadPrivateKeyFromString(String pem)
			throws InvalidKeySpecException, NoSuchAlgorithmException
	{
		final String pemContents = pem
			.replace("-----BEGIN RSA PRIVATE KEY-----", "")
			.replace("-----END RSA PRIVATE KEY-----", "");
		final byte[] data = Base64.getMimeDecoder().decode(pemContents);
		final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(data);
		final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		return keyFactory.generatePrivate(keySpec);
	}
}
