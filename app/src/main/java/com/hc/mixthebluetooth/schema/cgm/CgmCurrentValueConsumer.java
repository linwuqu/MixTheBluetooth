package com.hc.mixthebluetooth.schema.cgm;

import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

/**
 * CgmCurrentValueConsumer — CGM 当前数值显示消费者
 * <p>
 * 作用：
 * 收到 CgmSample 后，从 metrics 里取出 "current" 字段的值，
 * 更新界面上的 tvCurrentValue 文本框（显示实时血糖值）。
 * <p>
 * 只响应哪类数据？
 * 只有 CA:266 命令返回的样本才有 "current" 字段（METRIC_CURRENT）。
 * 其他样本（EIS / CA 普通值 / cache 行）没有 current 字段，
 * 消费者会静默忽略，不更新界面。
 * <p>
 * 触发场景：
 * 用户点击"开始测量"后，设备持续返回 CA:266 数据，
 * 每次回来这个消费者就把界面上的血糖值更新一次。
 */
public final class CgmCurrentValueConsumer implements SampleConsumer {

    /**
     * 界面上的当前数值 TextView
     */
    private final TextView currentValueView;

    public CgmCurrentValueConsumer(@NonNull TextView currentValueView) {
        this.currentValueView = currentValueView;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        // 只处理 CGM 的样本
        if (!(sample instanceof CgmSample)) return;

        // 从 metrics 里取 "current" 字段（CgmSample.METRIC_CURRENT = "current"）
        Float value = sample.metrics().get(CgmSample.METRIC_CURRENT);
        if (value != null) {
            // 有 current 字段（CA:266 的实时电流值）→ 更新界面
            currentValueView.setText(String.valueOf(value));
        }
        // 没有 current 字段 → 静默忽略，不更新界面
    }
}
