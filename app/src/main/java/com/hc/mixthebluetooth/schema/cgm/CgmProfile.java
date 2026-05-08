package com.hc.mixthebluetooth.schema.cgm;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.activity.tool.chart.SampleChartBinder;
import com.hc.mixthebluetooth.activity.tool.message.ControlRegistry;
import com.hc.mixthebluetooth.activity.tool.profile.DeviceProfile;
import com.hc.mixthebluetooth.activity.tool.profile.ProfileContext;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumerRegistry;
import com.hc.mixthebluetooth.databinding.FragmentMessageBinding;

public final class CgmProfile implements DeviceProfile<FragmentMessageBinding> {

    public static final String CHART_PRIMARY = "cgm_primary";
    public static final String CHART_CURRENT = "cgm_current";
    private static final int MAX_POINTS = 500;

    @Override
    public void registerSamples(@NonNull BluetoothSampleRegistry registry) {
        registry.register(new CgmParser());
    }

    @Override
    public void registerCharts(@NonNull ChartRegistry charts, @NonNull FragmentMessageBinding binding) {
        charts
                .register(
                        CHART_PRIMARY,
                        binding.chartPrimary,
                        new RealtimeLineChart.Config.Builder()
                                .label("CGM")
                                .color(Color.RED)
                                .maxPoints(MAX_POINTS)
                                .visibleWindowSeconds(60f)
                                .build()
                )
                .register(
                        CHART_CURRENT,
                        binding.chartCurrent,
                        new RealtimeLineChart.Config.Builder()
                                .label("Current")
                                .color(Color.BLUE)
                                .maxPoints(MAX_POINTS)
                                .visibleWindowSeconds(60f)
                                .build()
                );
    }

    @Override
    public void registerConsumers(
            @NonNull SampleConsumerRegistry consumers,
            @NonNull ProfileContext<FragmentMessageBinding> context
    ) {
        consumers
                .register(new CgmStatusConsumer(context.binding.tvStatus))
                .register(new CgmCurrentValueConsumer(context.binding.tvCurrentValue))
                .register(new CgmFileConsumer(context.runtime))
                .register(new CgmRecorderConsumer(context.runtime, context.recorder))
                .register(new SampleChartBinder(context.charts, context.recorder::isRecording)
                        .bind(CgmSample.METRIC_PRIMARY, CHART_PRIMARY)
                        .bind(CgmSample.METRIC_CURRENT, CHART_CURRENT));
    }

    @Override
    public void registerControls(
            @NonNull ControlRegistry controls,
            @NonNull ProfileContext<FragmentMessageBinding> context
    ) {
        controls
                .bind(context.binding.btnStartMeasure, () -> context.sender.send(CgmCommandSet.startNow()))
                .bind(context.binding.btnReadCache, () -> context.sender.send(CgmCommandSet.readCache()))
                .bind(context.binding.btnDeleteCache, () -> context.sender.send(CgmCommandSet.deleteCache()))
                .bind(context.binding.btnParams, () -> CgmParameterDialog.show(
                        context.runtime,
                        context.rolePolicy,
                        context.sender
                ));
    }
}
