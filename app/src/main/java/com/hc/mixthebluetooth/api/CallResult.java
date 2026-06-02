package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CallResult<T> {
    public static final int UNKNOWN = -1;
    public static final int NETWORK = -100;
    public static final int EMPTY_RESPONSE = -101;
    public static final int EMPTY_DATA = -102;
    public static final int LOCAL_FILE_NOT_FOUND = -200;
    public static final int DEVICE_REPLAY_INCOMPLETE = -300;

    public enum Status {
        OK,
        PENDING,
        ERROR
    }

    /**
     * Kept temporarily so older call sites that read the field directly keep compiling.
     */
    @Deprecated
    public enum State {
        OK,
        PENDING,
        ERROR
    }

    @NonNull
    public final Status status;
    @Deprecated
    @NonNull
    public final State state;
    public final int code;
    @NonNull
    public final String message;
    @Nullable
    public final T data;
    @Nullable
    public final Throwable cause;

    private CallResult(@NonNull Status status, int code, @NonNull String message,
                       @Nullable T data, @Nullable Throwable cause) {
        this.status = status;
        this.state = State.valueOf(status.name());
        this.code = code;
        this.message = message;
        this.data = data;
        this.cause = cause;
    }

    @NonNull
    public static <T> CallResult<T> ok(@Nullable T data) {
        return new CallResult<>(Status.OK, 0, "", data, null);
    }

    @NonNull
    public static <T> CallResult<T> pending(@NonNull String message) {
        return new CallResult<>(Status.PENDING, 0, message, null, null);
    }

    @NonNull
    public static <T> CallResult<T> error(int code, @NonNull String message,
                                          @Nullable Throwable cause) {
        return new CallResult<>(Status.ERROR, code, message, null, cause);
    }

    public boolean isOk() {
        return status == Status.OK;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }
}
