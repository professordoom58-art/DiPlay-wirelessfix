package com.shilapi.xcertplay.transport

import java.io.ByteArrayOutputStream

object EspBridgeFrameCodec {
    fun crc16(data: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        for (i in 0 until length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            for (j in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
            }
        }
        return crc and 0xFFFF
    }

    fun encode(type: Byte, seq: Byte, payload: ByteArray): ByteArray {
        val len = payload.size
        val raw = ByteArray(6 + len)
        raw[0] = type
        raw[1] = seq
        raw[2] = (len and 0xFF).toByte()
        raw[3] = ((len shr 8) and 0xFF).toByte()
        System.arraycopy(payload, 0, raw, 4, len)
        val crc = crc16(raw, 4 + len)
        raw[4 + len] = (crc and 0xFF).toByte()
        raw[5 + len] = ((crc shr 8) and 0xFF).toByte()

        val out = ByteArrayOutputStream()
        var codeIndex = 0
        var code: Byte = 1
        val dst = ArrayList<Byte>()
        dst.add(0)

        for (b in raw) {
            if (b == 0.toByte()) {
                dst[codeIndex] = code
                code = 1
                codeIndex = dst.size
                dst.add(0)
            } else {
                dst.add(b)
                code++
                if (code == 0xFF.toByte()) {
                    dst[codeIndex] = code
                    code = 1
                    codeIndex = dst.size
                    dst.add(0)
                }
            }
        }
        dst[codeIndex] = code
        val res = ByteArray(dst.size + 1)
        for (i in dst.indices) res[i] = dst[i]
        res[dst.size] = 0x00
        return res
    }

    fun decode(cobs: ByteArray, length: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        var r = 0
        while (r < length) {
            val code = cobs[r++].toInt() and 0xFF
            if (code == 0) return null
            for (i in 1 until code) {
                if (r >= length) return null
                out.write(cobs[r++].toInt())
            }
            if (code < 0xFF && r < length) {
                out.write(0)
            }
        }
        val raw = out.toByteArray()
        if (raw.size < 6) return null
        val payloadLen = (raw[2].toInt() and 0xFF) or ((raw[3].toInt() and 0xFF) shl 8)
        if (raw.size != 6 + payloadLen) return null
        val rxCrc = (raw[4 + payloadLen].toInt() and 0xFF) or ((raw[5 + payloadLen].toInt() and 0xFF) shl 8)
        val calcCrc = crc16(raw, 4 + payloadLen)
        return if (rxCrc == calcCrc) raw else null
    }
}

