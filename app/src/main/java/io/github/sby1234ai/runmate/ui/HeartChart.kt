package io.github.sby1234ai.runmate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** 시간에 따른 심박 선 그래프(아래는 옅게 채움). 최고·최저 사이로 맞춰 그림 */
class HeartChart @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var samples: List<Pair<Long, Int>> = emptyList()
        set(v) { field = v; invalidate() }
    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * d; color = 0xFFFF6B6B.toInt(); strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        if (samples.size < 2) return
        val w = width.toFloat(); val h = height.toFloat()
        val t0 = samples.first().first; val t1 = samples.last().first
        val lo = samples.minOf { it.second } - 5; val hi = samples.maxOf { it.second } + 5
        fun x(t: Long) = (t - t0).toFloat() / (t1 - t0).coerceAtLeast(1) * w
        fun y(b: Int) = h - (b - lo).toFloat() / (hi - lo).coerceAtLeast(1) * h
        val p = Path(); p.moveTo(x(t0), y(samples.first().second))
        samples.drop(1).forEach { p.lineTo(x(it.first), y(it.second)) }
        val area = Path(p); area.lineTo(w, h); area.lineTo(0f, h); area.close()
        fill.shader = LinearGradient(0f, 0f, 0f, h, 0x55FF6B6B, 0x00FF6B6B, Shader.TileMode.CLAMP)
        c.drawPath(area, fill); c.drawPath(p, line)
    }
}
