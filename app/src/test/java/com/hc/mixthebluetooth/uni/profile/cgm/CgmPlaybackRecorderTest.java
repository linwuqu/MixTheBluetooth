package com.hc.mixthebluetooth.uni.profile.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CgmPlaybackRecorderTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writesOnlyBetweenPlaybackMarkers() throws Exception {
        CgmPlaybackRecorder recorder = new CgmPlaybackRecorder(tmp.getRoot(), () -> "2026-05-23");

        assertFalse(recorder.onLine("noise").isCompleted());
        assertTrue(recorder.onLine("Start Playback").isRecording());
        assertTrue(recorder.onLine("raw-1").isRecording());
        CgmPlaybackRecorder.Result done = recorder.onLine("Playback all done");

        assertTrue(done.isCompleted());
        assertNotNull(done.file());
        assertEquals("2026-05-23CGM_Cache_data.txt", done.file().getName());

        String text = new String(Files.readAllBytes(done.file().toPath()), StandardCharsets.UTF_8);
        assertEquals("Start Playback\nraw-1\n", text);
    }

    @Test
    public void deleteMarkerDoesNotCreateFile() {
        CgmPlaybackRecorder recorder = new CgmPlaybackRecorder(tmp.getRoot(), () -> "2026-05-23");
        assertFalse(recorder.onLine("DELETE OK").isRecording());
    }
}
