import CocoaMQTT
import os

func loadX509Certificate(fromPem: String) -> SecCertificate? {
    let pemContents = fromPem
        .replacingOccurrences(of: "-----BEGIN CERTIFICATE-----", with: "")
        .replacingOccurrences(of: "-----END CERTIFICATE-----", with: "")
    guard let data = NSData.init(base64Encoded: pemContents, options: NSData.Base64DecodingOptions.ignoreUnknownCharacters) else
    {
        return nil
    }
    return SecCertificateCreateWithData(nil, data)
}

func loadPrivateKey(fromPem: String) -> SecKey? {
    let pemContents = fromPem
        .replacingOccurrences(of: "-----BEGIN RSA PRIVATE KEY-----", with: "")
        .replacingOccurrences(of: "-----END RSA PRIVATE KEY-----", with: "")
    guard let data = NSData.init(base64Encoded: pemContents, options: NSData.Base64DecodingOptions.ignoreUnknownCharacters) else
    {
        return nil
    }
    let options: [String: Any] = [
        kSecAttrType as String: kSecAttrKeyTypeRSA,
        kSecAttrKeyClass as String: kSecAttrKeyClassPrivate,
        kSecAttrKeySizeInBits as String: 2048 // supposes that the private key has 2048 bits
    ]
    return SecKeyCreateWithData(data, options as CFDictionary, nil)
}

@objc(MqttClient)
class MqttClient : RCTEventEmitter {
    static let APPLICATION_TAG = "com.github.emoto-kc-ak.react-native-mqtt-client"

    static let ROOT_CERTIFICATE_ATTR_LABEL = "Root certificate of an MQTT broker"

    // certificates for SSL connection.
    var certArray: CFArray?

    var client: CocoaMQTT?

    var hasListeners: Bool = false

    static override func moduleName() -> String! {
        return "MqttClient"
    }

    static override func requiresMainQueueSetup() -> Bool {
        return false
    }

    override func supportedEvents() -> [String] {
        return [
            "connected",
            "disconnected",
            "received-message",
            "got-error"
        ]
    }

    override func startObserving() -> Void {
        self.hasListeners = true
    }

    override func stopObserving() -> Void {
        self.hasListeners = false
    }

