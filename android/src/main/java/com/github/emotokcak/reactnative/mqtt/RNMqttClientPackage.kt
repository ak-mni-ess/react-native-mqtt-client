package com.github.emotokcak.reactnative.mqtt

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class RNMqttClientPackage : ReactPackage {
    override fun createViewManagers(reactContext: ReactApplicationContext):
            List< ViewManager< *, * > >
    {
        return emptyList< ViewManager< *, * > >()
    }

    override fun createNativeModules(reactContext: ReactApplicationContext):
            List< NativeModule >
    {
        return listOf(RNMqttClient(reactContext))
    }
}
