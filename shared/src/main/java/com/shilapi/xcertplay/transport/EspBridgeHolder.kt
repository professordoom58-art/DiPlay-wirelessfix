package com.shilapi.xcertplay.transport

object EspBridgeHolder {
    @Volatile
    var client: EspBridgeClient? = null
    var isEnabled: Boolean = true
}

