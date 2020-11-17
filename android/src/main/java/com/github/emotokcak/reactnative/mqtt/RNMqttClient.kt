package com.github.emotokcak.reactnative.mqtt

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
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
        /** Default alias for a root certificate in a key store. */
        const val DEFAULT_CA_CERT_ALIAS: String = "ca-certificate"

        /** Default alias for a private key in a key store. */
        const val DEFAULT_KEY_ALIAS: String = "private-key"

        private const val NAME: String = "MqttClient"

        private const val PROTOCOL: String = "ssl"
    }

    private var socketFactory: SSLSocketFactory? = null

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
     * Sets the identity for connection.
     *
     * The following key-value pairs have to be specified in `params`.
     * - `caCertPem`: {`String`} PEM representation of a root CA certificate.
     * - `certPem`: {`String`} PEM representation of a certificate.
     * - `keyPem`: {`String`} PEM representation of a private key.
     * - `keyStoreOptions`: {`ReadableMap`} options for a key store.
     *   May have the following optional key-value pairs,
     *     - `caCertAlias`: {`String`} alias for a root certificate.
     *       `DEFAULT_CA_CERT_ALIAS` if omitted.
     *     - `keyAlias`: {`String`} alias for a private key.
     *       `DEFAULT_KEY_ALIAS` if omitted.
     *
     * If there is already connection to an MQTT broker, this new identity
     * won't affect it.
     *
     * @param params
     *
     *   Parameters constituting an identity.
     *
     * @param promise
     *
     *   Promise that is resolved when the identity is set.
     */
    @ReactMethod
    fun setIdentity(params: ReadableMap, promise: Promise) {
        try {
            val keyStoreOptions: ReadableMap? =
                params.getOptionalMap("keyStoreOptions")
            this.socketFactory = SSLSocketFactoryUtil.createSocketFactory(
                caCertPem=params.getRequiredString("caCertPem"),
                certPem=params.getRequiredString("certPem"),
                keyPem=params.getRequiredString("keyPem"),
                caCertAlias=keyStoreOptions?.getOptionalString("caCertAlias") ?:
                    DEFAULT_CA_CERT_ALIAS,
                keyAlias=keyStoreOptions?.getOptionalString("keyAlias") ?:
                    DEFAULT_KEY_ALIAS
            )
            promise.resolve(null)
            return
        } catch (e: IllegalArgumentException) {
            Log.e(NAME, "invalid identity parameters", e)
            promise.reject("RANGE_ERROR", e)
            return
        } catch (e: Exception) {
            Log.e(NAME, "failed to create an SSLSocketFactory", e)
            promise.reject("INVALID_IDENTITY", e)
            return
        }
    }

    /**
     * Resets the identity stored in the key store.
     *
     * `options` may have the following optional key-value pairs,
     * - `ceCertAlias`: (`string`)
     *   alias associated with a root certificate to be cleared.
     *   `DEFAULT_CA_CERT_ALIAS` if omitted.
     * - `keyAlias`: (`string`)
     *   alias associated with a private key to be cleared.
     *   `DEFAULT_KEY_ALIAS` if omitted.
     *
     * @param options
     *
     *   Options for the key store.
     *
     * @param promise
     *
     *   Promise that is resolved when the identity is reset.
     */
    @ReactMethod
    fun resetIdentity(options: ReadableMap, promise: Promise) {
        try {
            SSLSocketFactoryUtil.resetAndroidKeyStore(
                options.getOptionalString("caCertAlias") ?:
                    DEFAULT_CA_CERT_ALIAS,
                options.getOptionalString("keyAlias") ?: DEFAULT_KEY_ALIAS
            )
            promise.resolve(null)
            return
        } catch (e: IllegalArgumentException) {
            Log.e(NAME, "invalid key store options", e)
            promise.reject("RANGE_ERROR", e)
            return
        } catch (e: Exception) {
            Log.e(NAME, "failed to reset the identity", e)
            promise.reject("INVALID_IDENTITY", e)
            return
        }
    }

    /**
     * Returns a socket factory.
     *
     * @return
     *
     *   `SSLSocketFactory` to connect to an MQTT broker.
     *   `null` if no socket factory is available.
     */
    private fun getSocketFactory(): SSLSocketFactory? {
        try {
            return this.socketFactory ?:
                SSLSocketFactoryUtil.createSocketFactoryFromAndroidKeyStore()
        } catch (e: Exception) {
            Log.e(NAME, "failed to obtain an SSLSocketFactory", e)
            return null
        }
    }

    /**
     * Connects to an MQTT broker.
     *
     * The following key-value pairs have to be specified in `params`.
     * - `clientId`: {`String`} Client ID of the device.
     * - `host`: {`String`} Host name of the MQTT broker.
     * - `port`: {`int`} Port of the MQTT broker.
     *
     * @param params
     *
     *   Parameters for connection.
     *   Please see above.
     *
     * @param promise
     *
     *   Resolved when connection has been established.
     */
    @ReactMethod
    fun connect(params: ReadableMap, promise: Promise) {
        // parses parameters
        val parsedParams: ConnectionParameters
        try {
            parsedParams = ConnectionParameters.parseReadableMap(params)
        } catch (e: IllegalArgumentException) {
            Log.e(NAME, "invalid connection parameters", e)
            promise.reject("RANGE_ERROR", e)
            return
        }
        // obtains a socket factory
        val socketFactory = this.getSocketFactory()
        if (socketFactory == null) {
            promise.reject(
                "ERROR_CONFIG",
                Exception("no identity is configured")
            )
            return
        }
        // initializes a client
        try {
            val brokerUri =
                "$PROTOCOL://${parsedParams.host}:${parsedParams.port}"
            val client = MqttAndroidClient(
                this.getReactApplicationContext().getBaseContext(),
                brokerUri,
                parsedParams.clientId
            )
            this.client = client
            client.setCallback(object: MqttCallbackExtended {
                override fun connectComplete(
                        reconnect: Boolean,
                        serverURI: String
                ) {
                    Log.d(NAME, "connectComplete")
                    this@RNMqttClient.notifyEvent("connected", null)
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.d(NAME, "connectionLost", cause)
                    this@RNMqttClient.notifyError("ERROR_CONNECTION", cause)
                    this@RNMqttClient.notifyEvent("disconnected", null)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    Log.d(NAME, "deliveryComplete")
                }

                override fun messageArrived(
                        topic: String,
                        message: MqttMessage
                ) {
                    Log.d(NAME, "messageArrived")
                    val arg = Arguments.createMap()
                    arg.putString("topic", topic)
                    arg.putString("payload", message.toString())
                    this@RNMqttClient.notifyEvent("received-message", arg)
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
                    promise.resolve(null)
                }

                override fun onFailure(
                        asyncActionToken: IMqttToken,
                        cause: Throwable?
                ) {
                    Log.e(
                        NAME,
                        "failed to connect, token: ${asyncActionToken}",
                        cause
                    )
                    promise.reject("ERROR_CONNECTION", cause)
                }
            })
        } catch (e: MqttException) {
            Log.e(NAME, "failed to connect", e)
            promise.reject("ERROR_CONNECTION", e)
            return
        }
    }

    /**
     * Disconnects from the MQTT broker.
     *
     * Does nothing if there is no MQTT connection.
     */
    @ReactMethod
    fun disconnect() {
        val client = this.client
        if (client == null) {
            Log.w(NAME, "no MQTT connection")
            return
        }
        try {
            val token = client.disconnect()
            token.setActionCallback(object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.d(NAME, "disconnected, token: ${asyncActionToken}")
                    this@RNMqttClient.client = null
                    this@RNMqttClient.notifyEvent("disconnected", null)
                }

                override fun onFailure(
                        asyncActionToken: IMqttToken,
                        cause: Throwable?
                ) {
                    Log.e(
                        NAME,
                        "failed to disconnect, token: ${asyncActionToken}",
                        cause
                    )
                    this@RNMqttClient.notifyError("ERROR_DISCONNECT", cause)
                }
            })
        } catch (e: MqttException) {
            Log.e(NAME, "failed to disconnect", e)
            return
        }
    }

    /**
     * Publishes given data to a specified topic.
     *
     * Does nothing if there is no MQTT connection.
     *
     * @param topic
     *
     *   Topic where to publish `payload`.
     *
     * @param payload
     *
     *   Payload to be published.
     *
     * @param promise
     *
     *   Resolved when publishing has finished.
     */
    @ReactMethod
    fun publish(topic: String, payload: String, promise: Promise) {
        val client = this.client
        if (client == null) {
            Log.w(NAME, "failed to publish. no MQTT connection")
            return
        }
        try {
            val token = client.publish(
                topic,
                payload.toByteArray(),
                1, // qos
                false // not retained
            )
            token.setActionCallback(object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.d(NAME, "published, token: ${asyncActionToken}")
                    promise.resolve(null)
                }

                override fun onFailure(
                        asyncActionToken: IMqttToken,
                        cause: Throwable?
                ) {
                    Log.e(
                        NAME,
                        "failed to publish, token: ${asyncActionToken}",
                        cause
                    )
                    this@RNMqttClient.notifyError("ERROR_PUBLISH", cause)
                    promise.reject("ERROR_PUBLISH", cause)
                }
            })
        } catch (e: MqttException) {
            Log.e(NAME, "failed to publish to ${topic}", e)
            promise.reject("ERROR_PUBLISH", e)
            return
        } catch (e: IllegalArgumentException) {
            // maybe Invalid ClientHandle
            Log.e(NAME, "failed to publish to ${topic}", e)
            promise.reject("ERROR_PUBLISH", e)
            return
        }
    }

    /**
     * Subscribes a specified topic.
     *
     * @param topic
     *
     *   Topic to subscribe.
     *
     * @param promise
     *
     *   Resolved when subscription has done.
     */
    @ReactMethod
    fun subscribe(topic: String, promise: Promise) {
        val client = this.client
        if (client == null) {
            promise.reject("NO_CONNECTION", Exception("no MQTT connection"))
            return
        }
        try {
            val token = client.subscribe(
                topic,
                1 // qos
            )
            token.setActionCallback(object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.d(NAME, "subscribed, token: ${asyncActionToken}")
                    promise.resolve(null)
                }

                override fun onFailure(
                        asyncActionToken: IMqttToken,
                        cause: Throwable?
                ) {
                    Log.e(
                        NAME,
                        "failed to subscribe, token: ${asyncActionToken}",
                        cause
                    )
                    this@RNMqttClient.notifyError("ERROR_SUBSCRIBE", cause)
                    // TODO: iOS may not be able to reject this case
                    promise.reject("ERROR_SUBSCRIBE", cause)
                }
            })
        } catch (e: MqttException) {
            Log.e(NAME, "failed to subscribe '$topic'", e)
            promise.reject("ERROR_SUBSCRIBE", e)
            return
        } catch (e: IllegalArgumentException) {
            // maybe Invalid ClientHandle
            Log.e(NAME, "failed to subscribe '$topic'", e)
            promise.reject("ERROR_SUBSCRIBE", e)
            return
        }
    }

    // Notifies a `got-error` event.
    private fun notifyError(code: String, cause: Throwable?) {
        val params = Arguments.createMap()
        params.putString("code", code)
        params.putString("message", cause?.message ?: "")
        this.notifyEvent("got-error", params)
    }

    // Notifies a given event.
    private fun notifyEvent(eventName: String, params: Any?) {
        Log.d(NAME, "notifying event $eventName")
        this.getReactApplicationContext()
            .getJSModule(RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    // Parameters for connection.
    private class ConnectionParameters(
        val host: String,
        val port: Int,
        val clientId: String
    ) {
        companion object {
            // Parses a given object from JavaScript.
            fun parseReadableMap(params: ReadableMap): ConnectionParameters {
                return ConnectionParameters(
                    host=params.getRequiredString("host"),
                    port=params.getRequiredInt("port"),
                    clientId=params.getRequiredString("clientId")
                )
            }
        }
    }
}
