package io.github.sby1234ai.runmate.logic

/**
 * 휴대폰 걸음 센서(TYPE_STEP_COUNTER: 기기를 켠 뒤 누적 걸음)로 이번 기록의 걸음 수와 케이던스(분당 걸음)를 셉니다.
 * 삼성 헬스·나이키 런 클럽도 같은 종류의 하드웨어 걸음 센서를 씁니다.
 * - 「일시정지」 중에 걸은 걸음은 넣지 않습니다.
 * - 센서 값이 줄어들면(재부팅 등) 그 사이는 버리고 다시 이어서 셉니다.
 */
class StepTracker(private val windowMs: Long = 20_000) {
    private var last: Float? = null
    private var counted = 0.0
    val total: Int get() = counted.toInt()
    private val samples = ArrayDeque<Pair<Long, Int>>()   // (경과 시각, 그때까지 걸음)

    /** 중간 저장본에서 이어 갈 때: 지금까지 센 걸음부터 다시 셉니다(센서 기준값은 다음 값부터 새로 잡음). */
    fun restore(steps: Int) { counted = steps.toDouble(); last = null }

    fun onSensor(cumulative: Float, nowElapsed: Long, counting: Boolean) {
        val prev = last
        last = cumulative
        if (prev != null) {
            val delta = cumulative - prev
            if (delta > 0 && delta < 1000 && counting) counted += delta
        }
        samples.addLast(nowElapsed to total)
        while (samples.size > 2 && nowElapsed - samples[1].first >= windowMs) samples.removeFirst()
    }

    /** 최근 약 20초의 분당 걸음. 자료가 모자라면 null, 5초 넘게 걸음이 없으면 0. */
    fun cadence(nowElapsed: Long): Int? {
        if (samples.isEmpty()) return null
        val (lastT, lastSteps) = samples.last()
        if (nowElapsed - lastT > 5_000) return 0
        val (firstT, firstSteps) = samples.first()
        val span = lastT - firstT
        if (span < 8_000) return null
        return ((lastSteps - firstSteps) * 60_000L / span).toInt()
    }
}
