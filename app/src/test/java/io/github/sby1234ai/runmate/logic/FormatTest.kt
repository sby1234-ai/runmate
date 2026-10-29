package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun 페이스와_시간_표시() {
        assertEquals("5'42\"", Format.pace(342.0))
        assertEquals("--'--\"", Format.pace(null))
        assertEquals("4:05", Format.duration(245_000))
        assertEquals("1:02:03", Format.duration(3_723_000))
        assertEquals("2.35", Format.km(2345.0))
    }
}
