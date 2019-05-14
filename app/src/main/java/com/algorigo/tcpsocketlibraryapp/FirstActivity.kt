package com.algorigo.tcpsocketlibraryapp

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_first.*

class FirstActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first)

        mainBtn.setOnClickListener {
            Intent(this, MainActivity::class.java).also {
                startActivity(it)
            }
        }
        sendBtn.setOnClickListener {
            Intent(this, SendActivity::class.java).also {
                startActivity(it)
            }
        }
        receiveBtn.setOnClickListener {
            Intent(this, ReceiveActivity::class.java).also {
                startActivity(it)
            }
        }
    }
}
