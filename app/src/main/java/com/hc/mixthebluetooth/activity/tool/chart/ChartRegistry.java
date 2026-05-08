package com.hc.mixthebluetooth.activity.tool.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.LineChart;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ChartRegistry — 图表仓库：管理一个 Fragment 里的所有实时折线图
 *
 * 作用：
 *   一个 Fragment 可能需要展示多个图表（如 CGM 的"主血糖值"和"实时值"两个图表）。
 *   ChartRegistry 负责：
 *     - 按 key 注册图表（String key → RealtimeLineChart 实例）
 *     - 按 key 推送数据点（chartRegistry.append("cgm_primary", 99.3f)）
 *     - 按 key 重置图表
 *
 * 为什么用字符串 key 而不是直接持有 View 引用？
 *   因为 key 需要跨模块传递：
 *     CgmProfile.registerCharts() 注册时知道具体的 View（binding.chartPrimary），
 *     但 SampleChartBinder 绑定 metric → chart 时只知道字符串 key。
 *     字符串 key 比 View 引用更稳定，不会因为配置变化而失效。
 *
 * 各方法说明：
 *
 *   register(key, chart, config)
 *     → 把一个图表注册进仓库。key 是这个图表的唯一标识。
 *     → 通常由 Profile 的 registerCharts() 调用。
 *
 *   append(key, value)
 *     → 往指定图表追加一个数据点。
 *     → 由 SampleChartBinder.consume() 调用，把采到的血糖值推给图表。
 *
 *   reset(key)
 *     → 重置指定图表：清空数据点，重置时间基准。
 *     → 开始录波前调用 chartRegistry.resetAll() 清空所有图表。
 *
 *   resetAll()
 *     → 重置所有已注册的图表。
 *     → startRecording() 时调用，清空历史数据，重新计时。
 */
public class ChartRegistry {

    /** String key → 图表实例 的映射 */
    private final Map<String, RealtimeLineChart> charts = new LinkedHashMap<>();

    /**
     * 注册一个图表。
     *
     * @param key    图表的字符串标识（如 "cgm_primary"）
     * @param chart  界面上实际的 LineChart 控件
     * @param config 图表配置（颜色、标签、最大点数、可见窗口等）
     */
    @NonNull
    public ChartRegistry register(@NonNull String key, @NonNull LineChart chart, @NonNull RealtimeLineChart.Config config) {
        charts.put(key, new RealtimeLineChart(chart, config));
        return this;
    }

    /** 往指定图表追加一个数据点。找不到 key 时静默忽略。 */
    public void append(@NonNull String key, float value) {
        RealtimeLineChart chart = charts.get(key);
        if (chart == null) return;
        chart.append(value);
    }

    /** 重置指定图表：清空数据，重置时间基准。找不到 key 时静默忽略。 */
    public void reset(@NonNull String key) {
        RealtimeLineChart chart = charts.get(key);
        if (chart == null) return;
        chart.reset();
    }

    /** 重置所有已注册的图表。 */
    public void resetAll() {
        for (RealtimeLineChart chart : charts.values()) {
            chart.reset();
        }
    }

    /** 检查指定 key 的图表是否已注册。 */
    public boolean contains(@NonNull String key) {
        return charts.containsKey(key);
    }

    /** 获取指定 key 对应的图表实例（可能为 null）。 */
    @Nullable
    public RealtimeLineChart get(@NonNull String key) {
        return charts.get(key);
    }
}
