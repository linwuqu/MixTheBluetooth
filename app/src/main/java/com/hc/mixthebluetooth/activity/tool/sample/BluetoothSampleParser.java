package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.Nullable;

/**
 * BluetoothSampleParser — 蓝牙文本行的解析器接口
 *
 * 作用：
 *   每种设备（CGM、心率等）有自己的蓝牙通信格式。
 *   解析器负责把设备发来的原始文本行，解析成 BluetoothSample 对象。
 *
 * 为什么是"文本行"？
 *   大部分蓝牙设备通过文本协议通信，数据以换行符分隔。
 *   MessagePipelineController 收到字节 → 解码成文本 → 一行一行地调用 parser.parse(line)。
 *
 * 实现规则：
 *   能解析 → 返回 BluetoothSample 对象
 *   不能解析 → 返回 null（不要抛异常）
 *
 * 典型实现：CgmParser
 *   输入 "CA:266,99.3"  → 返回 CgmSample { event="ca", metrics={"primary": 99.3f} }
 *   输入 "RI:ok"         → 返回 CgmSample { event="ri", status="RI:ok" }
 *   输入 "HELLO"         → 返回 null（不是 CGM 认识的数据）
 *
 * 注意：一个设备可能有多个解析器（CgmParser + HeartRateParser）。
 * BluetoothSampleRegistry 按顺序遍历，第一个能解析的赢。
 */
public interface BluetoothSampleParser {

    /**
     * 解析一行文本。
     *
     * @param line 蓝牙设备发来的一行文本（不含换行符）
     * @return 解析后的 BluetoothSample，或者 null（表示这行解析不了）
     */
    @Nullable
    BluetoothSample parse(@Nullable String line);
}
