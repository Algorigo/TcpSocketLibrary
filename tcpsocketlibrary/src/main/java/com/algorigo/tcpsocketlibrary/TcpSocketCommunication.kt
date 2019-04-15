package com.algorigo.tcpsocketlibrary

import android.util.Log
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject

open class TcpSocketCommunication {

    private var connection: TcpSocketConnection? = null
    private var connectSubject = BehaviorSubject.create<TcpSocketConnection>()
    private var subscribed = 0

    fun connectObservable(serverIp: String, serverPort: Int): Observable<TcpSocketConnection> {
        Observable.create<TcpSocketConnection> {
            if (connection != null) {
                it.onNext(connection!!)
            } else {
                try {
                    connection = TcpSocketConnection(serverIp, serverPort).also { connection ->
                        it.onNext(connection)
                        connection.disconnectListener = object : TcpSocketConnection.OnDisconnectListener {
                            override fun onDisconnected() {
                                if (subscribed != 0) {
                                    it.onError(TcpSocketConnection.DisconnectedException())
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    connectSubject.onError(e)
                }
            }
        }
            .subscribeOn(Schedulers.io())
            .subscribe({
                connectSubject.onNext(it)
            }, {
                Log.e(LOG_TAG, "", it)
                connectSubject.onError(it)
            })

        return connectSubject
            .doOnSubscribe {
                subscribed++
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