package io.github.sby1234ai.runmate.logic

import org.json.JSONArray
import org.json.JSONObject

/**
 * 달리는 중 기록의 중간 저장본. 휴대폰이 앱을 강제로 꺼도 이 저장본에서 이어서 기록합니다.
 * activeMs는 저장한 순간까지의 운동 시간, savedAt은 그 순간의 휴대폰 시각(epoch ms).
 */
data class Checkpoint(
    val startedAt: Long,
    val activeMs: Long,
    val savedAt: Long,
    val paused: Boolean,
    val manual: Boolean,
    val planId: Int,
    val autoPause: Boolean,
    val steps: Int,
    val points: List<TrackPoint>,
) {
    /**
     * 다시 이어 갈 때의 운동 시간. 앱이 꺼져 있던 동안에도 사용자는 계속 달렸을 것이므로 멈춘 상태가 아니었다면 그 시간을 더합니다
     * (6시간 넘게 지났으면 이어 붙이지 않음).
     */
    fun resumedActiveMs(now: Long): Long {
        if (paused) return activeMs
        val gap = now - savedAt
        return activeMs + if (gap in 0..MAX_GAP_MS) gap else 0
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (p in points) arr.put(JSONArray().apply {
            put(p.time); put(p.lat); put(p.lon); put(p.speed?.toDouble() ?: JSONObject.NULL)
            put(p.accuracy.toDouble()); put(p.motion.name); put(if (p.breakBefore) 1 else 0)
        })
        return JSONObject()
            .put("v", 1).put("startedAt", startedAt).put("activeMs", activeMs).put("savedAt", savedAt)
            .put("paused", paused).put("manual", manual).put("planId", planId).put("autoPause", autoPause)
            .put("steps", steps).put("points", arr).toString()
    }

    companion object {
        const val MAX_GAP_MS = 6 * 60 * 60 * 1000L

        fun fromJson(s: String): Checkpoint? = try {
            val o = JSONObject(s)
            val arr = o.getJSONArray("points")
            val pts = ArrayList<TrackPoint>(arr.length())
            for (i in 0 until arr.length()) {
                val a = arr.getJSONArray(i)
                pts += TrackPoint(a.getLong(0), a.getDouble(1), a.getDouble(2),
                    if (a.isNull(3)) null else a.getDouble(3).toFloat(), a.getDouble(4).toFloat(),
                    runCatching { Motion.valueOf(a.getString(5)) }.getOrDefault(Motion.WALK), a.optInt(6, 0) == 1)
            }
            Checkpoint(o.getLong("startedAt"), o.getLong("activeMs"), o.getLong("savedAt"),
                o.optBoolean("paused"), o.optBoolean("manual"), o.optInt("planId"), o.optBoolean("autoPause", true),
                o.optInt("steps"), pts)
        } catch (e: Exception) { null }
    }
}
