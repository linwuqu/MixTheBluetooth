package com.hc.mixthebluetooth.ui.cgm;

import androidx.annotation.NonNull;

public final class Profiles {
    private Profiles() {
    }

    @NonNull
    public static CgmController.ProfileSpec cgm() {
        return CgmProfile.create();
    }
}
