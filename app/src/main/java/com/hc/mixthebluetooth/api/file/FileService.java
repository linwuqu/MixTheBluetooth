package com.hc.mixthebluetooth.api.file;

import com.hc.mixthebluetooth.api.CallResult;

import java.io.File;
import java.util.function.Consumer;

public interface FileService {
    void upload(File file, Consumer<CallResult<UploadedFile>> callback);
}
