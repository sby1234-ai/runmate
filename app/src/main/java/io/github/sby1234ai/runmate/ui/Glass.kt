package io.github.sby1234ai.runmate.ui

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.ImageView
import java.lang.ref.WeakReference

/**
 * 유리 판(시안 확정: 2026-10-29). 각진 모서리, 아주 옅은 흰 채움, 왼쪽 위가 가장 밝고 가운데서 거의 사라졌다가
 * 오른쪽 아래에서 살짝 살아나는 1dp 빛 테두리. 배경 사진이 있는 화면(첫 화면·달리는 중)에서는 판 안에
 * 배경 사진을 흐리게 한 그림을 그 자리에 맞춰 깔아, 뒤가 흐리게 비치는 유리처럼 보이게 합니다.
 * (안드로이드 버전과 상관없이 같은 모양: 흐린 그림은 작게 줄인 사진을 앱이 직접 흐리게 만든 것)
 */
enum class GlassKind(val radiusDp: Float, val fill: Int, val edge: IntArray) {
    /** 카드·타일 */
    PANEL(10f, 0x09FFFFFF, EDGE_WHITE),
    /** 「달리기 시작」 같은 주요 버튼 */
    STRONG(12f, 0x0FFFFFFF, EDGE_WHITE),
    /** 「운동 종료」 */
    RUN(10f, 0x14F2542D, intArrayOf(0xD9FFBEAA.toInt(), 0x40F2542D, 0x73FFBEAA)),
    /** 안내 칩 */
    CHIP(6f, 0x0BFFFFFF, EDGE_WHITE),
    /** 고른 항목(준비 화면의 운동 카드): 청록 빛 테두리 */
    SELECTED(10f, 0x141FB3A2, intArrayOf(0xF21FB3A2.toInt(), 0x661FB3A2, 0xB31FB3A2.toInt())),
    /** 켜진 토글: 청록으로 채운 칸 */
    ON(10f, 0x4D1FB3A2, intArrayOf(0xF26FE3D6.toInt(), 0x661FB3A2, 0xB31FB3A2.toInt())),
    /** 대화상자 판 */
    SHEET(14f, 0x6B1E2830, EDGE_WHITE),
    /** 대화상자 「취소」 같은 보조 버튼 */
    PILL(12f, 0x1FFFFFFF, intArrayOf(0x38FFFFFF, 0x38FFFFFF, 0x38FFFFFF)),
}

private val EDGE_WHITE = intArrayOf(0x9EFFFFFF.toInt(), 0x1AFFFFFF, 0x0AFFFFFF, 0x42FFFFFF)

/** 배경 사진의 흐린 사본. 첫 화면이 배경을 바꿀 때 [set]을 부릅니다. */
object Backdrop {
    private const val SAMPLE = 8          // 사진을 1/8로 줄여서 흐리게(빠르고 충분히 흐림)
    private var blurred: Bitmap? = null
    private var photo: WeakReference<ImageView>? = null
    private val hosts = mutableListOf<WeakReference<View>>()
    /** 배경 위에 깔리는 어두운 막(SkyView와 같은 값)을 유리 안에도 똑같이 그리기 위한 값 */
    var dim = false
        set(v) { if (field != v) { field = v; invalidateAll() } }

    fun set(res: Resources, resId: Int, photoView: ImageView) {
        photo = WeakReference(photoView)
        blurred = if (resId == 0) null else try {
            val opts = BitmapFactory.Options().apply { inSampleSize = SAMPLE }
            BitmapFactory.decodeResource(res, resId, opts)?.let { blur(it, 6) }
        } catch (e: Throwable) { null }
        invalidateAll()
        // 사진 뷰가 새 사진으로 자리를 다시 잡은 뒤에 한 번 더 맞춰 그림
        photoView.post { invalidateAll() }
    }

    fun clear() { blurred = null; invalidateAll() }

    internal fun register(v: View) {
        hosts.removeAll { it.get() == null }
        if (hosts.none { it.get() === v }) hosts.add(WeakReference(v))
    }

    /** 스크롤로 판이 움직이면 그 자리의 흐린 그림도 따라 바뀌어야 하므로 다시 그리게 함 */
    fun invalidateAll() { hosts.forEach { it.get()?.invalidate() } }

