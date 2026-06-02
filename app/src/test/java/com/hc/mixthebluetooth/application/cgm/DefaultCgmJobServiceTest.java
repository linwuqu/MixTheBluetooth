package com.hc.mixthebluetooth.application.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.driver.implementation.http.CgmJobRespParsingTest;
import com.hc.mixthebluetooth.driver.implementation.http.RetrofitServerClient;
import com.hc.mixthebluetooth.driver.implementation.http.endpoint.BioAiEndpoints;

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
import okhttp3.mockwebserver.RecordedRequest;

public class DefaultCgmJobServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void uploadAndPollDeletesFileAndReturnsGeneratedJobData() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"code\":200,\"success\":true,\"msg\":\"success\",\"data\":{\"jobId\":456}}"));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(CgmJobRespParsingTest.SAMPLE_JSON));
            server.start();

            File file = temporaryFolder.newFile("CGM_Cache_data.txt");
            Files.write(file.toPath(),
                    "Start Playback\nEIS:1,1000,0.12\nPlayback all done\n"
                            .getBytes(StandardCharsets.UTF_8));

            BioAiEndpoints endpoints = RetrofitServerClient.create(server.url("/").toString(), () -> null, true);
            DefaultCgmJobService service = new DefaultCgmJobService(endpoints, (runnable, delayMillis) -> runnable.run());
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<CallResult<CgmResult>> result = new AtomicReference<>();

            service.uploadAndPoll(file, value -> {
                result.set(value);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertTrue(result.get().isOk());
            assertNotNull(result.get().data);
            assertEquals(456L, result.get().data.jobId);
            assertEquals("GENERATED", result.get().data.status);
            assertFalse(file.exists());

            RecordedRequest upload = server.takeRequest();
            assertEquals("POST", upload.getMethod());
            assertEquals("/api/test/v1/upload", upload.getPath());
            assertTrue(upload.getBody().readUtf8().contains("Start Playback"));

            RecordedRequest poll = server.takeRequest();
            assertEquals("GET", poll.getMethod());
            assertEquals("/api/cgm/v1/jobs/456", poll.getPath());
        }
    }

    @Test
    public void uploadHttpFailureKeepsFileAndReturnsError() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("{\"code\":500,\"success\":false,\"msg\":\"upload failed\",\"data\":null}"));
            server.start();

            File file = cacheFile();
            DefaultCgmJobService service = service(server);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<CallResult<CgmResult>> result = new AtomicReference<>();

            service.uploadAndPoll(file, value -> {
                result.set(value);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertFalse(result.get().isOk());
            assertTrue(file.exists());

            RecordedRequest upload = server.takeRequest();
            assertEquals("POST", upload.getMethod());
            assertEquals("/api/test/v1/upload", upload.getPath());
        }
    }

    @Test
    public void uploadSuccessWithoutJobIdKeepsFileAndReturnsEmptyData() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"code\":200,\"success\":true,\"msg\":\"success\",\"data\":{}}"));
            server.start();

            File file = cacheFile();
            DefaultCgmJobService service = service(server);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<CallResult<CgmResult>> result = new AtomicReference<>();

            service.uploadAndPoll(file, value -> {
                result.set(value);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertFalse(result.get().isOk());
            assertEquals(CallResult.EMPTY_DATA, result.get().code);
            assertTrue(file.exists());
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    public void pollContinuesUntilGenerated() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(CgmJobRespParsingTest.SAMPLE_JSON.replace("\"GENERATED\"", "\"PROCESSING\"")));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(CgmJobRespParsingTest.SAMPLE_JSON));
            server.start();

            DefaultCgmJobService service = service(server);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<CallResult<CgmResult>> result = new AtomicReference<>();

            service.poll(456L, value -> {
                result.set(value);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertTrue(result.get().isOk());
            assertEquals("GENERATED", result.get().data.status);

            RecordedRequest first = server.takeRequest();
            RecordedRequest second = server.takeRequest();
            assertEquals("/api/cgm/v1/jobs/456", first.getPath());
            assertEquals("/api/cgm/v1/jobs/456", second.getPath());
        }
    }

    @Test
    public void generatedResultWithMissingOptionalSummaryDoesNotCrashService() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("{\"code\":200,\"message\":\"success\",\"data\":{\"jobId\":456,\"status\":\"GENERATED\",\"pointCount\":0,\"unitCount\":0}}"));
            server.start();

            DefaultCgmJobService service = service(server);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<CallResult<CgmResult>> result = new AtomicReference<>();

            service.poll(456L, value -> {
                result.set(value);
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertTrue(result.get().isOk());
            assertNotNull(result.get().data);
            assertEquals(456L, result.get().data.jobId);
            assertEquals("GENERATED", result.get().data.status);
        }
    }

    private File cacheFile() throws Exception {
        File file = temporaryFolder.newFile("CGM_Cache_data.txt");
        Files.write(file.toPath(),
                "Start Playback\nEIS:1,1000,0.12\nPlayback all done\n"
                        .getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private DefaultCgmJobService service(MockWebServer server) {
        BioAiEndpoints endpoints = RetrofitServerClient.create(server.url("/").toString(), () -> null, true);
        return new DefaultCgmJobService(endpoints, (runnable, delayMillis) -> runnable.run());
    }
}
