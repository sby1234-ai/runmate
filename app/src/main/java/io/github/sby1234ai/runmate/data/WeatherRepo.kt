package io.github.sby1234ai.runmate.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.sby1234ai.runmate.BuildConfig
import io.github.sby1234ai.runmate.logic.Weather
import io.github.sby1234ai.runmate.logic.WeatherNow
import java.net.HttpURLConnection
import java.net.URL

/** 현재 날씨를 받아 30분 동안 저장해 둡니다. 실패하면 조용히 null(배경은 시간대 기본값). */
object WeatherRepo {
    private const val FRESH_MS = 30 * 60_000L
    private const val USABLE_MS = 3 * 60 * 60_000L
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var loading = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(ctx: Context): WeatherNow? {
        val p = prefs(ctx)
        val at = p.getLong("at", 0L)
        if (System.currentTimeMillis() - at > USABLE_MS) return null
        return Weather.parse(p.getString("json", null) ?: return null, at)
    }

    fun needsRefresh(ctx: Context): Boolean =
        System.currentTimeMillis() - prefs(ctx).getLong("at", 0L) > FRESH_MS

    fun clear(ctx: Context) { prefs(ctx).edit().clear().apply() }

    fun refresh(ctx: Context, lat: Double, lon: Double, done: (WeatherNow?) -> Unit) {
        if (loading) return
        loading = true
        val app = ctx.applicationContext
        Thread {
            val result = try {
                val conn = URL(Weather.url(lat, lon)).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "RunMate/${BuildConfig.VERSION_NAME} (+https://github.com/sby1234-ai/runmate)")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val now = System.currentTimeMillis()
                Weather.parse(body, now)?.also {
                    prefs(app).edit().putString("json", body).putLong("at", now).apply()
                }
            } catch (e: Exception) {
                null
            }
            loading = false
            main.post { done(result) }
        }.start()
    }
}
