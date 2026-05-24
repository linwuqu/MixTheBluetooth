package com.hc.mixthebluetooth;

import com.hc.basiclibrary.viewBasic.HomeApplication;
import com.hc.mixthebluetooth.impl.AppApiBootstrap;

public class MixBluetoothApplication extends HomeApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        AppApiBootstrap.init(this);
    }
}
