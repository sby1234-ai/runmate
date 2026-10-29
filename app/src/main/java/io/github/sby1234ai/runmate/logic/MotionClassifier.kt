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

    fun onActivity(activity: Activity, timeMs: Long) {
        lastActivity = activity
        lastActivityAt = timeMs
    }

    fun onSpeed(speedMps: Double?, timeMs: Long): Motion {
        if (speedMps != null && speedMps >= 0 && speedMps < 12) {
            speeds.addLast(speedMps)
            while (speeds.size > 5) speeds.removeFirst()
        }
        val median = median()
        val act = lastActivity?.takeIf { timeMs - lastActivityAt <= activityTtlMs }
        current = when (act) {
            Activity.RUNNING -> Motion.RUN
            Activity.WALKING -> if (median != null && median >= runSpeed + 0.3) Motion.RUN else Motion.WALK
            Activity.STILL -> if (median != null && median >= runSpeed + 0.3) Motion.RUN else Motion.STILL
            else -> bySpeed(median)
        }
        return current
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
        /**
         * GPS가 알려 준 속도와 위치 변화로 계산한 속도 중 무엇을 쓸지.
         * 어떤 휴대폰·상황에서는 실제로 움직이는데도 GPS 속도가 0으로 오므로,
         * 그때 위치가 초속 1m 넘게 바뀌었다면 계산한 속도를 씁니다(가만히 있을 때의 GPS 흔들림은 대개 그보다 작음).
         */
        fun pickSpeed(reported: Double?, derived: Double?): Double? = when {
            reported != null && reported > 0.2 -> reported
            derived != null && derived > 1.0 -> derived
            else -> reported ?: derived
        }
    }
}
