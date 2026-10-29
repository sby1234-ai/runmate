package io.github.sby1234ai.runmate.logic

import org.json.JSONObject

/** GitHub 릴리스로 새 버전이 있는지 판단합니다. */
object Update {
    data class Release(val version: String, val notes: String, val apkUrl: String, val shaUrl: String?)

    /** "v0.3.10" 같은 버전을 숫자로 비교합니다(0.3.10 > 0.3.9). */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').mapNotNull { it.toIntOrNull() }
        val a = parts(latest); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** GitHub API의 releases/latest 응답에서 설치 파일과 SHA-256 파일 주소를 꺼냅니다. */
    fun parse(json: String): Release? = try {
        val o = JSONObject(json)
        val assets = o.getJSONArray("assets")
        var apk: String? = null; var sha: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.getString("name"); val url = a.getString("browser_download_url")
            if (name.endsWith(".apk") && !name.contains("wear")) apk = url   // 워치용 파일은 휴대폰에 받지 않음
            if (name.endsWith(".apk.sha256") && !name.contains("wear")) sha = url
        }
        if (apk == null) null else Release(o.getString("tag_name").removePrefix("v"), o.optString("body", ""), apk, sha)
    } catch (e: Exception) { null }

    /** sha256sum 형식("해시  파일이름")에서 해시만 */
    fun shaFrom(text: String): String? = text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { it.length == 64 }
}
