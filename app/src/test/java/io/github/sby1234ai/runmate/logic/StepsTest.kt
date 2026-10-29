package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepsTest {
    @Test fun 걸음_수와_케이던스를_센다() {
        val s = StepTracker()
        var c = 50_000f
        for (t in 0..20) { s.onSensor(c, t * 1000L, counting = true); c += 2.7f }   // 초당 약 2.7걸음(분당 162)
        assertEquals(54, s.total, 2)
        assertEquals(162.0, s.cadence(20_000)!!.toDouble(), 6.0)
    }

    @Test fun 일시정지_중_걸음은_넣지_않고_센서가_줄면_건너뛴다() {
        val s = StepTracker()
        s.onSensor(100f, 0, true); s.onSensor(110f, 1000, true)      // +10
        s.onSensor(150f, 2000, false)                                  // 일시정지 중 +40 → 버림
        s.onSensor(160f, 3000, true)                                   // +10
        s.onSensor(5f, 4000, true)                                     // 재부팅으로 줄어듦 → 버림
        s.onSensor(15f, 5000, true)                                    // +10
        assertEquals(30, s.total)
    }

    @Test fun 걸음이_멈추면_케이던스는_0_자료가_적으면_null() {
        val s = StepTracker()
        s.onSensor(0f, 0, true)
        assertNull(s.cadence(1000))
        s.onSensor(10f, 9000, true)
        assertEquals(0, s.cadence(20_000))
    }

    @Test fun 케이던스로_걷기와_뛰기를_나눈다() {
        val m = MotionClassifier()
        m.onCadence(165, 0)
        assertEquals(Motion.RUN, m.onSpeed(1.8, 1000))      // GPS 속도가 낮아도 보폭이 빠르면 뛰기
        m.onCadence(112, 2000)
        assertEquals(Motion.WALK, m.onSpeed(1.4, 3000))
        m.onCadence(112, 4000)
        assertEquals(Motion.WALK, m.onSpeed(1.2, 30_000))   // 15초 넘은 케이던스는 안 씀 → 속도로
    }

    @Test fun 몇_km째인지와_그_구간_페이스() {
        val st = Stats(2400.0, 900_000, 900_000, 2400.0, 900_000, 0.0, 0, listOf(360_000, 360_000))
        val lap = Lap.of(st, 900_000)
        assertEquals(3, lap.number)
        assertEquals(400.0, lap.meters, 0.1)
        assertEquals(450.0, lap.paceSecPerKm!!, 0.1)      // 180초에 400m → 1km 7분 30초
    }
}
