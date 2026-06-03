package com.hc.mixthebluetooth.ui.cgm;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.driver.implementation.codec.Analysis;

public final class Codec {
    private Codec() {
    }

    public static final class Options {
        @Nullable
        public final String charset;
        public final boolean hex;
        public final boolean checkNewline;
        public final boolean trim;

        public Options(@Nullable String charset, boolean hex, boolean checkNewline) {
            this(charset, hex, checkNewline, true);
        }

        public Options(@Nullable String charset, boolean hex, boolean checkNewline, boolean trim) {
            this.charset = charset;
            this.hex = hex;
            this.checkNewline = checkNewline;
            this.trim = trim;
        }
    }

    @NonNull
    public static byte[] encodeText(@NonNull Context context, @NonNull String text) {
        String charset = AppApi.settingsStore().textEncoding();
        byte[] bytes = Analysis.getBytes(text, charset, false);
        return bytes != null ? bytes : new byte[0];
    }

    @Nullable
    public static String decode(@Nullable byte[] bytes, @NonNull Options options) {
        if (bytes == null || bytes.length == 0) return null;
        String charset = options.charset != null ? options.charset : "UTF-8";
        String text = Analysis.getByteToString(bytes.clone(), charset, options.hex, options.checkNewline);
        if (text == null) return null;
        text = text.replace("\u0000", "");
        if (options.trim) {
            text = text.trim();
        }
        return text.isEmpty() ? null : text;
    }
}
