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
    private final CgmReplayCompletionDetector detector = new CgmReplayCompletionDetector();

    public DefaultCgmWorkflow(@NonNull FileRecorder recorder,
                              @NonNull CgmService cgmJobService,
                              @NonNull AppLogger logger) {
        this.recorder = recorder;
        this.cgmJobService = cgmJobService;
        this.logger = logger;
    }

    @Override
    public synchronized void onDeviceLine(@NonNull String line,
                                          @NonNull ApiCallback<CallResult<CgmResult>> callback) {
        CgmReplayCompletionDetector.Event event = detector.consume(line);
        logger.text(OWNER, API_REPLAY, "event", event.name());

        if (event == CgmReplayCompletionDetector.Event.STARTED) {
            recorder.start();
            recorder.appendLine(line);
            callback.onResult(CallResult.pending("device replay pending"));
            return;
        }
        if (event == CgmReplayCompletionDetector.Event.RECORDING_LINE) {
            recorder.appendLine(line);
            callback.onResult(CallResult.pending("device replay pending"));
            return;
        }
        if (event == CgmReplayCompletionDetector.Event.COMPLETED) {
            uploadCompletedReplay(callback);
            return;
        }
        if (event == CgmReplayCompletionDetector.Event.INCOMPLETE) {
            callback.onResult(CallResult.error(
                    CallResult.DEVICE_REPLAY_INCOMPLETE,
                    "Device replay incomplete",
                    null
            ));
            return;
        }

        callback.onResult(CallResult.pending("device replay pending"));
    }

    @Override
    public synchronized void reset() {
        detector.reset();
        recorder.reset();
        logger.text(OWNER, API_REPLAY, "reset", "ok");
    }

    private void uploadCompletedReplay(@NonNull ApiCallback<CallResult<CgmResult>> callback) {
        File file;
        try {
            file = recorder.finish();
        } catch (IllegalStateException e) {
            callback.onResult(CallResult.error(
                    CallResult.DEVICE_REPLAY_INCOMPLETE,
                    "Device replay incomplete",
                    e
            ));
            return;
        }

        if (!file.exists() || !file.isFile()) {
            callback.onResult(CallResult.error(
                    CallResult.LOCAL_FILE_NOT_FOUND,
                    "CGM cache txt file not found",
                    null
            ));
            return;
        }

        logger.text(OWNER, API_UPLOAD_POLL, "file", file.getAbsolutePath());
        cgmJobService.uploadAndPoll(file, callback);
    }
}
