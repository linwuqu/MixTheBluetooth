package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.ApiModels.AccountLoginReq;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class RetrofitHttpContractTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void authApiUsesExpectedPaths() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(accountResponse());
            server.enqueue(accountResponse());
            server.enqueue(accountResponse());
            server.start();

            AuthApi authApi = ApiClient.createAuthApi(
                    server.url("/").toString(),
                    new OkHttpClient.Builder().build()
            );

            authApi.login(new AccountLoginReq("13800138000", "123456")).execute();
            authApi.register(new AccountRegisterReq("debug-user", "123456", "13800138000", null)).execute();
            authApi.detail().execute();

            RecordedRequest login = server.takeRequest();
            assertEquals("POST", login.getMethod());
            assertEquals("/api/account/v1/login", login.getPath());
            assertTrue(login.getBody().readUtf8().contains("13800138000"));

            RecordedRequest register = server.takeRequest();
            assertEquals("POST", register.getMethod());
            assertEquals("/api/account/v1/register", register.getPath());
            assertTrue(register.getBody().readUtf8().contains("debug-user"));

            RecordedRequest detail = server.takeRequest();
            assertEquals("GET", detail.getMethod());
            assertEquals("/api/account/v1/detail", detail.getPath());
        }
    }

    @Test
    public void uploadUseCaseSendsMultipartFileContract() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(uploadResponse());
            server.start();

            File file = temporaryFolder.newFile("CGM_Cache_data.txt");
            Files.write(file.toPath(),
                    "Start Playback\nEIS:1,1000,0.12\n".getBytes(StandardCharsets.UTF_8));

            FileApi fileApi = ApiClient.createFileApi(
                    server.url("/").toString(),
                    new OkHttpClient.Builder().build()
            );

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<FileUploadResp> result = new AtomicReference<>();
            AtomicReference<String> error = new AtomicReference<>();

            new FileUploadUseCase(fileApi).uploadRootFile(file, new FileUploadUseCase.ResultCallback() {
                @Override
                public void onSuccess(FileUploadResp resp) {
                    result.set(resp);
                    latch.countDown();
                }

                @Override
                public void onError(String message) {
                    error.set(message);
                    latch.countDown();
                }
            });

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(null, error.get());
            assertNotNull(result.get());

            RecordedRequest upload = server.takeRequest();
            assertEquals("POST", upload.getMethod());
            assertEquals("/api/file/v1/upload", upload.getPath());
            assertTrue(upload.getHeader("Content-Type").contains("multipart/form-data"));

            String body = upload.getBody().readUtf8();
            assertTrue(body.contains("fileName"));
            assertTrue(body.contains("fileSize"));
            assertTrue(body.contains("CGM_Cache_data.txt"));
            assertTrue(body.contains("Start Playback"));
            assertTrue(body.contains("EIS:1,1000,0.12"));
        }
    }

    private static MockResponse accountResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"success\":true,\"msg\":\"\",\"data\":{\"accountId\":1,\"username\":\"debug-user\",\"phone\":\"13800138000\",\"token\":\"token\"}}");
    }

    private static MockResponse uploadResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"success\":true,\"msg\":\"\",\"data\":{\"fileId\":9,\"fileName\":\"CGM_Cache_data.txt\",\"path\":\"/files/CGM_Cache_data.txt\"}}");
    }
}
