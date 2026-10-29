package io.github.sby1234ai.runmate.logic

/**
 * 일시정지를 한곳에서 셉니다. 멈춘 시간은 한 번만 빠지도록 "멈춤 구간"을 하나만 둡니다.
 *
 * - 자동: '멈춤' 상태가 delayMs 넘게 이어지면 그때부터 멈춤(자동 일시정지를 켠 경우만). 다시 걷거나 뛰면 저절로 풀림.
 * - 수동: 「일시정지」 버튼. 이미 자동으로 멈춘 상태에서 누르면 같은 구간을 '수동'으로 바꿀 뿐 새로 빼지 않음.
 * - 다시 시작: 어느 쪽이든 멈춤을 끝내고, 그 뒤 graceMs 동안은 자동 일시정지를 걸지 않음
 *   (막 출발할 때 속도가 아직 낮아 바로 다시 멈추는 것을 막음).
 */
class PauseTracker(
    private val autoEnabled: Boolean = true,
    private val delayMs: Long = 5_000,
    private val graceMs: Long = 15_000,
) {
    enum class Reason { AUTO, MANUAL }

    private var stillSince: Long? = null
    private var graceUntil = Long.MIN_VALUE
    var pausedAt: Long? = null; private set
    var reason: Reason? = null; private set
    var pausedTotal: Long = 0; private set

    val paused: Boolean get() = pausedAt != null
    val manual: Boolean get() = reason == Reason.MANUAL

    /** 위치가 들어올 때마다 지금 상태(걷기·뛰기·멈춤)를 알려 줍니다. */
    fun update(motion: Motion, now: Long) {
        if (manual) return                      // 수동으로 멈춘 동안에는 움직여도 저절로 풀지 않음
        if (motion == Motion.STILL) {
            if (!autoEnabled || now < graceUntil) { stillSince = null; return }
            val since = stillSince ?: now.also { stillSince = it }
            if (pausedAt == null && now - since >= delayMs) { pausedAt = since + delayMs; reason = Reason.AUTO }
        } else {
            stillSince = null
            if (pausedAt != null) close(now)
        }
    }

    fun pauseManually(now: Long) {
        if (pausedAt == null) pausedAt = now
        reason = Reason.MANUAL
    }

    fun resume(now: Long) {
        if (pausedAt != null) close(now)
        stillSince = null
        graceUntil = now + graceMs
    }

    private fun close(now: Long) {
        pausedTotal += (now - pausedAt!!).coerceAtLeast(0)
        pausedAt = null; reason = null
    }

    /** 시작부터 지금까지에서 멈춘 시간을 뺀 운동 시간(ms). */
    fun active(startedAt: Long, now: Long): Long = activeTime(startedAt, now, pausedAt, pausedTotal)

    companion object {
        fun activeTime(startedAt: Long, now: Long, pausedAt: Long?, pausedTotal: Long): Long {
            val end = minOf(pausedAt ?: now, now)
            return (end - startedAt - pausedTotal).coerceAtLeast(0)
        }
    }
}
