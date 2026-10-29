package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    @Test fun 날씨_코드를_하늘_상태로_바꾼다() {
        assertEquals(Sky.CLEAR, Weather.skyOf(0))
        assertEquals(Sky.PARTLY, Weather.skyOf(2))
        assertEquals(Sky.CLOUDY, Weather.skyOf(3))
        assertEquals(Sky.FOG, Weather.skyOf(45))
        assertEquals(Sky.RAIN, Weather.skyOf(61))
        assertEquals(Sky.RAIN, Weather.skyOf(81))
        assertEquals(Sky.SNOW, Weather.skyOf(73))
        assertEquals(Sky.STORM, Weather.skyOf(95))
    }

    @Test fun 응답을_읽는다() {
        val json = """{"latitude":35.9,"current":{"time":"2026-10-29T18:00","interval":900,
            "temperature_2m":14.6,"apparent_temperature":13.1,"weather_code":61,"wind_speed_10m":3.2,"is_day":0}}"""
        val w = Weather.parse(json, 1000L)!!
        assertEquals(14.6, w.tempC, 0.01)
        assertEquals(Sky.RAIN, w.sky)
        assertEquals(false, w.isDay)
        assertEquals("15°", Weather.tempText(w.tempC))
        assertTrue(Weather.tip(w).startsWith("비가"))
        assertNull(Weather.parse("{}", 0L))
    }

    @Test fun 위치는_소수점_첫째_자리까지만_보낸다() {
        val u = Weather.url(35.854321, 128.558765)
        assertTrue(u, u.contains("latitude=35.9&longitude=128.6&"))
    }

    @Test fun 점검용_가상_기록은_걷기와_뛰기가_섞여_있다() {
        val r = DemoRun.make(0L)
        val s = RunStats.of(r.points)
        assertTrue(s.runDistance > 3000)
        assertTrue(s.walkDistance > 500)
        assertEquals(2, s.splits.size.coerceAtMost(2))
    }
}
