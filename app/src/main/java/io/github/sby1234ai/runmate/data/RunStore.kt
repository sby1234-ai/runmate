package io.github.sby1234ai.runmate.data

import android.content.Context
import io.github.sby1234ai.runmate.logic.Motion
import io.github.sby1234ai.runmate.logic.Run
import io.github.sby1234ai.runmate.logic.TrackPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 기록은 이 휴대폰의 앱 전용 폴더에만 JSON 파일로 저장합니다. 다른 앱은 읽을 수 없습니다. */
object RunStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "runs").apply { mkdirs() }

    fun save(ctx: Context, run: Run) {
        val arr = JSONArray()
        for (p in run.points) {
            arr.put(JSONArray().apply {
                put(p.time); put(p.lat); put(p.lon)
                put(p.speed?.toDouble() ?: JSONObject.NULL); put(p.accuracy.toDouble()); put(p.motion.name)
            })
        }
        val json = JSONObject().put("version", 1).put("id", run.id).put("points", arr)
        val tmp = File(dir(ctx), "${run.id}.json.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(File(dir(ctx), "${run.id}.json"))
    }

    fun ids(ctx: Context): List<Long> =
        dir(ctx).listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { it.name.removeSuffix(".json").toLongOrNull() }
            ?.sortedDescending() ?: emptyList()

    fun load(ctx: Context, id: Long): Run? {
        val f = File(dir(ctx), "$id.json")
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val arr = o.getJSONArray("points")
            val pts = ArrayList<TrackPoint>(arr.length())
            for (i in 0 until arr.length()) {
                val a = arr.getJSONArray(i)
                pts.add(TrackPoint(
                    time = a.getLong(0), lat = a.getDouble(1), lon = a.getDouble(2),
                    speed = if (a.isNull(3)) null else a.getDouble(3).toFloat(),
                    accuracy = a.getDouble(4).toFloat(),
                    motion = runCatching { Motion.valueOf(a.getString(5)) }.getOrDefault(Motion.WALK),
                ))
            }
            Run(o.getLong("id"), pts)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(ctx: Context, id: Long) {
        File(dir(ctx), "$id.json").delete()
    }
}
