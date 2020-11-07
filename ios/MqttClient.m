#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(MqttClient, NSObject)

RCT_EXTERN_METHOD(connect:(NSDictionary*)options errorCallback:(RCTResponseSenderBlock)errorCallback successCallback:(RCTResponseSenderBlock)successCallback)

RCT_EXTERN_METHOD(disconnect)

@end
