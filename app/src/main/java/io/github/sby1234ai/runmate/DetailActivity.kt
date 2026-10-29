package io.github.sby1234ai.runmate

import io.github.sby1234ai.runmate.ui.showGlass
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import io.github.sby1234ai.runmate.data.Prefs
import io.github.sby1234ai.runmate.data.RunStore
import io.github.sby1234ai.runmate.logic.Format
import io.github.sby1234ai.runmate.logic.Gpx
import io.github.sby1234ai.runmate.logic.Records
import io.github.sby1234ai.runmate.logic.Run
import io.github.sby1234ai.runmate.logic.RunStats
import io.github.sby1234ai.runmate.logic.Stats
import io.github.sby1234ai.runmate.ui.MapPanel
import io.github.sby1234ai.runmate.ui.ShareImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailActivity : AppCompatActivity() {
    companion object { const val EXTRA_ID = "id" }

    private var run: Run? = null
    private var stats: Stats? = null
    private var panel: MapPanel? = null
    private var runTitle = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        io.github.sby1234ai.runmate.ui.EdgeInsets.apply(this)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val r = RunStore.load(this, id)
        if (r == null) { finish(); return }
        run = r
        val s = RunStats.of(r.points)
        stats = s
        runTitle = SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREA).format(Date(r.id))
        findViewById<TextView>(R.id.title).text = "$runTitle 기록"
        findViewById<TextView>(R.id.bigDist).text = Format.km(s.distance)

        val p = MapPanel(this, findViewById<FrameLayout>(R.id.mapBox), Prefs.map(this))
        p.setPoints(r.points, follow = false)
        p.fitAll()
        panel = p
        // 범례: 동그라미만 각 색으로 칠합니다
        val legend = android.text.SpannableStringBuilder()
        for ((label, color) in listOf("뛰기" to R.color.run, "걷기" to R.color.walk, "멈춤" to R.color.still)) {
            val start = legend.length
            legend.append("●")
            legend.setSpan(android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, color)), start, start + 1, 0)
            legend.append(" $label    ")
        }
        findViewById<TextView>(R.id.legend).text = legend

        val grid = findViewById<GridLayout>(R.id.grid)
        fun cell(label: String, value: String, color: Int = R.color.ink) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 10, 0, 10) }
            box.addView(TextView(this).apply { text = label; setTextAppearance(R.style.Label) })
            box.addView(TextView(this).apply {
                text = value; textSize = 22f; setTextColor(ContextCompat.getColor(context, color))
                paint.isFakeBoldText = true; fontFeatureSettings = "tnum"
            })
            grid.addView(box, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)))
        }
        val best1k = Records.bestEffort(r.points, 1000.0)
        cell("움직인 시간", Format.duration(s.moving))
        cell("평균 페이스", Format.pace(s.paceSecPerKm))
        cell("전체 시간", Format.duration(s.elapsed))
        cell("가장 빠른 1km", if (best1k != null) Format.duration(best1k) else "-")
        cell("뛴 거리", "${Format.km(s.runDistance)} km", R.color.run)
        cell("뛰기 페이스", Format.pace(s.runPaceSecPerKm), R.color.run)
        cell("걸은 거리", "${Format.km(s.walkDistance)} km", R.color.walk)
        cell("걸은 시간", Format.duration(s.walkTime), R.color.walk)
        if (r.steps > 0) {
            cell("걸음 수", String.format(Locale.KOREA, "%,d", r.steps))
            val minutes = s.moving / 60_000.0
            cell("평균 분당 걸음", if (minutes >= 1) "${(r.steps / minutes).toInt()}" else "-")
        }

        renderSplits(s)
        findBadges(r)
        loadHeart()

        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener { shareImage() }
        findViewById<MaterialButton>(R.id.btnGpx).setOnClickListener { exportGpx() }
        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage("이 기록을 지울까요? 지운 기록은 되살릴 수 없습니다.")
                .setPositiveButton("지우기") { _, _ -> RunStore.delete(this, r.id); finish() }
                .setNegativeButton("취소", null)
                .showGlass()
        }
    }

    override fun onResume() { super.onResume(); panel?.onResume() }
    override fun onPause() { panel?.onPause(); super.onPause() }
    override fun onDestroy() { panel?.onDestroy(); super.onDestroy() }

    // 1km마다 걸린 시간을 막대로. 가장 빠른 구간은 주홍, 나머지는 청록
    private fun renderSplits(s: Stats) {
        val box = findViewById<LinearLayout>(R.id.splits)
        if (s.splits.isEmpty()) {
            box.addView(TextView(this).apply { text = "1km를 넘으면 구간 기록이 나옵니다"; setTextAppearance(R.style.Label) })
            return
        }
        val max = s.splits.max().toDouble(); val min = s.splits.min()
        s.splits.forEachIndexed { i, ms ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_split, box, false)
            row.findViewById<TextView>(R.id.km).text = "${i + 1}km"
            row.findViewById<TextView>(R.id.pace).text = Format.pace(ms / 1000.0)
            val bar = row.findViewById<View>(R.id.bar)
            (bar.layoutParams as LinearLayout.LayoutParams).weight = (ms / max).toFloat().coerceIn(0.08f, 1f)
            bar.background.mutate().setTint(ContextCompat.getColor(this, if (ms == min && s.splits.size > 1) R.color.run else R.color.walk))
            box.addView(row)
        }
    }

    // 다른 기록과 비교해 이 기록이 최고 기록이면 표시합니다(기록이 많으면 시간이 걸리므로 뒤에서 계산)
    private fun findBadges(r: Run) {
        val distances = listOf(1000.0 to "1km", 5000.0 to "5km", 10000.0 to "10km")
        Thread {
            val mine = distances.map { Records.bestEffort(r.points, it.first) }
            if (mine.all { it == null }) return@Thread
            val others = RunStore.ids(this).filter { it != r.id }.mapNotNull { RunStore.load(this, it) }
            if (others.isEmpty()) return@Thread
            val wins = distances.indices.filter { i ->
                val m = mine[i] ?: return@filter false
                others.all { o -> Records.bestEffort(o.points, distances[i].first)?.let { m < it } ?: true }
            }.map { distances[it].second }
            if (wins.isEmpty()) return@Thread
            runOnUiThread {
                findViewById<TextView>(R.id.badges).apply {
                    text = "최고 기록 · 가장 빠른 ${wins.joinToString(" · ")}"
                    visibility = View.VISIBLE
                }
            }
        }.start()
    }

    // ───── 워치 심박(헬스 커넥트) ─────
    private val askHealth = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { loadHeart() }

    /** 이 기록 시간대의 워치 심박: 평균·최고, 1km 구간별 평균, 그래프. 권한이 없으면 가져오기 버튼 */
    private fun loadHeart() {
        val r = run ?: return
        val card = findViewById<View>(R.id.heartCard)
        if (!HealthLink.available(this)) return
        card.visibility = View.VISIBLE
        val text = findViewById<TextView>(R.id.heartText)
        val btn = findViewById<View>(R.id.heartBtn)
        val chart = findViewById<io.github.sby1234ai.runmate.ui.HeartChart>(R.id.heartChart)
        btn.setOnClickListener { askHealth.launch(HealthLink.PERMISSIONS) }
        lifecycleScope.launch {
            try {
                if (!HealthLink.granted(this@DetailActivity)) {
                    text.text = "갤럭시 워치를 차고 달렸다면, 삼성 헬스 → 설정 → 헬스 커넥트에서 심박 공유를 켠 뒤 아래 버튼을 눌러 주세요."
                    btn.visibility = View.VISIBLE; chart.visibility = View.GONE; return@launch
                }
                btn.visibility = View.GONE
                val end = r.points.lastOrNull()?.time ?: r.id
                val hr = HealthLink.heart(this@DetailActivity, r.id, end)
                if (hr.size < 2) { text.text = "이 시간대에 워치 심박 기록이 없습니다. 촘촘한 심박을 남기려면 달릴 때 워치에서도 운동을 시작해 주세요."; chart.visibility = View.GONE; return@launch }
                val avg = hr.map { it.second }.average().toInt(); val max = hr.maxOf { it.second }
                // 1km 구간별 평균 심박: 각 km를 지난 시각으로 나눔
                val marks = mutableListOf<Long>(); var acc = 0.0
                r.points.zipWithNext().forEach { (a, b) ->
                    if (!b.breakBefore) acc += io.github.sby1234ai.runmate.logic.Geo.distance(a.lat, a.lon, b.lat, b.lon)
                    if (acc >= (marks.size + 1) * 1000.0) marks += b.time
                }
                val bounds = listOf(r.id) + marks + listOf(end)
                val perKm = bounds.zipWithNext().mapIndexedNotNull { i, (a, b) ->
                    hr.filter { it.first in a..b }.takeIf { it.isNotEmpty() }?.let { l -> "${i + 1}km ${l.map { it.second }.average().toInt()}" }
                }
                text.text = "평균 $avg · 최고 $max bpm" + if (perKm.size > 1) "\n구간 평균  " + perKm.joinToString("  ·  ") else ""
                chart.samples = hr; chart.visibility = View.VISIBLE
            } catch (e: Exception) {
                text.text = "워치 심박을 읽지 못했습니다(${e.javaClass.simpleName})"
            }
        }
    }

    private fun shareImage() {
        val r = run ?: return; val s = stats ?: return
        val bmp = ShareImage.render(this, r, s, runTitle)
        val dir = File(cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "runmate-${r.id}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "기록 이미지 보내기"))
    }

    private fun exportGpx() {
        val r = run ?: return
        val dir = File(cacheDir, "gpx").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.KOREA).format(Date(r.id))
        val file = File(dir, "runmate-$stamp.gpx")
        file.writeText(Gpx.write(r, "$runTitle 기록"))
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "GPX 파일 보내기"))
    }
}
