package com.hc.mixthebluetooth.activity.tool.chart;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SampleChartBinder — 指标到图表的绑定器（也是一个消费者）
 * <p>
 * 作用：
 * 当流水线广播一个 BluetoothSample 时，SampleChartBinder 负责：
 * ① 从 sample.metrics() 里取出数值
 * ② 根据绑定关系，把数值推给对应的图表
 * <p>
 * 例如 CgmProfile 里注册：
 * new SampleChartBinder(charts, recorder::isRecording)
 * .bind("primary", "cgm_primary")   // 主血糖值 → 红色图表
 * .bind("current", "cgm_current")   // 实时血糖值 → 蓝色图表
 * <p>
 * 收到 CgmSample(current, metrics={"primary":99.3, "current":99.3}) 时：
 * → 取出 metrics.get("primary") = 99.3 → charts.append("cgm_primary", 99.3)
 * → 取出 metrics.get("current") = 99.3 → charts.append("cgm_current", 99.3)
 * → 两个图表同时收到数据，更新折线
 * <p>
 * 门控机制（Gate）：
 * 构造函数接受一个 Gate 参数：
 * new SampleChartBinder(charts, gate)
 * gate.enabled() == true  → 正常推数据到图表
 * gate.enabled() == false → 静默忽略，什么都不做
 * <p>
 * CgmProfile 里传入的是 recorder::isRecording：
 * → 开始录波后，recorder.isRecording() == true → gate 打开 → 图表更新
 * → 没有录波时，gate 关闭 → 不推图表（节省资源）
 * <p>
 * 为什么叫 Binder？
 * 因为它是"绑定"关系：metric key（比如 "primary"）和 chart key（比如 "cgm_primary"）
 * 的对应关系是在这里配置的。绑定了之后，数据流就自动从 sample.metrics() 流向对应图表。
 * <p>
 * 为什么它也实现了 SampleConsumer？
 * 因为它需要被 SampleConsumerRegistry 统一管理（register → consume），
 * 这样流水线广播时，它和其他消费者（更新 UI / 写文件）一样被统一调用。
 */
public final class SampleChartBinder implements SampleConsumer {

    /**
     * Gate — 门控接口。
     * 当 gate.enabled() == true 时才推送数据；否则静默忽略。
     * <p>
     * 用途：实现"录波时才更新图表"的需求。
     * 如果不用 Gate，那么即使没有在录波，每收到一个数据都会重绘图表，浪费资源。
     */
    public interface Gate {
        boolean enabled();
    }

    /**
     * 图表仓库：推送数据时从这里找到对应的图表
     */
    private final ChartRegistry chartRegistry;

    /**
     * 门控：只有在 gate.enabled() == true 时才推送
     */
    private final Gate gate;

    /**
     * metric key → chart key 的绑定关系。
     * 例如："primary" → "cgm_primary" 表示主血糖值推给 cgm_primary 图表。
     */
    private final Map<String, String> metricToChart = new LinkedHashMap<>();

    /**
     * 构造：使用默认 Gate（始终打开），适合不需要门控的场景。
     */
    public SampleChartBinder(@NonNull ChartRegistry chartRegistry) {
        this(chartRegistry, () -> true);
    }

    /**
     * 构造：指定门控。
     *
     * @param chartRegistry 图表仓库
     * @param gate          门控（传入 recorder::isRecording 实现录波门控）
     */
    public SampleChartBinder(@NonNull ChartRegistry chartRegistry, @NonNull Gate gate) {
        this.chartRegistry = chartRegistry;
        this.gate = gate;
    }

    /**
     * 绑定一个 metric key → chart key 的对应关系。
     *
     * @param metricKey sample.metrics() 里的 key（如 "primary" 或 "current"）
     * @param chartKey  ChartRegistry 里注册的 chart key（如 "cgm_primary"）
     * @return this，支持链式调用
     */
    @NonNull
    public SampleChartBinder bind(@NonNull String metricKey, @NonNull String chartKey) {
        metricToChart.put(metricKey, chartKey);
        return this;
    }

    /**
     * 实现 SampleConsumer 接口：消费一个样本。
     * 根据 gate.enabled() 决定是否推送数据。
     */
    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!gate.enabled()) return;  // gate 关闭，不推数据
        append(sample);
    }

    /**
     * 把 sample.metrics() 里的数值推给对应的图表。
     * <p>
     * 遍历所有绑定关系：
     * ① 从 sample.metrics() 取 metricKey 对应的值
     * ② 如果值存在，调用 chartRegistry.append(chartKey, value)
     * ③ 图表收到值后，内部计算时间戳，追加数据点，重绘
     */
    public void append(@NonNull BluetoothSample sample) {
        Map<String, Float> metrics = sample.metrics();
        for (Map.Entry<String, String> entry : metricToChart.entrySet()) {
            Float value = metrics.get(entry.getKey());
            if (value != null) {
                // chartRegistry.append("cgm_primary", 99.3f)
                //   → RealtimeLineChart.append(99.3f)
                //     → 计算相对时间戳（从 startTimeMs 开始的秒数）
                //     → LineData.addEntry(new Entry(x, y))
                //     → 超过 500 点就删最早的
                //     → chart.invalidate() 重绘
                chartRegistry.append(entry.getValue(), value);
            }
        }
    }
}
