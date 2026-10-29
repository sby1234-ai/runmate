package io.github.sby1234ai.runmate.logic

import org.junit.Assert.assertTrue
import org.junit.Test

class GpxTest {
    @Test fun GPX_형식으로_쓴다() {
        val run = Run(0, listOf(
            TrackPoint(1_700_000_000_000, 35.85, 128.55, 2.5f, 4f, Motion.RUN),
            TrackPoint(1_700_000_001_000, 35.851, 128.551, 2.5f, 4f, Motion.WALK),
        ))
        val x = Gpx.write(run, "아침 <러닝> & 산책")
        assertTrue(x.startsWith("<?xml"))
        assertTrue(x.contains("<trkpt lat=\"35.85\" lon=\"128.55\">"))
        assertTrue(x.contains("<time>2023-11-14T22:13:20Z</time>"))
        assertTrue(x.contains("<rm:motion>walk</rm:motion>"))
        assertTrue(x.contains("아침 &lt;러닝&gt; &amp; 산책"))
    }
}
