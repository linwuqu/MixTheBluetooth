package com.hc.mixthebluetooth;

import com.hc.basiclibrary.viewBasic.HomeApplication;
import com.hc.mixthebluetooth.application.auth.SessionLifecycleWatcher;
import com.hc.mixthebluetooth.runtime.AppApiBootstrap;

public class MixBluetoothApplication extends HomeApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        AppApiBootstrap.init(this);
        SessionLifecycleWatcher.install(this);
    }
}
