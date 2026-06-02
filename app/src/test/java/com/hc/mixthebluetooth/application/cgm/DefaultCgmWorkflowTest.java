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
    public void incompleteLineAppendsAndReturnsPending() throws Exception {
        FakeFileRecorder recorder = new FakeFileRecorder(existingFile());
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);
        AtomicReference<CallResult<CgmResult>> callback = new AtomicReference<>();

        workflow.onDeviceLine("Start Playback", callback::set);

        assertNotNull(callback.get());
        assertTrue(callback.get().isPending());
        assertEquals(1, recorder.startCount);
        assertEquals(1, recorder.lines.size());
        assertEquals("Start Playback", recorder.lines.get(0));
        assertFalse(cgmService.uploadCalled);
    }

    @Test
    public void completionLineFinishesFileAndStartsUploadPoll() throws Exception {
        File file = existingFile();
        FakeFileRecorder recorder = new FakeFileRecorder(file);
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);

        workflow.onDeviceLine("Start Playback", ignored -> {
        });
        workflow.onDeviceLine("EIS:1,1000,0.12", ignored -> {
        });
        workflow.onDeviceLine("Playback all done", ignored -> {
        });

        assertTrue(recorder.finished);
        assertTrue(cgmService.uploadCalled);
        assertSame(file, cgmService.uploadFile);
        assertEquals(2, recorder.lines.size());
        assertEquals("EIS:1,1000,0.12", recorder.lines.get(1));
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

        workflow.onDeviceLine("Start Playback", ignored -> {
        });
        workflow.onDeviceLine("Playback all done", callback::set);
        cgmService.complete(CallResult.ok(result));

        assertNotNull(callback.get());
        assertTrue(callback.get().isOk());
        assertSame(result, callback.get().data);
    }

    @Test
    public void resetClearsRecorderAndCompletionState() throws Exception {
        FakeFileRecorder recorder = new FakeFileRecorder(existingFile());
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);
        AtomicReference<CallResult<CgmResult>> callback = new AtomicReference<>();

        workflow.onDeviceLine("Start Playback", ignored -> {
        });
        workflow.reset();
        workflow.onDeviceLine("Playback all done", callback::set);

        assertTrue(recorder.resetCalled);
        assertNotNull(callback.get());
        assertTrue(callback.get().isError());
        assertEquals(CallResult.DEVICE_REPLAY_INCOMPLETE, callback.get().code);
        assertFalse(cgmService.uploadCalled);
    }

    @Test
    public void recorderFailureReturnsLocalFileError() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "missing.txt");
        FakeFileRecorder recorder = new FakeFileRecorder(missing);
        FakeCgmService cgmService = new FakeCgmService();
        DefaultCgmWorkflow workflow = workflow(recorder, cgmService);
        AtomicReference<CallResult<CgmResult>> callback = new AtomicReference<>();

        workflow.onDeviceLine("Start Playback", ignored -> {
        });
        workflow.onDeviceLine("Playback all done", callback::set);

        assertNotNull(callback.get());
        assertTrue(callback.get().isError());
        assertEquals(CallResult.LOCAL_FILE_NOT_FOUND, callback.get().code);
        assertFalse(cgmService.uploadCalled);
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
