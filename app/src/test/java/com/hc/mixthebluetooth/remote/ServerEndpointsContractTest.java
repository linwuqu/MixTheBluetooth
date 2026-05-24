package com.hc.mixthebluetooth.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class ServerEndpointsContractTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void authEndpointsUseExpectedHttpPaths() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(accountResponse());
            server.enqueue(accountResponse());
            server.enqueue(accountResponse());
            server.start();

            ServerEndpoints endpoints = ServerClient.create(
                    server.url("/").toString(),
                    new OkHttpClient.Builder().build()
            );

            endpoints.login(new ServerModels.LoginReq("13800138000", "123456")).execute();
            endpoints.register(new ServerModels.RegisterReq("debug-user", "123456", "13800138000", null)).execute();
            endpoints.detail().execute();

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
    public void uploadEndpointUsesMultipartContract() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(fileResponse());
            server.start();

            File file = temporaryFolder.newFile("CGM_Cache_data.txt");
            Files.write(file.toPath(),
                    "Start Playback\nEIS:1,1000,0.12\n".getBytes(StandardCharsets.UTF_8));

            ServerEndpoints endpoints = ServerClient.create(
                    server.url("/").toString(),
                    new OkHttpClient.Builder().build()
            );

            endpoints.upload(
                    text(file.getName()),
                    text(""),
                    text("0"),
                    text(String.valueOf(file.length())),
                    MultipartBody.Part.createFormData(
                            "file",
                            file.getName(),
                            RequestBody.create(file, MediaType.parse("text/plain"))
                    )
            ).execute();

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

    private static RequestBody text(String value) {
        return RequestBody.create(value, MediaType.parse("text/plain"));
    }

    private static MockResponse accountResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"success\":true,\"msg\":\"\",\"data\":{\"accountId\":1,\"username\":\"debug-user\",\"phone\":\"13800138000\",\"token\":\"token\"}}");
    }

    private static MockResponse fileResponse() {
        return new MockResponse()
                .setResponseCode(200)
                .setBody("{\"code\":0,\"success\":true,\"msg\":\"\",\"data\":{\"fileId\":9,\"fileName\":\"CGM_Cache_data.txt\",\"path\":\"/files/CGM_Cache_data.txt\"}}");
    }
}
