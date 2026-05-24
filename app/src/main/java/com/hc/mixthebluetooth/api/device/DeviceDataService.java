package com.hc.mixthebluetooth.api.device;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.file.UploadedFile;

import java.io.File;
import java.util.function.Consumer;

public interface DeviceDataService {
    void replaySample(Consumer<CallResult<File>> callback);

    void consumeLine(String line, Consumer<CallResult<File>> callback);

    File lastDataFile();

    void uploadLastDataFile(Consumer<CallResult<UploadedFile>> callback);
}
