package com.github.emotokcak.reactnative.mqtt

import android.util.Log
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import javax.net.ssl.SSLSocketFactory
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.android.service.MqttTraceHandler
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * An MQTT client.
 *
 * Can connect to only one MQTT broker at the same time, so far.
 *
 * Powered by [Paho MQTT for Android](https://github.com/eclipse/paho.mqtt.android).
 */
class RNMqttClient(reactContext: ReactApplicationContext)
        : ReactContextBaseJavaModule(reactContext)
{
    companion object {
        private val NAME: String = "MqttClient"

        private val PROTOCOL: String = "ssl"
    }

    private var client: MqttAndroidClient? = null

    init {
        reactContext.addLifecycleEventListener(
            object: LifecycleEventListener {
                override fun onHostResume() {
                    Log.d(NAME, "onHostResume")
                }

                override fun onHostPause() {
                    Log.d(NAME, "onHostPause")
                }

                override fun onHostDestroy() {
                    Log.d(NAME, "onHostDestroy")
                }
            }
        )
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        Log.d(NAME, "onCatalystInstanceDestroy")
        try {
            this.disconnect()
        } catch (e: MqttException) {
            Log.e(NAME, "failed to disconnect", e)
        }
    }

    override fun getName(): String = NAME

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
    fun connect(
            options: ReadableMap?,
            errorCallback: Callback?,
            successCallback: Callback?
    ) {
        // parses options
        val parsedOptions: ConnectionOptions
        try {
            parsedOptions = ConnectionOptions.parseReadableMap(options)
        } catch (e: IllegalArgumentException) {
            Log.e(NAME, "invalid connection options", e)
            errorCallback?.invoke(e.message)
            return
        }
        // initializes a socket factory
        val socketFactory: SSLSocketFactory
        try {
            socketFactory = SSLSocketFactoryUtil.createSocketFactory(
                parsedOptions.caCert,
                parsedOptions.cert,
                parsedOptions.key
            )
        } catch (e: Exception) {
            Log.e(NAME, "failed to initialize a socket factory", e)
            return
        }
        // initializes a client
        try {
            val brokerUri =
                "$PROTOCOL://${parsedOptions.host}:${parsedOptions.port}"
            val client = MqttAndroidClient(
                this.getReactApplicationContext().getBaseContext(),
                brokerUri,
                parsedOptions.clientId
            )
            this.client = client
            client.setCallback(object: MqttCallbackExtended {
                override fun connectComplete(
                        reconnect: Boolean,
                        serverURI: String
                ) {
                    Log.d(NAME, "connectComplete")
                }

                override fun connectionLost(cause: Throwable) {
                    Log.d(NAME, "connectionLost", cause)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    Log.d(NAME, "deliveryComplete")
                }

                override fun messageArrived(
                        topic: String,
                        message: MqttMessage
                ) {
                    Log.d(NAME, "messageArrived")
                }
            })
            client.setTraceEnabled(true)
            client.setTraceCallback(object: MqttTraceHandler {
                override fun traceDebug(source: String, message: String) {
                    Log.d("$NAME.trace", "$message ($source)")
                }

                override fun traceError(source: String, message: String) {
                    Log.e("$NAME.trace", "$message ($source)")
                }

                override fun traceException(
                        source: String,
                        message: String,
                        e: Exception)
                {
                    Log.e("$NAME.trace", "$message ($source)", e)
                }
            })
            val connectOptions = MqttConnectOptions()
            connectOptions.setSocketFactory(socketFactory)
            connectOptions.setCleanSession(true)
            Log.d(NAME, "connecting to the broker")
            val token = client.connect(connectOptions)
            token.setActionCallback(object: IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.d(NAME, "connected, token: ${asyncActionToken}")
                    successCallback?.invoke(
                        "connected, token: ${asyncActionToken}"
                    )
                    try {
                        this@RNMqttClient.subscribe()
                    } catch (e: MqttException) {
                        Log.e(NAME, "failed to subscribe", e)
                    }
                    try {
                        this@RNMqttClient.publish()
                    } catch (e: MqttException) {
                        Log.e(NAME, "failed to publish", e)
                    }
                }

                override fun onFailure(
                        asyncActionToken: IMqttToken,
                        exception: Throwable
                ) {
                    Log.e(
                        NAME,
                        "failed to connect, token: ${asyncActionToken}",
                        exception
                    )
                    errorCallback?.invoke(
                        "failed to connect, token: ${asyncActionToken}"
                    )
                }
            })
        } catch (e: MqttException) {
            Log.e(NAME, "failed to connect", e)
            return
        }
    }

    @ReactMethod
    fun disconnect() {
        val client = this.client
        if (client != null) {
            try {
                val token = client.disconnect()
                token.setActionCallback(object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken) {
                        Log.d(NAME, "disconnected, token: ${asyncActionToken}")
                    }

                    override fun onFailure(
                            asyncActionToken: IMqttToken,
                            cause: Throwable
                    ) {
                        Log.e(
                            NAME,
                            "failed to disconnect, token: ${asyncActionToken}",
                            cause
                        )
                    }
                })
            } catch (e: MqttException) {
                Log.e(NAME, "failed to disconnect", e)
                return
            }
        }
    }

    // @throws MqttException
    private fun subscribe() {
        val client = this.client
        if (client == null) {
            return
        }
        val token = client.subscribe(
            "sample-topic/test",
            1 // qos
        )
        token.setActionCallback(object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                Log.d(NAME, "subscribed, token: ${asyncActionToken}")
            }

            override fun onFailure(
                    asyncActionToken: IMqttToken,
                    cause: Throwable
            ) {
                Log.e(
                    NAME,
                    "failed to subscribe, token: ${asyncActionToken}",
                    cause
                )
            }
        })
    }

    // @throws MqttException
    private fun publish() {
        val client = this.client
        if (client == null) {
            return
        }
        val payload = "{\"co2\":1000}".toByteArray()
        val token = client.publish(
            "sample-topic/test",
            payload,
            1, // qos
            false // retained
        )
        token.setActionCallback(object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                Log.d(NAME, "published, token: ${asyncActionToken}")
            }

            override fun onFailure(
                    asyncActionToken: IMqttToken,
                    cause: Throwable
            ) {
                Log.e(
                    NAME,
                    "failed to publish, token: ${asyncActionToken}",
                    cause
                )
            }
        })
    }

    /** Options for connection. */
    private class ConnectionOptions(
        val caCert: String,
        val cert: String,
        val key: String,
        val host: String,
        val port: Int,
        val clientId: String
    ) {
        companion object {
            /** Parses a given object from JavaScript. */
            fun parseReadableMap(options: ReadableMap?): ConnectionOptions {
                if (options == null) {
                    throw IllegalArgumentException("options must be specified")
                }
                return ConnectionOptions(
                    caCert=options.getRequiredString("caCert"),
                    cert=options.getRequiredString("cert"),
                    key=options.getRequiredString("key"),
                    host=options.getRequiredString("host"),
                    port=options.getRequiredInt("port"),
                    clientId=options.getRequiredString("clientId")
                )
            }
        }
    }
}
