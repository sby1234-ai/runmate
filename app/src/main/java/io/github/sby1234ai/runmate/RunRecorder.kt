package io.github.sby1234ai.runmate

import android.location.Location
import android.os.Handler
import android.os.Looper
import io.github.sby1234ai.runmate.logic.Activity
import io.github.sby1234ai.runmate.logic.Geo
import io.github.sby1234ai.runmate.logic.Motion
import io.github.sby1234ai.runmate.logic.MotionClassifier
import io.github.sby1234ai.runmate.logic.Run
import io.github.sby1234ai.runmate.logic.RunStats
import io.github.sby1234ai.runmate.logic.Stats
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
    )

    fun interface Listener { fun onUpdate(s: Snapshot) }

    private const val MAX_ACCURACY_M = 30f
    private const val MAX_SPEED_MPS = 12.0

    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<Listener>()
    private val points = ArrayList<TrackPoint>()
    private var classifier = MotionClassifier()
    private var lastAccuracy: Float? = null
    private var stats: Stats = RunStats.of(emptyList())

    @Volatile var recording = false; private set
    var startedAt = 0L; private set
    var usingActivityRecognition = false
    private var savedRunId: Long? = null

    fun start(now: Long) {
        synchronized(this) {
            points.clear(); classifier = MotionClassifier(); stats = RunStats.of(emptyList())
            lastAccuracy = null; savedRunId = null
            startedAt = now; recording = true
        }
        notifyListeners()
    }

    fun onLocation(loc: Location) {
        if (!recording) return
        synchronized(this) {
            lastAccuracy = if (loc.hasAccuracy()) loc.accuracy else null
            if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return@synchronized
            val prev = points.lastOrNull()
            var derived: Double? = null
            if (prev != null) {
                val dt = (loc.time - prev.time) / 1000.0
                if (dt <= 0) return@synchronized
                val d = Geo.distance(prev.lat, prev.lon, loc.latitude, loc.longitude)
                derived = d / dt
                if (derived > MAX_SPEED_MPS) return@synchronized   // 순간 이동처럼 튄 점은 버림
            }
            val speed = if (loc.hasSpeed()) loc.speed.toDouble() else derived
            val motion = classifier.onSpeed(speed, loc.time)
            points.add(TrackPoint(loc.time, loc.latitude, loc.longitude, speed?.toFloat(), loc.accuracy, motion))
            stats = RunStats.of(points)
        }
        notifyListeners()
    }

    fun onActivity(a: Activity, now: Long) {
        synchronized(this) { classifier.onActivity(a, now) }
    }

    /** 기록을 끝내고, 저장할 만한 기록이면 돌려줍니다. */
    fun stop(): Run? {
        val run = synchronized(this) {
            recording = false
            if (points.size >= 2) Run(startedAt, ArrayList(points)) else null
        }
        notifyListeners()
        return run
    }

    fun markSaved(id: Long) { savedRunId = id; notifyListeners() }

    fun snapshot(): Snapshot = synchronized(this) {
        Snapshot(recording, startedAt, stats, classifier.current, lastAccuracy, usingActivityRecognition, savedRunId)
    }

    fun addListener(l: Listener) { listeners.add(l); l.onUpdate(snapshot()) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private fun notifyListeners() {
        val s = snapshot()
        main.post { listeners.toList().forEach { it.onUpdate(s) } }
    }
}
