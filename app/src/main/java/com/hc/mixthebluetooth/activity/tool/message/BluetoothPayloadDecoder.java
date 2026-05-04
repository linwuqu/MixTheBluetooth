package com.hc.mixthebluetooth.activity.tool.message;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.Analysis;

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
        // 获取编码 UTF-8? GBK? ASCII? Hex?
        String code = FragmentParameter.getInstance().getCodeFormat(context);
        return decodeResult(raw, code);
    }

    @NonNull
    public static Result decodeResult(@Nullable byte[] raw, @Nullable String code) {
        return decodeResult(raw, code, new Options.Builder().build());
    }

    @NonNull
    public static Result decodeResult(@Nullable byte[] raw, @Nullable String code, @NonNull Options options) {
        if (raw == null || raw.length == 0) return new Result("", false);

        boolean hasCrLf = endsWithCrLf(raw);
        boolean hasLineBreak = hasCrLf || endsWithLf(raw) || endsWithCr(raw);

        byte[] copy = raw.clone();
        String safeCode = code != null ? code : "";
        String text = Analysis.getByteToString(
                copy,
                safeCode,
                options.hex,
                options.removeTrailingCrLf && hasCrLf
        );
        if (text == null) return new Result("", hasLineBreak);

        if (options.cleanNull) {
            text = text.replace("\u0000", "");
        }

        if (options.trim) {
            text = text.trim();
        }

        return new Result(text, hasLineBreak);
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

    public static final class Options {
        public final boolean hex;
        public final boolean removeTrailingCrLf;
        public final boolean cleanNull;
        public final boolean trim;

        private Options(@NonNull Builder builder) {
            this.hex = builder.hex;
            this.removeTrailingCrLf = builder.removeTrailingCrLf;
            this.cleanNull = builder.cleanNull;
            this.trim = builder.trim;
        }

        public static final class Builder {
            private boolean hex = false;
            private boolean removeTrailingCrLf = true;
            private boolean cleanNull = true;
            private boolean trim = true;

            public Builder hex(boolean hex) {
                this.hex = hex;
                return this;
            }

            public Builder removeTrailingCrLf(boolean removeTrailingCrLf) {
                this.removeTrailingCrLf = removeTrailingCrLf;
                return this;
            }

            public Builder cleanNull(boolean cleanNull) {
                this.cleanNull = cleanNull;
                return this;
            }

            public Builder trim(boolean trim) {
                this.trim = trim;
                return this;
            }

            public Options build() {
                return new Options(this);
            }
        }
    }
}
