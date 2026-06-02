package com.hc.mixthebluetooth.driver.implementation.time;

import com.hc.mixthebluetooth.driver.capability.Clock;

public final class SystemClock implements Clock {
    @Override
    public long nowMillis() {
        return java.lang.System.currentTimeMillis();
    }
}
