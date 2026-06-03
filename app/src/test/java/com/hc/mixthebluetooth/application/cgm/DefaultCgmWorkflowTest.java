package com.hc.mixthebluetooth.application.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.driver.capability.FileRecorder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultCgmWorkflowTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void validReplayWritesFileAndStartsUploadPoll() throws Exception {
        File file = existingFile();
        FakeFileRecorder recorder = new FakeFileRecorder(file);
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);

        workflow.onReadCacheSent();
        CgmWorkflow.Update update = workflow.onDeviceText(
                "Start Playback\nEIS:1,1000,0.12\nPlayback all done\n",
                ignored -> {
                }
        );

        assertTrue(update.uploadStarted);
        assertTrue(recorder.finished);
        assertTrue(cgmService.uploadCalled);
        assertSame(file, cgmService.uploadFile);
        assertEquals(3, recorder.lines.size());
        assertEquals("Playback all done", recorder.lines.get(2));
    }

    @Test
    public void uploadResultIsReturnedToOriginalCallback() throws Exception {
        FakeFileRecorder recorder = new FakeFileRecorder(existingFile());
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);
        AtomicReference<CallResult<CgmResult>> callback = new AtomicReference<>();
        CgmResult result = new CgmResult();
        result.jobId = 456L;
        result.status = "GENERATED";

        workflow.onReadCacheSent();
        workflow.onDeviceText("Start Playback\nEIS:1,1000,0.12\nPlayback all done\n", callback::set);
        cgmService.complete(CallResult.ok(result));

        assertNotNull(callback.get());
        assertTrue(callback.get().isOk());
        assertSame(result, callback.get().data);
    }

    @Test
    public void invalidReplayRequestsReadCacheRetryWithoutUploading() throws Exception {
        FakeFileRecorder recorder = new FakeFileRecorder(existingFile());
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);

        workflow.onReadCacheSent();
        CgmWorkflow.Update update = workflow.onDeviceText(
                "Start Playback\n@@@\nPlayback all done\n",
                ignored -> {
                }
        );

        assertEquals(CgmCacheSyncBuffer.READ_CACHE_COMMAND, update.commandText);
        assertFalse(update.error);
        assertFalse(cgmService.uploadCalled);
        assertFalse(recorder.finished);
    }

    @Test
    public void uploadFailureReturnsErrorAndDoesNotConfirmDelete() throws Exception {
        FakeFileRecorder recorder = new FakeFileRecorder(existingFile());
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);
        AtomicReference<CallResult<CgmResult>> callback = new AtomicReference<>();

        workflow.onReadCacheSent();
        workflow.onDeviceText("Start Playback\nEIS:1,1000,0.12\nPlayback all done\n", callback::set);
        cgmService.complete(CallResult.error(CallResult.NETWORK, "network failed", null));

        assertNotNull(callback.get());
        assertTrue(callback.get().isError());
        assertFalse(workflow.onDeviceText("Log Cleared\n", ignored -> {
        }).deleteConfirmed);
    }

    @Test
    public void logClearedConfirmsDeleteOnlyAfterDeleteSent() throws Exception {
        FakeFileRecorder recorder = new FakeFileRecorder(existingFile());
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);

        assertFalse(workflow.onDeviceText("Log Cleared\n", ignored -> {
        }).deleteConfirmed);

        workflow.onDeleteCacheSent();
        CgmWorkflow.Update update = workflow.onDeviceText("Log Cleared\n", ignored -> {
        });

        assertTrue(update.deleteConfirmed);
    }

    @Test
    public void resetClearsRecorderAndSession() throws Exception {
        FakeFileRecorder recorder = new FakeFileRecorder(existingFile());
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);

        workflow.onReadCacheSent();
        workflow.reset();

        assertTrue(recorder.resetCalled);
    }

    private DefaultCgmWorkflow workflow(FakeFileRecorder recorder, FakeCgmService cgmService) {
        return new DefaultCgmWorkflow(recorder, cgmService, new NoOpLogger());
    }

    private File existingFile() throws Exception {
        File file = temporaryFolder.newFile("CGM_Cache_data.txt");
        assertTrue(file.exists());
        return file;
    }

    private static final class FakeFileRecorder implements FileRecorder {
        final File file;
        final List<String> lines = new ArrayList<>();
        int startCount;
        boolean finished;
        boolean resetCalled;

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
            resetCalled = true;
            lines.clear();
        }
    }

    private static final class FakeCgmService implements CgmService {
        boolean uploadCalled;
        File uploadFile;
        ApiCallback<CallResult<CgmResult>> uploadCallback;

        @Override
        public void uploadAndPoll(File cacheFile, ApiCallback<CallResult<CgmResult>> callback) {
            uploadCalled = true;
            uploadFile = cacheFile;
            uploadCallback = callback;
        }

        @Override
        public void poll(long jobId, ApiCallback<CallResult<CgmResult>> callback) {
        }

        void complete(CallResult<CgmResult> result) {
            uploadCallback.onResult(result);
        }
    }

    private static final class NoOpLogger implements AppLogger {
        @Override
        public void text(@NonNull String owner, @NonNull String api, @NonNull String stage, @NonNull String message) {
        }

        @Override
        public void json(@NonNull String owner, @NonNull String api, @NonNull String stage, Object value) {
        }
    }
}
