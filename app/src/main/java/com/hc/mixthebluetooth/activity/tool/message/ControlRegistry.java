package com.hc.mixthebluetooth.activity.tool.message;

import android.view.View;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ControlRegistry {

    private final Map<Integer, ControlAction> actions = new LinkedHashMap<>();

    @NonNull
    public ControlRegistry bind(@NonNull View view, @NonNull ControlAction action) {
        actions.put(view.getId(), action);
        return this;
    }

    public boolean dispatch(@NonNull View view) {
        ControlAction action = actions.get(view.getId());
        if (action == null) return false;
        action.run();
        return true;
    }
}
