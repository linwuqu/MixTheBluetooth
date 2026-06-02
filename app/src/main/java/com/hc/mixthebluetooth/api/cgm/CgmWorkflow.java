package com.hc.mixthebluetooth.api.cgm;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;

public interface CgmWorkflow {
    void onDeviceLine(@NonNull String line, @NonNull ApiCallback<CallResult<CgmResult>> callback);

    void reset();
}
