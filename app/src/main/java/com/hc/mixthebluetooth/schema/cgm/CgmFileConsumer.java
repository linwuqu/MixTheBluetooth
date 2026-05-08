package com.hc.mixthebluetooth.schema.cgm;

import android.os.Environment;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

public final class CgmFileConsumer implements SampleConsumer {

    private final FragmentRuntime runtime;

    public CgmFileConsumer(@NonNull FragmentRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!(sample instanceof CgmSample)) return;
        CgmSample cgm = (CgmSample) sample;
        String dir = String.valueOf(runtime.context().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));

        if (CgmSample.EVENT_CACHE_START.equals(cgm.event)
                || CgmSample.EVENT_CACHE_LINE.equals(cgm.event)
                || CgmSample.EVENT_CACHE_DONE.equals(cgm.event)) {
            Analysis.IO_input_data(cgm.raw, dir, "CGM_Cache_data.txt");
        }
    }
}
