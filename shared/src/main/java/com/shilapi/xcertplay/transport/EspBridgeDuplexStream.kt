package com.shilapi.xcertplay.transport

import java.io.IOException

class EspBridgeDuplexStream(
    private val client: EspBridgeClient
) : BlockingDuplexByteStream {

    override fun send(data: ByteArray) {
        client.send(0x30.toByte(), data)
    }

    override fun recv(maxBytes: Int, timeoutMs: Long): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val data = client.incomingData.poll()
            if (data != null) {
                return if (data.size <= maxBytes) {
                    data
                } else {
                    val slice = ByteArray(maxBytes)
                    System.arraycopy(data, 0, slice, 0, maxBytes)
                    slice
                }
            }
            try {
                Thread.sleep(10)
            } catch (_: InterruptedException) {
                break
            }
        }
        return null
    }

    override fun close() {
        client.send(0x23.toByte(), ByteArray(0)) // DISCONNECT
    }
}
