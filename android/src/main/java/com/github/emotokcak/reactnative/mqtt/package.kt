package com.github.emotokcak.reactnative.mqtt

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType

fun ReadableMap.getRequiredString(key: String): String {
    if (!this.hasKey(key)) {
        throw IllegalArgumentException("this.$key must be specified")
    }
    if (this.getType(key) != ReadableType.String) {
        throw IllegalArgumentException(
            "this.$key must be a string but ${this.getType(key)} was given"
        )
    }
    return this.getString(key)!! // should not be null
}

fun ReadableMap.getRequiredInt(key: String): Int {
    if (!this.hasKey(key)) {
        throw IllegalArgumentException("this.$key must be specified")
    }
    if (this.getType(key) != ReadableType.Number) {
        throw IllegalArgumentException(
            "this.$key must be an integer but ${this.getType(key)} was given"
        )
    }
    return this.getInt(key)
}
