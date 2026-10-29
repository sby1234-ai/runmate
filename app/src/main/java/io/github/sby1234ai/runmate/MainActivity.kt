package io.github.sby1234ai.runmate

import io.github.sby1234ai.runmate.ui.showGlass
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
    /** 알림 「종료」로 끝냈으면, 저장되는 대로 결과 화면을 엽니다(화면이 늦게 켜져도 놓치지 않게). */
    private var openDetailOnSave = false
    private var demoWeather: WeatherNow? = null
    private var currentSky = Sky.PARTLY
    private var currentNight = false
    private var darkTiles = false
    private var running = false
    /** 이번에 켠 동안 범주(bg_clear 등)별로 고른 사진 — 앱을 다시 켜면 새로 고름 */
    private val bgPick = mutableMapOf<String, Int>()
    private var shownBg = 0

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
        // 사진이 상태 표시줄·내비게이션 바 뒤까지 깔리게 하고, 글자·버튼은 그 높이만큼 안쪽에 둡니다
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = 0x00000000
        window.navigationBarColor = 0x00000000
        for (id in intArrayOf(R.id.home, R.id.runPanel)) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(id)) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, bars.top, 0, bars.bottom)
                insets
            }
        }
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

        // 유리 판(시안 확정): 각진 모서리, 빛 테두리, 판 안에 흐린 배경
        io.github.sby1234ai.runmate.ui.Glass.apply(findViewById(R.id.btnSettings), io.github.sby1234ai.runmate.ui.GlassKind.PANEL, true)
        io.github.sby1234ai.runmate.ui.Glass.apply(findViewById(R.id.weatherTip), io.github.sby1234ai.runmate.ui.GlassKind.CHIP, false)
        io.github.sby1234ai.runmate.ui.Glass.apply(findViewById(R.id.btnStart), io.github.sby1234ai.runmate.ui.GlassKind.STRONG, true)
        io.github.sby1234ai.runmate.ui.Glass.apply(findViewById(R.id.setupCard), io.github.sby1234ai.runmate.ui.GlassKind.PANEL, false)
        io.github.sby1234ai.runmate.ui.Glass.apply(findViewById(R.id.statsA), io.github.sby1234ai.runmate.ui.GlassKind.PANEL, false)
        io.github.sby1234ai.runmate.ui.Glass.apply(findViewById(R.id.statsB), io.github.sby1234ai.runmate.ui.GlassKind.PANEL, false)
        io.github.sby1234ai.runmate.ui.Glass.apply(findViewById(R.id.btnPause), io.github.sby1234ai.runmate.ui.GlassKind.PANEL, true)
        io.github.sby1234ai.runmate.ui.Glass.apply(findViewById(R.id.btnStop), io.github.sby1234ai.runmate.ui.GlassKind.RUN, true)
        findViewById<View>(R.id.startIcon).background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 8 * resources.displayMetrics.density; setColor(ContextCompat.getColor(this@MainActivity, R.color.walk))
        }
        for (id in intArrayOf(R.id.home, R.id.runPanel)) {
            findViewById<View>(id).setOnScrollChangeListener { _, _, _, _, _ -> io.github.sby1234ai.runmate.ui.Backdrop.invalidateAll() }
        }
        // 앱을 켤 때마다 배경 사진을 새로 고름
        if (savedInstanceState == null) bgPick.clear()
        findViewById<View>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btnStart).setOnClickListener { startRecording() }
        findViewById<View>(R.id.btnPause).setOnClickListener {
            val s = RunRecorder.snapshot()
            // 멈춘 상태(자동 포함)면 다시 시작, 아니면 일시정지
            if (s.recording) RecordingService.pause(this, !s.paused)
        }
        val stop = findViewById<View>(R.id.btnStop)
        stop.setOnClickListener {
            Toast.makeText(this, "실수로 끝나지 않도록, 운동 종료는 길게 누르세요", Toast.LENGTH_SHORT).show()
        }
        stop.setOnLongClickListener {
            if (RunRecorder.recording) { RecordingService.stop(this); true } else false
        }
        findViewById<MaterialButton>(R.id.btnBattery).setOnClickListener { requestBatteryExemption() }
        // 뒤로가기로 바로 꺼지지 않게 한 번 묻기. 기록 중이면 닫아도 기록은 알림과 함께 계속됨
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val recording = RunRecorder.recording
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("런메이트를 닫을까요?")
                    .setMessage(
                        if (recording) "화면만 닫히고 기록은 계속됩니다. 알림에서 시간·거리를 보고 일시정지·종료할 수 있습니다."
                        else null,
                    )
                    .setPositiveButton("닫기") { _, _ -> if (recording) moveTaskToBack(true) else finish() }
                    .setNegativeButton("취소", null)
                    .showGlass()
            }
        })
        // 잠금 화면에서 열렸을 때도 바로 잠금 위에 보이도록, 기록 중이면 첫 화면을 그리기 전에 설정
        if (RunRecorder.recording && Prefs.lockScreen(this)) showOnLockScreen(true)
        handleDemo(intent)
        handleStop(intent)
    }

    override fun onResume() {
        super.onResume()
        // 잠금 화면 위에 띄우려고 올린 알림은 화면이 보이면 지웁니다
        getSystemService(android.app.NotificationManager::class.java).cancel(RecordingService.LOCK_NOTIF_ID)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDemo(intent)
        handleStop(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!openDetailOnSave) openedSavedId = RunRecorder.snapshot().savedRunId
        RunRecorder.addListener(listener)
        ticker.post(tick)
        renderHome()
        loadWeather()
        // 새 버전: 허용 화면에서 돌아왔으면 설치를 이어 가고, 하루 한 번 확인
        Updater.resumePending(this)
        if (!BuildConfig.DEBUG) Updater.checkDaily(this)
        // 실시간 알림(초록 칩·나우 바)이 꺼져 있거나 잠금 화면에서 내용이 숨겨지면 안내(화면 점검용 디버그 빌드는 건너뜀)
        if (!BuildConfig.DEBUG && !RunRecorder.recording) LiveNotif.checkOnStart(this)
        // 휴대폰이 앱을 강제로 꺼서 멈춘 기록이 있으면 그 자리부터 이어서 기록
        if (!RunRecorder.recording && io.github.sby1234ai.runmate.data.CheckpointStore.exists(this) && hasFine()) {
            RecordingService.restore(this)
            Toast.makeText(this, "멈췄던 기록을 이어서 합니다", Toast.LENGTH_LONG).show()
        }
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
            // 초가 바뀌는 순간에 맞춰 다음 갱신(1초마다 정확히). 알림의 시계와 같은 기준점이라 같은 초를 보여 줍니다
            val delay = if (s.recording && !s.paused) 1000L - (s.activeNow() % 1000L) + 15L else 1000L
            ticker.postDelayed(this, delay)
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
        askBatteryOnce()
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
        val skyNow = w?.sky ?: Sky.PARTLY
        sky.setWeather(skyNow, night)
        currentSky = skyNow; currentNight = night
        applyBackground()
        styleTiles()
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

    /**
     * 날씨(와 달리는 중인지)에 맞는 사진이 앱에 들어 있으면 사진을, 없으면 그라데이션 하늘을 씁니다.
     * 사진 이름: bg_clear, bg_cloudy, bg_rain, bg_snow, bg_night, bg_run_day, bg_run_night (res/drawable-nodpi)
     */
    private fun applyBackground() {
        val base = when {
            running -> if (currentNight) "bg_run_night" else "bg_run_day"
            currentNight -> "bg_night"
            else -> when (currentSky) {
                Sky.CLEAR, Sky.PARTLY -> "bg_clear"
                Sky.CLOUDY, Sky.FOG -> "bg_cloudy"
                Sky.RAIN, Sky.STORM -> "bg_rain"
                Sky.SNOW -> "bg_snow"
            }
        }
        val photoView = findViewById<ImageView>(R.id.bgPhoto)
        // 같은 날씨라도 앱을 켤 때마다 다른 사진: bg_clear_1 … 중 하나를 무작위로, 지난번 사진은 피함
        @Suppress("DiscouragedApi")
        val ids = (1..8).map { resources.getIdentifier("${base}_$it", "drawable", packageName) }.filter { it != 0 }
        val id = if (ids.isEmpty()) 0 else bgPick.getOrPut(base) {
            val sp = getSharedPreferences("bg", MODE_PRIVATE)
            val last = sp.getInt(base, 0)
            val choices = ids.filter { it != last }.ifEmpty { ids }
            choices.random().also { sp.edit().putInt(base, it).apply() }
        }
        if (id != 0) {
            if (shownBg != id) { photoView.setImageResource(id); shownBg = id; io.github.sby1234ai.runmate.ui.Backdrop.set(resources, id, photoView) }
            photoView.visibility = View.VISIBLE; sky.photo = true
        } else {
            photoView.setImageDrawable(null); photoView.visibility = View.GONE; sky.photo = false
            shownBg = 0; io.github.sby1234ai.runmate.ui.Backdrop.clear()
        }
    }

    /** 어두운 배경(비·밤·사진)에서는 어두운 반투명 카드에 흰 글자, 밝은 배경에서는 흰 카드에 짙은 글자 */
    private fun styleTiles() {
        val grid = findViewById<GridLayout>(R.id.tiles)
        for (i in 0 until grid.childCount) {
            val v = grid.getChildAt(i)
            // 시안: 사진 위에는 항상 어두운 유리 카드에 흰 글자
            io.github.sby1234ai.runmate.ui.Glass.apply(v, io.github.sby1234ai.runmate.ui.GlassKind.PANEL, true)
            v.findViewById<TextView>(R.id.title).setTextColor(0xFFFFFFFF.toInt())
            v.findViewById<TextView>(R.id.sub).setTextColor(0xB3FFFFFF.toInt())
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
            // 아이콘: 반투명 색 동그라미 안에 그 색 아이콘
            val c = ContextCompat.getColor(this, t.color)
            v.findViewById<View>(R.id.iconBg).background.mutate().setTint(0x12FFFFFF)
            v.findViewById<ImageView>(R.id.icon).apply {
                setImageResource(t.icon); imageTintList = ColorStateList.valueOf(c)
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
        styleTiles()
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
        // 항목: 윗줄 「이름 · 걸리는 시간」, 아랫줄(작고 옅게) 「뛰기·걷기 구성」 — 한 줄에 다 넣으면 시간만 다음 줄로 떨어져 답답함
        fun item(title: String, detail: String): CharSequence {
            val sb = android.text.SpannableStringBuilder(title)
            if (detail.isNotEmpty()) {
                val start = sb.length
                sb.append("\n").append(detail)
                sb.setSpan(android.text.style.RelativeSizeSpan(0.84f), start, sb.length, 0)
                sb.setSpan(android.text.style.ForegroundColorSpan(0xB3FFFFFF.toInt()), start, sb.length, 0)
            }
            return sb
        }
        val names: Array<CharSequence> = (listOf(item("끄기", "자유롭게 달리기")) + plans.map {
            item("${it.shortName} · ${it.totalSec / 60}분", it.name.substringAfter(" · ", ""))
        }).toTypedArray()
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
            .showGlass()
    }

    // ───────── 달리는 중 화면 ─────────

    private fun renderTime(s: RunRecorder.Snapshot) {
        // 지금 몇 km째인지와 그 구간 페이스, 걸음 수, 케이던스
        val lap = io.github.sby1234ai.runmate.logic.Lap.of(s.stats, s.activeNow())
        findViewById<TextView>(R.id.lapPace).text = Format.pace(lap.paceSecPerKm)
        findViewById<TextView>(R.id.lapLabel).text = "${lap.number}km째 페이스"
        findViewById<TextView>(R.id.steps).text = String.format(java.util.Locale.KOREA, "%,d", s.steps)
        findViewById<TextView>(R.id.cadence).text = s.cadence?.toString() ?: "--"
        val nowE = android.os.SystemClock.elapsedRealtime()
        val shown = s.activeMs(nowE) / 1000
        timeView.text = android.text.format.DateUtils.formatElapsedTime(shown)
        // 디버그 빌드에서만: 화면에 보인 초와 그 순간의 기기 시계(에뮬레이터 시간 일치 점검용)
        if (BuildConfig.DEBUG) android.util.Log.d("RMTIME", "app $nowE $shown")
        timeLabel.text = if (s.paused) "멈춤 — 시간이 흐르지 않습니다" else "운동 시간 (멈춘 시간 제외)"
    }

    private fun renderInterval(s: RunRecorder.Snapshot) {
        val plan = s.plan
        if (!s.recording || plan == null) { intervalNow.text = ""; return }
        val ph = plan.phaseAt(s.activeNow() / 1000)
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
        io.github.sby1234ai.runmate.ui.Backdrop.dim = s.recording
        if (running != s.recording) { running = s.recording; applyBackground(); showOnLockScreen(s.recording && Prefs.lockScreen(this)) }

        val autoPaused = s.recording && s.autoPaused
        val paused = s.recording && s.paused
        // 상태 칩: 뛰기는 주홍, 걷기는 청록, 멈춤은 반투명
        val (label, chip) = when {
            s.manualPaused -> "일시정지" to 0x66141B24
            autoPaused -> "자동 일시정지" to 0x66141B24
            s.motion == Motion.RUN -> "뛰는 중" to ContextCompat.getColor(this, R.color.run)
            s.motion == Motion.WALK -> "걷는 중" to ContextCompat.getColor(this, R.color.walk)
            else -> "멈춤" to 0x66141B24
        }
        motionView.text = label
        motionView.setTextColor(0xFFFFFFFF.toInt())
        motionView.background.mutate().setTint(chip)
        findViewById<TextView>(R.id.pauseText).text = if (paused) "다시 시작" else "일시정지"
        findViewById<ImageView>(R.id.pauseIcon).setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause_bars)
        runner.motion = s.motion
        runner.paused = paused
        runner.ringColor = when {
            paused -> 0x33FFFFFF
            s.motion == Motion.RUN -> 0xF2F2542D.toInt()
            s.motion == Motion.WALK -> 0xF21FB3A2.toInt()
            else -> 0x33FFFFFF
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
        if (!s.recording && saved != null && (saved != openedSavedId || openDetailOnSave)) {
            openedSavedId = saved; openDetailOnSave = false
            renderHome()
            startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, saved))
        }
    }

    /**
     * 삼성 헬스처럼, 달리는 중에 화면을 껐다 켜면 잠금을 풀지 않아도 이 화면(시간·거리·일시정지)이 바로 보이게 합니다.
     * 기록이 끝나면 다시 잠금 화면 뒤로 숨깁니다. 다른 화면(설정 등)으로 가려면 잠금을 풀어야 합니다.
     */
    private fun showOnLockScreen(on: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(on)
        else {
            @Suppress("DEPRECATION")
            if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    /** 배터리 최적화에서 빼 달라는 시스템 창을 바로 띄웁니다(안 되는 휴대폰이면 배터리 설정 목록). */
    private fun requestBatteryExemption() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** 첫 달리기 때 한 번만 물어봅니다. 거절해도 기록은 시작합니다. */
    private fun askBatteryOnce() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        if (p.getBoolean("askedBattery", false)) return
        p.edit().putBoolean("askedBattery", true).apply()
        requestBatteryExemption()
    }

    private fun renderSetup() {
        val pm = getSystemService(PowerManager::class.java)
        val card = findViewById<View>(R.id.setupCard)
        if (pm.isIgnoringBatteryOptimizations(packageName)) { card.visibility = View.GONE; return }
        card.visibility = View.VISIBLE
        findViewById<TextView>(R.id.setupText).text =
            "휴대폰의 배터리 절약 기능이 화면이 꺼진 동안 기록을 멈출 수 있습니다. 아래 버튼을 눌러 「허용」을 고르거나, 갤럭시는 설정 → 애플리케이션 → 런메이트 → 배터리에서 「제한 없음」으로 바꿔 주세요."
    }

    // ───────── 화면 점검용(디버그 빌드에서만) ─────────

    /** 알림의 「종료」를 눌러 열렸으면 기록을 끝냅니다(저장되면 결과 화면이 자동으로 열림). */
    private fun handleStop(i: Intent?) {
        if (i?.action == RecordingService.ACTION_STOP_FROM_NOTIF && RunRecorder.recording) {
            openDetailOnSave = true
            RecordingService.stop(this)
            Toast.makeText(this, "기록을 끝냈습니다", Toast.LENGTH_SHORT).show()
        }
    }

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
            // 공유 이미지 점검: 첫 기록의 공유 이미지를 앱 전용 폴더에 저장(에뮬레이터 점검이 가져감)
            "share" -> RunStore.ids(this).firstOrNull()?.let { RunStore.load(this, it) }?.let { r ->
                val title = java.text.SimpleDateFormat("M월 d일 (E) a h:mm", java.util.Locale.KOREA).format(java.util.Date(r.id))
                val bmp = io.github.sby1234ai.runmate.ui.ShareImage.render(this, r, RunStats.of(r.points), title)
                java.io.File(getExternalFilesDir(null), "share_check.png").outputStream()
                    .use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
            "runs" -> startActivity(Intent(this, RunsActivity::class.java))
            "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
            "record" -> RecordingService.start(this)   // 실제 기록 시작(에뮬레이터 위치로 백그라운드 점검)
            "update" -> Updater.check(this, quiet = false, currentOverride = "0.0.1", autoConfirm = true)
            "updateAsk" -> Updater.check(this, quiet = false, currentOverride = "0.0.1")
            "pause" -> RecordingService.pause(this, true)
            "resume" -> RecordingService.pause(this, false)
        }
    }
}
