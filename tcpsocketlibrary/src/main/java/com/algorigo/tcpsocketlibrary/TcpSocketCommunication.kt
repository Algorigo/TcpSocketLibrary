package com.algorigo.tcpsocketlibrary

import android.util.Log
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject

open class TcpSocketCommunication(private val serverIp: String, private val serverPort: Int, private val receiveDataVarifier: ((byteArray: ByteArray) -> ByteArray?)? = null) {

    private var connection: TcpSocketConnection? = null
    private var connectSubject = BehaviorSubject.create<TcpSocketConnection>()
    private var subscribed = 0

    fun connectObservable(): Observable<TcpSocketConnection> {
        return connectSubject
            .doOnSubscribe {
                if (subscribed++ == 0) {
                    connectInner()
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

    private fun connectInner() {
        if (connection == null) {
            Observable.create<TcpSocketConnection> { emitter ->
                try {
                    connection = if (receiveDataVarifier == null) {
                        TcpSocketConnection(serverIp, serverPort)
                    } else {
                        TcpSocketConnection(serverIp, serverPort, receiveDataVarifier)
                    }.also {
                        emitter.onNext(it)
                        it.disconnectListener =
                            object : TcpSocketConnection.OnDisconnectListener {
                                override fun onDisconnected() {
                                    if (subscribed != 0) {
                                        emitter.onError(TcpSocketConnection.DisconnectedException())
                                    }
                                }
                            }
                    }
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

    fun sendDataSingle(byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean): Single<ByteArray>? {
        return connection?.sendDataSingle(byteArray, receiveDataVarifier)
    }

    fun sendDataObservable(byteArray: ByteArray, receiveDataVarifier: (byteArray: ByteArray) -> Boolean): Observable<ByteArray>? {
        return connection?.sendDataObservable(byteArray, receiveDataVarifier)
    }

    fun isConnected(): Boolean {
        return connection != null
    }

    companion object {
        private val LOG_TAG = TcpSocketCommunication::class.java.simpleName
    }
}