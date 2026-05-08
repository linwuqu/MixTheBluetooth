package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class SampleConsumerRegistry implements SampleConsumer {

    private final List<SampleConsumer> consumers = new ArrayList<>();

    @NonNull
    public SampleConsumerRegistry register(@NonNull SampleConsumer consumer) {
        consumers.add(consumer);
        return this;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        for (SampleConsumer consumer : consumers) {
            consumer.consume(sample);
        }
    }
}
