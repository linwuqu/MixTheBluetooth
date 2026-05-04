package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects streaming text chunks and emits complete lines.
 * <p>
 * Bluetooth data can arrive in fragments. This class keeps unfinished text
 * until a newline appears.
 */
public class LineBuffer {
    private final StringBuilder buffer = new StringBuilder();

    @NonNull
    public List<String> append(@NonNull String chunk) {
        List<String> lines = new ArrayList<>();

        if (chunk.isEmpty()) {
            return lines;
        }

        buffer.append(chunk);

        int newlineIndex;
        while ((newlineIndex = indexOfNewline()) >= 0) {
            String line = buffer.substring(0, newlineIndex).trim();

            int deleteEnd = newlineIndex + 1;
            if (newlineIndex + 1 < buffer.length() && buffer.charAt(newlineIndex) == '\r' && buffer.charAt(newlineIndex + 1) == '\n') {
                deleteEnd = newlineIndex + 2;
            }

            buffer.delete(0, deleteEnd);

            if (!line.isEmpty()) {
                lines.add(line);
            }
        }

        return lines;
    }

    public void clear() {
        buffer.setLength(0);
    }

    private int indexOfNewline() {
        int lf = buffer.indexOf("\n");
        int cr = buffer.indexOf("\r");

        if (lf < 0) return cr;
        if (cr < 0) return lf;

        return Math.min(lf, cr);
    }
}