    /** host 안에 그 자리의 흐린 배경을 그림. 그릴 사진이 없으면 false */
    internal fun drawInto(canvas: Canvas, host: View): Boolean {
        val bmp = blurred ?: return false
        val pv = photo?.get() ?: return false
        if (pv.visibility != View.VISIBLE || pv.drawable == null || !pv.isAttachedToWindow || !host.isAttachedToWindow) return false
        if (host.rootView !== pv.rootView) return false
        val hp = IntArray(2); val pp = IntArray(2)
        host.getLocationInWindow(hp); pv.getLocationInWindow(pp)
        // 원본 사진 → 사진 뷰 좌표(centerCrop) 행렬에, 줄인 배율과 두 뷰의 위치 차이를 더함
        val m = Matrix(pv.imageMatrix)
        val sx = pv.drawable.intrinsicWidth.toFloat() / bmp.width
        val sy = pv.drawable.intrinsicHeight.toFloat() / bmp.height
        m.preScale(sx, sy)
        m.postTranslate((pp[0] - hp[0]).toFloat(), (pp[1] - hp[1]).toFloat())
        canvas.drawBitmap(bmp, m, bitmapPaint)
        // SkyView가 사진 위에 까는 어두운 막과 같은 것을 화면 좌표에 맞춰 그림
        val h = pv.height.toFloat()
        val top = (pp[1] - hp[1]).toFloat()
        scrimPaint.shader = LinearGradient(0f, top, 0f, top + h,
            intArrayOf(0x99000000.toInt(), 0x4D000000, 0x40000000, 0xB3000000.toInt()), floatArrayOf(0f, 0.32f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPaint(scrimPaint)
        if (dim) { flat.color = 0x59000000; canvas.drawPaint(flat) }
        return true
    }

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint()
    private val flat = Paint()

    /** 상자 흐림 3번 = 가우스 흐림에 가까움 */
    private fun blur(src: Bitmap, r: Int): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) { boxH(px, tmp, w, h, r); boxV(tmp, px, w, h, r) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxH(s: IntArray, d: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            var rs = 0; var gs = 0; var bs = 0
            val row = y * w
            for (i in -r..r) { val c = s[row + i.coerceIn(0, w - 1)]; rs += c shr 16 and 255; gs += c shr 8 and 255; bs += c and 255 }
            for (x in 0 until w) {
                d[row + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val a = s[row + (x - r).coerceIn(0, w - 1)]; val b = s[row + (x + r + 1).coerceIn(0, w - 1)]
                rs += (b shr 16 and 255) - (a shr 16 and 255); gs += (b shr 8 and 255) - (a shr 8 and 255); bs += (b and 255) - (a and 255)
            }
        }
    }

    private fun boxV(s: IntArray, d: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) { val c = s[i.coerceIn(0, h - 1) * w + x]; rs += c shr 16 and 255; gs += c shr 8 and 255; bs += c and 255 }
            for (y in 0 until h) {
                d[y * w + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val a = s[(y - r).coerceIn(0, h - 1) * w + x]; val b = s[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rs += (b shr 16 and 255) - (a shr 16 and 255); gs += (b shr 8 and 255) - (a shr 8 and 255); bs += (b and 255) - (a and 255)
            }
        }
    }
}

class GlassDrawable(private val host: View?, private val kind: GlassKind, density: Float) : Drawable() {
    private val radius = kind.radiusDp * density
    private val stroke = 1f * density
    private val path = Path()
    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = kind.fill }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
    private var edgeKey = 0L

    init { host?.let { Backdrop.register(it) } }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        rect.set(b)
        path.reset(); path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        if (host != null && kind != GlassKind.SHEET) Backdrop.drawInto(canvas, host)
        canvas.drawRect(rect, fill)
        canvas.restore()
        // 빛 테두리: 왼쪽 위 → 오른쪽 아래 그라데이션
        val key = (b.width().toLong() shl 32) or b.height().toLong()
        if (key != edgeKey) {
            val c = kind.edge
            val pos = if (c.size == 4) floatArrayOf(0f, 0.38f, 0.62f, 1f) else floatArrayOf(0f, 0.45f, 1f)
            edge.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, c, pos, Shader.TileMode.CLAMP)
            edgeKey = key
        }
        rect.inset(stroke / 2, stroke / 2)
        canvas.drawRoundRect(rect, radius - stroke / 2, radius - stroke / 2, edge)
    }

    // 지도처럼 안쪽을 판 모양대로 잘라야 하는 뷰(clipToOutline)를 위해 둥근 사각형 윤곽을 알려 줌
    override fun getOutline(outline: android.graphics.Outline) { outline.setRoundRect(bounds, radius) }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; edge.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter; edge.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * 대화상자를 유리 판으로(시안 확정): 각진 판 + 빛 테두리, 반반 나눈 버튼 — 보조(취소 등)는 유리, 주(닫기 등)는 청록.
 * 뜨는 동안에는 뒤 화면의 글자·타일·버튼을 숨기고 배경 사진만 흐리게 남깁니다(휴대폰이 창 뒤 흐림을 꺼 둔 경우도 같게).
 * 숨길 뷰는 android:tag="fg", 흐리게 할 배경은 android:tag="bgBlur". 표시가 없는 화면은 내용 전체를 숨김.
 */
