package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;

public final class CgmParameters {
    @NonNull
    public final String controlRatio;
    @NonNull
    public final String extractionTime;
    @NonNull
    public final String highLevelTime;
    @NonNull
    public final String voltage;
    @NonNull
    public final String detectionTime;

    public CgmParameters(
            @NonNull String controlRatio,
            @NonNull String extractionTime,
            @NonNull String highLevelTime,
            @NonNull String voltage,
            @NonNull String detectionTime
    ) {
        this.controlRatio = controlRatio;
        this.extractionTime = extractionTime;
        this.highLevelTime = highLevelTime;
        this.voltage = voltage;
        this.detectionTime = detectionTime;
    }
}
