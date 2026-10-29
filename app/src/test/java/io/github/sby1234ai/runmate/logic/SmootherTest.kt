package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.random.Random

class SmootherTest {
    private fun pathLength(p: List<Pair<Double, Double>>) =
        p.zipWithNext().sumOf { (a, b) -> Geo.distance(a.first, a.second, b.first, b.second) }

    @Test fun 제자리에서_흔들리는_GPS의_가짜_거리를_크게_줄인다() {
        val rnd = Random(1)
        val raw = (0 until 120).map { 35.85 + rnd.nextDouble(-1.0, 1.0) * 6 / 111_195.0 to 128.55 + rnd.nextDouble(-1.0, 1.0) * 6 / 90_000.0 }
        val f = GpsSmoother()
        val sm = raw.mapIndexed { i, (a, b) -> f.filter(a, b, 8f, i * 2000L) }
        val rawLen = pathLength(raw); val smLen = pathLength(sm)
        assertTrue("원래 $rawLen m → 거른 뒤 $smLen m", smLen < rawLen * 0.6)
    }

    @Test fun 실제로_달린_거리는_거의_그대로_둔다() {
        val rnd = Random(2)
        val k = cos(Math.toRadians(35.85))
        // 북쪽으로 초속 3m, 2초마다, 600초 = 1800m, GPS 오차 ±4m
        val truth = (0..300).map { i -> 35.85 + i * 6.0 / 111_195.0 to 128.55 }
        val raw = truth.map { (a, b) -> a + rnd.nextDouble(-1.0, 1.0) * 4 / 111_195.0 to b + rnd.nextDouble(-1.0, 1.0) * 4 / (111_195.0 * k) }
        val f = GpsSmoother()
        val sm = raw.mapIndexed { i, (a, b) -> f.filter(a, b, 5f, i * 2000L) }
        val rawLen = pathLength(raw); val smLen = pathLength(sm)
        assertTrue("거른 거리 $smLen m(실제 1800m)", smLen in 1700.0..1900.0)
        assertTrue("원래 $rawLen m보다 실제에 가까워야 함", kotlin.math.abs(smLen - 1800) <= kotlin.math.abs(rawLen - 1800))
    }
}
