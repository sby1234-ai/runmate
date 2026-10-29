package io.github.sby1234ai.runmate

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.sby1234ai.runmate.data.Prefs
import io.github.sby1234ai.runmate.logic.IntervalPlan
import io.github.sby1234ai.runmate.ui.EdgeInsets
import io.github.sby1234ai.runmate.ui.Glass
import io.github.sby1234ai.runmate.ui.GlassKind

/**
 * 「달리기 시작」을 누르면 먼저 오는 준비 화면.
 * 운동(자유 달리기 / 인터벌)을 고르고, 음성 안내·자동 일시정지를 스위치로 정한 뒤
 * 「시작」 → 3·2·1 → 첫 화면으로 돌아가 기록을 시작합니다(RESULT_OK).
 */
class PrepActivity : AppCompatActivity() {
    companion object { const val EXTRA_BG = "bg" }
    private val main = Handler(Looper.getMainLooper())
    private var counting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prep)
        EdgeInsets.apply(this)
        val bg = intent.getIntExtra(EXTRA_BG, 0)
        val photo = findViewById<android.widget.ImageView>(R.id.prepBg)
        if (bg != 0) {
            photo.setImageResource(bg)
            if (android.os.Build.VERSION.SDK_INT >= 31) photo.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(14 * resources.displayMetrics.density, 14 * resources.displayMetrics.density, android.graphics.Shader.TileMode.CLAMP))
            io.github.sby1234ai.runmate.ui.Backdrop.set(resources, bg, photo)
        }
        renderPlans()

        // 음성 안내·자동 일시정지는 첫 화면의 아이콘 토글로 옮김(같은 설정이 두 곳에 있지 않게)
        for (id in intArrayOf(R.id.rowVoice, R.id.rowPause)) findViewById<View>(id).visibility = View.GONE
        ((findViewById<View>(R.id.rowVoice).parent as android.view.ViewGroup).let { p -> p.getChildAt(p.indexOfChild(findViewById(R.id.rowVoice)) - 1) })?.visibility = View.GONE

        val go = findViewById<View>(R.id.btnGo)
        Glass.apply(go, GlassKind.STRONG, true)
        findViewById<View>(R.id.goIcon).background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 8 * resources.displayMetrics.density; setColor(getColor(R.color.walk))
        }
        go.setOnClickListener { setResult(Activity.RESULT_OK); finish() }
        // 카운트다운 중 뒤로가기는 시작 취소(안드로이드 16 기준이라 onBackPressed 대신 콜백)
        onBackPressedDispatcher.addCallback(this, cancelCount)
    }

    private val cancelCount = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            main.removeCallbacksAndMessages(null); counting = false; isEnabled = false
            findViewById<View>(R.id.countdown).visibility = View.GONE
        }
    }

    /** 운동 카드: 고른 카드는 청록 테두리, 윗줄 이름·시간 / 아랫줄 구성 */
    private fun renderPlans() {
        val box = findViewById<LinearLayout>(R.id.plans)
        box.removeAllViews()
        val cur = Prefs.interval(this).let { if (IntervalPlan.byId(it) == null) 0 else it }
        data class Item(val id: Int, val title: String, val detail: String, val min: String, val plan: IntervalPlan?)
        val items = listOf(Item(0, "자유 달리기", "정해진 순서 없이 마음대로", "∞", null)) + IntervalPlan.PRESETS.map {
            val body = if (it.walkSec == 0) "${it.runSec / 60}분 뛰기" else it.name.substringAfter(" · ", "")
            val detail = if (it.warmupSec > 0) "준비 걷기 ${it.warmupSec / 60}분 · $body" else body
            Item(it.id, it.shortName, detail, "${it.totalSec / 60}", it)
        }
        val d = resources.displayMetrics.density
        fun px(v: Float) = (v * d).toInt()
        for (it in items) {
            val on = it.id == cur
            val ink = if (on) 0xFF0E141A.toInt() else 0xFFF4F7F9.toInt()
            val sub = if (on) 0xFF3A4855.toInt() else 0xFFC9D2D9.toInt()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(16f), px(14f), px(16f), px(14f))
                isClickable = true; isFocusable = true
                contentDescription = "${it.title}, ${it.detail}, ${it.min}분" + if (on) ", 선택됨" else ""
            }
            // 윗줄: 이름·구성 | 큰 시간 | (고르면) 청록 체크
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            left.addView(TextView(this).apply { text = it.title; textSize = 16f; paint.isFakeBoldText = true; setTextColor(ink) })
            left.addView(TextView(this).apply { text = it.detail; textSize = 12f; setTextColor(sub); setPadding(0, px(2f), 0, 0) })
            top.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(TextView(this).apply {
                val sb = android.text.SpannableStringBuilder(it.min)
                if (it.min != "∞") { val st = sb.length; sb.append("분"); sb.setSpan(android.text.style.RelativeSizeSpan(0.5f), st, sb.length, 0) }
                text = sb; textSize = 26f; paint.isFakeBoldText = true; fontFeatureSettings = "tnum"; setTextColor(ink)
            })
            if (on) top.addView(TextView(this).apply {
                text = "✓"; textSize = 14f; paint.isFakeBoldText = true; setTextColor(0xFFFFFFFF.toInt()); gravity = android.view.Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 6 * d; setColor(0xFF0E8F82.toInt()) }
            }, LinearLayout.LayoutParams(px(24f), px(24f)).apply { marginStart = px(10f) })
            card.addView(top)
            // 아랫줄: 구성 띠(뛰기 진하게 · 걷기 옅게, 같은 색의 진하기 차이)
            val runC = if (on) 0xD90E141A.toInt() else 0xEBFFFFFF.toInt()
            val walkC = if (on) 0x380E141A else 0x47FFFFFF
            val band = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun seg(sec: Int, c: Int) {
                band.addView(View(this).apply { background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 2 * d; setColor(c) } },
                    LinearLayout.LayoutParams(0, -1, sec.toFloat()).apply { marginEnd = px(2f) })
            }
            val pl = it.plan
            if (pl == null) seg(1, runC) else {
                if (pl.warmupSec > 0) seg(pl.warmupSec, walkC)
                repeat(pl.reps) { seg(pl.runSec, runC); if (pl.walkSec > 0) seg(pl.walkSec, walkC) }
            }
            card.addView(band, LinearLayout.LayoutParams(-1, px(6f)).apply { topMargin = px(10f) })
            Glass.apply(card, if (on) GlassKind.ON else GlassKind.PANEL, true)
            val id = it.id
            card.setOnClickListener { Prefs.setInterval(this, id); renderPlans() }
            box.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8f) })
        }
    }

    /** 스위치 줄: 줄 전체를 눌러도 바뀌고, 켜짐(청록)/꺼짐(회색) 글자로 상태를 보여 줌 */
    private fun bindSwitch(rowId: Int, swId: Int, stateId: Int, initial: Boolean, onText: String, save: (Boolean) -> Unit) {
        val row = findViewById<View>(rowId)
        val sw = findViewById<MaterialSwitch>(swId)
        val state = findViewById<TextView>(stateId)
        Glass.apply(row, GlassKind.PANEL, true)
        fun show(v: Boolean) {
            state.text = if (v) "켜짐 · $onText" else "꺼짐"
            state.setTextColor(if (v) getColor(R.color.walk) else 0x99FFFFFF.toInt())
        }
        sw.isChecked = initial; show(initial)
        sw.setOnCheckedChangeListener { _, v -> save(v); show(v) }
        row.setOnClickListener { sw.isChecked = !sw.isChecked }
    }

    private fun countdown() {
        if (counting) return
        counting = true
        cancelCount.isEnabled = true
        val t = findViewById<TextView>(R.id.countdown)
        t.visibility = View.VISIBLE
        var n = 3
        val tick = object : Runnable {
            override fun run() {
                if (isFinishing) return
                if (n == 0) { setResult(Activity.RESULT_OK); finish(); return }
                t.text = n.toString()
                t.scaleX = 1.3f; t.scaleY = 1.3f; t.alpha = 0f
                t.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start()
                n--
                main.postDelayed(this, 1000)
            }
        }
        main.post(tick)
    }

    override fun onDestroy() { main.removeCallbacksAndMessages(null); super.onDestroy() }
}
