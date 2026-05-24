package com.hc.mixthebluetooth.impl.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.file.UploadedFile;
import com.hc.mixthebluetooth.remote.MockServer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultFileServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void uploadWithMockServerReturnsUploadedFile() throws Exception {
        File file = temporaryFolder.newFile("CGM_Cache_data.txt");
        Files.write(file.toPath(), "Start Playback\n".getBytes(StandardCharsets.UTF_8));
        DefaultFileService service = new DefaultFileService(new MockServer());
        AtomicReference<CallResult<UploadedFile>> result = new AtomicReference<>();

        service.upload(file, result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isOk());
        assertEquals("CGM_Cache_data.txt", result.get().data.fileName);
    }

    @Test
    public void uploadMissingFileReturnsLocalError() {
        DefaultFileService service = new DefaultFileService(new MockServer());
        AtomicReference<CallResult<UploadedFile>> result = new AtomicReference<>();

        service.upload(new File(temporaryFolder.getRoot(), "missing.txt"), result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isError());
        assertEquals(CallResult.LOCAL_FILE_NOT_FOUND, result.get().code);
    }
}
