package com.hc.mixthebluetooth.impl.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.api.file.UploadedFile;
import com.hc.mixthebluetooth.local.DeviceDataRecorder;
import com.hc.mixthebluetooth.local.DeviceReplaySample;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultDeviceDataServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void replaySampleReturnsCompletedFile() throws Exception {
        DefaultDeviceDataService service = serviceWithSample(sampleLines());
        AtomicReference<CallResult<File>> result = new AtomicReference<>();

        service.replaySample(result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isOk());
        assertTrue(result.get().data.exists());
        String text = new String(Files.readAllBytes(result.get().data.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("Start Playback"));
        assertTrue(text.contains("EIS:1,1000,0.12"));
    }

    @Test
    public void consumeLineReturnsPendingUntilCompletion() {
        DefaultDeviceDataService service = serviceWithSample(sampleLines());
        AtomicReference<CallResult<File>> result = new AtomicReference<>();

        service.consumeLine("Start Playback", result::set);
        assertTrue(result.get().isPending());

        service.consumeLine("EIS:1,1000,0.12", result::set);
        assertTrue(result.get().isPending());

        service.consumeLine("Playback all done", result::set);
        assertTrue(result.get().isOk());
    }

    @Test
    public void uploadLastDataFileUsesFileService() {
        DefaultDeviceDataService service = serviceWithSample(sampleLines());
        AtomicReference<CallResult<File>> replay = new AtomicReference<>();
        AtomicReference<CallResult<UploadedFile>> upload = new AtomicReference<>();

        service.replaySample(replay::set);
        service.uploadLastDataFile(upload::set);

        assertTrue(replay.get().isOk());
        assertTrue(upload.get().isOk());
        assertEquals("2026-05-24CGM_Cache_data.txt", upload.get().data.fileName);
    }

    private DefaultDeviceDataService serviceWithSample(java.util.List<String> lines) {
        return new DefaultDeviceDataService(
                new DeviceDataRecorder(temporaryFolder.getRoot(), () -> "2026-05-24"),
                new DeviceReplaySample(lines),
                fakeFileService()
        );
    }

    private static FileService fakeFileService() {
        return (file, callback) -> {
            UploadedFile uploadedFile = new UploadedFile();
            uploadedFile.fileId = 1L;
            uploadedFile.fileName = file.getName();
            uploadedFile.path = "/test/" + file.getName();
            callback.onResult(CallResult.ok(uploadedFile));
        };
    }

    private static java.util.List<String> sampleLines() {
        return Arrays.asList(
                "Start Playback",
                "EIS:1,1000,0.12",
                "CA:1,0.08",
                "CA:2,0.09",
                "Playback all done"
        );
    }
}