    @objc(setIdentity:resolve:reject:)
    func setIdentity(params: NSDictionary, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) -> Void
    {
        let caCertPem: String = RCTConvert.nsString(params["caCertPem"])
        let certPem: String = RCTConvert.nsString(params["certPem"])
        let keyPem: String = RCTConvert.nsString(params["keyPem"])
        guard let caCert = loadX509Certificate(fromPem: caCertPem) else {
            reject("RANGE_ERROR", "invalid CA certificate", nil)
            return
        }
        guard let cert = loadX509Certificate(fromPem: certPem) else {
            reject("RANGE_ERROR", "invalid certificate", nil)
            return
        }
        guard let key = loadPrivateKey(fromPem: keyPem) else {
            reject("RANGE_ERROR", "invalid private key", nil)
            return
        }
        // adds the private key to the keychain
        let addKeyAttrs: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecValueRef as String: key,
            kSecAttrLabel as String: "Private key that signed an MQTT client certificate",
            kSecAttrApplicationTag as String: Self.APPLICATION_TAG
        ]
        var err = SecItemAdd(addKeyAttrs as CFDictionary, nil)
        guard err == errSecSuccess || err == errSecDuplicateItem else {
            reject("INVALID_IDENTITY", "failed to add the private key to the keychain: \(err)", nil)
            return
        }
        // adds the certificate to the keychain
        let addCertAttrs: [String: Any] = [
            kSecClass as String: kSecClassCertificate,
            kSecValueRef as String: cert,
            kSecAttrLabel as String: "Certificate for an MQTT client"
        ]
        err = SecItemAdd(addCertAttrs as CFDictionary, nil)
        guard err == errSecSuccess || err == errSecDuplicateItem else {
            reject("INVALID_IDENTITY", "failed to add the certificate to the keychain: \(err)", nil)
            return
        }
        // adds the root certificate to the keychain
        // TODO: root certificate may be stored in other place,
        //       because it is public information.
        let addCaCertAttrs: [String: Any] = [
            kSecClass as String: kSecClassCertificate,
            kSecValueRef as String: caCert,
            kSecAttrLabel as String: Self.ROOT_CERTIFICATE_ATTR_LABEL
        ]
        err = SecItemAdd(addCaCertAttrs as CFDictionary, nil)
        guard err == errSecSuccess || err == errSecDuplicateItem else {
            reject("INVALID_IDENTITY", "failed to add the root certificate to the keychain: \(err)", nil)
            return
        }
        // obtains the identity
        let queryIdentityAttrs: [String: Any] = [
            kSecClass as String: kSecClassIdentity,
            kSecAttrApplicationTag as String: Self.APPLICATION_TAG,
            kSecReturnRef as String: true
        ]
        let cfDict = queryIdentityAttrs as CFDictionary
        var identity: CFTypeRef?
        err = SecItemCopyMatching(cfDict, &identity)
        guard err == errSecSuccess else {
            reject("INVALID_IDENTITY", "failed to query the keychain for the identity: \(err)", nil)
            return
        }
        guard CFGetTypeID(identity) == SecIdentityGetTypeID() else {
            reject("INVALID_IDENTITY", "failed to query the keychain for the identity: type ID mismatch", nil)
            return
        }
        // remembers the identity and the CA certificate
        self.certArray = [identity!, caCert] as CFArray
        resolve(nil)
    }

    // loads the identity from the key chain.
    func loadIdentity() -> CFArray? {
        // obtains the CA certificate
        let queryCaCertAttrs: [String: Any] = [
            kSecClass as String: kSecClassCertificate,
            kSecAttrLabel as String: Self.ROOT_CERTIFICATE_ATTR_LABEL,
            kSecReturnRef as String: true
        ]
        var caCert: CFTypeRef?
        var err = SecItemCopyMatching(queryCaCertAttrs as CFDictionary, &caCert)
        guard err == errSecSuccess else {
            os_log("MqttClient: failed to query the keychain for the root certificate: %s", "\(err)")
            return nil
        }
        guard CFGetTypeID(caCert) == SecCertificateGetTypeID() else {
            os_log("MqttClient: failed to query the keychain for the root certificate: type ID mismatch")
            return nil
        }
        // obtains the identity
        let queryIdentityAttrs: [String: Any] = [
            kSecClass as String: kSecClassIdentity,
            kSecAttrApplicationTag as String: Self.APPLICATION_TAG,
            kSecReturnRef as String: true
        ]
        var identity: CFTypeRef?
        err = SecItemCopyMatching(queryIdentityAttrs as CFDictionary, &identity)
        guard err == errSecSuccess else {
            os_log("MqttClient: failed to query the keychain for the identity: %s", "\(err)")
            return nil
        }
        guard CFGetTypeID(identity) == SecIdentityGetTypeID() else {
            os_log("MqttClient: failed to query the keychain for the identity: type ID mismatch")
            return nil
        }
        return [identity!, caCert!] as CFArray
    }

    func getIdentity() -> CFArray? {
        return self.certArray ?? self.loadIdentity()
    }

    @objc(connect:resolve:reject:)
    func connect(params: NSDictionary, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) -> Void
    {
        guard let certArray = self.getIdentity() else {
            reject("ERROR_CONFIG", "no identity is configured", nil)
            return
        }
        let host: String = RCTConvert.nsString(params["host"])
        let port: Int = RCTConvert.nsInteger(params["port"])
        let clientId: String = RCTConvert.nsString(params["clientId"])
        self.client = CocoaMQTT(clientID: clientId, host: host, port: UInt16(port))
        self.client!.username = ""
        self.client!.password = ""
        let sslSettings: [String: NSObject] = [
            kCFStreamSSLCertificates as String: certArray
        ]
        self.client!.enableSSL = true
        self.client!.allowUntrustCACertificate = false
        self.client!.sslSettings = sslSettings
        self.client!.keepAlive = 60
        self.client!.delegate = self
        _ = self.client!.connect()
        resolve(nil)
    }

    @objc(disconnect)
    func disconnect() -> Void {
        os_log("MqttClient: disconnecting")
        self.client?.disconnect()
    }

    // https://stackoverflow.com/a/38161889
    func invalidate() -> Void {
        os_log("MqttClient: invalidating")
        self.disconnect()
    }

    @objc(publish:payload:resolve:reject:)
    func publish(topic: String, payload: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) -> Void
    {
        os_log("MqttClient: publishing to %s", topic)
        guard let client = self.client else {
            reject("NO_CONNECTION", "no MQTT connection", nil)
            return
        }
        client.publish(CocoaMQTTMessage(topic: topic, string: payload))
        resolve(nil)
    }

    @objc(subscribe:resolve:reject:)
    func subscribe(topic: String, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) -> Void
    {
        os_log("MqttClient: subscribing %s", topic)
        guard let client = self.client else {
            reject("NO_CONNECTION", "no MQTT connection", nil)
            return
        }
        client.subscribe(topic)
        // TODO: subscription has not been done
        resolve(nil)
    }

    func notifyEvent(eventName: String) -> Void {
        self.notifyEvent(eventName: eventName, arg: nil)
    }

    func notifyEvent(eventName: String, arg: Any?) -> Void {
        if self.hasListeners {
            self.sendEvent(withName: eventName, body: arg)
        }
    }

    func notifyError(code: String, message: String) -> Void {
        let arg: [String: String] = [
            "code": code,
            "message": message
        ]
        self.notifyEvent(eventName: "got-error", arg: arg)
    }
}

