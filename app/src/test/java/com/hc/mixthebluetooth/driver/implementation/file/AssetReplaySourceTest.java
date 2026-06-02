package com.hc.mixthebluetooth.driver.implementation.file;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.InputStream;
import java.util.List;

public class AssetReplaySourceTest {
    @Test
    public void readsPlaybackSampleLinesInOrder() throws Exception {
        InputStream input = getClass().getClassLoader()
                .getResourceAsStream("device/device_replay_sample.txt");

        List<String> lines = AssetReplaySource.readLines(input);

        assertEquals(5, lines.size());
        assertEquals("Start Playback", lines.get(0));
        assertEquals("EIS:1,1000,0.12", lines.get(1));
        assertEquals("CA:1,0.08", lines.get(2));
        assertEquals("CA:2,0.09", lines.get(3));
        assertEquals("Playback all done", lines.get(4));
    }
}
