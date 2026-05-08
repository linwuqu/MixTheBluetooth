package com.hc.mixthebluetooth.activity.tool.profile;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.message.ControlRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumerRegistry;

/**
 * DeviceProfile — 设备档案接口

 * 设计意图：
 *   FragmentMessage 是通用的消息界面，不应该写"CGM 设备"或"心率设备"的具体逻辑。
 *   每种设备（协议）写一个 DeviceProfile 实现，把自己的解析器、图表、消费者、按钮都注册进去。
 *   Fragment 只需要调用 profile.register*(registry, context)，不需要知道具体是哪种设备。

 *   换设备？只需改一行：
 *     profile = new CgmProfile();     →  profile = new HeartRateProfile();
 *     其他所有代码不动。

 * 策略模式：
 *   DeviceProfile 是"策略接口"，CgmProfile / HeartRateProfile 等是具体策略实现。
 *   FragmentMessage 是"上下文"，持有策略接口，通过接口调用策略的具体行为。

 * 四个注册方法：

 *   registerSamples(registry)
 *     → 把自己特有的解析器注册到 sampleRegistry
 *     → 例如 CgmProfile.registerSamples() 里写：registry.register(new CgmParser())
 *     → 必须在 initProfile() 阶段调用，因为 pipeline 和消费者都依赖它

 *   registerCharts(charts, binding)
 *     → 把自己需要展示的图表注册到 ChartRegistry
 *     → 例如 CgmProfile.registerCharts() 里注册"cgm_primary"和"cgm_current"两个图表
 *     → 需要 binding 参数，因为要拿到界面上的 chart view 来初始化

 *   registerConsumers(consumers, context)
 *     → 把自己收到样本后要做的操作注册到 SampleConsumerRegistry
 *     → 例如 CgmProfile.registerConsumers() 里注册 5 个消费者
 *     → 需要 context 参数，因为消费者需要取用 runtime / sender / charts / recorder 等工具

 *   registerControls(controls, context)
 *     → 把自己特有的按钮绑定关系注册到 ControlRegistry
 *     → 例如 CgmProfile.registerControls() 绑定 btnStartMeasure → startNow 命令
 *     → 需要 context 参数，因为按钮动作里要调用 sender.send()
 *
 * @param <B> 界面绑定类的类型（FragmentMessage 对应 FragmentMessageBinding）
 */
public interface DeviceProfile<B> {

    /**
     * 注册蓝牙数据解析器。
     * 告诉流水线：收到蓝牙文本行后，用这个解析器来解析。
     */
    void registerSamples(@NonNull BluetoothSampleRegistry registry);

    /**
     * 注册实时折线图。
     * 告诉界面：需要展示哪些图表。
     */
    void registerCharts(@NonNull ChartRegistry charts, @NonNull B binding);

    /**
     * 注册样本消费者。
     * 告诉流水线：解析出样本后，分发给哪些消费者处理。
     */
    void registerConsumers(@NonNull SampleConsumerRegistry consumers, @NonNull ProfileContext<B> context);

    /**
     * 注册按钮绑定关系。
     * 告诉界面：哪些按钮按下去要发什么命令或弹什么对话框。
     */
    void registerControls(@NonNull ControlRegistry controls, @NonNull ProfileContext<B> context);
}
