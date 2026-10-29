package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionClassifierTest {
    @Test fun 속도만으로_걷기와_뛰기를_나눈다() {
        val c = MotionClassifier()
        var t = 0L
        repeat(5) { c.onSpeed(1.4, t); t += 2000 }          // 1km 약 12분 → 걷기
        assertEquals(Motion.WALK, c.current)
        repeat(5) { c.onSpeed(3.0, t); t += 2000 }          // 1km 약 5분 30초 → 뛰기
        assertEquals(Motion.RUN, c.current)
        repeat(5) { c.onSpeed(0.1, t); t += 2000 }
        assertEquals(Motion.STILL, c.current)
    }

    @Test fun 경계_근처에서는_왔다갔다_하지_않는다() {
        val c = MotionClassifier()
        var t = 0L
        repeat(5) { c.onSpeed(3.0, t); t += 2000 }
        assertEquals(Motion.RUN, c.current)
        repeat(5) { c.onSpeed(2.1, t); t += 2000 }          // 기준 2.2보다 조금 느려도 여유 0.2 안이면 뛰기 유지
        assertEquals(Motion.RUN, c.current)
        repeat(5) { c.onSpeed(1.8, t); t += 2000 }
        assertEquals(Motion.WALK, c.current)
    }

    @Test fun 동작_인식을_먼저_쓰되_오래되면_속도로_돌아간다() {
        val c = MotionClassifier()
        c.onActivity(Activity.RUNNING, 0)
        assertEquals(Motion.RUN, c.onSpeed(1.2, 1000))      // 동작 인식이 뛰기라고 하면 뛰기
        assertEquals(Motion.WALK, c.onSpeed(1.2, 200_000))  // 90초가 지나면 속도 기준(걷기)
    }

    @Test fun 동작_인식이_걷기여도_분명히_빠르면_뛰기() {
        val c = MotionClassifier()
        c.onActivity(Activity.WALKING, 0)
        var t = 0L
        repeat(5) { c.onSpeed(3.2, t); t += 1000 }
        assertEquals(Motion.RUN, c.current)
    }

    @Test fun 튀는_GPS_속도는_무시한다() {
        val c = MotionClassifier()
        var t = 0L
        repeat(5) { c.onSpeed(1.3, t); t += 2000 }
        c.onSpeed(40.0, t)                                  // 시속 144km 같은 값은 버림
        assertEquals(Motion.WALK, c.current)
    }
}
