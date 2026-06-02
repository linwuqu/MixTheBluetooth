package com.hc.mixthebluetooth.api.device;

import androidx.annotation.NonNull;

public interface DeviceGateway {
    boolean isConnected();

    void sendText(@NonNull String text);

    void sendBytes(@NonNull byte[] bytes);

    void disconnect();
}
