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
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.sby1234ai.runmate.data.Prefs
import io.github.sby1234ai.runmate.logic.Format
import io.github.sby1234ai.runmate.logic.IntervalPlan
import io.github.sby1234ai.runmate.logic.Motion
import io.github.sby1234ai.runmate.ui.MapPanel

class MainActivity : AppCompatActivity() {
    private lateinit var motionView: TextView
    private lateinit var timeView: TextView
    private lateinit var timeLabel: TextView
    private lateinit var distView: TextView
    private lateinit var paceView: TextView
    private lateinit var runView: TextView
    private lateinit var walkView: TextView
    private lateinit var statusView: TextView
    private lateinit var intervalNow: TextView
    private lateinit var coachName: TextView
    private lateinit var startBtn: MaterialButton
    private lateinit var startHint: TextView
    private val ticker = Handler(Looper.getMainLooper())
    private var openedSavedId: Long? = null
    private var panel: MapPanel? = null
    private var drawnCount = -1

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
        timeLabel = findViewById(R.id.timeLabel)
        distView = findViewById(R.id.distance)
        paceView = findViewById(R.id.pace)
        runView = findViewById(R.id.runDist)
        walkView = findViewById(R.id.walkDist)
        statusView = findViewById(R.id.status)
        intervalNow = findViewById(R.id.intervalNow)
        coachName = findViewById(R.id.coachName)
        startBtn = findViewById(R.id.btnStart)
        startHint = findViewById(R.id.startHint)

