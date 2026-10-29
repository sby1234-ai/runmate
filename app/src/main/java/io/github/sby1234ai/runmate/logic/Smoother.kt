package io.github.sby1234ai.runmate.logic

import kotlin.math.max

/**
 * GPS 위치 흔들림 거르기(칼만 필터). 러닝 앱들이 흔히 쓰는 방식입니다.
 * 새 위치가 들어오면 GPS가 알려 준 오차(정확도 m)가 클수록 덜 믿고, 앞 위치에서 크게 벗어나지 않게 당깁니다.
 * 사람이 움직일 수 있는 빠르기(qMetersPerSec)만큼은 시간이 지날수록 새 위치를 더 믿습니다.
 * 결과: 제자리 흔들림이 줄고, 달린 길의 지그재그가 펴져 거리가 실제에 가까워집니다.
 */
class GpsSmoother(private val qMetersPerSec: Double = 3.0) {
    private var lat = 0.0
    private var lon = 0.0
    private var variance = -1.0      // m², 음수면 아직 위치 없음
    private var lastTime = 0L

    fun reset() { variance = -1.0 }

    /** 걸러진 (위도, 경도)를 돌려줍니다. accuracy는 GPS가 알려 준 오차 반경(m). */
    fun filter(latIn: Double, lonIn: Double, accuracy: Float, timeMs: Long): Pair<Double, Double> {
        val acc = max(accuracy.toDouble(), 1.0)
        if (variance < 0) {
            lat = latIn; lon = lonIn; variance = acc * acc; lastTime = timeMs
            return lat to lon
        }
        val dt = (timeMs - lastTime).coerceAtLeast(0)
        if (dt > 0) { variance += dt * qMetersPerSec * qMetersPerSec / 1000.0; lastTime = timeMs }
        val k = variance / (variance + acc * acc)
        lat += k * (latIn - lat)
        lon += k * (lonIn - lon)
        variance *= (1 - k)
        return lat to lon
    }
}
