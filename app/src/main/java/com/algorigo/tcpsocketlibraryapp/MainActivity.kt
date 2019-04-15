package com.algorigo.tcpsocketlibraryapp

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var ms48Communication: MatrixSensor48Communication? = null
    private var tcpDisposable: Disposable? = null
    private var getDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectBtn.setOnClickListener {
            val ip = ipEdit.text.toString()
            val port = portEdit.text.toString().toInt()
            if (!Regex(IP_PATTERN).containsMatchIn(ip)) {
                Log.e(LOG_TAG, "ip is not match pattern $IP_PATTERN")
                return@setOnClickListener
            }
            if (port < 1 && port > 65535) {
                Log.e(LOG_TAG, "port is not between 1 and 65535")
                return@setOnClickListener
            }
            connectWithTcp(ip, port)
        }
        disconnectBtn.setOnClickListener {
            disconnectTcp()
        }
        startGetBtn.setOnClickListener {
            startGet()
        }
        stopGetBtn.setOnClickListener {
            stopGet()
        }
    }

    override fun onStop() {
        super.onStop()
        disconnectTcp()
    }

    private fun connectWithTcp(ip: String, port: Int) {
        if (ms48Communication == null) {
            ms48Communication = MatrixSensor48Communication()
        }
        if (tcpDisposable == null) {
            tcpDisposable = ms48Communication
                    ?.connectObservable(ip, port)
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.doFinally {
                        onDisconnected()
                        tcpDisposable = null
                    }
                    ?.subscribe({
                        Log.e(LOG_TAG, "connection:$it")
                        onConnected()
                    }, {
                        Log.e(LOG_TAG, "", it)
                    })
        }
    }

    private fun disconnectTcp() {
        tcpDisposable?.let {
            it.dispose()
            tcpDisposable = null
        }
    }

    private fun startGet() {
        startGetBtn.visibility = View.GONE
        stopGetBtn.visibility = View.VISIBLE
        getDisposable = ms48Communication
                ?.getDataObservable()
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.doFinally {
                    getDisposable = null
                    if (ms48Communication?.isConnected() == true) {
                        startGetBtn.visibility = View.VISIBLE
                        stopGetBtn.visibility = View.GONE
                    }
                }
                ?.subscribe({
                    sensorView.sensorData = it
                    sensorView.invalidate()
                }, {
                    Log.e(LOG_TAG, "", it)
                })
    }

    private fun stopGet() {
        getDisposable?.dispose()
        getDisposable = null
    }

    private fun onConnected() {
        connectBtn.visibility = View.GONE
        disconnectBtn.visibility = View.VISIBLE
        startGetBtn.visibility = View.VISIBLE
        sensorView.visibility = View.VISIBLE
    }

    private fun onDisconnected() {
        connectBtn.visibility = View.VISIBLE
        disconnectBtn.visibility = View.GONE
        startGetBtn.visibility = View.GONE
        stopGetBtn.visibility = View.GONE
        sensorView.visibility = View.GONE
    }

    companion object {
        private const val IP_PATTERN = "^\\d{1,3}.\\d{1,3}.\\d{1,3}.\\d{1,3}$"
        private val LOG_TAG = MainActivity::class.java.simpleName
    }
}