        findViewById<MaterialButton>(R.id.btnRuns).setOnClickListener {
            startActivity(Intent(this, RunsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
        findViewById<View>(R.id.coachRow).setOnClickListener { chooseInterval() }
    }

    override fun onStart() {
        super.onStart()
        setupPanel()
        openedSavedId = RunRecorder.snapshot().savedRunId
        RunRecorder.addListener(listener)
        ticker.post(tick)
        renderSetup()
        renderCoach()
    }

    override fun onResume() { super.onResume(); panel?.onResume() }
    override fun onPause() { panel?.onPause(); super.onPause() }

    override fun onStop() {
        RunRecorder.removeListener(listener)
        ticker.removeCallbacks(tick)
        super.onStop()
    }

    override fun onDestroy() { panel?.onDestroy(); super.onDestroy() }

    /** 설정에서 지도를 켜고 끈 것이 바로 반영되도록, 화면이 다시 보일 때 맞는 종류로 만듭니다. */
    private fun setupPanel() {
        val useMap = Prefs.map(this)
        if (panel?.useMap == useMap) return
        panel?.onDestroy()
        val p = MapPanel(this, findViewById<FrameLayout>(R.id.mapBox), useMap)
        p.setEmptyText("시작하면 지나간 길이 여기에 그려집니다")
        panel = p
        drawnCount = -1
        findViewById<TextView>(R.id.mapNote).text =
            if (useMap) "지도 © OpenStreetMap 기여자 · 기록은 이 휴대폰에만 저장됩니다"
            else "지도 끔: 인터넷을 쓰지 않고 지나간 길 모양만 그립니다"
        val pts = RunRecorder.points()
        if (pts.isNotEmpty()) p.setPoints(pts, follow = RunRecorder.recording)
        else if (useMap && hasFine()) {
            // 처음 열었을 때 지도가 엉뚱한 곳을 보이지 않도록, 휴대폰이 이미 알고 있는 마지막 위치로 옮겨 둡니다
            try {
                LocationServices.getFusedLocationProviderClient(this).lastLocation
                    .addOnSuccessListener { loc -> if (loc != null && RunRecorder.points().isEmpty()) p.center(loc.latitude, loc.longitude) }
            } catch (_: SecurityException) {}
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val s = RunRecorder.snapshot()
            if (s.recording) {
                renderTime(s)
                renderInterval(s)
            }
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

    private fun chooseInterval() {
        if (RunRecorder.recording) {
            Toast.makeText(this, "인터벌은 기록을 시작하기 전에 고를 수 있습니다", Toast.LENGTH_SHORT).show()
            return
        }
        val plans = IntervalPlan.PRESETS
        val names = (listOf("끄기 · 자유롭게 달리기") + plans.map { "${it.name}  (${it.totalSec / 60}분)" }).toTypedArray()
        val cur = Prefs.interval(this)
        val checked = plans.indexOfFirst { it.id == cur }.let { if (it < 0) 0 else it + 1 }
        MaterialAlertDialogBuilder(this)
            .setTitle("인터벌 코칭")
            .setSingleChoiceItems(names, checked) { d, which ->
                Prefs.setInterval(this, if (which == 0) 0 else plans[which - 1].id)
                renderCoach()
                d.dismiss()
                if (which != 0 && !Prefs.voice(this)) {
                    Toast.makeText(this, "음성 안내가 꺼져 있어 화면으로만 알려 드립니다. 설정에서 켤 수 있습니다.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun renderCoach() {
        val plan = IntervalPlan.byId(Prefs.interval(this))
        coachName.text = plan?.name ?: "끄기 · 자유롭게 달리기"
    }

    private fun renderTime(s: RunRecorder.Snapshot) {
        val now = System.currentTimeMillis()
        val auto = Prefs.autoPause(this)
        timeView.text = when {
            s.recording || s.savedRunId != null -> Format.duration(if (auto) s.activeMs(now) else (s.endedAt ?: now) - s.startedAt)
            else -> Format.duration(0)
        }
        timeLabel.text = if (auto) "운동 시간 (멈춘 시간 제외)" else "전체 시간"
    }

    private fun renderInterval(s: RunRecorder.Snapshot) {
        val plan = s.plan
        if (!s.recording || plan == null) { intervalNow.text = ""; return }
        val ph = plan.phaseAt((System.currentTimeMillis() - s.startedAt) / 1000)
        val left = Format.duration(ph.remainingSec * 1000L)
        intervalNow.text = when (ph.kind) {
            IntervalPlan.Kind.WARMUP -> "준비 걷기 · $left 남음"
            IntervalPlan.Kind.RUN -> "${ph.rep}/${plan.reps} 뛰기 · $left 남음"
            IntervalPlan.Kind.WALK -> "${ph.rep}/${plan.reps} 걷기 · $left 남음"
            IntervalPlan.Kind.DONE -> "인터벌 완료"
        }
        intervalNow.setTextColor(ContextCompat.getColor(this, when (ph.kind) {
            IntervalPlan.Kind.RUN -> R.color.run
            IntervalPlan.Kind.DONE -> R.color.ink
            else -> R.color.walk
        }))
    }

    private fun render(s: RunRecorder.Snapshot) {
        val paused = s.recording && s.paused && Prefs.autoPause(this)
        val (label, color, tint) = when {
            !s.recording -> Triple("대기 중", R.color.still, R.color.still_tint)
            paused -> Triple("자동 일시정지", R.color.still, R.color.still_tint)
            s.motion == Motion.RUN -> Triple("뛰는 중", R.color.run, R.color.run_tint)
            s.motion == Motion.WALK -> Triple("걷는 중", R.color.walk, R.color.walk_tint)
            else -> Triple("멈춤", R.color.still, R.color.still_tint)
        }
        motionView.text = label
        motionView.setTextColor(ContextCompat.getColor(this, color))
        motionView.background.mutate().setTint(ContextCompat.getColor(this, tint))

        renderTime(s)
        renderInterval(s)
        val st = s.stats
        distView.text = Format.km(st.distance)
        paceView.text = Format.pace(st.paceSecPerKm)
        runView.text = Format.km(st.runDistance)
        walkView.text = Format.km(st.walkDistance)

        if (s.pointCount != drawnCount) {
            drawnCount = s.pointCount
            panel?.setPoints(RunRecorder.points(), follow = s.recording)
        }

        startBtn.text = if (s.recording) "정지" else "시작"
        startBtn.backgroundTintList = ContextCompat.getColorStateList(this, if (s.recording) R.color.run else R.color.ink)
        startHint.text = if (s.recording) "정지하려면 길게 누르세요" else "누르면 바로 기록을 시작합니다"
        findViewById<View>(R.id.coachRow).alpha = if (s.recording) 0.5f else 1f

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
        val card = findViewById<View>(R.id.setupCard)
        if (batteryOk) { card.visibility = View.GONE; return }
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.setupText).text =
            "휴대폰의 배터리 절약 기능이 기록 중인 앱을 멈출 수 있습니다. 배터리 설정에서 「런메이트」를 「최적화하지 않음」(갤럭시: 제한 없음)으로 바꿔 주세요."
    }
}
