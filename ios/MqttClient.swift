import os

@objc(MqttClient)
class MqttClient: NSObject, RCTInvalidating {
    @objc(connect:errorCallback:successCallback:)
    func connect(options: NSDictionary, errorCallback: RCTResponseSenderBlock, successCallback: RCTResponseSenderBlock) -> Void
    {
        os_log("MqttConnector: connected")
    }

    @objc(disconnect)
    func disconnect() -> Void {
        os_log("MqttConnector: disconnected")
    }

    // https://stackoverflow.com/a/38161889
    func invalidate() -> Void {
        os_log("MqttConnector: invalidating")
        self.disconnect()
    }
}
