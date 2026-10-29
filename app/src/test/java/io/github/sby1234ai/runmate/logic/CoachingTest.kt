package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CoachingTest {
    private fun line(meters: Double, seconds: Int, startT: Long = 0, startLat: Double = 35.85): List<TrackPoint> {
        val stepLat = meters / 111_195.0 / seconds
        return (0..seconds).map { i -> TrackPoint(startT + i * 1000L, startLat + stepLat * i, 128.55, null, 5f, Motion.RUN) }
    }

    @Test fun 인터벌_단계를_시간으로_정한다() {
        val p = IntervalPlan.byId(1)!!                         // 준비 180초, 뛰기 60초, 걷기 120초 × 6
        assertEquals(IntervalPlan.Kind.WARMUP, p.phaseAt(0).kind)
        assertEquals(180, p.phaseAt(0).remainingSec)
        assertEquals(IntervalPlan.Phase(IntervalPlan.Kind.RUN, 1, 60), p.phaseAt(180))
        assertEquals(IntervalPlan.Phase(IntervalPlan.Kind.WALK, 1, 120), p.phaseAt(240))
        assertEquals(IntervalPlan.Phase(IntervalPlan.Kind.RUN, 2, 60), p.phaseAt(360))
        assertEquals(IntervalPlan.Kind.DONE, p.phaseAt(p.totalSec.toLong()).kind)
        assertEquals(180 + 6 * 180, p.totalSec)
    }

    @Test fun 걷기가_없는_인터벌은_뛰기_뒤에_끝난다() {
        val p = IntervalPlan.byId(4)!!
        assertEquals(IntervalPlan.Kind.RUN, p.phaseAt(180 + 1799).kind)
        assertEquals(IntervalPlan.Kind.DONE, p.phaseAt(180 + 1800).kind)
    }

    @Test fun 오래_멈추면_일시정지하고_멈춘_시간을_뺀다() {
        val t = PauseTracker(delayMs = 5_000)
        t.update(Motion.RUN, 0); t.update(Motion.STILL, 10_000); t.update(Motion.STILL, 12_000)
        assertFalse(t.paused)                                  // 멈춘 지 2초: 아직 아님
        t.update(Motion.STILL, 16_000)
        assertTrue(t.paused)
        assertEquals(15_000, t.active(0, 30_000))              // 15초에 멈춘 것으로 봄
        t.update(Motion.WALK, 40_000)
        assertFalse(t.paused)
        assertEquals(25_000, t.pausedTotal)
        assertEquals(35_000, t.active(0, 60_000))
    }

    @Test fun 음성_시간_표현() {
        assertEquals("5분 42초", Speech.duration(342))
        assertEquals("6분", Speech.duration(360))
        assertEquals("1시간 2분", Speech.duration(3725))
        assertEquals("9초", Speech.duration(9))
        assertNull(Speech.pace(null))
    }

    @Test fun 코치는_1km마다_한_번_알린다() {
        val c = Coach(plan = null, autoPause = true)
        val pts = line(1005.0, 300)
        val s = RunStats.of(pts)
        val first = c.update(300_000, 0, 300_000, RunStats.of(pts.take(10)), Motion.RUN, false)
        assertEquals(listOf("기록을 시작합니다."), first)
        val km = c.update(300_000, 0, 300_000, s, Motion.RUN, false)
        assertEquals(1, km.size)
        assertTrue(km[0], km[0].startsWith("1킬로미터. 이번 1킬로미터 4분 5"))
        assertTrue(c.update(301_000, 0, 301_000, s, Motion.RUN, false).isEmpty())
        assertEquals(listOf("일시정지."), c.update(302_000, 0, 301_000, s, Motion.STILL, true))
        assertEquals(listOf("다시 시작합니다."), c.update(320_000, 0, 302_000, s, Motion.RUN, false))
    }

    @Test fun 걷기로_바뀐_것은_15초_이어진_뒤에_알린다() {
        val c = Coach(plan = null, autoPause = false)
        val s = RunStats.of(emptyList())
        c.update(0, 0, 0, s, Motion.RUN, false)
        assertTrue(c.update(1_000, 0, 0, s, Motion.WALK, false).isEmpty())
        assertTrue(c.update(10_000, 0, 0, s, Motion.WALK, false).isEmpty())
        assertEquals(listOf("걷기로 바뀌었습니다."), c.update(16_000, 0, 0, s, Motion.WALK, false))
        assertTrue(c.update(17_000, 0, 0, s, Motion.WALK, false).isEmpty())
    }

    @Test fun 인터벌이_있으면_단계마다_알린다() {
        val p = IntervalPlan.byId(1)!!
        val c = Coach(plan = p, autoPause = false)
        val s = RunStats.of(emptyList())
        assertEquals(listOf("기록을 시작합니다. 준비 운동으로 3분 걸으세요."), c.update(0, 0, 0, s, Motion.WALK, false))
        assertTrue(c.update(100_000, 0, 100_000, s, Motion.WALK, false).isEmpty())
        assertEquals(listOf("6번 중 1번째 뛰기입니다. 1분 뛰세요."), c.update(180_500, 0, 180_500, s, Motion.WALK, false))
        assertEquals(listOf("걸으세요. 2분 동안 숨을 고릅니다."), c.update(241_000, 0, 241_000, s, Motion.RUN, false))
    }

    @Test fun 가장_빠른_1km를_찾는다() {
        val slow = line(1000.0, 400)                           // 1km 400초
        val fast = line(1000.0, 250, startT = 401_000, startLat = slow.last().lat).drop(1)
        val best = Records.bestEffort(slow + fast, 1000.0)!!
        assertEquals(250_000.0, best.toDouble(), 3_000.0)
        assertNull(Records.bestEffort(slow, 5000.0))
    }

    @Test fun 이번_주와_이번_달_합계() {
        val tz = TimeZone.getTimeZone("Asia/Seoul")
        // 2026-10-29(목) 12:00 KST
        val now = 1_761_706_800_000L + 365L * 86_400_000L
        val day = 86_400_000L
        val items = listOf(
            Records.Item(now - 1 * day, 5000.0),     // 수: 이번 주
            Records.Item(now - 4 * day, 3000.0),     // 일(10/25): 지난주, 이번 달
            Records.Item(now - 40 * day, 7000.0),    // 9월
        )
        val s = Records.summary(items, now, tz)
        assertEquals(5000.0, s.week.distance, 0.1); assertEquals(1, s.week.count)
        assertEquals(8000.0, s.month.distance, 0.1); assertEquals(2, s.month.count)
        assertEquals(3, s.all.count)
    }
}
