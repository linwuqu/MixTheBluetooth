package com.hc.mixthebluetooth.schema.cgm;

import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

/**
 * CgmStatusConsumer — CGM 状态显示消费者
 *
 * 作用：
 *   收到 CgmSample 后，更新界面上的 tvStatus 文本框。
 *   用于显示设备当前的测量状态或事件名称。
 *
 * 显示规则：
 *   - 有 status 字段（CgmSample.status 非 null、非空）→ 显示 status 文字
 *     例如 RI 命令响应：CgmSample { status="RI:ok" } → tvStatus 显示 "RI:ok"
 *   - 没有 status 字段 → 显示 event 名称
 *     例如 EIS 数据：CgmSample { event="eis" } → tvStatus 显示 "eis"
 */
public final class CgmStatusConsumer implements SampleConsumer {

    /** 界面上的状态文字 TextView */
    private final TextView statusView;

    public CgmStatusConsumer(@NonNull TextView statusView) {
        this.statusView = statusView;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        // 只处理 CGM 的样本，忽略其他设备的样本
        if (!(sample instanceof CgmSample)) return;

        CgmSample cgm = (CgmSample) sample;
        if (cgm.status != null && !cgm.status.isEmpty()) {
            // 有状态文字 → 显示状态（如 "RI:ok"）
            statusView.setText(cgm.status);
        } else {
            // 没有状态文字 → 显示事件类型（如 "eis" / "current"）
            statusView.setText(cgm.event);
        }
    }
}
