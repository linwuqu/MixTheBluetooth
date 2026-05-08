package com.hc.mixthebluetooth.activity.tool.message;

import androidx.annotation.NonNull;

public final class MessageOptions {
    public final boolean showOutgoing;
    public final boolean showTime;
    public final boolean sendHex;
    public final boolean readHex;
    public final boolean autoClear;
    public final boolean sendNewline;
    @NonNull
    public final String codeFormat;

    public MessageOptions(
            boolean showOutgoing,
            boolean showTime,
            boolean sendHex,
            boolean readHex,
            boolean autoClear,
            boolean sendNewline,
            @NonNull String codeFormat
    ) {
        this.showOutgoing = showOutgoing;
        this.showTime = showTime;
        this.sendHex = sendHex;
        this.readHex = readHex;
        this.autoClear = autoClear;
        this.sendNewline = sendNewline;
        this.codeFormat = codeFormat;
    }

    @NonNull
    public static MessageOptions defaults(@NonNull String codeFormat) {
        return new MessageOptions(false, false, false, false, false, false, codeFormat);
    }

    @NonNull
    public BluetoothPayloadDecoder.Options decoderOptions(boolean removeTrailingCrLf) {
        return new BluetoothPayloadDecoder.Options.Builder()
                .hex(readHex)
                .removeTrailingCrLf(removeTrailingCrLf)
                .cleanNull(true)
                .trim(true)
                .build();
    }
}
