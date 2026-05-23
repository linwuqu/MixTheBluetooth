package com.hc.mixthebluetooth.api;

import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface FileApi {
    @Multipart
    @POST("/api/file/v1/upload")
    Call<JsonData<FileUploadResp>> upload(
            @Part("fileName") RequestBody fileName,
            @Part("identify") RequestBody identify,
            @Part("parentId") RequestBody parentId,
            @Part("fileSize") RequestBody fileSize,
            @Part MultipartBody.Part file
    );
}
