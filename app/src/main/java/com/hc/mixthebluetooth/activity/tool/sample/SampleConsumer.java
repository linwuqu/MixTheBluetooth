package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.NonNull;

/**
 * SampleConsumer — 样本消费者接口
 *
 * 作用：
 *   流水线解析出 BluetoothSample 后，由 SampleConsumerRegistry 广播给所有消费者。
 *   每个消费者实现这个接口，定义"收到样本后做什么"。
 *
 * 为什么用消费者/广播模式？
 *   一个样本可能需要同时做多件事：更新 UI、存文件、录波、推图表……
 *   如果把这些逻辑都写在流水线里，代码会变得臃肿，而且每次新增功能都要改流水线。
 *
 *   广播模式解决了这个问题：
 *     流水线只管解析和广播（sampleConsumers.consume(sample)）
 *     具体做什么，由消费者自己决定
 *     新增功能 → 写一个新 Consumer → register() → 完事
 *
 * 典型消费者：
 *   CgmStatusConsumer         — 更新界面状态文字
 *   CgmCurrentValueConsumer   — 更新界面血糖数值
 *   CgmFileConsumer           — 缓存数据写文件
 *   CgmRecorderConsumer       — 录波写 JSONL
 *   SampleChartBinder         — 推数据到图表
 */
public interface SampleConsumer {

    /**
     * 消费一个样本。
     *
     * @param sample 解析后的结构化样本（BluetoothSample 接口类型）
     *
     * 注意：大多数消费者会先做 instanceof 检查，
     * 确保只处理自己认识的设备类型（如 CgmSample），
     * 忽略其他设备的样本。
     */
    void consume(@NonNull BluetoothSample sample);
}
