package com.hc.mixthebluetooth.impl.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.remote.CgmJobRespParsingTest;
import com.hc.mixthebluetooth.remote.ServerClient;
import com.hc.mixthebluetooth.remote.ServerEndpoints;
import com.hc.mixthebluetooth.remote.ServerModels;

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

public class DefaultCgmServiceTest {
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

            ServerEndpoints endpoints = ServerClient.create(server.url("/").toString(), () -> null, true);
            DefaultCgmService service = new DefaultCgmService(endpoints, (runnable, delayMillis) -> runnable.run());
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<CallResult<ServerModels.CgmJobData>> result = new AtomicReference<>();

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
}
