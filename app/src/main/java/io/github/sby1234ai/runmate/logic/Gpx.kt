package io.github.sby1234ai.runmate.logic

import java.time.Instant

/** GPX 1.1 파일 내용. 걷기·뛰기 구분은 확장 항목으로 함께 적습니다. */
object Gpx {
    fun write(run: Run, name: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"RunMate\" xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        sb.append("xmlns:rm=\"https://github.com/sby1234-ai/runmate\">\n")
        sb.append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
        for (p in run.points) {
            sb.append("      <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">")
            sb.append("<time>").append(Instant.ofEpochMilli(p.time)).append("</time>")
            sb.append("<extensions><rm:motion>").append(p.motion.name.lowercase()).append("</rm:motion></extensions>")
            sb.append("</trkpt>\n")
        }
        sb.append("    </trkseg>\n  </trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
