package com.nusafit.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class HealthGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var bmi: Double = 0.0
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val left = 28f; val right = w - 28f; val cy = min(h * 0.58f, h - 38f)
        val rect = RectF(left, cy - 12f, right, cy + 12f)
        val minBmi = 15.0; val maxBmi = 35.0
        val segments = listOf(18.5 to 24.9, 25.0 to 29.9, 30.0 to 35.0)
        val starts = listOf(15.0, 18.5, 25.0)
        val ends = listOf(18.5, 25.0, 35.0)
        paint.style = Paint.Style.FILL
        // Neutral visual scale; colors communicate categories without appearance ranking.
        paint.color = 0xFFE7EEF5.toInt(); canvas.drawRoundRect(rect, 18f, 18f, paint)
        paint.color = 0xFF43A047.toInt(); canvas.drawRoundRect(RectF(xFor(starts[1], left, right, minBmi, maxBmi), rect.top, xFor(ends[1], left, right, minBmi, maxBmi), rect.bottom), 18f, 18f, paint)
        paint.color = 0xFFFFB300.toInt(); canvas.drawRoundRect(RectF(xFor(starts[2], left, right, minBmi, maxBmi), rect.top, xFor(ends[2], left, right, minBmi, maxBmi), rect.bottom), 18f, 18f, paint)
        val clamped = bmi.coerceIn(minBmi, maxBmi)
        val x = xFor(clamped, left, right, minBmi, maxBmi)
        paint.color = 0xFF0D47A1.toInt(); canvas.drawCircle(x, cy, 9f, paint)
        paint.color = 0xFF263238.toInt(); paint.textSize = 12f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText("15", xFor(15.0,left,right,minBmi,maxBmi), h - 10f, paint)
        canvas.drawText("18,5", xFor(18.5,left,right,minBmi,maxBmi), h - 10f, paint)
        canvas.drawText("24,9", xFor(24.9,left,right,minBmi,maxBmi), h - 10f, paint)
        canvas.drawText("30+", xFor(30.0,left,right,minBmi,maxBmi), h - 10f, paint)
        paint.textSize = 13f; paint.color = 0xFF0D47A1.toInt(); paint.textAlign = Paint.Align.LEFT
        canvas.drawText(String.format(java.util.Locale.US, "BMI %.1f", bmi), left, 20f, paint)
    }

    private fun xFor(v: Double, left: Float, right: Float, min: Double, max: Double): Float = left + ((v-min)/(max-min)).toFloat()*(right-left)
}
