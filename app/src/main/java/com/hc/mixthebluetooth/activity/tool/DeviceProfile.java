package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;

import java.util.HashMap;
import java.util.List;

public interface DeviceProfile<B> {
    List<BluetoothSampleParser> parsers();
    void registerCharts(@NonNull B binding, @NonNull HashMap<String, RealtimeLineChart> charts);
    List<SampleConsumer> consumers(@NonNull B binding, @NonNull SampleRecorder recorder);
    void registerControls(@NonNull B binding, @NonNull HashMap<Integer, Runnable> controls);
}
