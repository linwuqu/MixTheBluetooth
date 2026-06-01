package com.hc.mixthebluetooth.impl.log;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiTraceLogger {
    private static final String TAG = "BioAI.Http";
    private static final int MAX_CHARS = 4096;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ApiTraceLogger() {
    }

    public static void json(@NonNull String owner,
                            @NonNull String api,
                            @NonNull String label,
                            @Nullable Object body) {
        log(owner + " API " + api + " " + label + "\n" + truncate(toPrettyJson(body)));
    }

    public static void text(@NonNull String owner,
                            @NonNull String api,
                            @NonNull String label,
                            @Nullable String value) {
        log(owner + " API " + api + " " + label + "\n" + truncate(value));
    }

    public static void file(@NonNull String owner, @NonNull String api, @NonNull File file) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", file.getName());
        body.put("absolutePath", file.getAbsolutePath());
        body.put("length", file.length());
        body.put("preview", preview(file));
        json(owner, api, "file", body);
    }

    @NonNull
    public static Map<String, Object> maskedAuthBody(@Nullable String username,
                                                     @Nullable String phone,
                                                     @Nullable String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("phone", phone);
        body.put("password", password == null ? null : "***");
        return body;
    }

    @NonNull
    private static String toPrettyJson(@Nullable Object body) {
        if (body == null) {
            return "null";
        }
        if (body instanceof String) {
            try {
                JsonElement element = new JsonParser().parse((String) body);
                return GSON.toJson(element);
            } catch (RuntimeException ignored) {
                return (String) body;
            }
        }
        return GSON.toJson(body);
    }

    @NonNull
    private static String preview(@NonNull File file) {
        if (!file.exists() || !file.isFile()) {
            return "";
        }
        int length = (int) Math.min(file.length(), 2048);
        byte[] buffer = new byte[length];
        try (FileInputStream input = new FileInputStream(file)) {
            int read = input.read(buffer);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "preview failed: " + e.getMessage();
        }
    }

    @NonNull
    private static String truncate(@Nullable String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= MAX_CHARS) {
            return value;
        }
        return value.substring(0, MAX_CHARS) + "\n... truncated, totalChars=" + value.length();
    }

    private static void log(@NonNull String message) {
        try {
            Log.d(TAG, message);
        } catch (RuntimeException ignored) {
            System.out.println(TAG + ": " + message);
        }
    }
}
