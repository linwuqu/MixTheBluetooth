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

public final class SampleRecorderImpl implements SampleRecorder {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean recording = false;
    private volatile File recordFile = null;
    private int sampleCount = 0;

    public SampleRecorderImpl() {}

    @Override public void start(@NonNull Context c, @NonNull String p) {
        recordFile = createFile(c, p);
        recording = true;
        sampleCount = 0;
    }

    @Override public void stop() { recording = false; }

    @Override public boolean isRecording() { return recording; }

    @Override public int getSampleCount() { return sampleCount; }

    @Override public String exportPath() { return recordFile != null ? recordFile.getAbsolutePath() : ""; }

    @Override public void appendLine(@Nullable String json) {
        if (!recording || recordFile == null || json == null || json.trim().isEmpty()) return;
        File f = recordFile;
        String line = json.trim();
        sampleCount++;
        io.execute(() -> {
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
                w.write(line);
                w.newLine();
            } catch (Exception ignored) {}
        });
    }

    @Override public void release() { io.shutdown(); }

    @NonNull
    private File createFile(@NonNull Context c, @NonNull String p) {
        File dir = c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, p + "_" + ts + ".jsonl");
    }
}
