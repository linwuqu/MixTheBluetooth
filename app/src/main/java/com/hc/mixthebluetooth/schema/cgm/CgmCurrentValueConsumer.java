package com.hc.mixthebluetooth.schema.cgm;

import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

public final class CgmCurrentValueConsumer implements SampleConsumer {

    private final TextView currentValueView;

    public CgmCurrentValueConsumer(@NonNull TextView currentValueView) {
        this.currentValueView = currentValueView;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!(sample instanceof CgmSample)) return;
        Float value = sample.metrics().get(CgmSample.METRIC_CURRENT);
        if (value != null) {
            currentValueView.setText(String.valueOf(value));
        }
    }
}
