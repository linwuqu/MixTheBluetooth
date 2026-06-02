package com.hc.mixthebluetooth.application.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.file.UploadedFile;
import com.hc.mixthebluetooth.driver.implementation.http.RetrofitServerClient;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class DefaultFileUploadServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void uploadWithHttpServerReturnsUploadedFile() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"code\":0,\"success\":true,\"msg\":\"\",\"data\":{\"fileId\":9,\"fileName\":\"CGM_Cache_data.txt\",\"path\":\"/files/CGM_Cache_data.txt\"}}"));
            server.start();

            File file = temporaryFolder.newFile("CGM_Cache_data.txt");
            Files.write(file.toPath(), "Start Playback\n".getBytes(StandardCharsets.UTF_8));
            DefaultFileUploadService service = new DefaultFileUploadService(
                    RetrofitServerClient.create(server.url("/").toString(), () -> null, true)
            );
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<CallResult<UploadedFile>> result = new AtomicReference<>();

            service.upload(file, value -> {
                result.set(value);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertTrue(result.get().isOk());
            assertEquals("CGM_Cache_data.txt", result.get().data.fileName);
        }
    }

    @Test
    public void uploadMissingFileReturnsLocalError() {
        DefaultFileUploadService service = new DefaultFileUploadService(
                RetrofitServerClient.create("http://127.0.0.1:1/", () -> null, true)
        );
        AtomicReference<CallResult<UploadedFile>> result = new AtomicReference<>();

        service.upload(new File(temporaryFolder.getRoot(), "missing.txt"), result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isError());
        assertEquals(CallResult.LOCAL_FILE_NOT_FOUND, result.get().code);
    }
}
