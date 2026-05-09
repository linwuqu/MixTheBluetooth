package com.hc.mixthebluetooth.activity.tool;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SampleRecorder {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean recording = false;
    private volatile File recordFile = null;
    private int sampleCount = 0;

    public void start(@NonNull Context context, @NonNull String prefix) {
        recordFile = createFile(context, prefix);
        recording = true;
        sampleCount = 0;
    }

    public void stop() {
        recording = false;
    }

    public boolean isRecording() {
        return recording;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    @NonNull
    public String exportPath() {
        return recordFile != null ? recordFile.getAbsolutePath() : "";
    }

    public void appendLine(@Nullable String json) {
        if (!recording || recordFile == null || json == null || json.trim().isEmpty()) return;
        File file = recordFile;
        String line = json.trim();
        sampleCount++;
        io.execute(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                writer.write(line);
                writer.newLine();
            } catch (Exception ignored) {
            }
        });
    }

    public void release() {
        io.shutdown();
    }

    @NonNull
    private File createFile(@NonNull Context context, @NonNull String prefix) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, prefix + "_" + ts + ".jsonl");
    }
}
