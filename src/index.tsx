import { NativeModules } from 'react-native';

const { MqttClient: MqttClientImpl } = NativeModules;

/**
 * MQTT client.
 */
export class MqttClient {
  /**
   * Connects to an MQTT broker.
   */
  connect(
    options: MqttClientOptions,
    errorCallback?: CallbackFunction,
    successCallback?: CallbackFunction
  ) {
    MqttClientImpl.connect(options, errorCallback, successCallback);
  }

  /**
   * Disconnects from the MQTT broker.
   */
  disconnect() {
    MqttClientImpl.disconnect();
  }

  /**
   * Publishes a given payload to a specified topic.
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
 * Options for `MqttClient`.
 */
export type MqttClientOptions = {
  caCert: string;
  cert: string;
  key: string;
  host: string;
  port: string;
  clientId: string;
};

const defaultInstance = new MqttClient();

export default defaultInstance;
