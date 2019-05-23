package com.algorigo.tcpsocketlibrary

import android.util.Log
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Single
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.SingleEmitter
import io.reactivex.schedulers.Schedulers
import java.io.InputStream
import java.io.OutputStream
import java.lang.IllegalStateException
import java.net.InetAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.TimeoutException

class TcpSocketConnection(serverIp: String, serverPort: Int, timeout: Int = TIMEOUT_MILLIS) {

    class DisconnectedException : RuntimeException("Connection is disposed")
    class CancelCommunication : RuntimeException("Communication is canceled")
    class IllegalModeException : IllegalStateException("Connection mode is wrong")

    interface OnDisconnectListener {
        fun onDisconnected()
    }

    private abstract class SendData(val id: Long, val byteArray: ByteArray, val receiveDataVarifier: (byteArray: ByteArray) -> Boolean, var sentTimeStamp: Long = -1) {
        fun isSent(): Boolean {
            return sentTimeStamp >= 0
        }

        abstract fun emit(byteArray: ByteArray): SendData?
        abstract fun cancel(exception: Exception)
    }

    private class SingleSendData(id: Long, byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean, val emitter: SingleEmitter<ByteArray>, sentTimeStamp: Long = -1)
        : SendData(id, byteArray, receiveDataVarifier, sentTimeStamp) {
        override fun emit(byteArray: ByteArray): SendData? {
            if (!emitter.isDisposed) {
                emitter.onSuccess(byteArray)
            }
            return null
        }

        override fun cancel(exception: Exception) {
            if (!emitter.isDisposed) {
                emitter.onError(exception)
            }
        }
    }

    private class ObservableSendData(id: Long, byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean, val emitter: ObservableEmitter<ByteArray>, sentTimeStamp: Long = -1)
        : SendData(id, byteArray, receiveDataVarifier, sentTimeStamp) {
        override fun emit(byteArray: ByteArray): SendData? {
            if (emitter.isDisposed) {
                return null
            } else {
                emitter.onNext(byteArray)
                return ObservableSendData(id, this.byteArray, receiveDataVarifier, emitter)
            }
        }

        override fun cancel(exception: Exception) {
            if (!emitter.isDisposed) {
                emitter.onError(exception)
            }
        }
    }

    private lateinit var socket: Socket
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val byteBuffer = ByteBuffer.allocate(100000)
    private var bufferSize = 0
    private val sendDataQueue = ArrayDeque<SendData>()
    internal var disconnectListener: OnDisconnectListener? = null

    private var receiveDataVarifier: ((byteArray: ByteArray) -> ByteArray?)? = null
    private val receiveDataRelay = PublishRelay.create<ByteArray>()

    init {
        connect(serverIp, serverPort, timeout)
    }

    constructor(serverIp: String, serverPort: Int, receiveDataVarifier: (byteArray: ByteArray) -> ByteArray?, timeout: Int = TIMEOUT_MILLIS) : this(serverIp, serverPort, timeout) {
        this.receiveDataVarifier = receiveDataVarifier
    }

    private fun connect(serverIp: String, serverPort: Int, timeout: Int) {
        val serverAddr = InetAddress.getByName(serverIp)

        Log.i(LOG_TAG, "socket Connecting...")

        socket = Socket(serverAddr, serverPort).apply {
            soTimeout = timeout
        }

        outputStream = socket.getOutputStream()
        inputStream = socket.getInputStream()

        Thread(Runnable {
            messageHandle()
        }).start()
    }

    private fun messageHandle() {
        if (receiveDataVarifier == null) {
            handleMessageSending()
        } else {
            handleMessageReceiving()
        }
        Log.i(LOG_TAG, "disposed")
        disconnectListener?.onDisconnected()
    }

