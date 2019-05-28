package com.algorigo.tcpsocketlibraryapp

import Rx2ServiceBindingFactory
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.algorigo.tcpsocketlibrary.TcpSocketCommunication
import com.algorigo.tcpsocketlibrary.TcpSocketConnection
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_receive.*

class ReceiveActivity : AppCompatActivity() {

    private var deviceDisposable: Disposable? = null
    private var disposable: Disposable? = null
    private var tcpSocketConnection: TcpSocketConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive)

        connectBtn.setOnClickListener {
            if (disposable == null) {
                disposable = TcpSocketCommunication("localhost", ReceiveService.SERVERPORT)
                    .connectObservable()
                    .doOnSubscribe {
                        connectBtn.isEnabled = false
                        disconnectBtn.isEnabled = true
                    }
                    .doFinally {
                        tcpSocketConnection = null
                        connectBtn.isEnabled = true
                        disconnectBtn.isEnabled = false
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        tcpSocketConnection = it.apply {
                            receiveDataObservable({ connection, byteArray ->
                                if (byteArray.size > 0) {
                                    when (byteArray[0]) {
                                        0x00.toByte() -> {
                                            connection.sendDataSingle(byteArrayOf(0xff.toByte()), {
                                                true
                                            })
                                                .map {
                                                    true
                                                }
                                        }
                                        0xff.toByte() -> {
                                            connection.sendDataSingle(byteArrayOf(0x00.toByte()), {
                                                true
                                            })
                                                .map {
                                                    true
                                                }
                                        }
                                        else -> {
                                            null
                                        }
                                    }
                                } else {
                                    null
                                }
                            })
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({
                                    receiveText.text = it.contentToString()
                                }, {
                                    Log.e(LOG_TAG, "", it)
                                })
                        }
                    }, {
                        Log.e(LOG_TAG, "ReceiveActivity tcp error", it)
                    })
            }
        }
        disconnectBtn.setOnClickListener {
            if (disposable != null) {
                disposable?.dispose()
                disposable = null
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (deviceDisposable == null) {
            deviceDisposable = Rx2ServiceBindingFactory.bind<ReceiveService.ServiceBinder>(
                this,
                Intent(this, ReceiveService::class.java)
            )
                .doFinally {
                    deviceDisposable = null
                }
                .subscribe({
                }, {
                    Log.e(LOG_TAG, "", it)
                })
        }
    }

    override fun onStop() {
        super.onStop()
        deviceDisposable?.dispose()
        deviceDisposable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
        disposable = null
    }

    companion object {
        private val LOG_TAG = ReceiveActivity::class.java.simpleName
    }
}
