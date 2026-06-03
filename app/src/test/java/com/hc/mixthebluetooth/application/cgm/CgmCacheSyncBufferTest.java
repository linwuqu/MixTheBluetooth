package com.hc.mixthebluetooth.application.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.driver.capability.FileRecorder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CgmCacheSyncBufferTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptChunkReassemblesSplitLines() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();

        CgmCacheSyncBuffer.SyncResult first = buffer.acceptChunk("Start Play");
        CgmCacheSyncBuffer.SyncResult second = buffer.acceptChunk("back\nEIS:1,1000,0.12\n");

        assertEquals(0, first.appendedLines);
        assertEquals(2, second.appendedLines);
        assertTrue(second.sawStart);
        assertFalse(second.sawEnd);
    }

    @Test
    public void acceptChunkHandlesEndMarkerWithoutTrailingNewline() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();

        CgmCacheSyncBuffer.SyncResult result =
                buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\nPlayback all done");

        assertEquals(3, result.appendedLines);
        assertTrue(result.sawStart);
        assertTrue(result.sawEnd);
    }

    @Test
    public void validateAcceptsCompleteReplay() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\nPlayback all done\n");

        CgmCacheSyncBuffer.ValidationResult result = buffer.validate();

        assertTrue(result.valid);
        assertFalse(result.retryable);
        assertEquals(CgmCacheSyncBuffer.Phase.READY_TO_UPLOAD, buffer.phase());
    }

    @Test
    public void validateRejectsMissingEndAndAllowsRetry() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\n");

        CgmCacheSyncBuffer.ValidationResult result = buffer.validate();

        assertFalse(result.valid);
        assertTrue(result.retryable);
        assertTrue(result.message.contains("missing end marker"));
        assertTrue(buffer.canRetry());
    }

    @Test
    public void validateRejectsInvalidPayloadAndAllowsRetry() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\n@@@\nPlayback all done\n");

        CgmCacheSyncBuffer.ValidationResult result = buffer.validate();

        assertFalse(result.valid);
        assertTrue(result.retryable);
        assertTrue(result.message.contains("invalid payload line"));
    }

    @Test
    public void beginRetryClearsCurrentAttemptButKeepsRetryBudget() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer(2);
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\n");

        buffer.beginRetry();
        CgmCacheSyncBuffer.SyncResult result =
                buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\nPlayback all done\n");

        assertEquals(3, result.appendedLines);
        assertTrue(buffer.validate().valid);
        assertFalse(buffer.canRetry());
    }

    @Test
    public void writeToWritesOnlyValidatedReplay() throws Exception {
        File file = temporaryFolder.newFile("CGM_Cache_data.txt");
        FakeFileRecorder recorder = new FakeFileRecorder(file);
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\nPlayback all done\n");
        assertTrue(buffer.validate().valid);

        File written = buffer.writeTo(recorder);

        assertSame(file, written);
        assertEquals(1, recorder.startCount);
        assertTrue(recorder.finished);
        assertEquals("Start Playback", recorder.lines.get(0));
        assertEquals("Playback all done", recorder.lines.get(2));
    }

    @Test
    public void logClearedOnlyConfirmsWhenDeleteIsPending() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();

        assertFalse(buffer.acceptDeviceLine("Log Cleared\n").confirmed);

        buffer.markDeleteSent();
        CgmCacheSyncBuffer.DeleteResult result = buffer.acceptDeviceLine("Log Cleared\n");

        assertTrue(result.confirmed);
        assertEquals(CgmCacheSyncBuffer.Phase.DONE, buffer.phase());
    }

    private static final class FakeFileRecorder implements FileRecorder {
        final File file;
        final List<String> lines = new ArrayList<>();
        int startCount;
        boolean finished;

        FakeFileRecorder(File file) {
            this.file = file;
        }

        @Override
        public void start() {
            startCount++;
        }

        @Override
        public void appendLine(@NonNull String line) {
            lines.add(line);
        }

        @NonNull
        @Override
        public File finish() {
            finished = true;
            return file;
        }

        @Override
        public void reset() {
            lines.clear();
        }
    }
}
