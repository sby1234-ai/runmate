package io.github.sby1234ai.runmate.logic

import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/** 하늘 상태. 배경 그림과 달리기 조언을 고르는 데 씁니다. */
enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

data class WeatherNow(
    val tempC: Double,
    val feelsC: Double?,
    val code: Int,
    val windMs: Double,
    val isDay: Boolean,
    val fetchedAt: Long,
) {
    val sky: Sky get() = Weather.skyOf(code)
}

/**
 * Open-Meteo(무료·키 없음, CC BY 4.0) 현재 날씨.
 * 위치는 소수점 첫째 자리(약 10km 단위)로 잘라서만 보냅니다.
 */
object Weather {
    /** 세계기상기구(WMO) 날씨 코드 → 하늘 상태 */
    fun skyOf(code: Int): Sky = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        in 51..67, in 80..82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun label(sky: Sky): String = when (sky) {
        Sky.CLEAR -> "맑음"
        Sky.PARTLY -> "구름 조금"
        Sky.CLOUDY -> "흐림"
        Sky.FOG -> "안개"
        Sky.RAIN -> "비"
        Sky.SNOW -> "눈"
        Sky.STORM -> "뇌우"
    }

    /** 지금 날씨에서 달리기 전에 알아 두면 좋은 한 마디 */
    fun tip(w: WeatherNow): String = when {
        w.sky == Sky.STORM -> "천둥·번개가 있어요. 오늘은 실내 운동을 권해요"
        w.sky == Sky.RAIN -> "비가 와요. 미끄러지지 않게 보폭을 줄이세요"
        w.sky == Sky.SNOW -> "눈길이 미끄러워요. 천천히 달리세요"
        w.tempC >= 30 -> "더워요. 물을 챙기고 그늘진 길로 달리세요"
        w.tempC <= 0 -> "추워요. 몸을 충분히 풀고 시작하세요"
        w.sky == Sky.FOG -> "안개가 있어요. 밝은 옷을 입으세요"
        w.windMs >= 8 -> "바람이 강해요. 맞바람 구간은 천천히"
        !w.isDay -> "어두워요. 밝은 옷과 불빛을 챙기세요"
        else -> "달리기 좋은 날씨예요"
    }

    fun url(lat: Double, lon: Double): String = String.format(
        Locale.US,
        "https://api.open-meteo.com/v1/forecast?latitude=%.1f&longitude=%.1f" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
            "&wind_speed_unit=ms&timezone=auto",
        lat, lon,
    )

    fun parse(json: String, now: Long): WeatherNow? = try {
        val c = JSONObject(json).getJSONObject("current")
        WeatherNow(
            tempC = c.getDouble("temperature_2m"),
            feelsC = if (c.has("apparent_temperature") && !c.isNull("apparent_temperature")) c.getDouble("apparent_temperature") else null,
            code = c.getInt("weather_code"),
            windMs = c.optDouble("wind_speed_10m", 0.0),
            isDay = c.optInt("is_day", 1) == 1,
            fetchedAt = now,
        )
    } catch (e: Exception) {
        null
    }

    fun tempText(c: Double): String = "${c.roundToInt()}°"
}
