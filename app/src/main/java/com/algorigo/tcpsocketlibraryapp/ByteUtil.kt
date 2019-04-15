package com.algorigo.tcpsocketlibraryapp

import android.util.Log
import java.nio.ByteBuffer

object ByteUtil {

    enum class Endian {
        BIG,
        LITTLE,
    }

    fun getIntOfBytes(upper: Byte, lower: Byte, endian: Endian = ByteUtil.Endian.BIG): Int {
        when (endian) {
            ByteUtil.Endian.BIG -> {
                return ByteBuffer.wrap(byteArrayOf(0x00.toByte(), 0x00.toByte(), upper, lower)).int
            }
            ByteUtil.Endian.LITTLE -> {
                return ByteBuffer.wrap(byteArrayOf(lower, upper, 0x00.toByte(), 0x00.toByte())).int
            }
        }
    }

    fun getIntOfBytes(byteArray: ByteArray, endian: Endian = ByteUtil.Endian.BIG): Int {
        when (endian) {
            ByteUtil.Endian.BIG -> {
                return ByteBuffer.wrap(byteArrayOf(byteArray[0], byteArray[1], byteArray[2], byteArray[3])).int
            }
            ByteUtil.Endian.LITTLE -> {
                return ByteBuffer.wrap(byteArrayOf(byteArray[3], byteArray[2], byteArray[1], byteArray[0])).int
            }
        }
    }

    fun getBytesOfInt(value: Int, endian: Endian = ByteUtil.Endian.BIG): ByteArray {
        when (endian) {
            ByteUtil.Endian.BIG -> {
                return byteArrayOf((value shr 24 and 0xff).toByte(), (value shr 16 and 0xff).toByte(), (value shr 8 and 0xff).toByte(), (value and 0xff).toByte())
            }
            ByteUtil.Endian.LITTLE -> {
                return byteArrayOf((value and 0xff).toByte(), (value shr 8 and 0xff).toByte(), (value shr 16 and 0xff).toByte(), (value shr 24 and 0xff).toByte())
            }
        }
    }

    fun getBytesOfShort(value: Short, endian: Endian = ByteUtil.Endian.BIG): ByteArray {
        when (endian) {
            ByteUtil.Endian.BIG -> {
                return byteArrayOf((value.toInt() shr 8 and 0xff).toByte(), (value.toInt() and 0xff).toByte())
            }
            ByteUtil.Endian.LITTLE -> {
                return byteArrayOf((value.toInt() and 0xff).toByte(), (value.toInt() shr 8 and 0xff).toByte())
            }
        }
    }
}