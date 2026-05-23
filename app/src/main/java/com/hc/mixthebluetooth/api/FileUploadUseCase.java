package com.hc.mixthebluetooth.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class FileUploadUseCase {
    public interface ResultCallback {
        void onSuccess(@NonNull FileUploadResp resp);

        void onError(@NonNull String message);
    }

    private final FileApi fileApi;

    public FileUploadUseCase(@NonNull Context context) {
        this.fileApi = ApiClient.get(context).fileApi();
    }

    public void uploadRootFile(@NonNull File file, @NonNull ResultCallback callback) {
        if (!file.exists() || !file.isFile()) {
            callback.onError("文件不存在: " + file.getAbsolutePath());
            return;
        }

        RequestBody fileName = text(file.getName());
        RequestBody identify = text("");
        RequestBody parentId = text("0");
        RequestBody fileSize = text(String.valueOf(file.length()));
        RequestBody body = RequestBody.create(file, MediaType.parse("text/plain"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), body);

        fileApi.upload(fileName, identify, parentId, fileSize, part)
                .enqueue(new Callback<JsonData<FileUploadResp>>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonData<FileUploadResp>> call,
                                           @NonNull Response<JsonData<FileUploadResp>> response) {
                        JsonData<FileUploadResp> json = response.body();
                        if (response.isSuccessful() && json != null && json.success && json.data != null) {
                            callback.onSuccess(json.data);
                        } else {
                            callback.onError(json != null && json.msg != null ? json.msg : "上传失败");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonData<FileUploadResp>> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "网络错误");
                    }
                });
    }

    private static RequestBody text(@Nullable String value) {
        return RequestBody.create(value == null ? "" : value, MediaType.parse("text/plain"));
    }
}
