package com.algorigo.tcpsocketlibraryapp

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
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

            serverSocket?.let {
                while (!disposed) {
                    socket = it.accept()

                    var outputStream: OutputStream? = null
                    var inputStream: InputStream? = null
                    try {
                        outputStream = socket.getOutputStream()
                        inputStream = socket.getInputStream()

                        while (socket.isConnected) {
                            inputStream?.let {
                                val length = it.available()
                                if (length > 0) {
                                    val bytes = ByteArray(length)
                                    it.read(bytes)

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
                    } finally {
                        inputStream?.close()
                        outputStream?.close()
                    }
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
