package io.github.sby1234ai.runmate.logic

import java.util.Calendar
import java.util.TimeZone

/** 최고 기록과 주간·월간 합계. */
object Records {
    /**
     * 기록 안에서 연속으로 meters를 가장 빨리 지난 시간(ms). 거리가 모자라면 null.
     * 30초 넘게 끊긴 구간은 거리에 넣지 않아(RunStats와 같은 규칙) 순간 이동한 기록이 최고 기록이 되지 않게 합니다.
     */
    fun bestEffort(points: List<TrackPoint>, meters: Double): Long? {
        if (points.size < 2) return null
        val n = points.size
        val d = DoubleArray(n); val t = LongArray(n)
        t[0] = points[0].time
        for (i in 1 until n) {
            val a = points[i - 1]; val b = points[i]
            val dt = b.time - a.time
            val seg = if (dt <= 0 || dt > 30_000 || b.breakBefore) 0.0 else Geo.distance(a.lat, a.lon, b.lat, b.lon)
            d[i] = d[i - 1] + seg; t[i] = b.time
        }
        if (d[n - 1] < meters) return null
        var best: Long? = null
        var i = 0
        for (j in 1 until n) {
            if (d[j] < meters) continue
            val target = d[j] - meters
            while (i + 1 < j && d[i + 1] <= target) i++
            val span = d[i + 1] - d[i]
            val frac = if (span > 0) ((target - d[i]) / span).coerceIn(0.0, 1.0) else 0.0
            val startT = t[i] + ((t[i + 1] - t[i]) * frac).toLong()
            val took = t[j] - startT
            if (took > 0 && (best == null || took < best)) best = took
        }
        return best
    }

    data class Item(val id: Long, val distance: Double)
    data class Period(val distance: Double, val count: Int)
    data class Summary(val week: Period, val month: Period, val all: Period)

    /** 이번 주(월요일 시작)와 이번 달 합계. id는 시작 시각(epoch ms). */
    fun summary(items: List<Item>, now: Long, tz: TimeZone = TimeZone.getDefault()): Summary {
        val cal = Calendar.getInstance(tz).apply {
            firstDayOfWeek = Calendar.MONDAY
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val back = (dow - Calendar.MONDAY + 7) % 7
        val weekStart = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -back) }.timeInMillis
        val monthStart = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        fun sum(from: Long) = items.filter { it.id in from..now }.let { l -> Period(l.sumOf { it.distance }, l.size) }
        return Summary(sum(weekStart), sum(monthStart), Period(items.sumOf { it.distance }, items.size))
    }
}
