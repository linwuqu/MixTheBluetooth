package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import org.junit.Test;

import retrofit2.Response;

public class MockApiTest {
    @Test
    public void registerReturnsMockToken() throws Exception {
        Response<JsonData<AccountInfo>> response = MockApi.authApi()
                .register(new AccountRegisterReq("tester", "123456", "13800138000", null))
                .execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertTrue(response.body().success);
        assertEquals("mock-token", response.body().data.token);
    }
}
