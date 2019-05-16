package com.algorigo.tcpsocketlibraryapp

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

class ReceiveService : Service() {

    inner class ServiceBinder : Binder() {
        fun getService(): ReceiveService {
            return this@ReceiveService
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

                    var outputDisposable: Disposable? = null
                    var outputStream: OutputStream? = null
                    var inputStream: InputStream? = null
                    try {
                        outputStream = socket.getOutputStream()
                        inputStream = socket.getInputStream()

                        outputDisposable = Observable.interval(5, TimeUnit.SECONDS)
                            .map {
                                outputStream?.write(
                                    byteArrayOf(
                                        if (it%2 == 0L) {
                                            0xff.toByte()
                                        } else {
                                            0x00.toByte()
                                        }
                                    )
                                )
                                true
                            }
                            .doFinally {
                                outputDisposable = null
                            }
                            .subscribe({
                            }, {
                                Log.e(LOG_TAG, "", it)
                            })

                        while (socket.isConnected && outputDisposable != null) {
                            inputStream?.let {
                                val length = it.available()
                                if (length > 0) {
                                    val bytes = ByteArray(length)
                                    it.read(bytes)
                                }
                            }
                            Thread.sleep(100)
                        }
                    } catch (e: IOException) {
                        Log.e(LOG_TAG, "ReceiveService server error", e)
                        emitter.onError(e)
                        return@create
                    } finally {
                        inputStream?.close()
                        outputStream?.close()
                        outputDisposable?.dispose()
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

        const val SERVERPORT = 9998
    }
}
