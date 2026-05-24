package com.hc.mixthebluetooth.remote;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ServerEndpoints {
    @POST("/api/account/v1/login")
    Call<ServerResponse<ServerModels.AccountResp>> login(@Body ServerModels.LoginReq req);

    @POST("/api/account/v1/register")
    Call<ServerResponse<ServerModels.AccountResp>> register(@Body ServerModels.RegisterReq req);

    @GET("/api/account/v1/detail")
    Call<ServerResponse<ServerModels.AccountResp>> detail();

    @Multipart
    @POST("/api/file/v1/upload")
    Call<ServerResponse<ServerModels.FileResp>> upload(
            @Part("fileName") RequestBody fileName,
            @Part("identify") RequestBody identify,
            @Part("parentId") RequestBody parentId,
            @Part("fileSize") RequestBody fileSize,
            @Part MultipartBody.Part file
    );
}
