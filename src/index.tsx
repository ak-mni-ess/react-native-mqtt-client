import { NativeModules } from 'react-native';

type MqttClientType = {
  multiply(a: number, b: number): Promise<number>;
};

const { MqttClient } = NativeModules;

export default MqttClient as MqttClientType;
