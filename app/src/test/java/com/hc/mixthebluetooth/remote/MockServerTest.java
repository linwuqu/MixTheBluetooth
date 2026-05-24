package com.hc.mixthebluetooth.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

public class MockServerTest {
    @Test
    public void loginReturnsStaticAccountThroughCall() throws Exception {
        MockServer server = new MockServer();

        Response<ServerResponse<ServerModels.AccountResp>> response =
                server.login(new ServerModels.LoginReq("13800138000", "123456")).execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertTrue(response.body().success);
        assertEquals("13800138000", response.body().data.phone);
        assertEquals("mock-token", response.body().data.token);
    }

    @Test
    public void uploadReturnsStaticFileThroughCall() throws Exception {
        MockServer server = new MockServer();

        Response<ServerResponse<ServerModels.FileResp>> response = server.upload(
                text("CGM_Cache_data.txt"),
                text(""),
                text("0"),
                text("16"),
                MultipartBody.Part.createFormData(
                        "file",
                        "CGM_Cache_data.txt",
                        RequestBody.create("Start Playback", MediaType.parse("text/plain"))
                )
        ).execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertTrue(response.body().success);
        assertEquals("CGM_Cache_data.txt", response.body().data.fileName);
        assertEquals("/mock/CGM_Cache_data.txt", response.body().data.path);
    }

    private static RequestBody text(String value) {
        return RequestBody.create(value, MediaType.parse("text/plain"));
    }
}
