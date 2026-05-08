package com.hc.mixthebluetooth.activity.tool.profile;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.message.ControlRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumerRegistry;

public interface DeviceProfile<B> {
    void registerSamples(@NonNull BluetoothSampleRegistry registry);

    void registerCharts(@NonNull ChartRegistry charts, @NonNull B binding);

    void registerConsumers(@NonNull SampleConsumerRegistry consumers, @NonNull ProfileContext<B> context);

    void registerControls(@NonNull ControlRegistry controls, @NonNull ProfileContext<B> context);
}
