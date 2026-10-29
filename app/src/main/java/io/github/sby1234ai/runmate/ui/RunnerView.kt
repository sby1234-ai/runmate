package io.github.sby1234ai.runmate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import io.github.sby1234ai.runmate.logic.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 지금 상태(뛰기·걷기·멈춤)에 맞춰 팔다리를 움직이는 사람 그림.
 * 뛰면 몸을 앞으로 숙이고 크게·빠르게, 걸으면 작게·천천히, 멈추면 숨만 쉽니다.
 */
class RunnerView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var motion: Motion = Motion.STILL
        set(v) { if (field != v) { field = v; invalidate() } }
    var paused: Boolean = false
        set(v) { field = v; invalidate() }
    var color: Int = 0xFFFFFFFF.toInt()
        set(v) { field = v; invalidate() }
    var ringColor: Int = 0x33FFFFFF
        set(v) { field = v; invalidate() }

    private val limb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private class Gait(val freq: Float, val leg: Float, val knee: Float, val arm: Float, val elbow: Float, val lean: Float, val bob: Float)
    private val run = Gait(1.45f, 0.78f, 1.35f, 0.95f, 1.75f, 0.24f, 7f)
    private val walk = Gait(0.95f, 0.42f, 0.45f, 0.40f, 0.35f, 0.05f, 2.5f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0) return
        val s = size / 200f
        val cx = width / 2f; val cy = height / 2f
        val t = SystemClock.uptimeMillis() / 1000f
        val moving = !paused && motion != Motion.STILL
        val g = if (motion == Motion.RUN) run else walk

        // 바깥 고리: 움직일 때 숨 쉬듯 커졌다 작아짐
        val pulse = if (moving) 0.5f + 0.5f * sin(t * 2 * PI.toFloat() * g.freq / 2) else 0.5f + 0.5f * sin(t * 1.6f)
        fill.color = ringColor
        canvas.drawCircle(cx, cy, size * (0.46f + 0.03f * pulse), fill)

        val phi = if (moving) t * 2 * PI.toFloat() * g.freq else 0f
        val bob = if (moving) g.bob * abs(sin(phi)) else 2f * sin(t * 1.6f)
        val lean = if (moving) g.lean else 0f

        val hipX = cx - 4 * s; val hipY = cy + 18 * s - bob * s
        val torso = 52 * s
        val shX = hipX + sin(lean) * torso; val shY = hipY - cos(lean) * torso
        val headR = 13 * s
        val headX = shX + sin(lean) * (headR + 6 * s); val headY = shY - cos(lean) * (headR + 6 * s)
        limb.strokeWidth = 10 * s

        // 다리·팔 한 쌍씩: 뒤쪽은 흐리게 먼저 그리고 앞쪽을 나중에
        for (side in 0..1) {
            val back = side == 0
            val ph = phi + if (back) PI.toFloat() else 0f
            limb.color = if (back) (color and 0x00FFFFFF) or 0x80000000.toInt() else color

            val thigh = 40 * s; val shin = 40 * s
            val a = if (moving) g.leg * sin(ph) else if (back) -0.05f else 0.05f
            val kneeX = hipX + sin(a) * thigh; val kneeY = hipY + cos(a) * thigh
            val bend = if (moving) g.knee * max(0f, -cos(ph)) + 0.15f else 0.02f
            val b = a - bend
            val footX = kneeX + sin(b) * shin; val footY = kneeY + cos(b) * shin
            canvas.drawLine(hipX, hipY, kneeX, kneeY, limb)
            canvas.drawLine(kneeX, kneeY, footX, footY, limb)

            val upper = 30 * s; val fore = 27 * s
            val aa = if (moving) -g.arm * sin(ph) + lean else if (back) -0.12f else 0.12f
            val elX = shX + sin(aa) * upper; val elY = shY + cos(aa) * upper
            val fa = aa + if (moving) g.elbow else 0.1f
            val haX = elX + sin(fa) * fore; val haY = elY + cos(fa) * fore
            canvas.drawLine(shX, shY, elX, elY, limb)
            canvas.drawLine(elX, elY, haX, haY, limb)
        }
        limb.color = color
        canvas.drawLine(hipX, hipY, shX, shY, limb)
        fill.color = color
        canvas.drawCircle(headX, headY, headR, fill)

        // 뛸 때는 뒤로 지나가는 속도선
        if (moving && motion == Motion.RUN) {
            limb.strokeWidth = 4 * s
            limb.color = (color and 0x00FFFFFF) or 0x66000000
            for (i in 0..2) {
                val k = ((t * 1.8f + i / 3f) % 1f)
                val y = cy - 20 * s + i * 22 * s
                val x0 = cx - 46 * s - k * 40 * s
                canvas.drawLine(x0, y, x0 - 22 * s * (1 - k), y, limb)
            }
        }
        if (isShown) postInvalidateOnAnimation()
    }
}
