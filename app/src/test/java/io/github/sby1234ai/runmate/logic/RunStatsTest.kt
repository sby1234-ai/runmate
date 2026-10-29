package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunStatsTest {
    // 위도 1도 ≈ 111.2km. 북쪽으로 일정하게 이동하는 가상 기록을 만듭니다.
    private fun line(meters: Double, seconds: Int, motion: Motion, startT: Long = 0, startLat: Double = 35.85): List<TrackPoint> {
        val stepLat = meters / 111_195.0 / seconds
        return (0..seconds).map { i ->
            TrackPoint(startT + i * 1000L, startLat + stepLat * i, 128.55, null, 5f, motion)
        }
    }

    @Test fun 거리와_시간과_구간을_계산한다() {
        val pts = line(2010.0, 603, Motion.RUN)              // 2.01km를 603초 → 1km 300초
        val s = RunStats.of(pts)
        assertEquals(2010.0, s.distance, 5.0)
        assertEquals(603_000L, s.moving)
        assertEquals(2, s.splits.size)                      // 1km 구간 2개, 남은 10m는 넣지 않음
        assertEquals(300_000.0, s.splits[0].toDouble(), 1500.0)
        assertEquals(300.0, s.paceSecPerKm!!, 2.0)
    }

    @Test fun 걷기와_뛰기를_나눠_센다() {
        val run = line(1000.0, 300, Motion.RUN)
        val walk = line(500.0, 300, Motion.WALK, startT = 301_000, startLat = run.last().lat).drop(1)
        val s = RunStats.of(run + walk)
        assertEquals(1000.0, s.runDistance, 5.0)
        assertTrue(s.walkDistance in 490.0..520.0)
        assertEquals(300_000L, s.runTime)
    }

    @Test fun 멈춘_시간과_GPS가_끊긴_시간은_움직인_시간에_넣지_않는다() {
        val a = line(500.0, 150, Motion.RUN)
        val still = TrackPoint(a.last().time + 60_000, a.last().lat, a.last().lon, 0f, 5f, Motion.STILL)
        val b = line(500.0, 150, Motion.RUN, startT = still.time + 1000, startLat = still.lat)
        val s = RunStats.of(a + still + b)
        assertEquals(301_000L, s.moving)                    // 150초 + 1초(재출발) + 150초
    }

    @Test fun 점이_하나뿐이면_0() {
        val s = RunStats.of(line(0.0, 0, Motion.STILL))
        assertEquals(0.0, s.distance, 0.0)
        assertEquals(null, s.paceSecPerKm)
    }
}
