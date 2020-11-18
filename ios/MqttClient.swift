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
    static let DEFAULT_KEY_APPLICATION_TAG = "com.github.emoto-kc-ak.react-native-mqtt-client"

    static let DEFAULT_CA_CERT_LABEL = "Root certificate of an MQTT broker"

    static let DEFAULT_CERT_LABEL = "Certificate for an MQTT client"

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
        let keyStoreOptions = RCTConvert.nsDictionary(params["keyStoreOptions"])
        let caCertLabel: String = RCTConvert.nsString(keyStoreOptions?["caCertLabel"]) ?? Self.DEFAULT_CA_CERT_LABEL
        let certLabel: String = RCTConvert.nsString(keyStoreOptions?["certLabel"]) ?? Self.DEFAULT_CERT_LABEL
        let keyApplicationTag: String = RCTConvert.nsString(keyStoreOptions?["keyApplicationTag"]) ?? Self.DEFAULT_KEY_APPLICATION_TAG
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
            kSecAttrApplicationTag as String: keyApplicationTag
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
            kSecAttrLabel as String: certLabel
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
            kSecAttrLabel as String: caCertLabel
        ]
        err = SecItemAdd(addCaCertAttrs as CFDictionary, nil)
        guard err == errSecSuccess || err == errSecDuplicateItem else {
            reject("INVALID_IDENTITY", "failed to add the root certificate to the keychain: \(err)", nil)
            return
        }
        // obtains the identity
        let queryIdentityAttrs: [String: Any] = [
            kSecClass as String: kSecClassIdentity,
            kSecAttrApplicationTag as String: keyApplicationTag,
            kSecReturnRef as String: true
        ]
        var identity: CFTypeRef?
        err = SecItemCopyMatching(queryIdentityAttrs as CFDictionary, &identity)
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

    @objc(resetIdentity:resolve:reject:)
    func resetIdentity(options: NSDictionary?, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) -> Void
    {
        let caCertLabel: String = RCTConvert.nsString(options?["caCertLabel"]) ?? Self.DEFAULT_CA_CERT_LABEL
        let certLabel: String = RCTConvert.nsString(options?["certLabel"]) ?? Self.DEFAULT_CERT_LABEL
        let keyApplicationTag: String = RCTConvert.nsString(options?["keyApplicationTag"]) ?? Self.DEFAULT_KEY_APPLICATION_TAG
        // deletes a root certificate
        let queryCaCertAttrs: [String: Any] = [
            kSecClass as String: kSecClassCertificate,
            kSecAttrLabel as String: caCertLabel
        ]
        var err = SecItemDelete(queryCaCertAttrs as CFDictionary)
        guard err == errSecSuccess || err == errSecItemNotFound else {
            reject("ILLEGAL_STATE", "failed to delete a root certificate: \(err)", nil)
            return
        }
        // deletes a client certificate
        let queryCertAttrs: [String: Any] = [
            kSecClass as String: kSecClassCertificate,
            kSecAttrLabel as String: certLabel
        ]
        err = SecItemDelete(queryCertAttrs as CFDictionary)
        guard err == errSecSuccess || err == errSecItemNotFound else {
            reject("ILLEGAL_STATE", "failed to delete a certificate: \(err)", nil)
            return
        }
        // deletes a private key
        let queryKeyAttrs: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: keyApplicationTag
        ]
        err = SecItemDelete(queryKeyAttrs as CFDictionary)
        guard err == errSecSuccess || err == errSecItemNotFound else {
            reject("ILLEGAL_STATE", "failed to delete a private key: \(err)", nil)
            return
        }
        resolve(nil)
    }

    func getIdentity() -> CFArray? {
        // default identity is no longer provided
        return self.certArray
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
