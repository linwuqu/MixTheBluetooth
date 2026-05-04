package com.hc.mixthebluetooth.activity.single;

import androidx.annotation.NonNull;

import com.hc.bluetoothlibrary.DeviceModule;

/**
 * Typed payloads sent through {@link StaticConstants#CH_BT_DATA}.
 * <p>
 * Old code used one channel with several unrelated payload shapes:
 * DeviceModule, Object[]{DeviceModule, byte[]}, Integer, and log objects.
 * This class keeps the same event-bus transport but gives each payload a
 * clear Java type before we start migrating Fragment code.
 */
public abstract class BTPackage {
    private BTPackage() {
    }

    public static final class BTData extends BTPackage {
        @NonNull
        public final DeviceModule module;

        @NonNull
        public final byte[] bytes;

        public BTData(@NonNull DeviceModule module, @NonNull byte[] bytes) {
            this.module = module;
            this.bytes = bytes;
        }
    }

    public static final class Connected extends BTPackage {
        @NonNull
        public final DeviceModule module;

        public Connected(@NonNull DeviceModule module) {
            this.module = module;
        }
    }

    public static final class Disconnected extends BTPackage {
        public static final Disconnected INSTANCE = new Disconnected();

        private Disconnected() {
        }
    }

    public static final class Velocity extends BTPackage {
        public final int bytesPerSecond;

        public Velocity(int bytesPerSecond) {
            this.bytesPerSecond = bytesPerSecond;
        }
    }

    public static final class Log extends BTPackage {
        @NonNull
        public final String message;

        public Log(@NonNull String message) {
            this.message = message;
        }
    }
}