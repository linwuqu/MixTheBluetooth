package com.hc.mixthebluetooth.activity.tool.profile;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.viewBasic.HomeApplication;

public final class UserRolePolicy {

    private final String role;

    public UserRolePolicy(@NonNull HomeApplication application) {
        this.role = application.getLimits();
    }

    public boolean canEditParameters() {
        return "admin".equals(role);
    }
}
