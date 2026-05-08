package com.hc.mixthebluetooth.activity.tool;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface SampleRecorder {
    boolean isRecording();
    void start(@NonNull Context ctx, @NonNull String prefix);
    void stop();
    void appendLine(@Nullable String json);
    void release();
    int getSampleCount();
    String exportPath();
}
