package com.github.emotokcak.reactnative.mqtt;

import android.util.Log;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import javax.net.ssl.SSLSocketFactory;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttTraceHandler;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * An MQTT client.
 *
 * Can connect to only one MQTT broker at the same time, so far.
 *
 * Powered by [Paho MQTT for Android](https://github.com/eclipse/paho.mqtt.android).
 */
public class RNMqttClient extends ReactContextBaseJavaModule {
	private static final String NAME = "MqttClient";

	private static final String PROTOCOL = "ssl";

	private MqttAndroidClient client;

	public RNMqttClient(ReactApplicationContext reactContext) {
		super(reactContext);
		reactContext.addLifecycleEventListener(new LifecycleEventListener() {
			@Override
			public void onHostResume() {
				Log.d(NAME, "onHostResume");
			}

			@Override
			public void onHostPause() {
				Log.d(NAME, "onHostPause");
			}

			@Override
			public void onHostDestroy() {
				Log.d(NAME, "onHostDestroy");
			}
		});
	}

	@Override
	public void onCatalystInstanceDestroy() {
		super.onCatalystInstanceDestroy();
		Log.d(NAME, "onCatalystInstanceDestroy");
		try {
			this.disconnect();
		} catch (MqttException e) {
			Log.e(NAME, "failed to disconnect", e);
		}
	}

	@Override
	public String getName() {
		return NAME;
	}

	/**
	 * Connects to an MQTT broker.
	 *
	 * The following key-value pairs have to be specified in `options`.
	 * - `caCert`: (`String`) Root CA certificate.
	 *   A PEM formatted string.
	 * - `cert`: (`String`) Certificate that has signed the private key.
	 *   A PEM formatted string.
	 * - `key`: (`String`) Private key that identifies the device.
	 *   A PEM formatted string.
	 * - `clientId`: (`String`) Client ID of the device.
	 * - `host`: (`String`) Host name of the MQTT broker.
	 * - `port`: (`int`) Port of the MQTT broker.
	 */
	@ReactMethod
	public void connect(
			ReadableMap options,
			Callback errorCallback,
			Callback successCallback
	) {
		// parses options
		final ConnectionOptions parsedOptions;
		try {
			parsedOptions = ConnectionOptions.parseReadableMap(options);
		} catch (IllegalArgumentException e) {
			Log.e(NAME, "invalid connection options", e);
			errorCallback.invoke(e.getMessage());
			return;
		}
		// initializes a socket factory
		final SSLSocketFactory socketFactory;
		try {
			socketFactory = SSLSocketFactoryUtil.createSocketFactory(
				parsedOptions.caCert,
				parsedOptions.cert,
				parsedOptions.key
			);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// initializes a client
		try {
			final String brokerUri = PROTOCOL +
				"://" + parsedOptions.host +
				":" + parsedOptions.port;
			this.client = new MqttAndroidClient(
				this.getReactApplicationContext().getBaseContext(),
				brokerUri,
				parsedOptions.clientId
			);
			this.client.setCallback(new MqttCallbackExtended() {
				@Override
				public void
						connectComplete(boolean reconnect, String serverURI)
				{
					Log.d(NAME, "connectComplete");
				}

				@Override
				public void connectionLost(Throwable cause) {
					Log.d(NAME, "connectionLost", cause);
				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken token) {
					Log.d(NAME, "deliveryComplete");
				}

				@Override
				public void messageArrived(String topic, MqttMessage message) {
					Log.d(NAME, "messageArrived");
				}
			});
			this.client.setTraceEnabled(true);
			this.client.setTraceCallback(new MqttTraceHandler() {
				@Override
				public void traceDebug(String source, String message) {
					Log.d(NAME + ".trace", message + " (" + source + ")");
				}
				@Override
				public void traceError(String source, String message) {
					Log.e(NAME + ".trace", message + " (" + source + ")");
				}
				@Override
				public void traceException(String source, String message, Exception e) {
					Log.e(NAME + ".trace", message + " (" + source + ")", e);
				}
			});
			final MqttConnectOptions connectOptions = new MqttConnectOptions();
			connectOptions.setSocketFactory(socketFactory);
			connectOptions.setCleanSession(true);
			Log.d(NAME, "connecting to the broker");
			final IMqttToken token = this.client.connect(connectOptions);
			token.setActionCallback(new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					Log.d(
						NAME,
						"connected, token:" + asyncActionToken.toString()
					);
					if (successCallback != null) {
						successCallback.invoke("connected, token:" + asyncActionToken.toString());
					}
					try {
						RNMqttClient.this.subscribe();
					} catch (MqttException e) {
						Log.e(NAME, "failed to subscribe", e);
					}
					try {
						RNMqttClient.this.publish();
					} catch (MqttException e) {
						Log.e(NAME, "failed to publish", e);
					}
				}

				@Override
				public void onFailure(
						IMqttToken asyncActionToken,
						Throwable exception
				) {
					Log.e(
						NAME,
						"failed to connect, token:" + asyncActionToken.toString(),
						exception
					);
					if (errorCallback != null) {
						errorCallback.invoke("failed to connect, token:" + asyncActionToken.toString());
					}
				}
			});
		} catch (MqttException e) {
			Log.e(NAME, "failed to connect", e);
			throw new RuntimeException(e);
		}
	}

	public void disconnect() throws MqttException {
		if (this.client != null) {
			final IMqttToken token = this.client.disconnect();
			token.setActionCallback(new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					Log.d(NAME, "disconnected, token:" + asyncActionToken);
				}
				@Override
				public void onFailure(
						IMqttToken asyncActionToken,
						Throwable cause
				) {
					Log.e(
						NAME,
						"failed to disconnect, token:" + asyncActionToken,
						cause
					);
				}
			});
		}
	}

	private void subscribe() throws MqttException {
		final IMqttToken token = this.client.subscribe(
			"sample-topic/test",
			1 // qos
		);
		token.setActionCallback(new IMqttActionListener() {
			@Override
			public void onSuccess(IMqttToken asyncActionToken) {
				Log.d(NAME, "subscribed, token:" + asyncActionToken);
			}
			@Override
			public void onFailure(
					IMqttToken asyncActionToken,
					Throwable cause
			) {
				Log.e(
					NAME,
					"failed to subscribe, token:" + asyncActionToken,
					cause
				);
			}
		});
	}

	private void publish() throws MqttException {
		final byte[] payload = "{\"co2\":1000}".getBytes();
		final IMqttDeliveryToken token = this.client.publish(
			"sample-topic/test",
			payload,
			1, // qos
			false // retained
		);
		token.setActionCallback(new IMqttActionListener() {
			@Override
			public void onSuccess(IMqttToken asyncActionToken) {
				Log.d(NAME, "published, token:" + asyncActionToken);
			}
			@Override
			public void onFailure(
					IMqttToken asyncActionToken,
					Throwable cause
			) {
				Log.e(
					NAME,
					"failed to publish, token:" + asyncActionToken,
					cause
				);
			}
		});
	}

	/** Options for connection. */
	private static final class ConnectionOptions {
		String caCert;

		String cert;

		String key;

		String host;

		int port;

		String clientId;

		/** Parses a given object from JavaScript. */
		static ConnectionOptions parseReadableMap(ReadableMap options) {
			if (options == null) {
				throw new IllegalArgumentException("options must be specified");
			}
			ConnectionOptions parsed = new ConnectionOptions();
			parsed.caCert = getRequiredString(options, "caCert");
			parsed.cert = getRequiredString(options, "cert");
			parsed.key = getRequiredString(options, "key");
			parsed.host = getRequiredString(options, "host");
			parsed.port = getRequiredInt(options, "port");
			parsed.clientId = getRequiredString(options, "clientId");
			return parsed;
		}

		static String getRequiredString(ReadableMap options, String key) {
			if (!options.hasKey(key)) {
				throw new IllegalArgumentException(
					"options." + key + " must be specified"
				);
			}
			if (options.getType(key) != ReadableType.String) {
				throw new IllegalArgumentException(
					"options." + key + " must be a string but " +
					options.getType(key) + " was given"
				);
			}
			return options.getString(key);
		}

		static int getRequiredInt(ReadableMap options, String key) {
			if (!options.hasKey(key)) {
				throw new IllegalArgumentException(
					"options." + key + " must be specified"
				);
			}
			if (options.getType(key) != ReadableType.Number) {
				throw new IllegalArgumentException(
					"options." + key + "must be an int but " +
					options.getType(key) + " was given"
				);
			}
			return options.getInt(key);
		}
	}
}
