package com.hc.mixthebluetooth.application.cgm;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.api.log.AppLogger;
import com.hc.mixthebluetooth.driver.capability.FileRecorder;

import java.io.File;

public final class DefaultCgmWorkflow implements CgmWorkflow {
    private static final String OWNER = "DefaultCgmWorkflow";
    private static final String API_REPLAY = "CGM_REPLAY";
    private static final String API_UPLOAD_POLL = "CGM_UPLOAD_POLL";

    private final FileRecorder recorder;
    private final CgmService cgmJobService;
    private final AppLogger logger;
    private final CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();

    public DefaultCgmWorkflow(@NonNull FileRecorder recorder,
                              @NonNull CgmService cgmJobService,
                              @NonNull AppLogger logger) {
        this.recorder = recorder;
        this.cgmJobService = cgmJobService;
        this.logger = logger;
    }

    @NonNull
    @Override
    public synchronized Update onReadCacheSent() {
        buffer.beginRead();
        logger.text(OWNER, API_REPLAY, "read", "cache read started");
        return Update.message("cache read started");
    }

    @NonNull
    @Override
    public synchronized Update onDeleteCacheSent() {
        buffer.markDeleteSent();
        logger.text(OWNER, API_REPLAY, "delete", "cache delete sent");
        return Update.message("cache delete sent");
    }

    @NonNull
    @Override
    public synchronized Update onDeviceText(@NonNull String text,
                                            @NonNull ApiCallback<CallResult<CgmResult>> callback) {
        CgmCacheSyncBuffer.DeleteResult delete = buffer.acceptDeviceLine(text);
        if (delete.confirmed) {
            logger.text(OWNER, API_REPLAY, "delete", delete.message);
            return Update.deleteConfirmed(delete.message);
        }
        if (CgmCacheSyncBuffer.isDeleteAckText(text)) {
            logger.text(OWNER, API_REPLAY, "delete", delete.message);
            return Update.message(delete.message);
        }

        CgmCacheSyncBuffer.SyncResult sync = buffer.acceptChunk(text);
        if (!sync.sawEnd) {
            return Update.message("cache text accepted");
        }

        CgmCacheSyncBuffer.ValidationResult validation = buffer.validate();
        if (!validation.valid) {
            if (validation.retryable && buffer.canRetry()) {
                buffer.beginRetry();
                logger.text(OWNER, API_REPLAY, "retry", validation.message);
                return Update.command(validation.message, CgmCacheSyncBuffer.READ_CACHE_COMMAND);
            }
            buffer.markError();
            callback.onResult(CallResult.error(
                    CallResult.DEVICE_REPLAY_INCOMPLETE,
                    validation.message,
                    null
            ));
            return Update.error(validation.message);
        }

        return uploadValidatedReplay(callback);
    }

    @Override
    public synchronized void reset() {
        buffer.reset();
        recorder.reset();
        logger.text(OWNER, API_REPLAY, "reset", "ok");
    }

    @NonNull
    private Update uploadValidatedReplay(@NonNull ApiCallback<CallResult<CgmResult>> callback) {
        File file;
        try {
            // 写入本地
            file = buffer.writeTo(recorder);
        } catch (IllegalStateException e) {
            buffer.markError();
            callback.onResult(CallResult.error(
                    CallResult.DEVICE_REPLAY_INCOMPLETE,
                    "Device replay incomplete",
                    e
            ));
            return Update.error("Device replay incomplete");
        }

        if (!file.exists() || !file.isFile()) {
            buffer.markError();
            callback.onResult(CallResult.error(
                    CallResult.LOCAL_FILE_NOT_FOUND,
                    "CGM cache txt file not found",
                    null
            ));
            return Update.error("CGM cache txt file not found");
        }

        buffer.markUploading();
        logger.text(OWNER, API_UPLOAD_POLL, "file", file.getAbsolutePath());
        // 上传至服务器
        cgmJobService.uploadAndPoll(file, result -> {

            synchronized (DefaultCgmWorkflow.this) {
                if (result.isError()) {
                    buffer.markError();
                }
            }
            callback.onResult(result);
        });
        return Update.uploadStarted("upload started");
    }
}
