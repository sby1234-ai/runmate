package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 사용자가 겪은 문제(시간이 거꾸로 감, 다시 시작해도 안 흐름)가 다시 생기지 않는지 확인합니다. */
class PauseScenarioTest {
    @Test fun 멈춰_서서_일시정지를_눌러도_시간이_거꾸로_가지_않는다() {
        val t = PauseTracker()
        t.update(Motion.RUN, 0)
        t.update(Motion.STILL, 60_000)
        t.update(Motion.STILL, 66_000)                 // 65초부터 자동 일시정지
        assertTrue(t.paused); assertFalse(t.manual)
        val before = t.active(0, 70_000)
        t.pauseManually(70_000)                        // 이미 멈춘 상태에서 버튼
        assertTrue(t.manual)
        assertEquals(before, t.active(0, 70_000))
        assertEquals(before, t.active(0, 120_000))     // 멈춘 동안은 그대로(줄지 않음)
        assertEquals(65_000, before)
    }

    @Test fun 다시_시작하면_바로_시간이_흐른다() {
        val t = PauseTracker()
        t.update(Motion.RUN, 0)
        t.pauseManually(30_000)
        t.resume(90_000)                               // 60초 멈춤
        assertFalse(t.paused)
        assertEquals(30_000, t.active(0, 90_000))
        assertEquals(40_000, t.active(0, 100_000))     // 10초 흐름
    }

    @Test fun 자동_일시정지_중_다시_시작을_누르면_풀리고_잠시는_다시_안_멈춘다() {
        val t = PauseTracker()
        t.update(Motion.RUN, 0)
        t.update(Motion.STILL, 10_000); t.update(Motion.STILL, 16_000)
        assertTrue(t.paused)
        t.resume(20_000)
        assertFalse(t.paused)
        t.update(Motion.STILL, 22_000); t.update(Motion.STILL, 30_000)   // 15초 안
        assertFalse(t.paused)
        t.update(Motion.STILL, 36_000); t.update(Motion.STILL, 42_000)   // 15초 지나 5초 넘게 멈춤
        assertTrue(t.paused)
    }

    @Test fun 버튼으로_멈추면_움직여도_저절로_풀리지_않는다() {
        val t = PauseTracker()
        t.pauseManually(5_000)
        t.update(Motion.RUN, 8_000)
        assertTrue(t.paused); assertTrue(t.manual)
    }

    @Test fun 자동_일시정지를_끄면_멈춰_있어도_시간이_흐른다() {
        val t = PauseTracker(autoEnabled = false)
        t.update(Motion.STILL, 0); t.update(Motion.STILL, 60_000)
        assertFalse(t.paused)
        assertEquals(60_000, t.active(0, 60_000))
    }

    @Test fun 어떤_순서로_눌러도_시간은_줄지_않고_멈춤_시간만큼만_빠진다() {
        val rnd = Random(42)
        repeat(300) {
            val t = PauseTracker(autoEnabled = rnd.nextBoolean())
            var now = 0L; var last = 0L
            repeat(200) {
                now += rnd.nextLong(200, 4_000)
                when (rnd.nextInt(6)) {
                    0 -> t.pauseManually(now)
                    1 -> t.resume(now)
                    2, 3 -> t.update(Motion.STILL, now)
                    4 -> t.update(Motion.WALK, now)
                    else -> t.update(Motion.RUN, now)
                }
                val a = t.active(0, now)
                assertTrue("운동 시간이 줄어듦: $last → $a", a >= last)
                assertTrue(a <= now)
                last = a
            }
        }
    }
}
