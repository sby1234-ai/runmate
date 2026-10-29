package io.github.sby1234ai.runmate

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.sby1234ai.runmate.data.RunStore
import io.github.sby1234ai.runmate.logic.Format
import io.github.sby1234ai.runmate.logic.Records
import io.github.sby1234ai.runmate.logic.RunStats
import io.github.sby1234ai.runmate.logic.Stats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunsActivity : AppCompatActivity() {
    private class Row(val id: Long, val stats: Stats, val best: List<Long?>)
    private val prDistances = listOf(1000.0 to "1km", 5000.0 to "5km", 10000.0 to "10km")
    private var loadSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_runs)
        io.github.sby1234ai.runmate.ui.EdgeInsets.apply(this)
        findViewById<View>(R.id.tabAll).setOnClickListener { selectTab(false) }
        findViewById<View>(R.id.tabBest).setOnClickListener { selectTab(true) }
        selectTab(savedInstanceState?.getBoolean("best") ?: intent.getBooleanExtra(EXTRA_BEST, false))
    }

    private var best = false

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putBoolean("best", best) }

    /** 탭: 고른 쪽은 진한 유리판에 흰 글자, 다른 쪽은 옅은 유리판에 옅은 글자 */
    private fun selectTab(showBest: Boolean) {
        best = showBest
        val kinds = io.github.sby1234ai.runmate.ui.GlassKind.STRONG to io.github.sby1234ai.runmate.ui.GlassKind.PANEL
        for ((id, on) in listOf(R.id.tabAll to !showBest, R.id.tabBest to showBest)) {
            val t = findViewById<TextView>(id)
            io.github.sby1234ai.runmate.ui.Glass.apply(t, if (on) kinds.first else kinds.second, true)
            t.setTextColor(if (on) 0xFFFFFFFF.toInt() else 0x99FFFFFF.toInt())
            t.isSelected = on
        }
        findViewById<View>(R.id.allPane).visibility = if (showBest) View.GONE else View.VISIBLE
        findViewById<View>(R.id.prPane).visibility = if (showBest) View.VISIBLE else View.GONE
    }

    companion object { const val EXTRA_BEST = "best" }

    override fun onResume() {
        super.onResume()
        val seq = ++loadSeq
        findViewById<TextView>(R.id.summary).text = "불러오는 중…"
        // 기록 파일을 읽고 최고 기록을 찾는 일은 화면이 멈추지 않게 뒤에서 합니다
        Thread {
            val rows = RunStore.ids(this).mapNotNull { id ->
                val run = RunStore.load(this, id) ?: return@mapNotNull null
                Row(id, RunStats.of(run.points), prDistances.map { Records.bestEffort(run.points, it.first) })
            }
            runOnUiThread { if (seq == loadSeq && !isFinishing) show(rows) }
        }.start()
    }

    private fun show(rows: List<Row>) {
        val list = findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        findViewById<View>(R.id.empty).visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.summaryRow).visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE

        val sum = Records.summary(rows.map { Records.Item(it.id, it.stats.distance) }, System.currentTimeMillis())
        findViewById<TextView>(R.id.weekKm).text = "${Format.km(sum.week.distance)} km"
        findViewById<TextView>(R.id.weekN).text = "${sum.week.count}번 달림"
        findViewById<TextView>(R.id.monthKm).text = "${Format.km(sum.month.distance)} km"
        findViewById<TextView>(R.id.monthN).text = "${sum.month.count}번 달림"
        findViewById<TextView>(R.id.summary).text =
            if (rows.isEmpty()) "" else "모든 기록 ${sum.all.count}개 · ${Format.km(sum.all.distance)} km"

        val fmt = SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREA)
        val short = SimpleDateFormat("yyyy. M. d.", Locale.KOREA)
        renderPrs(rows, short)

        for (r in rows) {
            val s = r.stats
            val row = LayoutInflater.from(this).inflate(R.layout.item_run, list, false)
            io.github.sby1234ai.runmate.ui.Glass.applyTagged(row)
            row.findViewById<TextView>(R.id.date).text = fmt.format(Date(r.id))
            row.findViewById<TextView>(R.id.dist).text = Format.km(s.distance)
            row.findViewById<TextView>(R.id.meta).text = "${Format.duration(s.moving)}  ·  ${Format.pace(s.paceSecPerKm)}"
            val moving = (s.runDistance + s.walkDistance).coerceAtLeast(1.0)
            (row.findViewById<View>(R.id.ratioRun).layoutParams as LinearLayout.LayoutParams).weight = (s.runDistance / moving).toFloat()
            (row.findViewById<View>(R.id.ratioWalk).layoutParams as LinearLayout.LayoutParams).weight = (s.walkDistance / moving).toFloat()
            row.setOnClickListener { open(r.id) }
            list.addView(row)
        }
    }

    private fun renderPrs(rows: List<Row>, dateFmt: SimpleDateFormat) {
        val box = findViewById<LinearLayout>(R.id.prList)
        box.removeAllViews()
        val items = mutableListOf<Triple<String, String, Long>>()
        prDistances.forEachIndexed { i, (_, label) ->
            val best = rows.filter { it.best[i] != null }.minByOrNull { it.best[i]!! } ?: return@forEachIndexed
            items += Triple("가장 빠른 $label", Format.duration(best.best[i]!!), best.id)
        }
        rows.maxByOrNull { it.stats.distance }?.takeIf { it.stats.distance >= 100 }?.let {
            items += Triple("가장 긴 거리", "${Format.km(it.stats.distance)} km", it.id)
        }
        findViewById<View>(R.id.prCard).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        findViewById<View>(R.id.prEmpty).visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        for ((label, value, id) in items) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 12)
                isClickable = true; setOnClickListener { open(id) }
            }
            line.addView(TextView(this).apply {
                text = label; textSize = 15f; setTextColor(ContextCompat.getColor(context, R.color.ink))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(TextView(this).apply {
                text = dateFmt.format(Date(id)); setTextAppearance(R.style.Label); setPadding(0, 0, 24, 0)
            })
            line.addView(TextView(this).apply {
                text = value; textSize = 17f; paint.isFakeBoldText = true; fontFeatureSettings = "tnum"
                setTextColor(ContextCompat.getColor(context, R.color.run))
            })
            box.addView(line)
        }
    }

    private fun open(id: Long) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, id))
    }
}
