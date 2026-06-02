package com.hc.mixthebluetooth.api.codec;

import androidx.annotation.NonNull;

public interface TextCodec {
    @NonNull
    byte[] encode(@NonNull String text, @NonNull String charsetName);

    @NonNull
    String decode(@NonNull byte[] bytes, @NonNull String charsetName);
}
