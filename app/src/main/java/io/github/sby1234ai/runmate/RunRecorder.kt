package io.github.sby1234ai.runmate

import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.sby1234ai.runmate.logic.Activity
import io.github.sby1234ai.runmate.logic.Geo
import io.github.sby1234ai.runmate.logic.GpsSmoother
import io.github.sby1234ai.runmate.logic.IntervalPlan
import io.github.sby1234ai.runmate.logic.Motion
import io.github.sby1234ai.runmate.logic.MotionClassifier
import io.github.sby1234ai.runmate.logic.PauseTracker
import io.github.sby1234ai.runmate.logic.Run
import io.github.sby1234ai.runmate.logic.RunStats
import io.github.sby1234ai.runmate.logic.Stats
import io.github.sby1234ai.runmate.logic.StepTracker
import io.github.sby1234ai.runmate.logic.TrackPoint

/** 지금 진행 중인 기록 한 개를 들고 있는 곳. 기록 서비스가 채우고 화면이 읽습니다. */
object RunRecorder {
    data class Snapshot(
        val recording: Boolean,
        val startedAt: Long,
        val stats: Stats,
        val motion: Motion,
        val lastAccuracy: Float?,
        val usingActivityRecognition: Boolean,
        val savedRunId: Long?,
        val plan: IntervalPlan?,
        val pausedAt: Long?,
        val pausedTotal: Long,
        val pointCount: Int,
        val endedAt: Long?,
        /** 기록을 시작한 순간의 기기 경과 시계(SystemClock.elapsedRealtime). 운동 시간은 모두 이 시계로만 셉니다. */
        val startElapsed: Long,
        val endElapsed: Long?,
        /** 멈춘 이유가 「일시정지」 버튼이면 true, 자동이면 false (멈춰 있지 않으면 의미 없음) */
        val pausedByUser: Boolean,
        /** 이번 기록의 걸음 수와 지금 케이던스(분당 걸음, 모르면 null) */
        val steps: Int,
        val cadence: Int?,
    ) {
        /** 멈춘 상태(자동이든 버튼이든 하나로 셈) */
        val paused: Boolean get() = pausedAt != null
        val manualPaused: Boolean get() = paused && pausedByUser
        val autoPaused: Boolean get() = paused && !pausedByUser
        /**
         * 멈춘 시간을 뺀 운동 시간(ms). nowElapsed는 SystemClock.elapsedRealtime().
         * 앱 화면과 알림(잠금 화면) 시계가 같은 기준점에서 계산해 1초도 어긋나지 않게 합니다.
         * 휴대폰 시계를 바꾸거나 시간대가 바뀌어도 영향을 받지 않습니다.
         */
        fun activeMs(nowElapsed: Long): Long = PauseTracker.activeTime(startElapsed, endElapsed ?: nowElapsed, pausedAt, pausedTotal)
        fun activeNow(): Long = activeMs(SystemClock.elapsedRealtime())
    }

    fun interface Listener { fun onUpdate(s: Snapshot) }

    private const val MAX_ACCURACY_M = 30f
    private const val MAX_SPEED_MPS = 12.0

    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<Listener>()
    private val points = ArrayList<TrackPoint>()
    private var classifier = MotionClassifier()
    private var lastAccuracy: Float? = null
    private var stats: Stats = RunStats.of(emptyList())
    private var pause = PauseTracker()
    private var plan: IntervalPlan? = null
    private var endedAt: Long? = null
    private var startElapsed = 0L
    private var endElapsed: Long? = null
    private var demoMotion: Motion? = null
    private var breakNext = false
    private var stepper = StepTracker()
    private val smoother = GpsSmoother()

    @Volatile var recording = false; private set
    var startedAt = 0L; private set
    var usingActivityRecognition = false
    private var savedRunId: Long? = null

    fun start(now: Long, plan: IntervalPlan?, autoPause: Boolean = true) {
        synchronized(this) {
            points.clear(); classifier = MotionClassifier(); stats = RunStats.of(emptyList())
            lastAccuracy = null; savedRunId = null
            pause = PauseTracker(autoEnabled = autoPause); this.plan = plan; endedAt = null; demoMotion = null
            breakNext = false; stepper = StepTracker(); smoother.reset()
            startedAt = now; startElapsed = SystemClock.elapsedRealtime(); endElapsed = null; recording = true
        }
        notifyListeners()
    }