extension MqttClient : CocoaMQTTDelegate {
    func mqtt(_ mqtt: CocoaMQTT, didConnectAck ack: CocoaMQTTConnAck) {
        os_log("MqttClient: didConnectAck=%s", "\(ack)")
        if ack == .accept {
            self.notifyEvent(eventName: "connected")
        } else {
            self.notifyError(code: "ERROR_CONNECTION", message: "\(ack)")
        }
    }

    func mqtt(_ mqtt: CocoaMQTT, didStateChangeTo state: CocoaMQTTConnState) {
        os_log("MqttClient: didStateChangeTo=%s", "\(state)")
    }

    func mqtt(_ mqtt: CocoaMQTT, didPublishMessage message: CocoaMQTTMessage, id: UInt16)
    {
        os_log("MqttClient: didPublishMessage=%s", message.string ?? "")
    }

    func mqtt(_ mqtt: CocoaMQTT, didPublishAck id: UInt16) {
        os_log("MqttClient: didPublishAck=%d", id)
    }

    func mqtt(_ mqtt: CocoaMQTT, didReceiveMessage message: CocoaMQTTMessage, id: UInt16)
    {
        os_log("MqttClient: didReceiveMessage=%s", message.string ?? "")
        let event: [String: String] = [
            "topic": message.topic,
            "payload": message.string ?? ""
        ]
        self.notifyEvent(eventName: "received-message", arg: event)
    }

    func mqtt(_ mqtt: CocoaMQTT, didSubscribeTopic topics: [String]) {
        os_log("MqttClient: didSubscribeTopic=%s", "\(topics)")
    }

    func mqtt(_ mqtt: CocoaMQTT, didUnsubscribeTopic topic: String) {
        os_log("MqttClient: didUnsubscribeTopic=%s", topic)
    }

    func mqttDidPing(_ mqtt: CocoaMQTT) {
        os_log("MqttClient: didPing")
    }

    func mqttDidReceivePong(_ mqtt: CocoaMQTT) {
        os_log("MqttClient: didReceivePong")
    }

    func mqttDidDisconnect(_ mqtt: CocoaMQTT, withError err: Error?) {
        os_log("MqttClient: didDisconnect")
        if err != nil {
            self.notifyError(code: "ERROR_CONNECTION", message: "\(err!)")
        } else {
            self.notifyEvent(eventName: "disconnected")
        }
    }
}
