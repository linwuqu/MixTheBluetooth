package com.hc.mixthebluetooth.schema.cgm;

import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

public final class CgmStatusConsumer implements SampleConsumer {

    private final TextView statusView;

    public CgmStatusConsumer(@NonNull TextView statusView) {
        this.statusView = statusView;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!(sample instanceof CgmSample)) return;
        CgmSample cgm = (CgmSample) sample;
        if (cgm.status != null && !cgm.status.isEmpty()) {
            statusView.setText(cgm.status);
        } else {
            statusView.setText(cgm.event);
        }
    }
}
