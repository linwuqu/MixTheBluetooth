package com.hc.mixthebluetooth.uni;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.uni.profile.eis.EisProfile;

public final class Profiles {
    private Profiles() {
    }

    @NonNull
    public static Controller.ProfileSpec eis() {
        return EisProfile.create();
    }
}
