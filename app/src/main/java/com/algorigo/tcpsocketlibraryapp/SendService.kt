package com.algorigo.tcpsocketlibraryapp

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.reactivex.Completable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.*
import java.net.ServerSocket
import java.net.Socket


class SendService : Service() {

    inner class ServiceBinder : Binder() {
        fun getService(): SendService {
            return this@SendService
        }
    }

    private val binder = ServiceBinder()

    private var disposable: Disposable? = null
    private var serverSocket: ServerSocket? = null
    override fun onCreate() {
        super.onCreate()
        startSocket()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
        disposable = null
    }

    fun startSocket() {
        var disposed = false
        disposable = Completable.create { emitter ->
            var socket: Socket
            try {
                serverSocket = ServerSocket(SERVERPORT)
            } catch (e: IOException) {
                Log.e(LOG_TAG, "", e)
            }

            Log.e("!!!", "0000")
            serverSocket?.let {
                while (!disposed) {
                    Log.e("!!!", "1111")
                    socket = it.accept()
                    Log.e("!!!", "2222")

                    try {
                        val outputStream = socket.getOutputStream()
                        val inputStream = socket.getInputStream()

                        Log.e("!!!", "3333")
                        while (socket.isConnected) {
                            inputStream?.let {
                                val length = it.available()
                                if (length > 0) {
                                    val bytes = ByteArray(length)
                                    it.read(bytes)

                                    Log.e("!!!", "read:${bytes.contentToString()}")
                                    if (bytes.size > 0) {
                                        outputStream?.write(
                                            byteArrayOf(
                                                bytes[0],
                                                0xff.toByte()
                                            )
                                        )
                                    }
                                }
                            }
                            Thread.sleep(100)
                        }
                    } catch (e: IOException) {
                        Log.e(LOG_TAG, "server error", e)
                        emitter.onError(e)
                        return@create
                    }
                    Log.e("!!!", "4444")
                }
            }
            emitter.onComplete()
        }
            .subscribeOn(Schedulers.io())
            .doFinally {
                disposed = true
            }
            .subscribe({

            }, {
                Log.e(LOG_TAG, "", it)
            })
    }

    companion object {
        private val LOG_TAG = SendService::class.java.simpleName

        const val SERVERPORT = 9999
    }
}
