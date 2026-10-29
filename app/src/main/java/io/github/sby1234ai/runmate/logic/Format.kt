package io.github.sby1234ai.runmate.logic

import java.util.Locale
import kotlin.math.roundToLong

object Format {
    fun km(meters: Double): String = String.format(Locale.KOREA, "%.2f", meters / 1000.0)

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.KOREA, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.KOREA, "%d:%02d", m, sec)
    }

    /** 1km 페이스. 예: 5'42" */
    fun pace(secPerKm: Double?): String {
        if (secPerKm == null || secPerKm.isNaN() || secPerKm.isInfinite() || secPerKm > 59 * 60) return "--'--\""
        val t = secPerKm.roundToLong()
        return String.format(Locale.KOREA, "%d'%02d\"", t / 60, t % 60)
    }
}
