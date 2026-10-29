package io.github.sby1234ai.runmate.logic

/** 기록 하나의 요약. 거리 m, 시간 ms. */
data class Stats(
    val distance: Double,
    val elapsed: Long,
    val moving: Long,
    val runDistance: Double,
    val runTime: Long,
    val walkDistance: Double,
    val walkTime: Long,
    val splits: List<Long>,   // 1km마다 걸린 시간(ms). 마지막 1km 미만 구간은 넣지 않음
) {
    /** 움직인 시간 기준 1km 평균 시간(초). 거리가 너무 짧으면 null. */
    val paceSecPerKm: Double? get() = if (distance < 50) null else moving / 1000.0 / (distance / 1000.0)
    val runPaceSecPerKm: Double? get() = if (runDistance < 50) null else runTime / 1000.0 / (runDistance / 1000.0)
}

object RunStats {
    /** 두 지점 사이가 이보다 길게 떨어지면(초) GPS가 끊긴 것으로 보고 이동 시간에 넣지 않습니다. */
    private const val GAP_MS = 30_000L

    fun of(points: List<TrackPoint>): Stats {
        if (points.size < 2) {
            return Stats(0.0, 0, 0, 0.0, 0, 0.0, 0, emptyList())
        }
        var dist = 0.0; var moving = 0L
        var runD = 0.0; var runT = 0L; var walkD = 0.0; var walkT = 0L
        val splits = mutableListOf<Long>()
        var nextKm = 1000.0
        var splitStart = points.first().time
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            val dt = b.time - a.time
            if (dt <= 0) continue
            val d = Geo.distance(a.lat, a.lon, b.lat, b.lon)
            if (dt > GAP_MS) continue
            dist += d
            when (b.motion) {
                Motion.RUN -> { runD += d; runT += dt; moving += dt }
                Motion.WALK -> { walkD += d; walkT += dt; moving += dt }
                Motion.STILL -> {}
            }
            while (dist >= nextKm) {
                // 이 구간 안에서 1km 지점을 지난 시각을 선형으로 나눠 계산
                val over = dist - nextKm
                val frac = if (d > 0) 1 - over / d else 1.0
                val crossAt = a.time + (dt * frac).toLong()
                splits.add(crossAt - splitStart)
                splitStart = crossAt
                nextKm += 1000.0
            }
        }
        return Stats(dist, points.last().time - points.first().time, moving, runD, runT, walkD, walkT, splits)
    }
}
