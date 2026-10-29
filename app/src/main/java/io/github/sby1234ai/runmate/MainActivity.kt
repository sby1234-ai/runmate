package io.github.sby1234ai.runmate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import io.github.sby1234ai.runmate.logic.Format
import io.github.sby1234ai.runmate.logic.Motion

class MainActivity : AppCompatActivity() {
    private lateinit var motionView: TextView
    private lateinit var timeView: TextView
    private lateinit var distView: TextView
    private lateinit var paceView: TextView
    private lateinit var runView: TextView
    private lateinit var walkView: TextView
    private lateinit var statusView: TextView
    private lateinit var startBtn: MaterialButton
    private lateinit var startHint: TextView
    private val ticker = Handler(Looper.getMainLooper())
    private var openedSavedId: Long? = null

    private val listener = RunRecorder.Listener { render(it) }

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasFine()
        if (!fine) {
            Toast.makeText(this, "위치 권한이 있어야 경로와 거리를 기록할 수 있습니다", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        RecordingService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        motionView = findViewById(R.id.motion)
        timeView = findViewById(R.id.time)
        distView = findViewById(R.id.distance)
        paceView = findViewById(R.id.pace)
        runView = findViewById(R.id.runDist)
        walkView = findViewById(R.id.walkDist)
        statusView = findViewById(R.id.status)
        startBtn = findViewById(R.id.btnStart)
        startHint = findViewById(R.id.startHint)

        findViewById<MaterialButton>(R.id.btnRuns).setOnClickListener {
            startActivity(Intent(this, RunsActivity::class.java))
        }
        startBtn.setOnClickListener {
            if (RunRecorder.recording) {
                Toast.makeText(this, "실수로 멈추지 않도록, 정지는 길게 누르세요", Toast.LENGTH_SHORT).show()
            } else {
                startRecording()
            }
        }
        startBtn.setOnLongClickListener {
            if (RunRecorder.recording) { RecordingService.stop(this); true } else false
        }
        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onStart() {
        super.onStart()
        openedSavedId = RunRecorder.snapshot().savedRunId
        RunRecorder.addListener(listener)
        ticker.post(tick)
        renderSetup()
    }

    override fun onStop() {
        RunRecorder.removeListener(listener)
        ticker.removeCallbacks(tick)
        super.onStop()
    }

    private val tick = object : Runnable {
        override fun run() {
            val s = RunRecorder.snapshot()
            if (s.recording) timeView.text = Format.duration(System.currentTimeMillis() - s.startedAt)
            ticker.postDelayed(this, 1000)
        }
    }

    private fun hasFine() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startRecording() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) need.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) RecordingService.start(this) else askPermissions.launch(missing.toTypedArray())
    }

    private fun render(s: RunRecorder.Snapshot) {
        val (label, color, tint) = when {
            !s.recording -> Triple("대기 중", R.color.still, R.color.still_tint)
            s.motion == Motion.RUN -> Triple("뛰는 중", R.color.run, R.color.run_tint)
            s.motion == Motion.WALK -> Triple("걷는 중", R.color.walk, R.color.walk_tint)
            else -> Triple("멈춤", R.color.still, R.color.still_tint)
        }
        motionView.text = label
        motionView.setTextColor(ContextCompat.getColor(this, color))
        motionView.background.mutate().setTint(ContextCompat.getColor(this, tint))

        val st = s.stats
        if (!s.recording && s.savedRunId == null) timeView.text = Format.duration(0)
        if (!s.recording && s.savedRunId != null) timeView.text = Format.duration(st.elapsed)
        distView.text = Format.km(st.distance)
        paceView.text = Format.pace(st.paceSecPerKm)
        runView.text = Format.km(st.runDistance)
        walkView.text = Format.km(st.walkDistance)

        startBtn.text = if (s.recording) "정지" else "시작"
        startBtn.backgroundTintList = ContextCompat.getColorStateList(this, if (s.recording) R.color.run else R.color.ink)
        startHint.text = if (s.recording) "정지하려면 길게 누르세요" else "누르면 바로 기록을 시작합니다"

        statusView.text = when {
            !s.recording -> ""
            s.lastAccuracy == null -> "GPS 신호를 찾는 중입니다. 하늘이 트인 곳에서 잠시 기다려 주세요."
            else -> {
                val acc = "GPS 정확도 ±${s.lastAccuracy.toInt()}m"
                val ar = if (s.usingActivityRecognition) "걷기·뛰기 자동 인식 사용 중" else "걷기·뛰기는 속도로 구분 중"
                "$acc · $ar"
            }
        }

        // 방금 정지해서 저장된 기록이면 상세 화면을 한 번 열어 줍니다
        val saved = s.savedRunId
        if (!s.recording && saved != null && saved != openedSavedId) {
            openedSavedId = saved
            startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, saved))
        }
    }

    private fun renderSetup() {
        val pm = getSystemService(PowerManager::class.java)
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)
        val card = findViewById<android.view.View>(R.id.setupCard)
        if (batteryOk) { card.visibility = android.view.View.GONE; return }
        card.visibility = android.view.View.VISIBLE
        findViewById<TextView>(R.id.setupText).text =
            "휴대폰의 배터리 절약 기능이 기록 중인 앱을 멈출 수 있습니다. 배터리 설정에서 「런메이트」를 「최적화하지 않음」(갤럭시: 제한 없음)으로 바꿔 주세요."
    }
}
