package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EspBridgeClient(
    private val port: UsbSerialPort,
    private val connection: UsbDeviceConnection
) {
    var localMac: String = "00:00:00:00:00:00"
    private var isConnected = false
    private var seq: Byte = 0
    val incomingData = ConcurrentLinkedQueue<ByteArray>()
    private var linkUpLatch: CountDownLatch? = null

    fun start() {
        port.open(connection)
        try {
            port.dtr = false
            port.rts = false
        } catch (_: Exception) {}
        port.setParameters(921600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

        Thread { readLoop() }.start()
        send(0x01, ByteArray(0)) // HELLO_REQ
    }

    fun connectDevice(mac: ByteArray): Boolean {
        linkUpLatch = CountDownLatch(1)
        send(0x20, mac) // CONNECT frame
        return linkUpLatch?.await(6, TimeUnit.SECONDS) == true
    }

    fun send(type: Byte, payload: ByteArray) {
        val frame = EspBridgeFrameCodec.encode(type, seq++, payload)
        try {
            port.write(frame, 1000)
        } catch (_: IOException) {}
    }

    private fun readLoop() {
        val buf = ByteArray(1024)
        val cobsBuf = ByteArray(2048)
        var cobsLen = 0

        while (true) {
            try {
                val len = port.read(buf, 200)
                if (len > 0) {
                    for (i in 0 until len) {
                        val b = buf[i]
                        if (b == 0x00.toByte()) {
                            if (cobsLen > 0) {
                                val frame = EspBridgeFrameCodec.decode(cobsBuf, cobsLen)
                                if (frame != null) handleFrame(frame)
                                cobsLen = 0
                            }
                        } else {
                            if (cobsLen < cobsBuf.size) cobsBuf[cobsLen++] = b
                            else cobsLen = 0
                        }
                    }
                }
            } catch (e: Exception) {
                break
            }
        }
    }

    private fun handleFrame(raw: ByteArray) {
        val type = raw[0]
        val payloadLen = (raw[2].toInt() and 0xFF) or ((raw[3].toInt() and 0xFF) shl 8)
        val payload = ByteArray(payloadLen)
        System.arraycopy(raw, 4, payload, 0, payloadLen)

        when (type) {
            0x01.toByte() -> { // HELLO
                if (payloadLen >= 8) {
                    localMac = String.format(
                        "%02X:%02X:%02X:%02X:%02X:%02X",
                        payload[2], payload[3], payload[4], payload[5], payload[6], payload[7]
                    )
                }
            }
            0x21.toByte() -> { // LINK_UP
                isConnected = true
                linkUpLatch?.countDown()
            }
            0x30.toByte() -> { // DATA
                incomingData.add(payload)
            }
        }
    }

    fun close() {
        try {
            port.close()
        } catch (_: Exception) {}
    }
}
