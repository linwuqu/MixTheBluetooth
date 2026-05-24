package com.hc.mixthebluetooth.api.device;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.file.UploadedFile;

import java.io.File;

public interface DeviceDataService {
    void replaySample(ApiCallback<CallResult<File>> callback);

    void consumeLine(String line, ApiCallback<CallResult<File>> callback);

    File lastDataFile();

    void uploadLastDataFile(ApiCallback<CallResult<UploadedFile>> callback);
}
