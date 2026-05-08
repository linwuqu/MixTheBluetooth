package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CgmSample — CGM 设备蓝牙数据的结构化表示
 *
 * 职责：
 *   解析器（CgmParser）把蓝牙文本行解析后，返回一个 CgmSample 对象。
 *   这个对象是"CGM 设备发来的一条数据"的完整描述。
 *
 * 设计原则：
 *   CgmSample 实现了 BluetoothSample 接口，确保所有设备（CGM、心率等）
 *   解析后的数据结构统一。后续消费者（更新 UI、写文件、录波）只需要认识
 *   BluetoothSample 接口，不需要知道 CGM 的具体格式。
 *
 * 数据结构：
 *
 *   event    — 事件类型，标识这条数据是什么（测量值？状态？缓存开始？）
 *              如 "current" / "eis" / "ca" / "cache_start" / "cache_line"
 *
 *   raw      — 原始文本行，如 "CA:266,99.3"
 *              保留原始文本，方便调试和写文件
 *
 *   status   — 状态文字，某些 event 类型特有（如 RI 命令返回的状态信息）
 *              大部分 event 的 status 为 null
 *
 *   metrics  — 数值指标 Map，CGM 的核心数据
 *              key = "primary"（主血糖值）或 "current"（当前实时血糖值）
 *              value = float 血糖浓度
 *
 * 五种构造方法（工厂方法）：
 *
 *   event(event, raw)
 *     → 普通事件，没有数值（如 cache_start / cache_done / cache_line）
 *
 *   status(event, raw, status)
 *     → 带状态文字（如 RI 命令的响应）
 *
 *   metric(event, raw, key, value)
 *     → 带一个数值指标（EIS/CA 命令的血糖值）
 *
 *   current(raw, value)
 *     → 实时电流值（CA:266），同时设置 primary 和 current 两个指标为同一值
 *
 * Event 类型说明：
 *
 *   EVENT_CACHE_START  — 读缓存开始（设备发 "Start Playback"）
 *   EVENT_CACHE_LINE   — 读缓存过程中的历史数据行（格式不确定）
 *   EVENT_CACHE_DONE   — 读缓存结束（设备发 "Playback all done"）
 *   EVENT_CA          — CA 命令返回的血糖浓度（metrics.primary = 浓度值）
 *   EVENT_EIS         — EIS 命令返回的电化学阻抗谱值（metrics.primary = 浓度值）
 *   EVENT_RI          — RI 命令返回的设备状态
 *   EVENT_CURRENT     — CA:266 实时电流值（metrics.primary = metrics.current = 浓度值）
 */
public final class CgmSample implements BluetoothSample {

    /** 样本类型标识（实现 BluetoothSample 接口） */
    public static final String TYPE = "cgm";

    /** 主血糖值指标 key（用于 metrics Map） */
    public static final String METRIC_PRIMARY = "primary";

    /** 实时血糖值指标 key（用于 metrics Map，只有 CA:266 才有） */
    public static final String METRIC_CURRENT = "current";

    // ── Event 常量 ────────────────────────────────────────────

    /** 事件类型：读缓存开始 */
    public static final String EVENT_CACHE_START = "cache_start";

    /** 事件类型：读缓存结束 */
    public static final String EVENT_CACHE_DONE = "cache_done";

    /** 事件类型：读缓存过程中的一行历史数据（格式未知） */
    public static final String EVENT_CACHE_LINE = "cache_line";

    /** 事件类型：CA 命令（血糖浓度） */
    public static final String EVENT_CA = "ca";

    /** 事件类型：EIS 命令（电化学阻抗谱） */
    public static final String EVENT_EIS = "eis";

    /** 事件类型：RI 命令（设备状态） */
    public static final String EVENT_RI = "ri";

    /** 事件类型：CA:266 实时电流值（同时设置 primary 和 current） */
    public static final String EVENT_CURRENT = "current";

    // ── 字段 ────────────────────────────────────────────────

    /** 事件类型（字符串常量，如 "current"） */
    @NonNull
    public final String event;

    /** 原始文本行（如 "CA:266,99.3"） */
    @NonNull
    public final String raw;

    /** 状态文字（某些 event 有，如 RI 命令的响应；大部分为 null） */
    @Nullable
    public final String status;

    /** 数值指标 Map：key = 指标名（"primary" 或 "current"），value = 血糖浓度 */
    @NonNull
    private final Map<String, Float> metrics;

    // ── 构造函数 ─────────────────────────────────────────────

    public CgmSample(
            @NonNull String event,
            @NonNull String raw,
            @Nullable String status,
            @NonNull Map<String, Float> metrics
    ) {
        this.event = event;
        this.raw = raw;
        this.status = status;
        // 每次都复制一份新的 Map，防止外部修改
        this.metrics = new LinkedHashMap<>(metrics);
    }

    // ── 工厂方法 ────────────────────────────────────────────

    /**
     * 创建普通事件（无数值指标）。
     * 用于：cache_start / cache_done / cache_line 等没有具体血糖值的事件。
     */
    @NonNull
    public static CgmSample event(@NonNull String event, @NonNull String raw) {
        return new CgmSample(event, raw, null, Collections.emptyMap());
    }

    /**
     * 创建带状态文字的事件。
     * 用于：RI 命令的响应，status 字段有具体内容。
     */
    @NonNull
    public static CgmSample status(@NonNull String event, @NonNull String raw, @NonNull String status) {
        return new CgmSample(event, raw, status, Collections.emptyMap());
    }

    /**
     * 创建带一个数值指标的事件。
     * 用于：EIS 和普通 CA 命令的响应。
     *
     * @param key   指标名（METRIC_PRIMARY = "primary"）
     * @param value 血糖浓度值（如 99.3f）
     */
    @NonNull
    public static CgmSample metric(@NonNull String event, @NonNull String raw, @NonNull String key, float value) {
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(key, value);
        return new CgmSample(event, raw, event, values); // status 字段默认用 event 填充
    }

    /**
     * 创建实时电流值事件（CA:266）。
     * 同时设置 primary 和 current 两个指标为同一值，
     * 这样 SampleChartBinder 可以同时推两个图表。
     */
    @NonNull
    public static CgmSample current(@NonNull String raw, float value) {
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(METRIC_PRIMARY, value);
        values.put(METRIC_CURRENT, value);
        return new CgmSample(EVENT_CURRENT, raw, EVENT_CURRENT, values);
    }

    // ── 接口方法实现 ─────────────────────────────────────────

    /** 样本类型，返回 "cgm" */
    @NonNull
    @Override
    public String type() {
        return TYPE;
    }

    /** 原始文本行 */
    @NonNull
    @Override
    public String raw() {
        return raw;
    }

    /** 数值指标 Map（返回不可变副本，防止外部修改） */
    @NonNull
    @Override
    public Map<String, Float> metrics() {
        return Collections.unmodifiableMap(metrics);
    }
}
