package io.github.sby1234ai.runmate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.sby1234ai.runmate.data.Prefs
import io.github.sby1234ai.runmate.data.RunStore
import io.github.sby1234ai.runmate.data.WeatherRepo
import io.github.sby1234ai.runmate.logic.DemoRun
import io.github.sby1234ai.runmate.logic.Format
import io.github.sby1234ai.runmate.logic.IntervalPlan
import io.github.sby1234ai.runmate.logic.Motion
import io.github.sby1234ai.runmate.logic.Records
import io.github.sby1234ai.runmate.logic.RunStats
import io.github.sby1234ai.runmate.logic.Sky
import io.github.sby1234ai.runmate.logic.Weather
import io.github.sby1234ai.runmate.logic.WeatherNow
import io.github.sby1234ai.runmate.ui.RunnerView
import io.github.sby1234ai.runmate.ui.SkyView
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var sky: SkyView
    private lateinit var home: View
    private lateinit var runPanel: View
    private lateinit var runner: RunnerView
    private lateinit var motionView: TextView
    private lateinit var timeView: TextView
    private lateinit var timeLabel: TextView
    private lateinit var distView: TextView
    private lateinit var paceView: TextView
    private lateinit var runView: TextView
    private lateinit var walkView: TextView
    private lateinit var statusView: TextView
    private lateinit var intervalNow: TextView
    private val ticker = Handler(Looper.getMainLooper())
    private var openedSavedId: Long? = null
    private var demoWeather: WeatherNow? = null

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
        sky = findViewById(R.id.sky)
        home = findViewById(R.id.home)
        runPanel = findViewById(R.id.runPanel)
        runner = findViewById(R.id.runner)
        motionView = findViewById(R.id.motion)
        timeView = findViewById(R.id.time)
        timeLabel = findViewById(R.id.timeLabel)
        distView = findViewById(R.id.distance)
        paceView = findViewById(R.id.pace)
        runView = findViewById(R.id.runDist)
        walkView = findViewById(R.id.walkDist)
        statusView = findViewById(R.id.status)
        intervalNow = findViewById(R.id.intervalNow)

        findViewById<View>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<MaterialButton>(R.id.btnStart).setOnClickListener { startRecording() }
        val stop = findViewById<MaterialButton>(R.id.btnStop)
        stop.setOnClickListener {
            Toast.makeText(this, "실수로 멈추지 않도록, 정지는 길게 누르세요", Toast.LENGTH_SHORT).show()
        }
        stop.setOnLongClickListener {
            if (RunRecorder.recording) { RecordingService.stop(this); true } else false
        }
        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        handleDemo(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDemo(intent)
    }

    override fun onStart() {
        super.onStart()
        openedSavedId = RunRecorder.snapshot().savedRunId
        RunRecorder.addListener(listener)
        ticker.post(tick)
        renderHome()
        loadWeather()
    }

    override fun onStop() {
        RunRecorder.removeListener(listener)
        ticker.removeCallbacks(tick)
        super.onStop()
    }

    private val tick = object : Runnable {
        override fun run() {
            val s = RunRecorder.snapshot()
            if (s.recording) { renderTime(s); renderInterval(s) }
            ticker.postDelayed(this, 1000)
        }
    }

    private fun hasFine() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasCoarse() = hasFine() ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startRecording() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) need.add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) RecordingService.start(this) else askPermissions.launch(missing.toTypedArray())
    }

    // ───────── 날씨 배경 ─────────

    private fun loadWeather() {
        demoWeather?.let { applyWeather(it); return }
        if (!Prefs.weather(this)) { applyWeather(null, "날씨 배경 꺼짐", "설정에서 켤 수 있습니다"); return }
        val cached = WeatherRepo.cached(this)
        applyWeather(cached, if (cached == null) "날씨 정보 없음" else null,
            if (cached == null && !hasCoarse()) "위치 권한을 주면 날씨에 맞춰 바뀝니다" else null)
        if (!WeatherRepo.needsRefresh(this) || !hasCoarse()) return
        try {
            LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { loc ->
                if (loc == null || isFinishing) return@addOnSuccessListener
                WeatherRepo.refresh(this, loc.latitude, loc.longitude) { w -> if (w != null && !isFinishing) applyWeather(w) }
            }
        } catch (_: SecurityException) {}
    }

    private fun applyWeather(w: WeatherNow?, label: String? = null, meta: String? = null) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = w?.let { !it.isDay } ?: (hour < 6 || hour >= 19)
        sky.setWeather(w?.sky ?: Sky.PARTLY, night)
        val (top, bottom) = sky.edgeColors()
        window.statusBarColor = top
        window.navigationBarColor = bottom
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        findViewById<TextView>(R.id.greeting).text = when (hour) {
            in 5..10 -> "좋은 아침이에요"
            in 11..16 -> "좋은 오후예요"
            in 17..20 -> "좋은 저녁이에요"
            else -> "늦은 시간이에요"
        }
        val temp = findViewById<TextView>(R.id.temp)
        val tip = findViewById<TextView>(R.id.weatherTip)
        if (w == null) {
            temp.text = "--°"
            findViewById<TextView>(R.id.weatherLabel).text = label ?: "날씨 정보 없음"
            findViewById<TextView>(R.id.weatherMeta).text = meta ?: "잠시 뒤 다시 확인합니다"
            tip.text = if (night) "어두워요. 밝은 옷과 불빛을 챙기세요" else "오늘도 가볍게 달려 볼까요"
            findViewById<TextView>(R.id.credit).text = ""
        } else {
            temp.text = Weather.tempText(w.tempC)
            findViewById<TextView>(R.id.weatherLabel).text = Weather.label(w.sky)
            val feels = w.feelsC?.let { "체감 ${Weather.tempText(it)} · " } ?: ""
            findViewById<TextView>(R.id.weatherMeta).text = "${feels}바람 ${"%.1f".format(w.windMs)}m/s"
            tip.text = Weather.tip(w)
            findViewById<TextView>(R.id.credit).text = "날씨 정보: Open-Meteo.com (CC BY 4.0)"
        }
    }

    // ───────── 대기 화면(타일) ─────────

    private class Tile(val icon: Int, val color: Int, val title: String, val sub: String, val onClick: () -> Unit)

    private fun renderHome() {
        renderSetup()
        val grid = findViewById<GridLayout>(R.id.tiles)
        val plan = IntervalPlan.byId(Prefs.interval(this))
        fun onOff(v: Boolean) = if (v) "켜짐" else "꺼짐"
        val tiles = listOf(
            Tile(R.drawable.ic_timer, R.color.walk, "인터벌 코칭", plan?.shortName ?: "자유롭게 달리기") { chooseInterval() },
            Tile(R.drawable.ic_list, R.color.ink, "내 기록", "불러오는 중…") { startActivity(Intent(this, RunsActivity::class.java)) },
            Tile(R.drawable.ic_trophy, R.color.gold, "최고 기록", "불러오는 중…") { startActivity(Intent(this, RunsActivity::class.java)) },
            Tile(R.drawable.ic_volume, R.color.sky, "음성 안내", onOff(Prefs.voice(this))) {
                Prefs.setVoice(this, !Prefs.voice(this)); renderHome()
            },
            Tile(R.drawable.ic_pause, R.color.still, "자동 일시정지", onOff(Prefs.autoPause(this))) {
                Prefs.setAutoPause(this, !Prefs.autoPause(this)); renderHome()
            },
            Tile(R.drawable.ic_settings, R.color.sub, "설정", "지도 · 날씨 · 저장 공간") {
                startActivity(Intent(this, SettingsActivity::class.java))
            },
        )
        grid.removeAllViews()
        val gap = (6 * resources.displayMetrics.density).toInt()
        val views = tiles.map { t ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_tile, grid, false)
            v.findViewById<View>(R.id.iconBg).background.mutate().setTint(ContextCompat.getColor(this, t.color))
            v.findViewById<ImageView>(R.id.icon).apply {
                setImageResource(t.icon); imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
            }
            v.findViewById<TextView>(R.id.title).text = t.title
            v.findViewById<TextView>(R.id.sub).text = t.sub
            v.setOnClickListener { t.onClick() }
            v.contentDescription = "${t.title}, ${t.sub}"
            val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f))
            lp.width = 0; lp.setMargins(gap, gap, gap, gap)
            grid.addView(v, lp)
            v
        }
        // 기록 요약은 파일을 읽어야 하므로 뒤에서 계산합니다
        Thread {
            val runs = RunStore.ids(this).mapNotNull { RunStore.load(this, it) }
            val items = runs.map { Records.Item(it.id, RunStats.of(it.points).distance) }
            val sum = Records.summary(items, System.currentTimeMillis())
            val best1k = runs.mapNotNull { Records.bestEffort(it.points, 1000.0) }.minOrNull()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                views[1].findViewById<TextView>(R.id.sub).text = if (runs.isEmpty()) "아직 없음" else "${runs.size}개 · ${Format.km(sum.all.distance)} km"
                views[2].findViewById<TextView>(R.id.sub).text = best1k?.let { "1km ${Format.duration(it)}" } ?: "1km를 달려 보세요"
                findViewById<TextView>(R.id.weekLine).text =
                    "이번 주 ${Format.km(sum.week.distance)} km · ${sum.week.count}번 달림"
            }
        }.start()
    }

    private fun chooseInterval() {
        val plans = IntervalPlan.PRESETS
        val names = (listOf("끄기 · 자유롭게 달리기") + plans.map { "${it.name}  (${it.totalSec / 60}분)" }).toTypedArray()
        val cur = Prefs.interval(this)
        val checked = plans.indexOfFirst { it.id == cur }.let { if (it < 0) 0 else it + 1 }
        MaterialAlertDialogBuilder(this)
            .setTitle("인터벌 코칭")
            .setSingleChoiceItems(names, checked) { d, which ->
                Prefs.setInterval(this, if (which == 0) 0 else plans[which - 1].id)
                renderHome()
                d.dismiss()
                if (which != 0 && !Prefs.voice(this)) {
                    Toast.makeText(this, "음성 안내가 꺼져 있어 화면으로만 알려 드립니다", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    // ───────── 달리는 중 화면 ─────────

    private fun renderTime(s: RunRecorder.Snapshot) {
        val now = System.currentTimeMillis()
        val auto = Prefs.autoPause(this)
        timeView.text = Format.duration(if (auto) s.activeMs(now) else (s.endedAt ?: now) - s.startedAt)
        timeLabel.text = if (auto) "운동 시간 (멈춘 시간 제외)" else "전체 시간"
    }

    private fun renderInterval(s: RunRecorder.Snapshot) {
        val plan = s.plan
        if (!s.recording || plan == null) { intervalNow.text = ""; return }
        val ph = plan.phaseAt((System.currentTimeMillis() - s.startedAt) / 1000)
        val left = Format.duration(ph.remainingSec * 1000L)
        intervalNow.text = when (ph.kind) {
            IntervalPlan.Kind.WARMUP -> "준비 걷기 · $left"
            IntervalPlan.Kind.RUN -> "${ph.rep}/${plan.reps} 뛰기 · $left"
            IntervalPlan.Kind.WALK -> "${ph.rep}/${plan.reps} 걷기 · $left"
            IntervalPlan.Kind.DONE -> "인터벌 완료"
        }
    }

    private fun render(s: RunRecorder.Snapshot) {
        home.visibility = if (s.recording) View.GONE else View.VISIBLE
        runPanel.visibility = if (s.recording) View.VISIBLE else View.GONE
        sky.dim = s.recording

        val paused = s.recording && s.paused && Prefs.autoPause(this)
        val (label, color, tint) = when {
            paused -> Triple("자동 일시정지", R.color.still, R.color.still_tint)
            s.motion == Motion.RUN -> Triple("뛰는 중", R.color.run, R.color.run_tint)
            s.motion == Motion.WALK -> Triple("걷는 중", R.color.walk, R.color.walk_tint)
            else -> Triple("멈춤", R.color.still, R.color.still_tint)
        }
        motionView.text = label
        motionView.setTextColor(ContextCompat.getColor(this, color))
        motionView.background.mutate().setTint(ContextCompat.getColor(this, tint))
        runner.motion = s.motion
        runner.paused = paused
        runner.ringColor = when {
            paused -> 0x26FFFFFF
            s.motion == Motion.RUN -> 0x55F2542D
            s.motion == Motion.WALK -> 0x551B998B
            else -> 0x26FFFFFF
        }

        if (s.recording) { renderTime(s); renderInterval(s) }
        val st = s.stats
        distView.text = Format.km(st.distance)
        paceView.text = Format.pace(st.paceSecPerKm)
        runView.text = Format.km(st.runDistance)
        walkView.text = Format.km(st.walkDistance)

        statusView.text = when {
            !s.recording -> ""
            s.lastAccuracy == null -> "GPS 신호를 찾는 중입니다. 하늘이 트인 곳에서 잠시 기다려 주세요."
            else -> {
                val acc = "GPS ±${s.lastAccuracy.toInt()}m"
                val ar = if (s.usingActivityRecognition) "걷기·뛰기 자동 인식" else "속도로 걷기·뛰기 구분"
                "$acc · $ar"
            }
        }

        // 방금 정지해서 저장된 기록이면 지도와 함께 결과 화면을 엽니다
        val saved = s.savedRunId
        if (!s.recording && saved != null && saved != openedSavedId) {
            openedSavedId = saved
            renderHome()
            startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, saved))
        }
    }

    private fun renderSetup() {
        val pm = getSystemService(PowerManager::class.java)
        val card = findViewById<View>(R.id.setupCard)
        if (pm.isIgnoringBatteryOptimizations(packageName)) { card.visibility = View.GONE; return }
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.setupText).text =
            "휴대폰의 배터리 절약 기능이 기록 중인 앱을 멈출 수 있습니다. 배터리 설정에서 「런메이트」를 「최적화하지 않음」(갤럭시: 제한 없음)으로 바꿔 주세요."
    }

    // ───────── 화면 점검용(디버그 빌드에서만) ─────────

    private fun handleDemo(i: Intent?) {
        if (!BuildConfig.DEBUG) return
        val demo = i?.getStringExtra("demo") ?: return
        i.getStringExtra("sky")?.let { name ->
            val night = name == "NIGHT"
            val skyVal = if (night) Sky.CLEAR else runCatching { Sky.valueOf(name) }.getOrDefault(Sky.CLEAR)
            val code = when (skyVal) { Sky.CLEAR -> 0; Sky.PARTLY -> 2; Sky.CLOUDY -> 3; Sky.FOG -> 45; Sky.RAIN -> 61; Sky.SNOW -> 73; Sky.STORM -> 95 }
            val temp = when (skyVal) { Sky.SNOW -> -2.0; Sky.RAIN, Sky.STORM -> 12.0; else -> 17.0 }
            demoWeather = WeatherNow(temp, temp - 1.5, code, 2.4, !night, System.currentTimeMillis())
            demoWeather?.let { applyWeather(it) }
        }
        val now = System.currentTimeMillis()
        if (RunStore.ids(this).isEmpty()) {
            for (k in 0..2) RunStore.save(this, DemoRun.make(now - (k * 2 + 1) * 86_400_000L, scale = 1.0 - k * 0.25))
        }
        when (demo) {
            "run", "walk" -> {
                val r = DemoRun.make(now - 1_200_000L)
                val part = r.points.take(if (demo == "run") 520 else 700)
                RunRecorder.demo(part, if (demo == "run") Motion.RUN else Motion.WALK, now)
            }
            "detail" -> startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, RunStore.ids(this).first()))
            "runs" -> startActivity(Intent(this, RunsActivity::class.java))
            "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
