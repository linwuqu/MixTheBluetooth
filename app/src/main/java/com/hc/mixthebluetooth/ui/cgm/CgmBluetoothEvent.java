package com.hc.mixthebluetooth.ui.cgm;

import androidx.annotation.NonNull;

import com.hc.bluetoothlibrary.DeviceModule;

/**
 * Typed payloads sent through Bluetooth event channels.
 * <p>
 * Old code used one channel with several unrelated payload shapes:
 * DeviceModule, Object[]{DeviceModule, byte[]}, Integer, and log objects.
 * This class keeps the same event-bus transport but gives each payload a
 * clear Java type before we start migrating Fragment code.
 */
public abstract class CgmBluetoothEvent {
    private CgmBluetoothEvent() {
    }

    public static final class BTData extends CgmBluetoothEvent {
        @NonNull
        public final DeviceModule module;

        @NonNull
        public final byte[] bytes;

        public BTData(@NonNull DeviceModule module, @NonNull byte[] bytes) {
            this.module = module;
            this.bytes = bytes;
        }
    }

    public static final class Connected extends CgmBluetoothEvent {
        @NonNull
        public final DeviceModule module;

        public Connected(@NonNull DeviceModule module) {
            this.module = module;
        }
    }

    public static final class Disconnected extends CgmBluetoothEvent {
        public static final Disconnected INSTANCE = new Disconnected();

        private Disconnected() {
        }
    }

    public static final class Velocity extends CgmBluetoothEvent {
        public final int bytesPerSecond;

        public Velocity(int bytesPerSecond) {
            this.bytesPerSecond = bytesPerSecond;
        }
    }

    public static final class Log extends CgmBluetoothEvent {
        @NonNull
        public final String message;

        public Log(@NonNull String message) {
            this.message = message;
        }
    }

    public static final class BTPost extends CgmBluetoothEvent {
        @NonNull
        public final DeviceModule module;

        @NonNull
        public final byte[] bytes;

        public BTPost(@NonNull DeviceModule module, @NonNull byte[] bytes) {
            this.module = module;
            this.bytes = bytes;
        }
    }

    public static final class ConnectState extends CgmBluetoothEvent {
        @NonNull
        public final String state;

        public ConnectState(@NonNull String state) {
            this.state = state;
        }
    }

    public static final class SentBytes extends CgmBluetoothEvent {
        public final int count;

        public SentBytes(int count) {
            this.count = count;
        }
    }

    public static final class StopLoopSend extends CgmBluetoothEvent {
        public static final StopLoopSend INSTANCE = new StopLoopSend();

        private StopLoopSend() {
        }
    }
}
