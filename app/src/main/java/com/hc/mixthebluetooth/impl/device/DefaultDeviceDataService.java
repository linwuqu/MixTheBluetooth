package com.hc.mixthebluetooth.impl.device;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.api.file.UploadedFile;
import com.hc.mixthebluetooth.local.DeviceDataRecorder;
import com.hc.mixthebluetooth.local.DeviceReplaySample;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public final class DefaultDeviceDataService implements DeviceDataService {
    private final DeviceDataRecorder recorder;
    private final DeviceReplaySample sample;
    private final FileService fileService;

    public DefaultDeviceDataService(@NonNull DeviceDataRecorder recorder,
                                    @NonNull DeviceReplaySample sample,
                                    @NonNull FileService fileService) {
        this.recorder = recorder;
        this.sample = sample;
        this.fileService = fileService;
    }

    @Override
    public void replaySample(Consumer<CallResult<File>> callback) {
        try {
            List<String> lines = sample.readDefaultLines();
            CallResult<File> last = CallResult.error(
                    CallResult.DEVICE_REPLAY_INCOMPLETE,
                    "设备回放数据不完整",
                    null
            );
            for (String line : lines) {
                last = recorder.consumeLine(line);
            }
            callback.accept(last.isOk() ? last : CallResult.error(
                    CallResult.DEVICE_REPLAY_INCOMPLETE,
                    "设备回放数据不完整",
                    null
            ));
        } catch (Exception e) {
            callback.accept(CallResult.error(CallResult.DEVICE_REPLAY_INCOMPLETE, "设备回放失败", e));
        }
    }

    @Override
    public void consumeLine(String line, Consumer<CallResult<File>> callback) {
        callback.accept(recorder.consumeLine(line));
    }

    @Override
    public File lastDataFile() {
        return recorder.currentFile();
    }

    @Override
    public void uploadLastDataFile(Consumer<CallResult<UploadedFile>> callback) {
        File file = lastDataFile();
        if (file == null || !file.exists()) {
            callback.accept(CallResult.error(CallResult.LOCAL_FILE_NOT_FOUND, "文件不存在", null));
            return;
        }
        fileService.upload(file, callback);
    }
}