    private fun handleMessageSending() {
        while (socket.isConnected) {
            try {
                inputStream?.let {
                    val length = it.available()
                    if (length > 0) {
                        val bytes = ByteArray(length)
                        it.read(bytes)
                        byteBuffer.put(bytes)
                        bufferSize += length
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "", e)
                break
            }

            if (sendDataQueue.size > 0) {
                val sendData = sendDataQueue.peek()

                if (sendData.isSent()) {
                    if (System.currentTimeMillis() - sendData.sentTimeStamp > TIMEOUT_MILLIS) {
                        byteBuffer.clear()
                        bufferSize = 0
                        sendDataQueue.pop()
                        sendData.cancel(TimeoutException(""))
                    } else {
                        val byteArray = byteBuffer.array().copyOf(bufferSize)
                        try {
                            if (sendData.receiveDataVarifier(byteArray)) {
                                byteBuffer.clear()
                                bufferSize = 0
                                sendDataQueue.pop()
                                sendData.emit(byteArray)?.let {
                                    sendDataQueue.push(it)
                                }
                            }
                        } catch (e: Exception) {
                            byteBuffer.clear()
                            bufferSize = 0
                            sendDataQueue.pop()
                            sendData.cancel(e)
                        }
                    }
                } else {
                    try {
                        sendData(sendData)
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "", e)
                        break
                    }
                }
            }

            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Log.e(LOG_TAG, "interruptedExcpetion")
            }
        }
    }

    private fun handleMessageReceiving() {
        while (socket.isConnected) {
            try {
                inputStream?.let {
                    val length = it.available()
                    if (length > 0) {
                        val bytes = ByteArray(length)
                        it.read(bytes)
                        byteBuffer.put(bytes)
                        bufferSize += length
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "", e)
                break
            }

            val byteArray = byteBuffer.array().copyOf(bufferSize)
            receiveDataVarifier!!(byteArray)?.let {
                byteBuffer.clear()
                bufferSize = 0
                receiveDataRelay.accept(byteArray)
                sendDataSingleInner(it, {
                    true
                })
                    .subscribe({
                    }, {
                        Log.e(LOG_TAG, "", it)
                    })
            }

            if (sendDataQueue.size > 0) {
                val sendData = sendDataQueue.peek()

                try {
                    sendData(sendData)
                    sendDataQueue.remove(sendData)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "", e)
                    break
                }
            }

            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Log.e(LOG_TAG, "interruptedExcpetion")
            }
        }
    }

    fun close() {
        try {
            socket.close()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "", e)
        } finally {
            inputStream?.close()
            outputStream?.close()
            inputStream = null
            outputStream = null
        }

        var iter = sendDataQueue.iterator()
        while (iter.hasNext()) {
            val sendData = iter.next()
            sendData.cancel(DisconnectedException())
            iter.remove()
        }
    }

    private fun sendData(sendData: SendData) {
        sendData.sentTimeStamp = System.currentTimeMillis()
        outputStream?.write(sendData.byteArray)
    }

    private fun cancelData(id: Long) {
        for (sendData in sendDataQueue) {
            if (sendData.id == id) {
                if (sendData.isSent()) {
                    sendData.cancel(CancelCommunication())
                }
                sendDataQueue.remove(sendData)
                break
            }
        }
    }

    fun sendDataSingle(byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean): Single<ByteArray> {
        if (this.receiveDataVarifier == null) {
            return sendDataSingleInner(byteArray, receiveDataVarifier)
        } else {
            return Single.error(IllegalModeException())
        }
    }

    private fun sendDataSingleInner(byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean): Single<ByteArray> {
        var id = 0L
        return Single.create<ByteArray> {
            id = generateId()
            sendDataQueue.push(SingleSendData(id, byteArray, receiveDataVarifier, it))
        }
            .doFinally {
                cancelData(id)
            }
            .subscribeOn(Schedulers.io())
    }

    fun sendDataObservable(byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean): Observable<ByteArray> {
        if (this.receiveDataVarifier == null) {
            return sendDataObservableInner(byteArray, receiveDataVarifier)
        } else {
            return Observable.error(IllegalModeException())
        }
    }

    private fun sendDataObservableInner(byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean): Observable<ByteArray> {
        var id = 0L
        return Observable.create<ByteArray> {
            id = generateId()
            sendDataQueue.push(ObservableSendData(id, byteArray, receiveDataVarifier, it))
        }
            .doFinally {
                cancelData(id)
            }
            .subscribeOn(Schedulers.io())
    }

    fun receiveDataObservable(): Observable<ByteArray> {
        if (this.receiveDataVarifier != null) {
            return receiveDataRelay
        } else {
            return Observable.error(IllegalModeException())
        }
    }

    private fun generateId(): Long {
        return Math.round(Math.random() * Long.MAX_VALUE)
    }

    companion object {
        private const val TIMEOUT_MILLIS = 30000
        private val LOG_TAG = TcpSocketConnection::class.java.simpleName
    }
}