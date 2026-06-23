package com.hc.mixthebluetooth.application.auth;

import android.app.Activity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class ActiveActivities {
    private static final Set<Activity> SET = Collections.newSetFromMap(new WeakHashMap<>());

    static void add(Activity a) {
        if (a != null) SET.add(a);
    }

    static void remove(Activity a) {
        SET.remove(a);
    }

    static Set<Activity> getAll() {
        return SET;
    }

    private ActiveActivities() {
    }
}
