package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;

public final class CgmRecorderConsumer implements SampleConsumer {

    private final FragmentRuntime runtime;
    private final SampleRecorder recorder;

    public CgmRecorderConsumer(@NonNull FragmentRuntime runtime, @NonNull SampleRecorder recorder) {
        this.runtime = runtime;
        this.recorder = recorder;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!recorder.isRecording()) return;
        if (!(sample instanceof CgmSample)) return;
        recorder.appendLine(CgmJsonLineBuilder.build(runtime.module(), (CgmSample) sample));
    }
}