fun com.google.android.material.dialog.MaterialAlertDialogBuilder.showGlass(): androidx.appcompat.app.AlertDialog {
    val dlg = create()
    val ctx = context
    val density = ctx.resources.displayMetrics.density
    fun dp(v: Float) = (v * density).toInt()
    dlg.window?.let { w ->
        w.setBackgroundDrawable(android.graphics.drawable.InsetDrawable(
            GlassDrawable(null, GlassKind.SHEET, density), dp(22f), dp(60f), dp(22f), dp(60f)))
        w.setDimAmount(0.25f)
    }
    // 뒤 화면: 앞의 내용은 숨기고 배경만 흐리게
    val act = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<android.app.Activity>().firstOrNull()
    val root = act?.findViewById<android.view.ViewGroup>(android.R.id.content)
    val fg = mutableListOf<View>(); val blurs = mutableListOf<View>()
    fun walk(v: View) {
        when (v.tag) { "fg" -> fg.add(v); "bgBlur" -> blurs.add(v) }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
    }
    root?.let { walk(it) }
    if (fg.isEmpty()) root?.getChildAt(0)?.let { fg.add(it) }
    val hidden = fg.filter { it.visibility == View.VISIBLE }
    dlg.setOnDismissListener {
        hidden.forEach { it.animate().alpha(1f).setDuration(160).start() }
        if (android.os.Build.VERSION.SDK_INT >= 31) blurs.forEach { it.setRenderEffect(null) }
    }
    dlg.setOnShowListener {
        hidden.forEach { it.animate().alpha(0f).setDuration(160).start() }
        if (android.os.Build.VERSION.SDK_INT >= 31) blurs.forEach {
            it.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(dp(18f).toFloat(), dp(18f).toFloat(), Shader.TileMode.CLAMP))
        }
        try {
            // 여백·글자 크기: 넉넉하게
            dlg.findViewById<View>(androidx.appcompat.R.id.parentPanel)?.setPadding(dp(4f), dp(14f), dp(4f), dp(6f))
            dlg.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)?.apply { textSize = 20f; setLineSpacing(0f, 1.15f) }
            dlg.findViewById<android.widget.TextView>(android.R.id.message)?.apply { textSize = 15f; setLineSpacing(dp(3f).toFloat(), 1f); setTextColor(0xFFDDE5EA.toInt()) }
            val pos = dlg.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            val neg = dlg.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
            val neu = dlg.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)
            val shown = listOfNotNull(neu, neg, pos).filter { it.visibility == View.VISIBLE && it.text.isNotEmpty() }
            if (shown.isEmpty()) return@setOnShowListener
            val bar = pos.parent as? android.widget.LinearLayout
            val row = bar != null && bar.orientation == android.widget.LinearLayout.HORIZONTAL
            if (bar != null) {
                if (row) for (i in 0 until bar.childCount) { val c = bar.getChildAt(i); if (c !is android.widget.Button) c.visibility = View.GONE }
                bar.setPadding(dp(19f), dp(22f), dp(19f), dp(20f))
            }
            for (b in shown) {
                (b.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { lp ->
                    if (row) { lp.width = 0; lp.weight = 1f }
                    lp.height = dp(52f); lp.marginStart = dp(5f); lp.marginEnd = dp(5f); lp.topMargin = 0; lp.bottomMargin = 0
                    b.layoutParams = lp
                }
                b.isAllCaps = false
                b.textSize = 16f
                b.minHeight = 0; b.minimumHeight = 0
                if (b is com.google.android.material.button.MaterialButton) { b.backgroundTintList = null; b.rippleColor = null; b.strokeWidth = 0; b.insetTop = 0; b.insetBottom = 0 }
                if (b === pos) {
                    val bg = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 12 * density; setColor(0xFF1FB3A2.toInt()) }
                    b.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null)
                    b.setTextColor(0xFF06221F.toInt())
                    b.typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    Glass.apply(b, GlassKind.PILL, pressable = true)
                    b.setTextColor(0xFFF4F7F9.toInt())
                }
            }
        } catch (e: Exception) { }
    }
    dlg.show()
    return dlg
}

object Glass {
    /** android:tag="glass"인 뷰들의 배경을 유리 판으로(설정·기록 화면처럼 XML로 만든 카드) */
    fun applyTagged(root: View) {
        if (root.tag == "glass") apply(root, GlassKind.PANEL)
        if (root is android.view.ViewGroup) for (i in 0 until root.childCount) applyTagged(root.getChildAt(i))
    }

    /** 뷰 배경을 유리 판으로. 누를 수 있는 뷰면 누를 때 번지는 효과도 같은 모양으로 */
    fun apply(v: View, kind: GlassKind, pressable: Boolean = v.isClickable) {
        val density = v.resources.displayMetrics.density
        val d = GlassDrawable(v, kind, density)
        val mask = android.graphics.drawable.GradientDrawable().apply { cornerRadius = kind.radiusDp * density; setColor(0xFFFFFFFF.toInt()) }
        v.background = if (pressable) RippleDrawable(ColorStateList.valueOf(0x26FFFFFF), d, mask) else d
        if (pressable) press(v)
    }

    /** 모든 버튼·카드 공통 누름 효과: 살짝 작아졌다 돌아옴(옅은 빛은 리플) */
    fun press(v: View) {
        val sla = android.animation.StateListAnimator()
        fun anim(scale: Float, ms: Long) = android.animation.ObjectAnimator.ofPropertyValuesHolder(v,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, scale),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, scale)).setDuration(ms)
        sla.addState(intArrayOf(android.R.attr.state_pressed), anim(0.97f, 90))
        sla.addState(intArrayOf(), anim(1f, 140))
        v.stateListAnimator = sla
    }
}
