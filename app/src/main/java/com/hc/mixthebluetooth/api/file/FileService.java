package com.hc.mixthebluetooth.api.file;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;

import java.io.File;

public interface FileService {
    void upload(File file, ApiCallback<CallResult<UploadedFile>> callback);
}
