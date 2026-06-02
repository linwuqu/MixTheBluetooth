package com.hc.mixthebluetooth.ui.cgm;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public final class Output {
    private final SampleRecorder recorder = new SampleRecorder();

    public void start(@NonNull Context context, @NonNull String prefix) {
        recorder.start(context, prefix);
    }

    public void stop() {
        recorder.stop();
    }

    public boolean isRecording() {
        return recorder.isRecording();
    }

    public int sampleCount() {
        return recorder.getSampleCount();
    }

    @NonNull
    public String exportPath() {
        return recorder.exportPath();
    }

    public void appendJsonLine(@Nullable String json) {
        recorder.appendLine(json);
    }

    public void release() {
        recorder.release();
    }
}
