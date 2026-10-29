package io.github.sby1234ai.runmate.logic

/**
 * 기록 중 음성으로 알려 줄 말을 정합니다(말하는 일은 서비스가 합니다).
 * - 인터벌을 고른 경우: 단계가 바뀔 때마다 무엇을 얼마나 할지
 * - 1km마다: 거리, 방금 1km에 걸린 시간, 전체 운동 시간
 * - 자동 일시정지/다시 시작
 * - 인터벌이 없을 때: 걷기↔뛰기가 15초 넘게 이어져 바뀐 것이 확실하면 알림(1분에 한 번까지)
 */
class Coach(
    private val plan: IntervalPlan?,
    private val autoPause: Boolean,
    private val motionHoldMs: Long = 15_000,
    private val motionGapMs: Long = 60_000,
) {
    private var started = false
    private var lastKm = 0
    private var lastPhase: Pair<IntervalPlan.Kind, Int>? = null
    private var announcedMotion: Motion? = null
    private var candidate: Motion? = null
    private var candidateSince = 0L
    private var lastMotionCueAt = Long.MIN_VALUE / 2
    private var wasPaused = false

    /** 중간 저장본에서 이어 갈 때: 이미 지난 km 안내와 시작 안내를 다시 말하지 않게 합니다. */
    fun prime(stats: Stats, paused: Boolean) {
        started = true
        lastKm = (stats.distance / 1000).toInt()
        wasPaused = paused
    }

    fun update(now: Long, startedAt: Long, activeMs: Long, stats: Stats, motion: Motion, paused: Boolean): List<String> {
        val out = mutableListOf<String>()

        if (plan != null) {
            // 인터벌도 운동 시간 기준: 일시정지하면 인터벌도 같이 멈춤
            val ph = plan.phaseAt((activeMs / 1000).coerceAtLeast(0))
            val key = ph.kind to ph.rep
            if (key != lastPhase) {
                lastPhase = key
                val head = if (!started) "기록을 시작합니다. " else ""
                out += head + phaseText(plan, ph)
            }
        } else if (!started) {
            out += "기록을 시작합니다."
        }
        started = true

        val km = (stats.distance / 1000).toInt()
        if (km > lastKm) {
            lastKm = km
            val split = stats.splits.getOrNull(km - 1)
            val sb = StringBuilder("${km}킬로미터.")
            if (split != null) sb.append(" 이번 1킬로미터 ").append(Speech.duration(split / 1000)).append('.')
            sb.append(" 운동 시간 ").append(Speech.duration(activeMs / 1000)).append('.')
            out += sb.toString()
        }

        if (paused != wasPaused) {
            out += if (paused) "일시정지." else "다시 시작합니다."
        }
        wasPaused = paused

        if (plan == null && motion != Motion.STILL) {
            when {
                announcedMotion == null -> announcedMotion = motion
                motion == announcedMotion -> candidate = null
                else -> {
                    if (candidate != motion) { candidate = motion; candidateSince = now }
                    else if (now - candidateSince >= motionHoldMs && now - lastMotionCueAt >= motionGapMs) {
                        out += if (motion == Motion.RUN) "뛰기로 바뀌었습니다." else "걷기로 바뀌었습니다."
                        announcedMotion = motion; candidate = null; lastMotionCueAt = now
                    }
                }
            }
        }
        return out
    }

    companion object {
        fun phaseText(plan: IntervalPlan, ph: IntervalPlan.Phase): String = when (ph.kind) {
            IntervalPlan.Kind.WARMUP -> "준비 운동으로 ${Speech.duration(ph.remainingSec.toLong())} 걸으세요."
            IntervalPlan.Kind.RUN ->
                if (plan.reps == 1) "이제 뛰세요. ${Speech.duration(plan.runSec.toLong())} 동안 뜁니다."
                else "${plan.reps}번 중 ${ph.rep}번째 뛰기입니다. ${Speech.duration(plan.runSec.toLong())} 뛰세요."
            IntervalPlan.Kind.WALK -> "걸으세요. ${Speech.duration(plan.walkSec.toLong())} 동안 숨을 고릅니다."
            IntervalPlan.Kind.DONE -> "인터벌을 모두 마쳤습니다. 수고하셨습니다. 끝내려면 정지를 길게 누르세요."
        }
    }
}
