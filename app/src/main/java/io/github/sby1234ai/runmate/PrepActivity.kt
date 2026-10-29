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
    private val main = Handler(Looper.getMainLooper())
    private var counting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prep)
        EdgeInsets.apply(this)
        renderPlans()

        bindSwitch(R.id.rowVoice, R.id.swVoice, R.id.voiceState, Prefs.voice(this), "1km마다 거리와 시간을 읽어 줍니다") { Prefs.setVoice(this, it) }
        bindSwitch(R.id.rowPause, R.id.swPause, R.id.pauseState, Prefs.autoPause(this), "멈춰 서면 운동 시간을 멈춥니다") { Prefs.setAutoPause(this, it) }

        val go = findViewById<View>(R.id.btnGo)
        Glass.apply(go, GlassKind.STRONG, true)
        findViewById<View>(R.id.goIcon).background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 8 * resources.displayMetrics.density; setColor(getColor(R.color.walk))
        }
        go.setOnClickListener { countdown() }
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
        val cur = Prefs.interval(this)
        val items = listOf(Triple(0, "자유 달리기", "정해진 순서 없이 마음대로")) +
            IntervalPlan.PRESETS.map { Triple(it.id, "${it.shortName} · ${it.totalSec / 60}분", it.name.substringAfter(" · ", "인터벌")) }
        val d = resources.displayMetrics.density
        for ((id, title, detail) in items) {
            val on = id == (if (IntervalPlan.byId(cur) == null) 0 else cur)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * d).toInt(), (13 * d).toInt(), (16 * d).toInt(), (13 * d).toInt())
                isClickable = true; isFocusable = true
                contentDescription = "$title, $detail" + if (on) ", 선택됨" else ""
            }
            card.addView(TextView(this).apply {
                text = if (on) "✓  $title" else title
                textSize = 16f; paint.isFakeBoldText = true
                setTextColor(if (on) getColor(R.color.walk) else getColor(R.color.ink))
            })
            card.addView(TextView(this).apply {
                text = detail; textSize = 13f; setTextColor(0xB3FFFFFF.toInt()); setPadding(0, (2 * d).toInt(), 0, 0)
            })
            Glass.apply(card, if (on) GlassKind.SELECTED else GlassKind.PANEL, true)
            card.setOnClickListener { Prefs.setInterval(this, id); renderPlans() }
            box.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (8 * d).toInt() })
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
