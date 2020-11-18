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
   * Resets the identity for connection.
   *
   * Certificates and a key stored in a device specific key store are cleared.
   *
   * @function resetIdentity
   *
   * @param options
   *
   *   Options for the identity key store.
   *
   * @return Promise<void>
   *
   *   Resolved when the identity is reset.
   */
  resetIdentity(options?: KeyStoreOptions): Promise<void> {
    return MqttClientImpl.resetIdentity(options);
  }

  /**
   * Connects to an MQTT broker.
   *
   * @function connect
   *
   * @param params
   *
   * @return Promise<void>
   *
   *   Resolved when connection has been established.
   */
  connect(params: ConnectionParameters): Promise<void> {
    return MqttClientImpl.connect(params);
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
   *
   * @param topic
   *
   * @param payload
   *
   * @return Promise<void>
   *
   *   Resolved when publishing has finished.
   */
  publish(topic: string, payload: string): Promise<void> {
    return MqttClientImpl.publish(topic, payload);
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
  /**
   * Options for an identity key store.
   *
   * @member {KeyStoreOptions} keyStoreOptions
   */
  keyStoreOptions?: KeyStoreOptions;
};

/**
 * Options for an identity key store.
 */
export type KeyStoreOptions = {
  /**
   * Alias for a root certificate (Android only).
   *
   * A default value is used if omitted.
   *
   * @member {string} caCertTag
   */
  caCertAlias?: string;
  /**
   * Alias for a private key (Android only).
   *
   * A default value is used if omitted.
   *
   * @member {string} keyTag
   */
  keyAlias?: string;
  /**
   * Label associated with a certificate (iOS only).
   *
   * A default value is used if omitted.
   *
   * @member {string} caCertLabel
   */
  caCertLabel?: string;
  /**
   * Label associated with a certificate (iOS only).
   *
   * A default value is used if omitted.
   *
   * @member {string} certLabel
   */
  certLabel?: string;
  /**
   * Application tag associated with a private key (iOS only).
   *
   * A default value is used if omitted.
   *
   * @member {string} keyApplicationTag
   */
  keyApplicationTag?: string;
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
