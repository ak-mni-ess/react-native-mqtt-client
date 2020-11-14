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
    @Synchronized
    @ReactMethod
    fun setIdentity(params: ReadableMap, promise: Promise) {
        try {
            this.socketFactory = SSLSocketFactoryUtil.createSocketFactory(
                params.getRequiredString("caCertPem"),
                params.getRequiredString("certPem"),
                params.getRequiredString("keyPem")
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
     * Returns a socket factory.
     *
     * @return
     *
     *   `SSLSocketFactory` to connect to an MQTT broker.
     *   `null` if no socket factory is available.
     */
    @Synchronized
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
     * @param errorCallback
     *
     *   Called when an error has occurred.
     *
     * @param successCallback
     *
     *   Called when connection has been established.
     */
    @ReactMethod
    fun connect(
            params: ReadableMap,
            errorCallback: Callback?,
            successCallback: Callback?
    ) {
        // parses parameters
        val parsedParams: ConnectionParameters
        try {
            parsedParams = ConnectionParameters.parseReadableMap(params)
        } catch (e: IllegalArgumentException) {
            Log.e(NAME, "invalid connection parameters", e)
            errorCallback?.invoke(e.message)
            return
        }
        // obtains a socket factory
        val socketFactory = this.getSocketFactory()
        if (socketFactory == null) {
            errorCallback?.invoke("no SSLSocketFactory is available")
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

                override fun connectionLost(cause: Throwable) {
                    Log.d(NAME, "connectionLost", cause)
                    this@RNMqttClient.notifyEvent("ERROR_CONNECTION", cause)
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
                    val params = Arguments.createMap()
                    params.putString("topic", topic)
                    params.putString("payload", message.toString())
                    this@RNMqttClient.notifyEvent("received-message", params)
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
                }

                override fun onFailure(
                        asyncActionToken: IMqttToken,
                        cause: Throwable
                ) {
                    Log.e(
                        NAME,
                        "failed to connect, token: ${asyncActionToken}",
                        cause
                    )
                    errorCallback?.invoke(
                        "failed to connect, token: ${asyncActionToken}"
                    )
                    this@RNMqttClient.notifyEvent("ERROR_CONNECTION", cause);
                }
            })
        } catch (e: MqttException) {
            Log.e(NAME, "failed to connect", e)
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
                        cause: Throwable
                ) {
                    Log.e(
                        NAME,
                        "failed to disconnect, token: ${asyncActionToken}",
                        cause
                    )
                    this@RNMqttClient.notifyEvent("ERROR_DISCONNECT", cause)
                }
            })
        } catch (e: MqttException) {
            Log.e(NAME, "failed to disconnect", e)
            return
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
                this@RNMqttClient.notifyEvent("ERROR_SUBSCRIBE", cause)
            }
        })
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
     * @param errorCallback
     *
     *   Called when an error has occurred.
     */
    @ReactMethod
    fun publish(topic: String, payload: String, errorCallback: Callback?) {
        val client = this.client
        if (client == null) {
            Log.w(NAME, "failed to publish. no MQTT connection")
            return
        }
        val token = client.publish(
            topic,
            payload.toByteArray(),
            1, // qos
            false // not retained
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
                errorCallback?.invoke("failed to publish to $topic")
                this@RNMqttClient.notifyEvent("ERROR_PUBLISH", cause)
            }
        })
    }

    // Notifies a `got-error` event.
    private fun notifyError(code: String, cause: Throwable) {
        val params = Arguments.createMap()
        params.putString("code", code)
        params.putString("message", cause.message)
        this.notifyEvent("got-error", params)
    }

    // Notifies a given event.
    private fun notifyEvent(eventName: String, params: Any?) {
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
