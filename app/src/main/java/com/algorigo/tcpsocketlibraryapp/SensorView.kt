package com.algorigo.tcpsocketlibraryapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class SensorView(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : View(context, attrs, defStyleAttr) {

    constructor(context: Context?) : this(context, null, 0)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)

    var sensorData: Array<FloatArray>? = null

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        sensorData?.let {
            val paint = Paint()
            for (x in 0 until it.size) {
                for (y in 0 until it.get(x).size) {
                    val value = Math.round(it.get(x)[y] * 255)
                    paint.color = Color.rgb(value, value, value)
                    val l = width.toFloat() * x / it.size
                    val r = width.toFloat() * (x+1) / it.size
                    val t = height.toFloat() * y / it.get(x).size
                    val b = height.toFloat() * (y+1) / it.get(x).size
                    canvas?.drawRect(l, t, r, b, paint)
                }
            }
        }
    }
}