    fun onLocation(loc: Location) {
        if (!recording) return
        synchronized(this) {
            lastAccuracy = if (loc.hasAccuracy()) loc.accuracy else null
            if (pause.manual) return@synchronized                   // 「일시정지」 중에는 기록하지 않음
            if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return@synchronized
            val prev = points.lastOrNull()
            var derived: Double? = null
            if (prev != null && !breakNext) {
                val dt = (loc.time - prev.time) / 1000.0
                if (dt <= 0) return@synchronized
                val d = Geo.distance(prev.lat, prev.lon, loc.latitude, loc.longitude)
                derived = d / dt
                if (derived > MAX_SPEED_MPS) return@synchronized   // 순간 이동처럼 튄 점은 버림
            }
            // 흔들림을 거른 위치로 저장·계산합니다(다시 시작한 첫 점은 앞과 이어지지 않으므로 새로 시작)
            if (breakNext) smoother.reset()
            val (lat, lon) = smoother.filter(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 10f, loc.time)
            if (prev != null && !breakNext) {
                val dt = (loc.time - prev.time) / 1000.0
                derived = Geo.distance(prev.lat, prev.lon, lat, lon) / dt
            }
            val speed = MotionClassifier.pickSpeed(if (loc.hasSpeed()) loc.speed.toDouble() else null, derived)
            val raw = classifier.onSpeed(speed, loc.time)
            val candidate = TrackPoint(loc.time, lat, lon, speed?.toFloat(), loc.accuracy, raw)
            // 속도로는 '멈춤'이어도 최근 10초에 12m 넘게 움직였으면 걷는 중으로 봅니다
            val motion = if (raw == Motion.STILL && !breakNext &&
                MotionClassifier.movedRecently(points + candidate, loc.time)) Motion.WALK else raw
            points.add(TrackPoint(loc.time, lat, lon, speed?.toFloat(), loc.accuracy, motion, breakBefore = breakNext && prev != null))
            breakNext = false
            pause.update(motion, SystemClock.elapsedRealtime())   // 시간 계산은 모두 기기 경과 시계 하나로
            stats = RunStats.of(points)
        }
        notifyListeners()
    }

    /** 걸음 센서 값(기기를 켠 뒤 누적). 일시정지 중 걸음은 넣지 않고, 케이던스를 걷기·뛰기 판단에 넘깁니다. */
    fun onSteps(cumulative: Float) {
        if (!recording) return
        synchronized(this) {
            val nowE = SystemClock.elapsedRealtime()
            stepper.onSensor(cumulative, nowE, counting = !pause.manual)
            classifier.onCadence(stepper.cadence(nowE), System.currentTimeMillis())
        }
    }

    fun onActivity(a: Activity, now: Long) {
        synchronized(this) { classifier.onActivity(a, now) }
    }

    /** 기록을 끝내고, 저장할 만한 기록이면 돌려줍니다. */
    fun stop(): Run? {
        val run = synchronized(this) {
            recording = false
            endedAt = System.currentTimeMillis()
            endElapsed = SystemClock.elapsedRealtime()
            if (points.size >= 2) Run(startedAt, ArrayList(points), stepper.total) else null
        }
        notifyListeners()
        return run
    }

    fun markSaved(id: Long) { savedRunId = id; notifyListeners() }

    fun snapshot(): Snapshot = synchronized(this) {
        Snapshot(recording, startedAt, stats, demoMotion ?: points.lastOrNull()?.motion ?: classifier.current, lastAccuracy, usingActivityRecognition, savedRunId,
            plan, pause.pausedAt, pause.pausedTotal, points.size, endedAt, startElapsed, endElapsed, pause.manual,
            stepper.total, stepper.cadence(SystemClock.elapsedRealtime()))
    }

    /**
     * 「일시정지」(on=true)·「다시 시작」(on=false) 버튼.
     * 자동으로 멈춘 상태에서 「다시 시작」을 누르면 바로 시간이 흐르고, 잠시 동안 자동 일시정지를 걸지 않습니다.
     * 버튼으로 멈춘 동안의 이동은 거리·시간에 넣지 않습니다.
     */
    fun setManualPause(on: Boolean) {
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (!recording) return
            if (on) pause.pauseManually(now)
            else {
                if (pause.manual) breakNext = true
                pause.resume(now)
                classifier.resetSpeeds()     // 멈춰 있던 동안의 느린 속도가 남아 바로 다시 '멈춤'이 되지 않게
            }
        }
        notifyListeners()
    }

    /** 화면 점검용(디버그 빌드에서만 호출): 서비스 없이 '기록 중' 상태를 만듭니다. */
    fun demo(pts: List<TrackPoint>, motion: Motion, now: Long) {
        synchronized(this) {
            points.clear(); points.addAll(pts); stats = RunStats.of(points)
            classifier = MotionClassifier(); pause = PauseTracker(); plan = IntervalPlan.byId(1)
            val dur = pts.last().time - pts.first().time
            startedAt = now - dur; startElapsed = SystemClock.elapsedRealtime() - dur; endedAt = null; endElapsed = null; savedRunId = null
            lastAccuracy = 4f; demoMotion = motion; recording = true
        }
        notifyListeners()
    }

    /** 지금까지의 점 복사본(지도 그리기용). */
    fun points(): List<TrackPoint> = synchronized(this) { ArrayList(points) }

    fun addListener(l: Listener) { listeners.add(l); l.onUpdate(snapshot()) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private fun notifyListeners() {
        val s = snapshot()
        main.post { listeners.toList().forEach { it.onUpdate(s) } }
    }
}
