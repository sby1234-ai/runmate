package io.github.sby1234ai.runmate.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import io.github.sby1234ai.runmate.logic.Sky
import kotlin.math.sin
import kotlin.random.Random

/**
 * 날씨에 맞춰 바뀌는 배경. 하늘색 그라데이션 위에 해, 별, 구름, 비, 눈이 천천히 움직입니다.
 * 시간을 기준으로 그려서 기기의 '애니메이션 끄기' 설정과 상관없이 같은 모습입니다.
 */
class SkyView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var sky: Sky = Sky.PARTLY; private set
    var night: Boolean = false; private set
    /** 달리는 중 화면처럼 글자가 많은 곳에서는 배경을 어둡게 눌러 줍니다. */
    var dim: Boolean = false
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val rnd = Random(7)
    private val starX = FloatArray(60) { rnd.nextFloat() }
    private val starY = FloatArray(60) { rnd.nextFloat() * 0.6f }
    private val starP = FloatArray(60) { rnd.nextFloat() * 6.28f }
    private val dropX = FloatArray(90) { rnd.nextFloat() }
    private val dropY = FloatArray(90) { rnd.nextFloat() }
    private val dropV = FloatArray(90) { 0.7f + rnd.nextFloat() * 0.5f }
    private val flakeX = FloatArray(70) { rnd.nextFloat() }
    private val flakeY = FloatArray(70) { rnd.nextFloat() }
    private val flakeR = FloatArray(70) { 1.5f + rnd.nextFloat() * 2.5f }
    private val cloudY = floatArrayOf(0.10f, 0.22f, 0.34f, 0.16f)
    private val cloudS = floatArrayOf(1.0f, 0.75f, 1.2f, 0.6f)
    private val cloudV = floatArrayOf(0.010f, 0.016f, 0.007f, 0.013f)

    private val bg = Paint()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shaderKey = ""

    fun setWeather(sky: Sky, night: Boolean) {
        if (this.sky == sky && this.night == night) return
        this.sky = sky; this.night = night; shaderKey = ""; invalidate()
    }

    /** 위·아래 색(상태 표시줄·내비게이션 바 색을 맞추는 데 씀) */
    fun edgeColors(): Pair<Int, Int> = colors().let { it.first() to it.last() }

    private fun colors(): IntArray = if (night) when (sky) {
        Sky.RAIN, Sky.STORM -> intArrayOf(0xFF111A26.toInt(), 0xFF22303F.toInt(), 0xFF334356.toInt())
        Sky.SNOW -> intArrayOf(0xFF1B2638.toInt(), 0xFF34465E.toInt(), 0xFF5A6E86.toInt())
        else -> intArrayOf(0xFF0B1630.toInt(), 0xFF1B2D55.toInt(), 0xFF3B4C7A.toInt())
    } else when (sky) {
        Sky.CLEAR -> intArrayOf(0xFF2F86D6.toInt(), 0xFF6DB5EC.toInt(), 0xFFF6D7A7.toInt())
        Sky.PARTLY -> intArrayOf(0xFF3D86C6.toInt(), 0xFF7FB3DF.toInt(), 0xFFCFE2F2.toInt())
        Sky.CLOUDY -> intArrayOf(0xFF5D6B7A.toInt(), 0xFF8794A2.toInt(), 0xFFB9C2CB.toInt())
        Sky.FOG -> intArrayOf(0xFF6F7C86.toInt(), 0xFF9AA5AD.toInt(), 0xFFCBD2D7.toInt())
        Sky.RAIN -> intArrayOf(0xFF34485C.toInt(), 0xFF566C81.toInt(), 0xFF7F93A6.toInt())
        Sky.STORM -> intArrayOf(0xFF222B38.toInt(), 0xFF3D4858.toInt(), 0xFF5D6878.toInt())
        Sky.SNOW -> intArrayOf(0xFF6C88A6.toInt(), 0xFF9FB6CC.toInt(), 0xFFDCE6EF.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val key = "$sky$night$width$height"
        if (key != shaderKey) {
            bg.shader = LinearGradient(0f, 0f, 0f, h, colors(), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            shaderKey = key
        }
        canvas.drawRect(0f, 0f, w, h, bg)
        val t = SystemClock.uptimeMillis() / 1000f

        if (night) drawStars(canvas, w, h, t)
        else if (sky == Sky.CLEAR || sky == Sky.PARTLY) drawSun(canvas, w, h, t)
        when (sky) {
            Sky.PARTLY -> drawClouds(canvas, w, h, t, 2, 0.75f)
            Sky.CLOUDY, Sky.FOG -> drawClouds(canvas, w, h, t, 4, 0.55f)
            Sky.RAIN -> { drawClouds(canvas, w, h, t, 4, 0.35f); drawRain(canvas, w, h, t, 70) }
            Sky.STORM -> { drawClouds(canvas, w, h, t, 4, 0.25f); drawRain(canvas, w, h, t, 90); drawFlash(canvas, w, h, t) }
            Sky.SNOW -> { drawClouds(canvas, w, h, t, 3, 0.45f); drawSnow(canvas, w, h, t) }
            else -> {}
        }
        if (sky == Sky.FOG) { p.shader = null; p.color = 0x40FFFFFF; canvas.drawRect(0f, h * 0.45f, w, h, p) }
        if (dim) { p.shader = null; p.color = 0x59000000; canvas.drawRect(0f, 0f, w, h, p) }

        if (isShown) postInvalidateDelayed(40)
    }

    private fun drawSun(c: Canvas, w: Float, h: Float, t: Float) {
        val cx = w * 0.82f; val cy = h * 0.12f
        val r = w * (0.42f + 0.02f * sin(t * 0.8f))
        p.shader = RadialGradient(cx, cy, r, intArrayOf(0x88FFF3C4.toInt(), 0x33FFE8A8, 0x00FFE8A8), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.color = 0xEEFFF8E1.toInt()
        c.drawCircle(cx, cy, 26 * d, p)
    }

    private fun drawStars(c: Canvas, w: Float, h: Float, t: Float) {
        p.shader = null
        for (i in starX.indices) {
            val a = (0.35f + 0.65f * (0.5f + 0.5f * sin(t * 1.3f + starP[i]))) * 255
            p.color = (a.toInt() shl 24) or 0xFFFFFF
            c.drawCircle(starX[i] * w, starY[i] * h, (if (i % 7 == 0) 1.8f else 1.1f) * d, p)
        }
        // 달
        p.color = 0xF2F4F1E4.toInt()
        c.drawCircle(w * 0.80f, h * 0.11f, 20 * d, p)
    }

    private fun drawClouds(c: Canvas, w: Float, h: Float, t: Float, n: Int, alpha: Float) {
        p.shader = null
        p.color = ((alpha * 255).toInt() shl 24) or 0xFFFFFF
        for (i in 0 until n) {
            val s = cloudS[i] * w * 0.32f
            val span = w + s * 2
            val x = ((cloudV[i] * t * w + i * w * 0.37f) % span) - s
            val y = cloudY[i] * h
            c.drawOval(x - s * 0.6f, y - s * 0.16f, x + s * 0.6f, y + s * 0.16f, p)
            c.drawCircle(x - s * 0.2f, y - s * 0.12f, s * 0.22f, p)
            c.drawCircle(x + s * 0.15f, y - s * 0.18f, s * 0.28f, p)
        }
    }

    private fun drawRain(c: Canvas, w: Float, h: Float, t: Float, n: Int) {
        p.shader = null
        p.color = 0x8CDCE9F5.toInt()
        p.strokeWidth = 1.6f * d; p.strokeCap = Paint.Cap.ROUND
        val len = 16 * d
        for (i in 0 until minOf(n, dropX.size)) {
            val y = ((dropY[i] + t * dropV[i] * 0.9f) % 1f) * (h + len) - len
            val x = dropX[i] * w + y * 0.12f
            c.drawLine(x, y, x + len * 0.12f, y + len, p)
        }
    }

    private fun drawSnow(c: Canvas, w: Float, h: Float, t: Float) {
        p.shader = null
        p.color = 0xE6FFFFFF.toInt()
        for (i in flakeX.indices) {
            val y = ((flakeY[i] + t * 0.05f * (0.6f + flakeR[i] / 4f)) % 1f) * h
            val x = flakeX[i] * w + sin(t * 0.9f + i) * 10 * d
            c.drawCircle(x, y, flakeR[i] * d, p)
        }
    }

    private fun drawFlash(c: Canvas, w: Float, h: Float, t: Float) {
        val phase = t % 9f
        if (phase < 0.12f || (phase > 0.25f && phase < 0.32f)) {
            p.shader = null; p.color = 0x40FFFFFF; c.drawRect(0f, 0f, w, h, p)
        }
    }
}
