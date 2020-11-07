@objc(MqttClient)
class MqttClient: NSObject {
    let logger = Logger()

    @objc(connect:options:errorCallback:successCallback:)
    func connect(options: NSDictionary, errorCallback: RCTResponseSenderBlock, successCallback: RCTResponseSenderBlock) -> Void
    {
        logger.debug("MqttConnector: connected")
    }

    @objc(disconnect)
    func disconnect() -> Void {
        logger.debug("MqttConnector: disconnected")
    }
}
