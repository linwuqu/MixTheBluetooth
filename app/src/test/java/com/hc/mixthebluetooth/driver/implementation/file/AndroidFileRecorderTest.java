package com.hc.mixthebluetooth.driver.implementation.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.CallResult;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AndroidFileRecorderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void consumeLineRecordsReplayUntilCompletion() throws Exception {
        AndroidFileRecorder recorder = new AndroidFileRecorder(temporaryFolder.getRoot(), () -> "2026-05-24");

        assertTrue(recorder.consumeLine("Start Playback").isPending());
        assertTrue(recorder.consumeLine("EIS:1,1000,0.12").isPending());
        CallResult<File> result = recorder.consumeLine("Playback all done");

        assertTrue(result.isOk());
        assertNotNull(result.data);
        assertEquals("2026-05-24CGM_Cache_data.txt", result.data.getName());
        String text = new String(Files.readAllBytes(result.data.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("Start Playback"));
        assertTrue(text.contains("EIS:1,1000,0.12"));
    }

    @Test
    public void resetClearsCurrentFile() {
        AndroidFileRecorder recorder = new AndroidFileRecorder(temporaryFolder.getRoot(), () -> "2026-05-24");

        recorder.consumeLine("Start Playback");
        assertNotNull(recorder.currentFile());

        recorder.reset();

        assertEquals(null, recorder.currentFile());
    }
}
