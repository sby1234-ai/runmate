package io.github.sby1234ai.runmate.logic

/**
 * 자동 일시정지. '멈춤' 상태가 delayMs 넘게 이어지면 그때부터 멈춘 것으로 보고,
 * 다시 걷거나 뛰면 이어서 셉니다. 멈춘 시간은 운동 시간에서 뺍니다.
 */
class PauseTracker(private val delayMs: Long = 5_000) {
    private var stillSince: Long? = null
    var pausedAt: Long? = null; private set
    var pausedTotal: Long = 0; private set

    val paused: Boolean get() = pausedAt != null

    fun update(motion: Motion, now: Long) {
        if (motion == Motion.STILL) {
            val since = stillSince ?: now.also { stillSince = it }
            if (pausedAt == null && now - since >= delayMs) pausedAt = since + delayMs
        } else {
            stillSince = null
            pausedAt?.let { pausedTotal += (now - it).coerceAtLeast(0); pausedAt = null }
        }
    }

    /** 시작부터 지금까지에서 멈춘 시간을 뺀 운동 시간(ms). */
    fun active(startedAt: Long, now: Long): Long =
        activeTime(startedAt, now, pausedAt, pausedTotal)

    companion object {
        fun activeTime(startedAt: Long, now: Long, pausedAt: Long?, pausedTotal: Long): Long {
            val end = pausedAt ?: now
            return (end - startedAt - pausedTotal).coerceAtLeast(0)
        }
    }
}
