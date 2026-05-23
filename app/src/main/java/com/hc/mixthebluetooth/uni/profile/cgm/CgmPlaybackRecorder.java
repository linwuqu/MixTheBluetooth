package com.hc.mixthebluetooth.uni.profile.cgm;

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

public final class CgmPlaybackRecorder {
    public interface DateProvider {
        @NonNull
        String today();
    }

    public static final class Result {
        private final boolean recording;
        private final boolean completed;
        @Nullable
        private final File file;

        private Result(boolean recording, boolean completed, @Nullable File file) {
            this.recording = recording;
            this.completed = completed;
            this.file = file;
        }

        public boolean isRecording() {
            return recording;
        }

        public boolean isCompleted() {
            return completed;
        }

        @Nullable
        public File file() {
            return file;
        }
    }

    private final File dir;
    private final DateProvider dateProvider;
    private boolean recording;
    @Nullable
    private File currentFile;

    public CgmPlaybackRecorder(@NonNull Context context) {
        this(defaultDir(context), () -> new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
    }

    CgmPlaybackRecorder(@NonNull File dir, @NonNull DateProvider dateProvider) {
        this.dir = dir;
        this.dateProvider = dateProvider;
    }

    @NonNull
    public Result onLine(@Nullable String line) {
        if (line == null) return new Result(recording, false, currentFile);
        if (line.contains("Start Playback")) {
            recording = true;
            currentFile = new File(dir, dateProvider.today() + "CGM_Cache_data.txt");
            append(line);
            return new Result(true, false, currentFile);
        }
        if (line.contains("Playback all done")) {
            recording = false;
            return new Result(false, true, currentFile);
        }
        if (recording) {
            append(line);
        }
        return new Result(recording, false, currentFile);
    }

    @Nullable
    public File currentFile() {
        return currentFile;
    }

    private void append(@NonNull String line) {
        if (!dir.exists()) dir.mkdirs();
        if (currentFile == null) return;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(currentFile, true), StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.write("\n");
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private static File defaultDir(@NonNull Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        return dir != null ? dir : new File(context.getFilesDir(), "documents");
    }
}
