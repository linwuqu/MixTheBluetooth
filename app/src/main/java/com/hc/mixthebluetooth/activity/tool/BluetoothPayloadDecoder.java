package com.hc.mixthebluetooth.activity.tool;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;

/**
 * Converts bluetooth byte payloads into cleaned text.
 * <p>
 * This keeps the legacy Analysis.getByteToString behavior in one place:
 * code format, CRLF detection, null removal, and trimming.
 */
public final class BluetoothPayloadDecoder {

    private BluetoothPayloadDecoder() {
    }

    @NonNull
    public static Result decodeResult(@Nullable Context context, @Nullable byte[] raw) {
        if (context == null) return new Result("", false);
        if (raw == null || raw.length == 0) return new Result("", false);

        String code = FragmentParameter.getInstance().getCodeFormat(context);
        return decodeResult(raw, code);
    }

    @NonNull
    public static Result decodeResult(@Nullable byte[] raw, @Nullable String code) {
        if (raw == null || raw.length == 0) return new Result("", false);

        boolean hasCrLf = endsWithCrLf(raw);
        boolean hasLineBreak = hasCrLf || endsWithLf(raw) || endsWithCr(raw);

        byte[] copy = raw.clone();
        String safeCode = code != null ? code : "";
        String text = Analysis.getByteToString(copy, safeCode, false, hasCrLf);
        if (text == null) return new Result("", hasLineBreak);

        return new Result(clean(text), hasLineBreak);
    }

    public static boolean endsWithLf(@Nullable byte[] raw) {
        return raw != null
                && raw.length >= 1
                && raw[raw.length - 1] == 10;
    }

    public static boolean endsWithCr(@Nullable byte[] raw) {
        return raw != null
                && raw.length >= 1
                && raw[raw.length - 1] == 13;
    }


    public static boolean endsWithCrLf(@Nullable byte[] raw) {
        return raw != null && raw.length >= 2 && raw[raw.length - 2] == 13 && raw[raw.length - 1] == 10;
    }

    @NonNull
    public static String clean(@Nullable String text) {
        if (text == null) return "";
        return text.replace("\u0000", "").trim();
    }

    public static final class Result {
        @NonNull
        public final String text;

        public final boolean endsWithLineBreak;

        private Result(@NonNull String text, boolean endsWithLineBreak) {
            this.text = text;
            this.endsWithLineBreak = endsWithLineBreak;
        }

        public boolean isEmpty() {
            return text.isEmpty();
        }
    }
}
