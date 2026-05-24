package com.hc.mixthebluetooth.local;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Supplies fixed replay lines for mock/debug verification when no Bluetooth board is connected.
 */
public final class DeviceReplaySample {
    public static final String DEFAULT_ASSET = "device/device_replay_sample.txt";

    private final Context context;
    private final List<String> fixedLines;

    public DeviceReplaySample(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.fixedLines = null;
    }

    public DeviceReplaySample(@NonNull List<String> fixedLines) {
        this.context = null;
        this.fixedLines = new ArrayList<>(fixedLines);
    }

    @NonNull
    public List<String> readDefaultLines() throws IOException {
        if (fixedLines != null) {
            return new ArrayList<>(fixedLines);
        }
        try (InputStream input = context.getAssets().open(DEFAULT_ASSET)) {
            return readLines(input);
        }
    }

    @NonNull
    public static List<String> readLines(@NonNull InputStream input) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
