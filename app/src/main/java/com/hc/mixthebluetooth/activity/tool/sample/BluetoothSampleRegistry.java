package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * BluetoothSampleRegistry — 解析器仓库（按顺序遍历，第一个能解析的赢）
 * <p>
 * 作用：
 * 管理多个 BluetoothSampleParser，支持多协议共存。
 * parse(line) 按顺序遍历所有已注册的解析器，第一个返回非 null 的解析器赢。
 * <p>
 * 为什么支持多个解析器？
 * 可能同时连接多种蓝牙设备，每种有不同的通信格式。
 * 或者同一个设备可能同时发多种格式的数据。
 * <p>
 * 设计原则：短路（Short-circuit）
 * 遍历过程中，一旦某个解析器返回非 null，就立即返回，不再继续。
 * 这样既保证了优先级，又避免了不必要的解析尝试。
 * <p>
 * 典型使用：
 * BluetoothSampleRegistry registry = new BluetoothSampleRegistry();
 * registry.register(new CgmParser());         // CGM 解析器
 * registry.register(new HeartRateParser());   // 心率解析器
 * <p>
 * BluetoothSample sample = registry.parse("CA:266,99.3");
 * → CgmParser 能解析，返回 CgmSample
 * <p>
 * BluetoothSample sample = registry.parse("PMH:72");
 * → CgmParser 不认识，HeartRateParser 认识，返回 HeartRateSample
 * <p>
 * BluetoothSample sample = registry.parse("UNKNOWN");
 * → 都没有，返回 null
 */
public final class BluetoothSampleRegistry {

    /**
     * 已注册的解析器列表（按注册顺序遍历）
     */
    private final List<BluetoothSampleParser> parsers = new ArrayList<>();

    /**
     * 注册一个解析器
     */
    @NonNull
    public BluetoothSampleRegistry register(@NonNull BluetoothSampleParser parser) {
        parsers.add(parser);
        return this;
    }

    /**
     * 解析一行文本：按顺序遍历解析器，第一个能解析的赢。
     *
     * @param line 蓝牙文本行
     * @return 第一个能解析的结果，或 null（所有解析器都不认识）
     */
    @Nullable
    public BluetoothSample parse(@Nullable String line) {
        for (BluetoothSampleParser parser : parsers) {
            BluetoothSample sample = parser.parse(line);
            if (sample != null) return sample;
        }
        return null;
    }
}
