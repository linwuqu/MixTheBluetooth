package com.hc.mixthebluetooth.schema.eis;

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

/**
 * EisProfile — EIS（电化学阻抗谱）设备的档案
 * <p>
 * 注册内容：
 * 解析器  — EisParser（匹配 "670258.375Ω,1.492uS" 等原始行）
 * 图表    — 一个折线图，显示阻抗值
 * 消费者  — SampleChartBinder（指标绑定图表）
 * EisRecorderConsumer（录波写 JSONL）
 */
public final class EisProfile implements DeviceProfile<FragmentMessageBinding> {

    public static final String CHART_EIS = "eis";

    private static final int MAX_POINTS = 500;

    @Override
    public void registerSamples(@NonNull BluetoothSampleRegistry registry) {
        registry.register(new EisParser());
    }

    @Override
    public void registerCharts(@NonNull ChartRegistry charts, @NonNull FragmentMessageBinding binding) {
        charts.register(
                CHART_EIS,
                binding.chartPrimary,
                new RealtimeLineChart.Config.Builder()
                        .label("EIS")
                        .color(Color.RED)
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
                // 图表绑定：ohm → eis 图表
                .register(new SampleChartBinder(context.charts, context.recorder::isRecording)
                        .bind(EisSample.METRIC_OHM, CHART_EIS))
                // 录波
                .register(new EisRecorderConsumer(context.runtime, context.recorder));
    }

    @Override
    public void registerControls(
            @NonNull ControlRegistry controls,
            @NonNull ProfileContext<FragmentMessageBinding> context
    ) {
        // EIS 设备暂无专用按钮，按钮面板复用 CGM 的，但这里不注册任何动作
    }
}
