package com.algorigo.tcpsocketlibraryapp

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.algorigo.tcpsocketlibrary.TcpSocketCommunication
import com.algorigo.tcpsocketlibrary.TcpSocketConnection
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_send.*
import java.nio.charset.Charset

class SendActivity : AppCompatActivity() {

    private var deviceDisposable: Disposable? = null
    private var disposable: Disposable? = null
    private var tcpSocketConnection: TcpSocketConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)

        if (deviceDisposable == null) {
            deviceDisposable = Rx2ServiceBindingFactory.bind<SendService.ServiceBinder>(
                this,
                Intent(this, SendService::class.java)
            )
                .doFinally {
                    deviceDisposable = null
                }
                .subscribe({
                    Log.e("!!!", "onComplete")
                }, {
                    Log.e(LOG_TAG, "", it)
                })
        }

        connectBtn.setOnClickListener {
            if (disposable == null) {
                disposable = TcpSocketCommunication().connectObservable("localhost", SendService.SERVERPORT)
                    .doOnSubscribe {
                        connectBtn.isEnabled = false
                        disconnectBtn.isEnabled = true
                        sendBtn.isEnabled = true
                    }
                    .doFinally {
                        tcpSocketConnection = null
                        connectBtn.isEnabled = true
                        disconnectBtn.isEnabled = false
                        sendBtn.isEnabled = false
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        tcpSocketConnection = it
                    }, {
                        Log.e(LOG_TAG, "tcp error", it)
                    })
            }
        }
        disconnectBtn.setOnClickListener {
            if (disposable != null) {
                disposable?.dispose()
                disposable = null
            }
        }
        sendBtn.setOnClickListener {
            tcpSocketConnection?.sendDataSingle(sendEdit.text.toString().toByteArray(), {
                true
            })
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe({
                    receiveText.setText(it.contentToString())
                }, {
                    Log.e(LOG_TAG, "send data", it)
                })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        deviceDisposable?.dispose()
        deviceDisposable = null
    }

    companion object {
        private val LOG_TAG = SendActivity::class.java.simpleName
    }
}
