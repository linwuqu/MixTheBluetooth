package com.hc.mixthebluetooth.cgm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.InputStream;
import java.util.List;

public class CgmReplayFixtureTest {
    @Test
    public void readsPlaybackSampleLinesInOrder() throws Exception {
        InputStream input = getClass().getClassLoader()
                .getResourceAsStream("cgm/cgm_playback_sample.txt");

        List<String> lines = CgmReplayFixture.readLines(input);

        assertEquals(5, lines.size());
        assertEquals("Start Playback", lines.get(0));
        assertEquals("EIS:1,1000,0.12", lines.get(1));
        assertEquals("CA:1,0.08", lines.get(2));
        assertEquals("CA:2,0.09", lines.get(3));
        assertEquals("Playback all done", lines.get(4));
    }
}
