package com.algorigo.tcpsocketlibraryapp

import com.algorigo.tcpsocketlibrary.TcpSocketCommunication
import io.reactivex.rxjava3.core.Observable
import java.nio.ByteBuffer

class MatrixSensor48Communication(ip: String, port: Int) : TcpSocketCommunication(ip, port) {

    private val getBytes: ByteArray

    init {
        val buffer = ByteBuffer.allocate(10)
        buffer.put(ByteUtil.getBytesOfInt(10, ByteUtil.Endian.LITTLE))
        buffer.put(ByteUtil.getBytesOfShort(0x0031.toShort(), ByteUtil.Endian.LITTLE))
        buffer.put(ByteUtil.getBytesOfInt(0, ByteUtil.Endian.LITTLE))
        getBytes = buffer.array()
    }

    fun getDataObservable(): Observable<Array<FloatArray>>? {
        return sendDataObservable(getBytes, receiveDataVarifier)
            ?.map {
                val dataArray = Array(48) { FloatArray(24) }
                var idx = 4
                for (y in 0 until 48) {
                    for (x in 0 until 24) {
                        val upperIdx = idx * 2
                        val lowerIdx = upperIdx + 1
                        val upper = it[upperIdx]
                        val lower = it[lowerIdx]
                        dataArray[y][x]  = ByteUtil.getIntOfBytes(upper, lower, ByteUtil.Endian.BIG) / 65535f
                        ++idx
                    }
                }
                dataArray
            }
    }

    val receiveDataVarifier = { byteArray: ByteArray ->
        if (byteArray.size < 4) {
            false
        } else {
            val byteBuffer = ByteBuffer.wrap(byteArray)
            val contentLengthBytes = ByteArray(4)
            byteBuffer.get(contentLengthBytes)
            val contentLength = ByteUtil.getIntOfBytes(contentLengthBytes, ByteUtil.Endian.LITTLE)
            if (contentLength == byteArray.size) {
                true
            } else {
                false
            }
        }
    }

    companion object {
        private val LOG_TAG = MatrixSensor48Communication::class.java.simpleName
    }
}