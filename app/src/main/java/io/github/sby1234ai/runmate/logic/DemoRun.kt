package io.github.sby1234ai.runmate.logic

import kotlin.math.cos
import kotlin.math.sin

/**
 * 화면 점검용 가상 기록(실제 사람의 기록 아님). 걷기로 몸을 풀고, 뛰기와 걷기를 섞은 뒤 걸으며 마칩니다.
 * 디버그 빌드의 화면 캡처와 단위 테스트에서만 씁니다.
 */
object DemoRun {
    private data class Part(val motion: Motion, val seconds: Int, val speed: Double)

    fun make(startTime: Long, lat0: Double = 35.8530, lon0: Double = 128.5580, scale: Double = 1.0): Run {
        val parts = listOf(
            Part(Motion.WALK, 240, 1.4), Part(Motion.RUN, (900 * scale).toInt(), 2.9),
            Part(Motion.STILL, 40, 0.0), Part(Motion.WALK, 120, 1.5),
            Part(Motion.RUN, (600 * scale).toInt(), 3.1), Part(Motion.WALK, 180, 1.3),
        )
        val pts = ArrayList<TrackPoint>()
        var lat = lat0; var lon = lon0; var t = startTime; var elapsed = 0.0
        val mPerLat = 111_195.0
        val mPerLon = 111_195.0 * cos(Math.toRadians(lat0))
        pts += TrackPoint(t, lat, lon, 0f, 5f, Motion.STILL)
        for (p in parts) {
            var s = 0
            while (s < p.seconds) {
                s += 2; elapsed += 2; t += 2000
                // 천천히 방향을 틀어 공원 둘레를 도는 모양
                val heading = elapsed / 520.0 + 0.35 * sin(elapsed / 75.0)
                val d = p.speed * 2
                lat += d * cos(heading) / mPerLat
                lon += d * sin(heading) / mPerLon
                pts += TrackPoint(t, lat, lon, p.speed.toFloat(), 5f, p.motion)
            }
        }
        return Run(startTime, pts)
    }
}
