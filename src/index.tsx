import { NativeModules } from 'react-native';
// no type declaration for EventEmitter
// @ts-ignore
import EventEmitter from 'react-native/Libraries/vendor/emitter/EventEmitter';

const { MqttClient: MqttClientImpl } = NativeModules;

/**
 * MQTT client.
 *
 * @class MqttClient
 */
export class MqttClient extends EventEmitter {
  constructor() {
    super();
  }

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
  setIdentity(params: IdentityParameters) {
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
}

/**
 * Callback function.
 */
type CallbackFunction = (message: string) => void;

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
