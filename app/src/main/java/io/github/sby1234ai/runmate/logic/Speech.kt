package io.github.sby1234ai.runmate.logic

import kotlin.math.roundToLong

/** 음성으로 읽기 좋은 한국어 표현. */
object Speech {
    fun duration(totalSec: Long): String {
        val s = totalSec.coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return when {
            h > 0 && m > 0 -> "${h}시간 ${m}분"
            h > 0 -> "${h}시간"
            m > 0 && sec > 0 -> "${m}분 ${sec}초"
            m > 0 -> "${m}분"
            else -> "${sec}초"
        }
    }

    fun pace(secPerKm: Double?): String? {
        if (secPerKm == null || secPerKm.isNaN() || secPerKm.isInfinite() || secPerKm > 59 * 60) return null
        return duration(secPerKm.roundToLong())
    }

    fun km(meters: Double): String {
        val tenths = (meters / 100).toLong()
        val whole = tenths / 10; val dec = tenths % 10
        return if (dec == 0L) "${whole}킬로미터" else "${whole}.${dec}킬로미터"
    }
}
