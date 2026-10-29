package io.github.sby1234ai.runmate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import io.github.sby1234ai.runmate.R
import io.github.sby1234ai.runmate.logic.Motion
import io.github.sby1234ai.runmate.logic.TrackPoint
import kotlin.math.cos

/** 지도 없이 경로 모양만 그립니다. 화면(RouteView)과 공유 이미지가 같이 씁니다. */
class RouteDrawer(ctx: Context, strokePx: Float, private val dotPx: Float) {
    private fun paint(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(ctx, colorRes)
        strokeWidth = strokePx; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE
    }
    private val runPaint = paint(R.color.run)
    private val walkPaint = paint(R.color.walk)
    private val stillPaint = paint(R.color.still).apply { strokeWidth = strokePx * 0.6f }
    private val ink = ContextCompat.getColor(ctx, R.color.ink)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dotPx * 0.45f; color = ink }

    /** 그릴 점이 모자라면 false. */
    fun draw(canvas: Canvas, box: RectF, points: List<TrackPoint>): Boolean {
        if (points.size < 2) return false
        val k = cos(Math.toRadians(points.map { it.lat }.average()))
        val xs = DoubleArray(points.size) { points[it].lon * k }
        val ys = DoubleArray(points.size) { points[it].lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val w = box.width().toDouble(); val h = box.height().toDouble()
        val scale = minOf(w / (maxX - minX).coerceAtLeast(1e-9), h / (maxY - minY).coerceAtLeast(1e-9))
        val ox = box.left + (w - (maxX - minX) * scale) / 2
        val oy = box.top + (h - (maxY - minY) * scale) / 2
        fun x(i: Int) = (ox + (xs[i] - minX) * scale).toFloat()
        fun y(i: Int) = (oy + (maxY - ys[i]) * scale).toFloat()
        for (i in 1 until points.size) {
            val p = when (points[i].motion) { Motion.RUN -> runPaint; Motion.WALK -> walkPaint; Motion.STILL -> stillPaint }
            canvas.drawLine(x(i - 1), y(i - 1), x(i), y(i), p)
        }
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x(0), y(0), dotPx, fill); canvas.drawCircle(x(0), y(0), dotPx, ring)
        fill.color = ink
        canvas.drawCircle(x(points.size - 1), y(points.size - 1), dotPx, fill)
        return true
    }
}
