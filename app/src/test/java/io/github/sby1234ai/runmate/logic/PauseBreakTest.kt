package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseBreakTest {
    @Test fun 일시정지_뒤_첫_점까지의_이동은_거리와_시간에서_뺀다() {
        val a = TrackPoint(0, 35.8500, 128.55, 3f, 5f, Motion.RUN)
        val b = TrackPoint(10_000, 35.8503, 128.55, 3f, 5f, Motion.RUN)          // 약 33m
        // 일시정지 중 차를 타고 500m쯤 이동한 뒤 다시 시작(20초 뒤)
        val c = TrackPoint(30_000, 35.8548, 128.55, 3f, 5f, Motion.RUN, breakBefore = true)
        val d = TrackPoint(40_000, 35.8551, 128.55, 3f, 5f, Motion.RUN)          // 약 33m
        val s = RunStats.of(listOf(a, b, c, d))
        assertEquals(66.7, s.distance, 2.0)
        assertEquals(20_000L, s.moving)
    }

    @Test fun GPS_속도가_0이어도_움직였으면_계산한_속도를_쓴다() {
        assertEquals(2.8, MotionClassifier.pickSpeed(0.0, 2.8)!!, 0.001)
        assertEquals(3.1, MotionClassifier.pickSpeed(3.1, 2.8)!!, 0.001)
        assertEquals(0.0, MotionClassifier.pickSpeed(0.0, 0.6)!!, 0.001)   // 제자리 흔들림은 무시
        assertEquals(1.5, MotionClassifier.pickSpeed(null, 1.5)!!, 0.001)
    }

    @Test fun 최근_10초에_12m_넘게_움직였으면_멈춤으로_보지_않는다() {
        fun p(t: Long, dLat: Double) = TrackPoint(t, 35.85 + dLat, 128.55, 0f, 5f, Motion.STILL)
        val moving = listOf(p(0, 0.0), p(4_000, 0.00008), p(8_000, 0.00016))       // 8초에 약 18m
        assertEquals(true, MotionClassifier.movedRecently(moving, 8_000))
        val jitter = listOf(p(0, 0.0), p(4_000, 0.00003), p(8_000, 0.00001))       // 제자리 흔들림
        assertEquals(false, MotionClassifier.movedRecently(jitter, 8_000))
        val old = listOf(p(0, 0.0), p(30_000, 0.0003))                               // 10초 넘게 떨어진 점은 안 봄
        assertEquals(false, MotionClassifier.movedRecently(old, 30_000))
    }
}
