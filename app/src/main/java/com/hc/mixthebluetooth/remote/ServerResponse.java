package com.hc.mixthebluetooth.remote;

import androidx.annotation.Nullable;

public final class ServerResponse<T> {
    public int code;
    public boolean success;
    @Nullable
    public String msg;
    @Nullable
    public T data;

    public static <T> ServerResponse<T> success(T data) {
        ServerResponse<T> response = new ServerResponse<>();
        response.code = 0;
        response.success = true;
        response.msg = "";
        response.data = data;
        return response;
    }
}
