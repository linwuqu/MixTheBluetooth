package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;

/**
 * CgmRecorderConsumer — CGM 录波消费者
 * <p>
 * 作用：
 * 当用户在录波时（recorder.isRecording() == true），
 * 把每次收到的血糖样本转成 JSON 行，写入 JSONL 录波文件。
 * <p>
 * 为什么叫"录波"？
 * "录波"是连续血糖监测领域的术语，类似于示波器的"记录波形"。
 * 用户开始录波后，每次设备发来血糖值，都记录下来（写文件），
 * 录波结束后导出一个 JSONL 文件，包含完整的测量时间序列。
 * <p>
 * 触发条件（两个都要满足）：
 * ① recorder.isRecording() == true（用户在录波）
 * ② sample instanceof CgmSample（CGM 的数据）
 * <p>
 * 不触发的场景：
 * 没有在录波（recorder.isRecording() == false）→ 静默忽略
 * 其他设备的样本 → 静默忽略
 * <p>
 * JSON 行格式：
 * 由 CgmJsonLineBuilder 生成，格式如：
 * {"type":"cgm","event":"current","raw":"CA:266,99.3","metrics":{"primary":99.3,"current":99.3},"module":"DeviceName","timestamp":1715076300000}
 * <p>
 * 为什么不在这里检查录波状态，而是放在 consume() 开头？
 * 放在开头检查是最优的：如果没在录波，后续所有操作都跳过，
 * 不会做无用的 JSON 构造和判断。
 */
public final class CgmRecorderConsumer implements SampleConsumer {

    /**
     * 运行时中介：用于获取当前连接的设备模块名称（写入 JSON）
     */
    private final FragmentRuntime runtime;

    /**
     * 录波器：写入 JSONL 文件
     */
    private final SampleRecorder recorder;

    public CgmRecorderConsumer(@NonNull FragmentRuntime runtime, @NonNull SampleRecorder recorder) {
        this.runtime = runtime;
        this.recorder = recorder;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        // ① 检查是否在录波：不在录波就跳过
        if (!recorder.isRecording()) return;

        // ② 只处理 CGM 的样本
        if (!(sample instanceof CgmSample)) return;

        // ③ 构造 JSON 行，追加到录波文件
        // CgmJsonLineBuilder.build() 根据 runtime.module() 获取设备名，写入 JSON
        recorder.appendLine(CgmJsonLineBuilder.build(runtime.module(), (CgmSample) sample));
    }
}
