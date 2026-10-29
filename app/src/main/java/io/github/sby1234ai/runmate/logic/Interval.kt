package io.github.sby1234ai.runmate.logic

/**
 * 시간으로 진행하는 걷기·뛰기 인터벌. 준비 걷기 → (뛰기 → 걷기) × 횟수 순서입니다.
 * 처음 달리기를 시작하는 사람이 많이 쓰는 '뛰기와 걷기를 번갈아 하며 늘려 가는' 방식입니다.
 */
data class IntervalPlan(
    val id: Int,
    val name: String,
    val warmupSec: Int,
    val runSec: Int,
    val walkSec: Int,
    val reps: Int,
) {
    enum class Kind { WARMUP, RUN, WALK, DONE }
    data class Phase(val kind: Kind, val rep: Int, val remainingSec: Int)

    val totalSec: Int get() = warmupSec + reps * (runSec + walkSec)
    val shortName: String get() = name.substringBefore(" · ")

    fun phaseAt(sec: Long): Phase {
        if (sec < warmupSec) return Phase(Kind.WARMUP, 0, (warmupSec - sec).toInt())
        val cycle = runSec + walkSec
        if (cycle <= 0) return Phase(Kind.DONE, reps, 0)
        val t = sec - warmupSec
        val rep = (t / cycle).toInt()
        if (rep >= reps) return Phase(Kind.DONE, reps, 0)
        val within = (t % cycle).toInt()
        return if (within < runSec) Phase(Kind.RUN, rep + 1, runSec - within)
        else Phase(Kind.WALK, rep + 1, cycle - within)
    }

    companion object {
        val PRESETS = listOf(
            IntervalPlan(1, "첫걸음 · 1분 뛰기 / 2분 걷기 × 6", 180, 60, 120, 6),
            IntervalPlan(2, "익숙해지기 · 2분 뛰기 / 1분 걷기 × 6", 180, 120, 60, 6),
            IntervalPlan(3, "늘리기 · 5분 뛰기 / 1분 걷기 × 4", 180, 300, 60, 4),
            IntervalPlan(4, "30분 쉬지 않고 뛰기", 180, 1800, 0, 1),
        )
        fun byId(id: Int): IntervalPlan? = PRESETS.firstOrNull { it.id == id }
    }
}
