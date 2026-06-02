package com.hc.mixthebluetooth.application.file;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.file.FileUploadService;
import com.hc.mixthebluetooth.api.file.UploadedFile;
import com.hc.mixthebluetooth.driver.implementation.http.endpoint.BioAiEndpoints;
import com.hc.mixthebluetooth.driver.implementation.http.dto.ServerDtos;
import com.hc.mixthebluetooth.driver.implementation.http.dto.ServerResponse;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DefaultFileUploadService implements FileUploadService {
    private final BioAiEndpoints endpoints;

    public DefaultFileUploadService(@NonNull BioAiEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void upload(File file, ApiCallback<CallResult<UploadedFile>> callback) {
        if (file == null || !file.exists() || !file.isFile()) {
            callback.onResult(CallResult.error(CallResult.LOCAL_FILE_NOT_FOUND, "File not found", null));
            return;
        }

        RequestBody fileName = text(file.getName());
        RequestBody identify = text("");
        RequestBody parentId = text("0");
        RequestBody fileSize = text(String.valueOf(file.length()));
        RequestBody body = RequestBody.create(file, MediaType.parse("text/plain"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), body);

        endpoints.upload(fileName, identify, parentId, fileSize, part)
                .enqueue(new Callback<ServerResponse<ServerDtos.FileResp>>() {
                    @Override
                    public void onResponse(@NonNull Call<ServerResponse<ServerDtos.FileResp>> call,
                                           @NonNull Response<ServerResponse<ServerDtos.FileResp>> response) {
                        callback.onResult(mapFileResponse(response.body()));
                    }

                    @Override
                    public void onFailure(@NonNull Call<ServerResponse<ServerDtos.FileResp>> call,
                                          @NonNull Throwable t) {
                        callback.onResult(CallResult.error(CallResult.NETWORK, "Network error", t));
                    }
                });
    }

    @NonNull
    private static CallResult<UploadedFile> mapFileResponse(ServerResponse<ServerDtos.FileResp> response) {
        if (response == null) {
            return CallResult.error(CallResult.EMPTY_RESPONSE, "Empty server response", null);
        }
        if (!response.success) {
            return CallResult.error(response.code, response.msg != null ? response.msg : "Upload failed", null);
        }
        if (response.data == null) {
            return CallResult.error(CallResult.EMPTY_DATA, "Empty file data", null);
        }
        return CallResult.ok(toUploadedFile(response.data));
    }

    @NonNull
    private static UploadedFile toUploadedFile(@NonNull ServerDtos.FileResp resp) {
        UploadedFile file = new UploadedFile();
        file.fileId = resp.fileId;
        file.fileName = resp.fileName;
        file.path = resp.path;
        file.url = resp.url;
        return file;
    }

    private static RequestBody text(String value) {
        return RequestBody.create(value, MediaType.parse("text/plain"));
    }
}
