package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * SampleConsumerRegistry — 消费者仓库（广播模式）
 * <p>
 * 作用：
 * 管理多个 SampleConsumer，收到样本时广播给所有消费者。
 * 本身也实现了 SampleConsumer 接口——这是组合模式的体现。
 * <p>
 * 设计要点：
 * - 一个样本进来，所有消费者都会收到
 * - 消费者之间互不感知，各自独立处理
 * - 新增消费者只需 register()，流水线代码不动
 * <p>
 * 典型使用：
 * SampleConsumerRegistry consumers = new SampleConsumerRegistry();
 * consumers.register(new CgmStatusConsumer(tvStatus));
 * consumers.register(new CgmCurrentValueConsumer(tvCurrentValue));
 * consumers.register(new CgmFileConsumer(runtime));
 * consumers.register(new CgmRecorderConsumer(runtime, recorder));
 * consumers.register(new SampleChartBinder(charts, gate).bind(...).bind(...));
 * <p>
 * // 收到样本时，调用一次，全部消费者都收到
 * consumers.consume(sample);
 * <p>
 * 组合模式：
 * SampleConsumerRegistry 本身实现了 SampleConsumer 接口。
 * 这样流水线调用 sampleConsumers.consume(sample) 时，
 * 内部自动分发给所有已注册的消费者。
 * 对流水线来说，sampleConsumers 和单个 Consumer 没有区别。
 */
public final class SampleConsumerRegistry implements SampleConsumer {

    /**
     * 已注册的消费者列表
     */
    private final List<SampleConsumer> consumers = new ArrayList<>();

    /**
     * 注册一个消费者
     */
    @NonNull
    public SampleConsumerRegistry register(@NonNull SampleConsumer consumer) {
        consumers.add(consumer);
        return this;
    }

    /**
     * 广播样本：分发给所有已注册的消费者。
     * <p>
     * 对每个消费者调用 consumer.consume(sample)。
     * 注意：这不是并行执行，而是顺序执行。
     * 如果某个消费者抛异常，后续消费者不会收到。
     */
    @Override
    public void consume(@NonNull BluetoothSample sample) {
        for (SampleConsumer consumer : consumers) {
            consumer.consume(sample);
        }
    }
}
