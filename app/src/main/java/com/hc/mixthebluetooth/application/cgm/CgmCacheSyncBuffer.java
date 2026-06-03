package com.hc.mixthebluetooth.application.cgm;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.driver.capability.FileRecorder;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class CgmCacheSyncBuffer {
    public static final String READ_CACHE_COMMAND = "ALL\n\r";

    static final String START_MARKER = "Start Playback";
    static final String END_MARKER = "Playback all done";
    static final String DELETE_ACK = "Log Cleared";

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Pattern PAYLOAD_LINE = Pattern.compile("[A-Za-z0-9_:\\-.,;=\\s]+");

    public enum Phase {
        IDLE,
        READING_CACHE,
        READY_TO_UPLOAD,
        UPLOADING,
        WAITING_DELETE_CONFIRM,
        DONE,
        ERROR
    }

    public static final class SyncResult {
        public final int appendedLines;
        public final boolean sawStart;
        public final boolean sawEnd;

        SyncResult(int appendedLines, boolean sawStart, boolean sawEnd) {
            this.appendedLines = appendedLines;
            this.sawStart = sawStart;
            this.sawEnd = sawEnd;
        }
    }

    public static final class ValidationResult {
        public final boolean valid;
        public final boolean retryable;
        @NonNull
        public final String message;

        ValidationResult(boolean valid, boolean retryable, @NonNull String message) {
            this.valid = valid;
            this.retryable = retryable;
            this.message = message;
        }
    }

    public static final class DeleteResult {
        public final boolean confirmed;
        @NonNull
        public final String message;

        DeleteResult(boolean confirmed, @NonNull String message) {
            this.confirmed = confirmed;
            this.message = message;
        }
    }

    private final int maxAttempts;
    private final StringBuilder pending = new StringBuilder();
    private final ArrayList<String> lines = new ArrayList<>();
    private Phase phase = Phase.IDLE;
    private int attempt;

    public CgmCacheSyncBuffer() {
        this(DEFAULT_MAX_ATTEMPTS);
    }

    public CgmCacheSyncBuffer(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public synchronized void beginRead() {
        attempt = 1;
        clearCurrentAttempt();
        phase = Phase.READING_CACHE;
    }

    @NonNull
    public synchronized SyncResult acceptChunk(@NonNull String chunk) {
        pending.append(chunk.replace("\r", ""));
        int appended = 0;
        boolean sawStart = false;
        boolean sawEnd = false;

        while (true) {
            int newline = pending.indexOf("\n");
            if (newline < 0) {
                break;
            }
            LineResult result = appendLine(pending.substring(0, newline));
            pending.delete(0, newline + 1);
            appended += result.appended ? 1 : 0;
            sawStart |= result.sawStart;
            sawEnd |= result.sawEnd;
        }

        if (pending.indexOf(END_MARKER) >= 0) {
            LineResult result = appendLine(pending.toString());
            pending.setLength(0);
            appended += result.appended ? 1 : 0;
            sawStart |= result.sawStart;
            sawEnd |= result.sawEnd;
        }

        return new SyncResult(appended, sawStart, sawEnd);
    }

    @NonNull
    public synchronized ValidationResult validate() {
        int start = firstMarkerIndex(START_MARKER, 0);
        if (start < 0) {
            return retryable("missing start marker");
        }

        int end = firstMarkerIndex(END_MARKER, start + 1);
        if (end < 0) {
            return retryable("missing end marker");
        }

        if (end <= start + 1) {
            return retryable("missing payload lines");
        }

        for (int i = start + 1; i < end; i++) {
            String line = lines.get(i);
            if (!PAYLOAD_LINE.matcher(line).matches()) {
                return retryable("invalid payload line: " + line);
            }
        }

        phase = Phase.READY_TO_UPLOAD;
        return new ValidationResult(true, false, "replay validation passed");
    }

    public synchronized boolean canRetry() {
        return attempt < maxAttempts;
    }

    public synchronized void beginRetry() {
        if (canRetry()) {
            attempt++;
        }
        clearCurrentAttempt();
        phase = Phase.READING_CACHE;
    }

    @NonNull
    public synchronized File writeTo(@NonNull FileRecorder recorder) {
        if (phase != Phase.READY_TO_UPLOAD) {
            throw new IllegalStateException("replay is not ready to write: " + phase);
        }
        recorder.reset();
        recorder.start();
        for (String line : lines) {
            recorder.appendLine(line);
        }
        return recorder.finish();
    }

    public synchronized void markUploading() {
        phase = Phase.UPLOADING;
    }

    public synchronized void markError() {
        phase = Phase.ERROR;
    }

    public synchronized void markDeleteSent() {
        phase = Phase.WAITING_DELETE_CONFIRM;
    }

    @NonNull
    public synchronized DeleteResult acceptDeviceLine(@NonNull String text) {
        if (phase == Phase.WAITING_DELETE_CONFIRM && text.contains(DELETE_ACK)) {
            phase = Phase.DONE;
            return new DeleteResult(true, "cache delete confirmed");
        }
        return new DeleteResult(false, "delete not confirmed");
    }

    @NonNull
    public synchronized Phase phase() {
        return phase;
    }

    public synchronized void reset() {
        attempt = 0;
        clearCurrentAttempt();
        phase = Phase.IDLE;
    }

    @NonNull
    private ValidationResult retryable(@NonNull String message) {
        return new ValidationResult(false, canRetry(), message);
    }

    private int firstMarkerIndex(@NonNull String marker, int from) {
        for (int i = Math.max(0, from); i < lines.size(); i++) {
            if (lines.get(i).contains(marker)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    private LineResult appendLine(@NonNull String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty()) {
            return new LineResult(false, false, false);
        }
        lines.add(line);
        return new LineResult(true, line.contains(START_MARKER), line.contains(END_MARKER));
    }

    private void clearCurrentAttempt() {
        pending.setLength(0);
        lines.clear();
    }

    private static final class LineResult {
        final boolean appended;
        final boolean sawStart;
        final boolean sawEnd;

        LineResult(boolean appended, boolean sawStart, boolean sawEnd) {
            this.appended = appended;
            this.sawStart = sawStart;
            this.sawEnd = sawEnd;
        }
    }
}
