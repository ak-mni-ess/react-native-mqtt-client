# react-native-mqtt-client

MQTT client for React Native

## Installation

```sh
npm install react-native-mqtt-client
```

## Usage

```js
import MqttClient from "react-native-mqtt-client";
```

## iOS Tips

### Pod installation error

You may face an error similar to the following, when you run `pod install`.

```
[!] The following Swift pods cannot yet be integrated as static libraries:

The Swift pod `CocoaMQTT` depends upon `CocoaAsyncSocket`, which does not define modules. To opt into those targets generating module maps (which is necessary to import them from Swift when building as static libraries), you may set `use_modular_headers!` globally in your Podfile, or specify `:modular_headers => true` for particular dependencies.
```

In my case (React Native v0.63.3, CocoaPod v1.9.3), this error was solved by adding the following line to my `Podfile`.

```
pod 'CocoaAsyncSocket', :modular_headers => true
```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
