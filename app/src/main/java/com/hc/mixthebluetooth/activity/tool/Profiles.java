package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.uni.Controller;

/**
 * @deprecated Use {@link com.hc.mixthebluetooth.uni.Profiles}.
 */
@Deprecated
public final class Profiles {
    private Profiles() {
    }

    @NonNull
    public static Controller.ProfileSpec eis() {
        return com.hc.mixthebluetooth.uni.Profiles.eis();
    }
}
