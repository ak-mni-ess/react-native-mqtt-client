package com.github.emotokcak.reactnative.mqtt

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType

/**
 * Requires a string value from a given `ReadableMap`.
 *
 * @param key
 *
 *   Key associated with the value to be obtained.
 *
 * @throws IllegalArgumentException
 *
 *   If there is no value associated with `key`,
 *   or if the value associated with `key` is not a string.
 */
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

/**
 * Requires an integer value from a given `ReadableMap`.
 *
 * @param key
 *
 *   Key associated with the value to be obtained.
 *
 * @throws IllegalArgumentException
 *
 *   If there is no value associated with `key`,
 *   or if the value associated with `key` is not a number.
 */
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
