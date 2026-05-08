package com.hc.mixthebluetooth.schema.eis;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;

/**
 * 把 EisSample 写成 JSONL 行。
 * 只在录波时生效。
 */
public final class EisRecorderConsumer implements SampleConsumer {

    private final FragmentRuntime runtime;
    private final SampleRecorder recorder;

    public EisRecorderConsumer(@NonNull FragmentRuntime runtime, @NonNull SampleRecorder recorder) {
        this.runtime = runtime;
        this.recorder = recorder;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!recorder.isRecording()) return;
        if (!(sample instanceof EisSample)) return;
        recorder.appendLine(EisJsonLineBuilder.build(runtime.module(), (EisSample) sample));
    }
}
