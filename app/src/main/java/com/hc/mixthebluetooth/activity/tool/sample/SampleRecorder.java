package com.hc.mixthebluetooth.activity.tool.sample;

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

/**
 * Records parsed sample JSON lines to one file per recording session.
 */
public final class SampleRecorder {

    public interface Callback {
        void onRecordStateChanged(boolean recording);

        void onRecordExported(@NonNull String path);

        void onRecordError(@NonNull String message);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Callback callback;

    private volatile boolean recording = false;
    private volatile File recordFile = null;
    private int sampleCount = 0;

    public SampleRecorder(@Nullable Callback callback) {
        this.callback = callback;
    }

    public void start(@NonNull Context context, @NonNull String filePrefix) {
        recordFile = createRecordFile(context, filePrefix);
        recording = true;
        sampleCount = 0;
        notifyStateChanged(true);
    }

    public void stop() {
        recording = false;
        notifyStateChanged(false);
    }

    public boolean isRecording() {
        return recording;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    @NonNull
    public String exportPath() {
        String path = getRecordPath();
        if (callback != null) {
            callback.onRecordExported(path);
        }
        return path;
    }

    public void appendLine(@Nullable String jsonLine) {
        if (!canAppend(jsonLine)) return;

        File file = recordFile;
        String safeLine = jsonLine.trim();
        sampleCount++;
        io.execute(() -> appendUtf8Line(file, safeLine));
    }

    public void release() {
        io.shutdown();
    }

    private boolean canAppend(@Nullable String jsonLine) {
        if (!recording) return false;
        if (recordFile == null) return false;
        return jsonLine != null && !jsonLine.trim().isEmpty();
    }

    @NonNull
    private String getRecordPath() {
        return recordFile != null ? recordFile.getAbsolutePath() : "";
    }

    private void notifyStateChanged(boolean recording) {
        if (callback != null) {
            callback.onRecordStateChanged(recording);
        }
    }

    @NonNull
    private File createRecordFile(@NonNull Context context, @NonNull String filePrefix) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(dir, filePrefix + "_" + ts + ".jsonl");

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && callback != null) {
            callback.onRecordError("Create record directory failed: " + parent.getAbsolutePath());
        }
        return file;
    }

    private void appendUtf8Line(@NonNull File file, @NonNull String line) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
        } catch (Exception e) {
            if (callback != null) {
                callback.onRecordError("Record write failed: " + e.getMessage());
            }
        }
    }
}
