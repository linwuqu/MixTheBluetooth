package com.hc.mixthebluetooth.schema.cgm;

import android.os.Environment;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

/**
 * CgmFileConsumer — CGM 缓存数据文件写入消费者
 * <p>
 * 作用：
 * 当用户在读取设备缓存（device 返回 "Start Playback" → 多行历史数据 → "Playback all done"）时，
 * 把所有缓存相关的原始行追加写入 CGM_Cache_data.txt 文件。
 * <p>
 * 触发的条件：
 * sample instanceof CgmSample &&
 * sample.event 是以下三者之一：
 * EVENT_CACHE_START  — "Start Playback"（读缓存开始）
 * EVENT_CACHE_LINE   — 读缓存过程中的历史数据行（解析器不认识格式的行）
 * EVENT_CACHE_DONE   — "Playback all done"（读缓存结束）
 * <p>
 * 不触发的场景：
 * 实时测量数据（EIS / CA / RI）的 event 不是 cache_* → 静默忽略
 * <p>
 * 为什么只写缓存行，不写所有数据？
 * 缓存数据是设备的历史记录，用户需要单独保存以便后续分析。
 * 实时测量数据可以通过录波器（SampleRecorder）以 JSONL 格式保存，格式更规范。
 */
public final class CgmFileConsumer implements SampleConsumer {

    /**
     * 运行时中介：用于获取 Context（获取文件目录）
     */
    private final FragmentRuntime runtime;

    public CgmFileConsumer(@NonNull FragmentRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        // 只处理 CGM 样本
        if (!(sample instanceof CgmSample)) return;

        CgmSample cgm = (CgmSample) sample;
        // 获取文件写入目录（Documents）
        String dir = String.valueOf(runtime.context().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));

        // 只写缓存相关的行
        if (CgmSample.EVENT_CACHE_START.equals(cgm.event)
                || CgmSample.EVENT_CACHE_LINE.equals(cgm.event)
                || CgmSample.EVENT_CACHE_DONE.equals(cgm.event)) {
            // Analysis.IO_input_data()：把原始行追加写入文本文件
            Analysis.IO_input_data(cgm.raw, dir, "CGM_Cache_data.txt");
        }
        // 实时测量数据（eis / ca / ri）→ 静默忽略，不写文件
    }
}
