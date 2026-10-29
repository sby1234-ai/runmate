package io.github.sby1234ai.runmate

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import io.github.sby1234ai.runmate.data.RunStore
import io.github.sby1234ai.runmate.logic.Format
import io.github.sby1234ai.runmate.logic.Gpx
import io.github.sby1234ai.runmate.logic.Run
import io.github.sby1234ai.runmate.logic.RunStats
import io.github.sby1234ai.runmate.ui.RouteView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailActivity : AppCompatActivity() {
    companion object { const val EXTRA_ID = "id" }

    private var run: Run? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val r = RunStore.load(this, id)
        if (r == null) { finish(); return }
        run = r
        val s = RunStats.of(r.points)
        val title = SimpleDateFormat("M월 d일 (E) a h:mm 기록", Locale.KOREA).format(Date(r.id))
        findViewById<TextView>(R.id.title).text = title
        findViewById<RouteView>(R.id.route).setPoints(r.points)

        val grid = findViewById<GridLayout>(R.id.grid)
        fun cell(label: String, value: String, color: Int = R.color.ink) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
            box.addView(TextView(this).apply { text = label; setTextAppearance(R.style.Label) })
            box.addView(TextView(this).apply {
                text = value; textSize = 22f; setTextColor(ContextCompat.getColor(context, color))
                paint.isFakeBoldText = true; fontFeatureSettings = "tnum"
            })
            grid.addView(box, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)))
        }
        cell("거리", "${Format.km(s.distance)} km")
        cell("전체 시간", Format.duration(s.elapsed))
        cell("평균 페이스", Format.pace(s.paceSecPerKm))
        cell("움직인 시간", Format.duration(s.moving))
        cell("뛴 거리", "${Format.km(s.runDistance)} km", R.color.run)
        cell("뛰기 페이스", Format.pace(s.runPaceSecPerKm), R.color.run)
        cell("걸은 거리", "${Format.km(s.walkDistance)} km", R.color.walk)
        cell("걸은 시간", Format.duration(s.walkTime), R.color.walk)

        val splits = findViewById<LinearLayout>(R.id.splits)
        if (s.splits.isEmpty()) {
            splits.addView(TextView(this).apply { text = "1km를 넘으면 구간 기록이 나옵니다"; setTextAppearance(R.style.Label) })
        }
        s.splits.forEachIndexed { i, ms ->
            splits.addView(TextView(this).apply {
                text = "${i + 1} km    ${Format.pace(ms / 1000.0)}"
                textSize = 17f; setTextColor(ContextCompat.getColor(context, R.color.ink))
                fontFeatureSettings = "tnum"; gravity = Gravity.START; setPadding(8, 10, 8, 10)
            })
        }

        findViewById<MaterialButton>(R.id.btnGpx).setOnClickListener { exportGpx(title) }
        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("이 기록을 지울까요? 지운 기록은 되살릴 수 없습니다.")
                .setPositiveButton("지우기") { _, _ -> RunStore.delete(this, r.id); finish() }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun exportGpx(name: String) {
        val r = run ?: return
        val dir = File(cacheDir, "gpx").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.KOREA).format(Date(r.id))
        val file = File(dir, "runmate-$stamp.gpx")
        file.writeText(Gpx.write(r, name))
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "GPX 파일 보내기"))
    }
}
