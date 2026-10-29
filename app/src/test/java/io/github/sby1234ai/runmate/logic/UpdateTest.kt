package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test fun 버전을_숫자로_비교한다() {
        assertTrue(Update.isNewer("v0.3.10", "0.3.9"))
        assertTrue(Update.isNewer("0.4.0", "0.3.99"))
        assertFalse(Update.isNewer("v0.3.4", "0.3.4"))
        assertFalse(Update.isNewer("0.3.3", "0.3.4"))
    }

    @Test fun 릴리스_응답에서_설치_파일을_찾는다() {
        val json = """{"tag_name":"v0.3.5","body":"바뀐 점","assets":[
            {"name":"runmate-v0.3.5.apk","browser_download_url":"https://x/a.apk"},
            {"name":"runmate-v0.3.5.apk.sha256","browser_download_url":"https://x/a.sha"}]}"""
        val r = Update.parse(json)!!
        assertEquals("0.3.5", r.version); assertEquals("https://x/a.apk", r.apkUrl); assertEquals("https://x/a.sha", r.shaUrl)
        assertNull(Update.parse("""{"tag_name":"v1","assets":[]}"""))
        assertEquals("ab".repeat(32), Update.shaFrom("${"AB".repeat(32)}  runmate.apk\n"))
    }
}
