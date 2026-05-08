package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.NonNull;

/**
 * BluetoothSample — 所有蓝牙设备解析结果的统一数据结构
 *
 * 作用：
 *   不同设备（CGM、心率等）发来的数据格式不同，但解析后都用这个接口统一表示。
 *   这样后续处理（消费者、图表、录波器）只需要认识这一个接口，不需要知道每个设备的格式。
 *
 * 设计原则：依赖倒置
 *   消费者（SampleConsumer）只认识 BluetoothSample 接口，
 *   不需要知道 CgmSample / HeartRateSample 等具体实现。
 *   新增一个设备类型，只需要：
 *     ① 写一个新的解析器（实现 BluetoothSampleParser）
 *     ② 写一个新的 Sample 实现（实现 BluetoothSample）
 *     ③ 在对应 Profile 的 registerSamples() 里注册新解析器
 *     → 其他所有代码（消费者、流水线）不用动
 *
 * 三个字段：
 *   type()   — 样本类型标识，如 "cgm"，用于 instanceof 判断
 *   raw()    — 原始文本行，方便调试和写文件
 *   metrics() — 数值指标 Map：key = 指标名（"primary" / "current"），value = float 数值
 */
public interface BluetoothSample {

    /** 样本类型，如 "cgm" */
    @NonNull
    String type();

    /** 原始文本行 */
    @NonNull
    String raw();

    /** 数值指标 Map。key 是指标名（字符串），value 是 float 数值 */
    @NonNull
    java.util.Map<String, Float> metrics();
}
