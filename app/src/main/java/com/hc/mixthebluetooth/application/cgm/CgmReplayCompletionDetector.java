package com.hc.mixthebluetooth.application.cgm;

import androidx.annotation.Nullable;

final class CgmReplayCompletionDetector {
    enum Event {
        IDLE,
        STARTED,
        RECORDING_LINE,
        RECORDING_IDLE,
        COMPLETED,
        INCOMPLETE
    }

    private boolean recording;

    Event consume(@Nullable String line) {
        if (line == null || line.isEmpty()) {
            return recording ? Event.RECORDING_IDLE : Event.IDLE;
        }
        if (line.contains("Start Playback")) {
            recording = true;
            return Event.STARTED;
        }
        if (line.contains("Playback all done")) {
            if (!recording) {
                return Event.INCOMPLETE;
            }
            recording = false;
            return Event.COMPLETED;
        }
        return recording ? Event.RECORDING_LINE : Event.IDLE;
    }

    void reset() {
        recording = false;
    }
}
