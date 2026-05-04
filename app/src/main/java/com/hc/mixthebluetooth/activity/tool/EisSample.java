package com.hc.mixthebluetooth.activity.tool;

/**
 * Parsed EIS sample.
 */
public final class EisSample {

    public final float ohm;
    public final float us;
    public final String raw;

    public EisSample(float ohm, float us, String raw) {
        this.ohm = ohm;
        this.us = us;
        this.raw = raw;
    }
}
