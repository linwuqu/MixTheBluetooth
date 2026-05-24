package com.hc.mixthebluetooth.local;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.CallResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DeviceDataRecorder {
    public interface DateProvider {
        @NonNull
        String today();
    }

    private final File dir;
    private final DateProvider dateProvider;
    private boolean recording;
    @Nullable
    private File currentFile;

    public DeviceDataRecorder(@NonNull Context context) {
        this(defaultDir(context), () -> new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
    }

    public DeviceDataRecorder(@NonNull File dir, @NonNull DateProvider dateProvider) {
        this.dir = dir;
        this.dateProvider = dateProvider;
    }

    @NonNull
    public CallResult<File> consumeLine(@Nullable String line) {
        if (line == null || line.isEmpty()) {
            return CallResult.pending(recording ? "recording" : "idle");
        }
        if (line.contains("Start Playback")) {
            recording = true;
            currentFile = new File(dir, dateProvider.today() + "CGM_Cache_data.txt");
            append(line);
            return CallResult.pending("recording");
        }
        if (line.contains("Playback all done")) {
            recording = false;
            return currentFile != null
                    ? CallResult.ok(currentFile)
                    : CallResult.error(CallResult.DEVICE_REPLAY_INCOMPLETE, "设备回放数据不完整", null);
        }
        if (recording) {
            append(line);
            return CallResult.pending("recording");
        }
        return CallResult.pending("idle");
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
