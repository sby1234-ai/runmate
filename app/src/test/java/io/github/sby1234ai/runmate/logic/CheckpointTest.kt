package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckpointTest {
    @Test fun 저장본을_그대로_되읽는다() {
        val run = DemoRun.make(1_000_000L)
        val c = Checkpoint(1_000_000L, 754_000, 2_000_000L, paused = false, manual = false, planId = 2,
            autoPause = true, steps = 4321, points = run.points.take(50) + run.points[50].copy(breakBefore = true))
        val back = Checkpoint.fromJson(c.toJson())!!
        assertEquals(c, back)
    }

    @Test fun 꺼져_있던_동안의_시간은_달리는_중이었으면_더하고_멈춤이었으면_안_더한다() {
        val running = Checkpoint(0, 600_000, 10_000_000, false, false, 0, true, 0, emptyList())
        assertEquals(630_000, running.resumedActiveMs(10_030_000))
        val paused = running.copy(paused = true, manual = true)
        assertEquals(600_000, paused.resumedActiveMs(10_030_000))
        assertEquals(600_000, running.resumedActiveMs(10_000_000 + Checkpoint.MAX_GAP_MS + 1))   // 너무 오래 지나면 안 붙임
    }
}
