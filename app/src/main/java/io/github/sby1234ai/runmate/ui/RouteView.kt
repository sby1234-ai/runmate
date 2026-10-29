package io.github.sby1234ai.runmate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.github.sby1234ai.runmate.R
import io.github.sby1234ai.runmate.logic.Motion
import io.github.sby1234ai.runmate.logic.TrackPoint
import kotlin.math.cos
import kotlin.math.max

/** 지도 없이 경로 모양만 그립니다(인터넷이 필요 없음). 구간 색은 걷기·뛰기·멈춤에 따라 다릅니다. */
class RouteView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private var points: List<TrackPoint> = emptyList()
    private val density = resources.displayMetrics.density
    private fun paint(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorRes)
        strokeWidth = 5 * density; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE
    }
    private val runPaint = paint(R.color.run)
    private val walkPaint = paint(R.color.walk)
    private val stillPaint = paint(R.color.still)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.sub); textSize = 14 * density; textAlign = Paint.Align.CENTER
    }

    fun setPoints(p: List<TrackPoint>) { points = p; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) {
            canvas.drawText("경로를 그릴 위치 기록이 부족합니다", width / 2f, height / 2f, textPaint)
            return
        }
        val lat0 = points.map { it.lat }.average()
        val k = cos(Math.toRadians(lat0))
        val xs = points.map { it.lon * k }; val ys = points.map { it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val pad = 24 * density
        val w = width - 2 * pad; val h = height - 2 * pad
        val span = max(maxX - minX, maxY - minY).coerceAtLeast(1e-6)
        val scale = minOf(w / (maxX - minX).coerceAtLeast(1e-9), h / (maxY - minY).coerceAtLeast(1e-9), (minOf(w, h) / span))
        val ox = pad + (w - (maxX - minX) * scale) / 2
        val oy = pad + (h - (maxY - minY) * scale) / 2
        fun x(i: Int) = (ox + (xs[i] - minX) * scale).toFloat()
        fun y(i: Int) = (oy + (maxY - ys[i]) * scale).toFloat()
        for (i in 1 until points.size) {
            val p = when (points[i].motion) { Motion.RUN -> runPaint; Motion.WALK -> walkPaint; Motion.STILL -> stillPaint }
            canvas.drawLine(x(i - 1), y(i - 1), x(i), y(i), p)
        }
        dotPaint.color = ContextCompat.getColor(context, R.color.ink)
        canvas.drawCircle(x(0), y(0), 7 * density, dotPaint)
        dotPaint.color = ContextCompat.getColor(context, R.color.run)
        canvas.drawCircle(x(points.size - 1), y(points.size - 1), 7 * density, dotPaint)
    }
}
