package com.hc.bluetoothlibrary.bleBluetooth;

import java.util.Locale;

final class BleNotificationTrace {

    static final class Sample {
        final long sequence;
        final long gapMillis;
        final long elapsedMillis;
        final long totalBytes;

        Sample(long sequence, long gapMillis, long elapsedMillis, long totalBytes) {
            this.sequence = sequence;
            this.gapMillis = gapMillis;
            this.elapsedMillis = elapsedMillis;
            this.totalBytes = totalBytes;
        }
    }

    private long sequence;
    private long totalBytes;
    private long firstTimestampMillis;
    private long lastTimestampMillis;

    synchronized Sample record(long timestampMillis, int byteCount) {
        if (sequence == 0) firstTimestampMillis = timestampMillis;
        long gapMillis = sequence == 0 ? 0 : timestampMillis - lastTimestampMillis;
        sequence++;
        totalBytes += byteCount;
        lastTimestampMillis = timestampMillis;
        return new Sample(
                sequence,
                gapMillis,
                timestampMillis - firstTimestampMillis,
                totalBytes
        );
    }

    synchronized void reset() {
        sequence = 0;
        totalBytes = 0;
        firstTimestampMillis = 0;
        lastTimestampMillis = 0;
    }

    static String hexPreview(byte[] data, int maxBytes) {
        if (data == null || data.length == 0 || maxBytes <= 0) return "";
        int length = Math.min(data.length, maxBytes);
        StringBuilder result = new StringBuilder(length * 3 + 4);
        for (int i = 0; i < length; i++) {
            if (i > 0) result.append(' ');
            result.append(String.format(Locale.US, "%02X", data[i] & 0xFF));
        }
        if (data.length > maxBytes) result.append(" ...");
        return result.toString();
    }
}
