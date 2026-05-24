package com.hc.mixthebluetooth.impl.file;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.api.file.UploadedFile;
import com.hc.mixthebluetooth.remote.ServerEndpoints;
import com.hc.mixthebluetooth.remote.ServerModels;
import com.hc.mixthebluetooth.remote.ServerResponse;

import java.io.File;
import java.util.function.Consumer;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DefaultFileService implements FileService {
    private final ServerEndpoints endpoints;

    public DefaultFileService(@NonNull ServerEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void upload(File file, Consumer<CallResult<UploadedFile>> callback) {
        if (file == null || !file.exists() || !file.isFile()) {
            callback.accept(CallResult.error(CallResult.LOCAL_FILE_NOT_FOUND, "文件不存在", null));
            return;
        }

        RequestBody fileName = text(file.getName());
        RequestBody identify = text("");
        RequestBody parentId = text("0");
        RequestBody fileSize = text(String.valueOf(file.length()));
        RequestBody body = RequestBody.create(file, MediaType.parse("text/plain"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), body);

        endpoints.upload(fileName, identify, parentId, fileSize, part)
                .enqueue(new Callback<ServerResponse<ServerModels.FileResp>>() {
                    @Override
                    public void onResponse(@NonNull Call<ServerResponse<ServerModels.FileResp>> call,
                                           @NonNull Response<ServerResponse<ServerModels.FileResp>> response) {
                        callback.accept(mapFileResponse(response.body()));
                    }

                    @Override
                    public void onFailure(@NonNull Call<ServerResponse<ServerModels.FileResp>> call,
                                          @NonNull Throwable t) {
                        callback.accept(CallResult.error(CallResult.NETWORK, "网络错误", t));
                    }
                });
    }

    @NonNull
    private static CallResult<UploadedFile> mapFileResponse(ServerResponse<ServerModels.FileResp> response) {
        if (response == null) {
            return CallResult.error(CallResult.EMPTY_RESPONSE, "服务端响应为空", null);
        }
        if (!response.success) {
            return CallResult.error(response.code, response.msg != null ? response.msg : "上传失败", null);
        }
        if (response.data == null) {
            return CallResult.error(CallResult.EMPTY_DATA, "文件数据为空", null);
        }
        return CallResult.ok(toUploadedFile(response.data));
    }

    @NonNull
    private static UploadedFile toUploadedFile(@NonNull ServerModels.FileResp resp) {
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
