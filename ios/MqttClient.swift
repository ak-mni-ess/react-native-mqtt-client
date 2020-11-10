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
class MqttClient: NSObject, RCTBridgeModule, RCTInvalidating {
    static let APPLICATION_TAG = "com.github.emoto-kc-ak.react-native-mqtt-client"

    var client: CocoaMQTT?

    static func moduleName() -> String! {
        return "MqttClient"
    }

    static func requiresMainQueueSetup() -> Bool {
        return false
    }

    @objc(connect:errorCallback:successCallback:)
    func connect(options: NSDictionary, errorCallback: RCTResponseSenderBlock, successCallback: RCTResponseSenderBlock) -> Void
    {
        let caCertPem: String = RCTConvert.nsString(options["caCert"])
        let certPem: String = RCTConvert.nsString(options["cert"])
        let keyPem: String = RCTConvert.nsString(options["key"])
        let host: String = RCTConvert.nsString(options["host"])
        let port: Int = RCTConvert.nsInteger(options["port"])
        let clientId: String = RCTConvert.nsString(options["clientId"])
        guard let caCert = loadX509Certificate(fromPem: caCertPem) else {
            errorCallback(["invalid CA certificate"])
            return
        }
        guard let cert = loadX509Certificate(fromPem: certPem) else {
            errorCallback(["invalid certificate"])
            return
        }
        guard let key = loadPrivateKey(fromPem: keyPem) else {
            errorCallback(["invalid private key"])
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
            errorCallback(["failed to add the private key to the keychain: " + String(err)])
            return
        }
        // checks the private key
        let queryKeyAttrs: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: Self.APPLICATION_TAG,
            kSecReturnAttributes as String: true,
            kSecReturnRef as String: true
        ]
        var queryKeyResult: CFTypeRef?
        err = SecItemCopyMatching(queryKeyAttrs as CFDictionary, &queryKeyResult)
        guard err == errSecSuccess else {
            errorCallback(["failed to query the keychain for the private key" + String(err)])
            return
        }
        guard let keyAttrMap = queryKeyResult as? [String: Any] else {
            errorCallback(["failed to extract private key query results"])
            return
        }
        guard let appLabel = keyAttrMap[kSecAttrApplicationLabel as String] as? Data else
        {
            errorCallback(["failed to extract the public key hash of the private key: " + (kSecAttrApplicationLabel as String)])
            return
        }
        os_log("MqttClient: public key hash=%s", appLabel.base64EncodedString())
        // adds the certificate to the keychain
        let addCertAttrs: [String: Any] = [
            kSecClass as String: kSecClassCertificate,
            kSecValueRef as String: cert,
            kSecAttrLabel as String: "Certificate for an MQTT client"
        ]
        err = SecItemAdd(addCertAttrs as CFDictionary, nil)
        guard err == errSecSuccess || err == errSecDuplicateItem else {
            errorCallback(["failed to add the certificate to the keychain" + String(err)])
            return
        }
        // checks the certificate
        let queryCertAttrs: [String: Any] = [
            kSecClass as String: kSecClassCertificate,
            kSecAttrLabel as String: "Certificate for an MQTT client",
            kSecReturnAttributes as String: true,
            kSecReturnRef as String: true
        ]
        var queryCertResult: CFTypeRef?
        err = SecItemCopyMatching(queryCertAttrs as CFDictionary, &queryCertResult)
        guard err == errSecSuccess else {
            errorCallback(["failed to query the keychain for the certificate: " + String(err)])
            return
        }
        guard let certAttrMap = queryCertResult as? [String: Any] else {
            errorCallback(["failed to extract certificate query results"])
            return
        }
        guard let pubKey = certAttrMap[kSecAttrPublicKeyHash as String] as? Data else
        {
            errorCallback(["failed to extract the public key has of the certificate" + (kSecAttrPublicKeyHash as String)])
            return
        }
        os_log("MqttConnector: public key hash=%s", pubKey.base64EncodedString())
        // obtains the identity
        let queryIdentityAttrs: [String: Any] = [
            kSecClass as String: kSecClassIdentity,
            kSecAttrApplicationTag as String: Self.APPLICATION_TAG,
            kSecReturnRef as String: true
        ]
        let cfDict = queryIdentityAttrs as CFDictionary
        var identityRef: CFTypeRef?
        err = SecItemCopyMatching(cfDict, &identityRef)
        guard err == errSecSuccess else {
            errorCallback(["failed to query the keychain for the identity: " + String(err)])
            return
        }
        guard CFGetTypeID(identityRef) == SecIdentityGetTypeID() else {
            errorCallback(["failed to query the keychain for the identity: type ID mismatch"])
            return
        }
        let identity = identityRef as! SecIdentity
        // configures an MQTT client
        let certArray = [identity, caCert] as CFArray
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
        os_log("MqttConnector: connected")
    }

    @objc(disconnect)
    func disconnect() -> Void {
        self.client?.disconnect()
        os_log("MqttConnector: disconnected")
    }

    // https://stackoverflow.com/a/38161889
    func invalidate() -> Void {
        os_log("MqttConnector: invalidating")
        self.disconnect()
    }
}

extension MqttClient : CocoaMQTTDelegate {
    func mqtt(_ mqtt: CocoaMQTT, didConnectAck ack: CocoaMQTTConnAck) {
        os_log("MqttClient: didConnectAck=%s", "\(ack)")
        mqtt.subscribe("sample-topic/test", qos: CocoaMQTTQOS.qos1)
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
    }

    func mqtt(_ mqtt: CocoaMQTT, didSubscribeTopic topics: [String]) {
        os_log("MqttClient: didSubscribeTopic=%s", "\(topics)")
        mqtt.publish(CocoaMQTTMessage(topic:"sample-topic/test", string: "{\"co2\":1000}"))
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
    }
}
