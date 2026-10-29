package io.github.sby1234ai.runmate.logic

/**
 * 걷기·뛰기·멈춤을 정합니다.
 * 1) 최근 90초 안에 휴대폰 동작 인식이 알려 준 상태가 있으면 그것을 먼저 씁니다.
 * 2) 동작 인식이 '걷기'인데 GPS 속도가 분명히 빠르면(뛰기 기준 + 0.3 m/s) 뛰기로 고칩니다. 동작 인식이 늦게 바뀌는 경우를 메웁니다.
 * 3) 동작 인식이 없으면 최근 5개 GPS 속도의 중앙값으로 정하고, 경계에서 왔다 갔다 하지 않도록 0.2 m/s 여유를 둡니다.
 * 뛰기 기준 기본값 2.2 m/s ≈ 1km에 7분 35초.
 */
class MotionClassifier(
    private val runSpeed: Double = 2.2,
    private val stillSpeed: Double = 0.5,
    private val hysteresis: Double = 0.2,
    private val activityTtlMs: Long = 90_000,
) {
    private val speeds = ArrayDeque<Double>()
    private var lastActivity: Activity? = null
    private var lastActivityAt: Long = Long.MIN_VALUE
    var current: Motion = Motion.STILL
        private set
    private var cadence: Int? = null
    private var cadenceAt: Long = Long.MIN_VALUE

    /** 걸음 센서의 케이던스(분당 걸음). 걷기는 보통 100~125, 달리기는 150~180. */
    fun onCadence(spm: Int?, timeMs: Long) { cadence = spm; cadenceAt = timeMs }

    fun onActivity(activity: Activity, timeMs: Long) {
        lastActivity = activity
        lastActivityAt = timeMs
    }

    /** 일시정지에서 다시 시작할 때: 지난 속도 기록을 비우고 '걷기'에서 출발합니다. */
    fun resetSpeeds() {
        speeds.clear()
        current = Motion.WALK
    }

    fun onSpeed(speedMps: Double?, timeMs: Long): Motion {
        if (speedMps != null && speedMps >= 0 && speedMps < 12) {
            speeds.addLast(speedMps)
            while (speeds.size > 5) speeds.removeFirst()
        }
        val median = median()
        val act = lastActivity?.takeIf { timeMs - lastActivityAt <= activityTtlMs }
        val cad = cadence?.takeIf { timeMs - cadenceAt <= 15_000 }
        val fast = median != null && median >= runSpeed + 0.3
        current = when {
            act == Activity.RUNNING -> Motion.RUN
            // 걸음 센서가 있으면 보폭 빠르기로 먼저 판단: 분당 140걸음 이상이면 뛰기, 60 이상이면 걷기(속도가 분명히 빠르면 뛰기)
            cad != null && cad >= RUN_CADENCE -> Motion.RUN
            cad != null && cad >= 60 -> if (fast) Motion.RUN else Motion.WALK
            else -> byActivity(act, median)
        }
        return current
    }

    private fun byActivity(act: Activity?, median: Double?): Motion {
        return when (act) {
            Activity.RUNNING -> Motion.RUN
            Activity.WALKING -> if (median != null && median >= runSpeed + 0.3) Motion.RUN else Motion.WALK
            Activity.STILL -> if (median != null && median >= runSpeed + 0.3) Motion.RUN else Motion.STILL
            else -> bySpeed(median)
        }
    }

    private fun bySpeed(median: Double?): Motion {
        if (median == null) return current
        return when (current) {
            Motion.RUN -> when {
                median < stillSpeed -> Motion.STILL
                median < runSpeed - hysteresis -> Motion.WALK
                else -> Motion.RUN
            }
            Motion.WALK -> when {
                median >= runSpeed + hysteresis -> Motion.RUN
                median < stillSpeed -> Motion.STILL
                else -> Motion.WALK
            }
            Motion.STILL -> when {
                median >= runSpeed + hysteresis -> Motion.RUN
                median >= stillSpeed + hysteresis -> Motion.WALK
                else -> Motion.STILL
            }
        }
    }

    private fun median(): Double? {
        if (speeds.isEmpty()) return null
        val s = speeds.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    companion object {
        /** 이 이상 분당 걸음이면 뛰기 */
        const val RUN_CADENCE = 140
        /**
         * GPS가 알려 준 속도와 위치 변화로 계산한 속도 중 무엇을 쓸지.
         * 어떤 휴대폰·상황에서는 실제로 움직이는데도 GPS 속도가 0으로 오므로,
         * 그때 위치가 초속 1m 넘게 바뀌었다면 계산한 속도를 씁니다(가만히 있을 때의 GPS 흔들림은 대개 그보다 작음).
         */
        /**
         * 최근 windowMs 동안 실제로 minMeters 넘게 이동했는지.
         * 화면이 꺼져 위치가 드문드문 들어오면 순간 속도가 낮게 계산될 수 있어,
         * 이동 거리로 한 번 더 확인해 움직이는 중에 '멈춤'(자동 일시정지)이 되지 않게 합니다.
         */
        fun movedRecently(points: List<TrackPoint>, now: Long, windowMs: Long = 10_000, minMeters: Double = 12.0): Boolean {
            if (points.size < 2) return false
            val last = points.last()
            if (now - last.time > windowMs) return false
            var i = points.size - 1
            while (i > 0 && last.time - points[i - 1].time <= windowMs && !points[i].breakBefore) i--
            val first = points[i]
            return first !== last && Geo.distance(first.lat, first.lon, last.lat, last.lon) >= minMeters
        }

        fun pickSpeed(reported: Double?, derived: Double?): Double? = when {
            reported != null && reported > 0.2 -> reported
            derived != null && derived > 1.0 -> derived
            else -> reported ?: derived
        }
    }
}
