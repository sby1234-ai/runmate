package io.github.sby1234ai.runmate

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.sby1234ai.runmate.data.RunStore
import io.github.sby1234ai.runmate.logic.Format
import io.github.sby1234ai.runmate.logic.RunStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RunsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_runs)
    }

    override fun onResume() {
        super.onResume()
        val list = findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        val ids = RunStore.ids(this)
        findViewById<View>(R.id.empty).visibility = if (ids.isEmpty()) View.VISIBLE else View.GONE
        val fmt = SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREA)
        var total = 0.0
        for (id in ids) {
            val run = RunStore.load(this, id) ?: continue
            val s = RunStats.of(run.points)
            total += s.distance
            val row = LayoutInflater.from(this).inflate(R.layout.item_run, list, false)
            row.findViewById<TextView>(R.id.date).text = fmt.format(Date(id))
            row.findViewById<TextView>(R.id.dist).text = Format.km(s.distance)
            row.findViewById<TextView>(R.id.meta).text = "${Format.duration(s.elapsed)}  ·  ${Format.pace(s.paceSecPerKm)}"
            val moving = (s.runDistance + s.walkDistance).coerceAtLeast(1.0)
            (row.findViewById<View>(R.id.ratioRun).layoutParams as LinearLayout.LayoutParams).weight = (s.runDistance / moving).toFloat()
            (row.findViewById<View>(R.id.ratioWalk).layoutParams as LinearLayout.LayoutParams).weight = (s.walkDistance / moving).toFloat()
            row.setOnClickListener {
                startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, id))
            }
            list.addView(row)
        }
        findViewById<TextView>(R.id.summary).text =
            if (ids.isEmpty()) "" else "기록 ${ids.size}개 · 모두 ${Format.km(total)} km"
    }
}
