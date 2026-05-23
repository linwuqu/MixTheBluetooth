package com.hc.mixthebluetooth.api;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountLoginReq;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("/api/account/v1/register")
    Call<JsonData<AccountInfo>> register(@Body AccountRegisterReq req);

    @POST("/api/account/v1/login")
    Call<JsonData<AccountInfo>> login(@Body AccountLoginReq req);

    @GET("/api/account/v1/detail")
    Call<JsonData<AccountInfo>> detail();
}
