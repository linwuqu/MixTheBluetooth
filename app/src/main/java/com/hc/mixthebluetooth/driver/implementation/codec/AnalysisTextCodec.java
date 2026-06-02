package com.hc.mixthebluetooth.driver.implementation.codec;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.api.codec.TextCodec;

public final class AnalysisTextCodec implements TextCodec {
    @NonNull
    @Override
    public byte[] encode(@NonNull String text, @NonNull String charsetName) {
        byte[] bytes = Analysis.getBytes(text, charsetName, false);
        return bytes != null ? bytes : new byte[0];
    }

    @NonNull
    @Override
    public String decode(@NonNull byte[] bytes, @NonNull String charsetName) {
        String text = Analysis.getByteToString(bytes.clone(), charsetName, false, false);
        if (text == null) return "";
        return text.replace("\u0000", "").trim();
    }
}
