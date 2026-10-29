package io.github.sby1234ai.runmate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.github.sby1234ai.runmate.R
import io.github.sby1234ai.runmate.logic.TrackPoint

/** 지도를 끈 경우에 쓰는 경로 그림(인터넷이 필요 없음). 구간 색은 걷기·뛰기·멈춤에 따라 다릅니다. */
class RouteView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private var points: List<TrackPoint> = emptyList()
    private val density = resources.displayMetrics.density
    private val drawer = RouteDrawer(ctx, 5 * density, 6 * density)
    private val box = RectF()
    var emptyText = "경로를 그릴 위치 기록이 아직 없습니다"
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.sub); textSize = 14 * density; textAlign = Paint.Align.CENTER
    }

    fun setPoints(p: List<TrackPoint>) { points = p; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 24 * density
        box.set(pad, pad, width - pad, height - pad)
        if (!drawer.draw(canvas, box, points)) canvas.drawText(emptyText, width / 2f, height / 2f, textPaint)
    }
}
