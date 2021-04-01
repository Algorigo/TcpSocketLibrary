package com.algorigo.tcpsocketlibrary

import android.util.Log
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject

open class TcpSocketCommunication(private val serverIp: String, private val serverPort: Int) {

    private var connection: TcpSocketConnection? = null
    private var connectSubject = BehaviorSubject.create<TcpSocketConnection>()
    private var subscribed = 0

    fun connectObservable(timeout: Int = TIMEOUT_MILLIS, printLog: Boolean = false): Observable<TcpSocketConnection> {
        return connectSubject
            .doOnSubscribe {
                if (subscribed++ == 0) {
                    Observable.create<TcpSocketConnection> { emitter ->
                        try {
                            connection = TcpSocketConnection(serverIp, serverPort, timeout, printLog).also {
                                it.disconnectListener =
                                    object : TcpSocketConnection.OnDisconnectListener {
                                        override fun onDisconnected() {
                                            if (subscribed != 0) {
                                                emitter.onError(TcpSocketConnection.DisconnectedException())
                                            }
                                        }
                                    }
                            }
                            emitter.onNext(connection!!)
                        } catch (e: Exception) {
                            connectSubject.onError(e)
                        }
                    }
                        .subscribeOn(Schedulers.io())
                        .subscribe({
                            connectSubject.onNext(it)
                        }, {
                            Log.e(LOG_TAG, "", it)
                            connectSubject.onError(it)
                        })
                }
            }
            .doOnError {
                connectSubject = BehaviorSubject.create<TcpSocketConnection>()
            }
            .doFinally {
                if (--subscribed == 0) {
                    connection?.close()
                    connection = null
                }
            }
    }

    fun sendDataSingle(byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean): Single<ByteArray> {
        return connection!!.sendDataSingle(byteArray, receiveDataVarifier)
    }

    fun sendDataObservable(byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean): Observable<ByteArray> {
        return connection!!.sendDataObservable(byteArray, receiveDataVarifier)
    }

    fun receiveDataObservable(function: (tcpSocketConnection: TcpSocketConnection, byteArray: ByteArray) -> Single<Boolean>?): Observable<ByteArray> {
        return connection!!.receiveDataObservable(function)
    }

    fun isConnected(): Boolean {
        return connection != null
    }

    companion object {
        private val LOG_TAG = TcpSocketCommunication::class.java.simpleName

        private const val TIMEOUT_MILLIS = 30000
    }
}