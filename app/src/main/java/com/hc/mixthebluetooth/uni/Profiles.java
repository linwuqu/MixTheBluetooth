package com.hc.mixthebluetooth.uni;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.uni.profile.cgm.CgmProfile;
import com.hc.mixthebluetooth.uni.profile.eis.EisProfile;

public final class Profiles {
    private Profiles() {
    }

    @NonNull
    public static Controller.ProfileSpec eis() {
        return EisProfile.create();
    }

    @NonNull
    public static Controller.ProfileSpec cgm() {
        return CgmProfile.create();
    }
}
