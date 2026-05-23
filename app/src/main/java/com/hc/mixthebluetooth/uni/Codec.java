package com.hc.mixthebluetooth.uni;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.Analysis;

public final class Codec {
    private Codec() {
    }

    public static final class Options {
        @Nullable
        public final String charset;
        public final boolean hex;
        public final boolean checkNewline;

        public Options(@Nullable String charset, boolean hex, boolean checkNewline) {
            this.charset = charset;
            this.hex = hex;
            this.checkNewline = checkNewline;
        }
    }

    @NonNull
    public static byte[] encodeText(@NonNull Context context, @NonNull String text) {
        String charset = FragmentParameter.getInstance().getCodeFormat(context);
        byte[] bytes = Analysis.getBytes(text, charset, false);
        return bytes != null ? bytes : new byte[0];
    }

    @Nullable
    public static String decode(@Nullable byte[] bytes, @NonNull Options options) {
        if (bytes == null || bytes.length == 0) return null;
        String charset = options.charset != null ? options.charset : "UTF-8";
        String text = Analysis.getByteToString(bytes.clone(), charset, options.hex, options.checkNewline);
        if (text == null) return null;
        text = text.replace("\u0000", "").trim();
        return text.isEmpty() ? null : text;
    }
}
