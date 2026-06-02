package com.hc.mixthebluetooth.driver.implementation.http.endpoint;

import com.hc.mixthebluetooth.driver.implementation.http.dto.ServerDtos;
import com.hc.mixthebluetooth.driver.implementation.http.dto.ServerResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface BioAiEndpoints {
    @POST("/api/account/v1/login")
    Call<ServerResponse<ServerDtos.AccountResp>> login(@Body ServerDtos.LoginReq req);

    @POST("/api/account/v1/register")
    Call<ServerResponse<ServerDtos.AccountResp>> register(@Body ServerDtos.RegisterReq req);

    @GET("/api/account/v1/detail")
    Call<ServerResponse<ServerDtos.AccountResp>> detail();

    @Multipart
    @POST("/api/file/v1/upload")
    Call<ServerResponse<ServerDtos.FileResp>> upload(
            @Part("fileName") RequestBody fileName,
            @Part("identify") RequestBody identify,
            @Part("parentId") RequestBody parentId,
            @Part("fileSize") RequestBody fileSize,
            @Part MultipartBody.Part file
    );

    @Multipart
    @POST("/api/test/v1/upload")
    Call<ServerResponse<Object>> testUpload(@Part MultipartBody.Part file);

    @GET("/api/cgm/v1/jobs/{jobId}")
    Call<ServerDtos.CgmJobResp> cgmJob(@Path("jobId") long jobId);
}
