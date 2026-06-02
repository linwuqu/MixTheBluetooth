package com.hc.mixthebluetooth.driver.implementation.file;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.driver.capability.DeviceReplayRecorder;
import com.hc.mixthebluetooth.driver.capability.FileRecorder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AndroidFileRecorder implements DeviceReplayRecorder, FileRecorder {
    public interface DateProvider {
        @NonNull
        String today();
    }

    private final File dir;
    private final DateProvider dateProvider;
    private boolean recording;
    @Nullable
    private File currentFile;

    public AndroidFileRecorder(@NonNull Context context) {
        this(defaultDir(context), () -> new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
    }

    public AndroidFileRecorder(@NonNull File dir, @NonNull DateProvider dateProvider) {
        this.dir = dir;
        this.dateProvider = dateProvider;
    }

    @NonNull
    @Override
    public CallResult<File> consumeLine(@Nullable String line) {
        if (line == null || line.isEmpty()) {
            return CallResult.pending(recording ? "recording" : "idle");
        }
        if (line.contains("Start Playback")) {
            recording = true;
            currentFile = new File(dir, dateProvider.today() + "CGM_Cache_data.txt");
            appendLine(line);
            return CallResult.pending("recording");
        }
        if (line.contains("Playback all done")) {
            recording = false;
            return currentFile != null
                    ? CallResult.ok(currentFile)
                    : CallResult.error(CallResult.DEVICE_REPLAY_INCOMPLETE, "设备回放数据不完整", null);
        }
        if (recording) {
            appendLine(line);
            return CallResult.pending("recording");
        }
        return CallResult.pending("idle");
    }

    @Nullable
    @Override
    public File currentFile() {
        return currentFile;
    }

    @Override
    public void appendLine(@NonNull String line) {
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
    @Override
    public File finish() {
        if (currentFile == null) {
            throw new IllegalStateException("No replay file has been started");
        }
        recording = false;
        return currentFile;
    }

    @Override
    public void reset() {
        recording = false;
        currentFile = null;
    }

    @NonNull
    private static File defaultDir(@NonNull Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        return dir != null ? dir : new File(context.getFilesDir(), "documents");
    }
}
