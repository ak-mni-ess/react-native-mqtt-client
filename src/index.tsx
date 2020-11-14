import { NativeEventEmitter, NativeModules } from 'react-native';

const { MqttClient: MqttClientImpl } = NativeModules;

const eventBridge = new NativeEventEmitter(MqttClientImpl);

/**
 * MQTT client.
 *
 * #### Events
 *
 * Emits the following events.
 * - `"connected"`
 * - `"disconnected"`
 * - `"got-error"`
 *
 * ##### connected
 *
 * Notified when connection to an MQTT broker has been established.
 *
 * No arguments.
 *
 * ##### disconnected
 *
 * Notified when the client is disconnected from the MQTT broker.
 *
 * No arguments.
 *
 * ##### received-message
 *
 * Notified when the client has received a message from the MQTT broker.
 *
 * The argument is an object which has the following fields,
 * - `topic`: {`string`} topic of the received message.
 * - `payload`: {`string`} payload of the received message.
 *
 * ##### got-error
 *
 * Notified when an error has occurred.
 *
 * The argument is an object that has the following fields,
 * - `code`: {`string`} error code.
 * - `message`: {`string`} explanation of the error.
 *
 * @class MqttClient
 */
export class MqttClient {
  /**
   * Sets the identity for connection.
   *
   * Certificates and a key constituting the identity is stored in a device
   * specific keystore.
   *
   * @function setIdentity
   *
   * @param params
   *
   *   Parameters constituting an identity.
   *
   * @return Promise<void>
   *
   *   Resolved when the identity is set.
   */
  setIdentity(params: IdentityParameters): Promise<void> {
    return MqttClientImpl.setIdentity(params);
  }

  /**
   * Connects to an MQTT broker.
   *
   * @function connect
   */
  connect(
    params: ConnectionParameters,
    errorCallback?: CallbackFunction,
    successCallback?: CallbackFunction
  ) {
    MqttClientImpl.connect(params, errorCallback, successCallback);
  }

  /**
   * Disconnects from the MQTT broker.
   *
   * @function disconnect
   */
  disconnect() {
    MqttClientImpl.disconnect();
  }

  /**
   * Publishes a given payload to a specified topic.
   *
   * @function publish
   */
  publish(topic: string, payload: string, errorCallback?: CallbackFunction) {
    MqttClientImpl.publish(topic, payload, errorCallback);
  }

  /**
   * Subscribes a specified topic.
   *
   * @function subscribe
   *
   * @param topic
   *
   *   Topic to subscribe.
   *
   * @return {Promise<void>}
   *
   *   Resolved when subscription has done.
   */
  subscribe(topic: string): Promise<void> {
    return MqttClientImpl.subscribe(topic);
  }

  /**
   * Listens for a given event from this client.
   *
   * @function addListener
   */
  addListener(eventName: string, listener: ListenerFunction) {
    eventBridge.addListener(eventName, listener);
  }

  /**
   * Unlistens for a given event from this client.
   *
   * @function removeListener
   */
  removeListener(eventName: string, listener: ListenerFunction) {
    eventBridge.removeListener(eventName, listener);
  }
}

/**
 * Callback function.
 */
type CallbackFunction = (message: string) => void;

/**
 * Listener function.
 */
type ListenerFunction = (...args: any[]) => void;

/**
 * Parameters constituting an identity.
 *
 * @interface IdentityParameters
 */
export type IdentityParameters = {
  /**
   * PEM representating of a CA certificate.
   *
   * @member {string} caCertPem
   */
  caCertPem: string;
  /**
   * PEM representation of a certificate.
   *
   * @member {string} certPem
   */
  certPem: string;
  /**
   * PEM representation of a private key that has signed `certificate`.
   *
   * @member {string} keyPem
   */
  keyPem: string;
};

/**
 * Parameters for connection to an MQTT broker.
 */
export type ConnectionParameters = {
  host: string;
  port: string;
  clientId: string;
};

const defaultInstance = new MqttClient();

export default defaultInstance;
