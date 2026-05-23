package com.hc.mixthebluetooth.cgm;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;
import com.hc.mixthebluetooth.api.FileUploadUseCase;
import com.hc.mixthebluetooth.uni.profile.cgm.CgmPlaybackRecorder;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public final class CgmReplayUploadUseCase {
    public interface ResultCallback {
        void onSuccess(@NonNull File file, @NonNull FileUploadResp resp);

        void onError(@NonNull String message);
    }

    private final Context context;
    private final FileUploadUseCase uploadUseCase;

    public CgmReplayUploadUseCase(@NonNull Context context) {
        this(context, new FileUploadUseCase(context));
    }

    CgmReplayUploadUseCase(@NonNull Context context, @NonNull FileUploadUseCase uploadUseCase) {
        this.context = context.getApplicationContext();
        this.uploadUseCase = uploadUseCase;
    }

    public void replayAssetAndUpload(@NonNull String assetPath, @NonNull ResultCallback callback) {
        File completedFile;
        try (InputStream input = context.getAssets().open(assetPath)) {
            List<String> lines = CgmReplayFixture.readLines(input);
            CgmPlaybackRecorder recorder = new CgmPlaybackRecorder(context);
            completedFile = replay(lines, recorder);
        } catch (Exception e) {
            callback.onError(e.getMessage() != null ? e.getMessage() : "CGM 回放失败");
            return;
        }

        uploadUseCase.uploadRootFile(completedFile, new FileUploadUseCase.ResultCallback() {
            @Override
            public void onSuccess(@NonNull FileUploadResp resp) {
                callback.onSuccess(completedFile, resp);
            }

            @Override
            public void onError(@NonNull String message) {
                callback.onError(message);
            }
        });
    }

    @NonNull
    private static File replay(@NonNull List<String> lines, @NonNull CgmPlaybackRecorder recorder) {
        File file = null;
        boolean completed = false;
        for (String line : lines) {
            CgmPlaybackRecorder.Result result = recorder.onLine(line);
            if (result.file() != null) {
                file = result.file();
            }
            if (result.isCompleted()) {
                completed = true;
            }
        }
        if (!completed || file == null) {
            throw new IllegalStateException("CGM fixture 没有完整的 Playback 边界");
        }
        return file;
    }
}
