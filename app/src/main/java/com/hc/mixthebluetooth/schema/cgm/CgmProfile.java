package com.hc.mixthebluetooth.schema.cgm;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.activity.tool.chart.SampleChartBinder;
import com.hc.mixthebluetooth.activity.tool.message.ControlRegistry;
import com.hc.mixthebluetooth.activity.tool.profile.DeviceProfile;
import com.hc.mixthebluetooth.activity.tool.profile.ProfileContext;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumerRegistry;
import com.hc.mixthebluetooth.databinding.FragmentMessageBinding;

/**
 * CgmProfile — CGM（连续血糖监测仪）设备在代码里的"档案"

 * 作用：描述 CGM 这台设备的所有特征——用什么解析器、有几个图表、收到数据后怎么展示、有几个按钮。

 * 为什么要有这个类？
 *   FragmentMessage 是通用的消息界面，不应该写"CGM 设备"相关的具体逻辑。
 *   如果以后换一个设备（比如心率设备），只需要把 profile = new CgmProfile() 改成
 *   profile = new HeartRateProfile()，FragmentMessage 的其他所有代码一行不用动。

 * 实现原理：策略模式
 *   DeviceProfile 接口规定了四个 register* 方法，每个设备档案（CGM、心率等）
 *   实现这四个方法，注册自己的解析器、图表、消费者、按钮。

 *   FragmentMessage 做的事：
 *     ① new 一个空的仓库（BluetoothSampleRegistry、ChartRegistry、SampleConsumerRegistry 等）
 *     ② new 一个空的按钮调度器（ControlRegistry）
 *     ③ 调用 profile.register*(registry, context)，让 Profile 自己把东西注册进来

 *   这样 Fragment 只提供"容器"，具体"装什么"由 Profile 决定。
 */
public final class CgmProfile implements DeviceProfile<FragmentMessageBinding> {

    /** 血糖主值图表的 key（用于在 ChartRegistry 里定位这个图表） */
    public static final String CHART_PRIMARY = "cgm_primary";

    /** 实时血糖值图表的 key */
    public static final String CHART_CURRENT = "cgm_current";

    /** 图表最多保留的数据点数量（超出后删除最早的点，保持内存稳定） */
    private static final int MAX_POINTS = 500;

    // ══════════════════════════════════════════════════════════════
    // registerSamples — 注册解析器
    // ══════════════════════════════════════════════════════════════

    /**
     * 注册 CGM 设备的蓝牙数据解析器。

     * 解析器的作用：
     *   蓝牙发来的是原始文本行（如 "CA:266,99.3"），解析器负责把它转换成
     *   结构化的 CgmSample 对象（包含事件类型、原始文本、数值指标）。

     * CgmParser 能识别的行：
     *   "Start Playback"              → CgmSample(EVENT_CACHE_START)  — 读缓存开始
     *   "Playback all done"           → CgmSample(EVENT_CACHE_DONE)  — 读缓存结束
     *   "RI:..."                      → CgmSample(EVENT_RI)         — 状态信息
     *   "EIS:foo,bar,99.3,more"       → CgmSample(EVENT_EIS, primary=99.3)
     *   "CA:266,99.3"                 → CgmSample(EVENT_CA, primary=99.3)
     *   "CA:266,266.0"                → CgmSample(EVENT_CURRENT, primary=99.3, current=99.3)
     *   其他未知行（在读缓存过程中）  → CgmSample(EVENT_CACHE_LINE)  — 原始缓存数据

     * 解析后的 CgmSample 对象会被 SampleConsumerRegistry 广播给所有消费者。
     */
    @Override
    public void registerSamples(@NonNull BluetoothSampleRegistry registry) {
        registry.register(new CgmParser());
    }

    // ══════════════════════════════════════════════════════════════
    // registerCharts — 注册图表
    // ══════════════════════════════════════════════════════════════

    /**
     * 注册 CGM 设备需要展示的实时折线图。

     * 这里注册了两个图表：
     *   CHART_PRIMARY ("cgm_primary")  — 血糖主值图表（红色折线）
     *       用于显示 EIS/CA 命令返回的血糖浓度值
     *   CHART_CURRENT ("cgm_current")  — 实时血糖值图表（蓝色折线）
     *       用于显示 CA:266 命令返回的当前实时血糖值

     * 为什么用字符串 key 而不是直接用 View？
     *   因为 key 会跨模块传递（SampleChartBinder.bind("primary", "cgm_primary")）。
     *   字符串 key 比 View 引用更稳定，不会因配置变化而失效。

     * RealtimeLineChart.Config 参数说明：
     *   label                — 图表左上角的图例文字
     *   color                — 折线颜色
     *   maxPoints            — 最多保留多少个数据点（超出的删最早的）
     *   visibleWindowSeconds — X 轴最多显示多少秒（超出范围的自动滚动）
     */
    @Override
    public void registerCharts(@NonNull ChartRegistry charts, @NonNull FragmentMessageBinding binding) {
        charts
                .register(
                        CHART_PRIMARY,
                        binding.chartPrimary,
                        new RealtimeLineChart.Config.Builder()
                                .label("CGM")
                                .color(Color.RED)
                                .maxPoints(MAX_POINTS)
                                .visibleWindowSeconds(60f)
                                .build()
                )
                .register(
                        CHART_CURRENT,
                        binding.chartCurrent,
                        new RealtimeLineChart.Config.Builder()
                                .label("Current")
                                .color(Color.BLUE)
                                .maxPoints(MAX_POINTS)
                                .visibleWindowSeconds(60f)
                                .build()
                );
    }

    // ══════════════════════════════════════════════════════════════
    // registerConsumers — 注册消费者
    // ══════════════════════════════════════════════════════════════

    /**
     * 注册消费者（Consumer）：收到解析好的 CgmSample 后，具体做什么。

     * SampleConsumerRegistry 使用广播模式：一个 sample 来了，会同时分发给所有消费者。
     * 每个消费者各司其职，互不感知。

     * 本方法注册了 5 个消费者：

     * ① CgmStatusConsumer — 状态显示
     *     消费所有 CgmSample，更新界面上的 tvStatus 文字。
     *     有状态文字（如 RI 命令的响应）就显示状态，没有就显示事件名。

     * ② CgmCurrentValueConsumer — 当前数值显示
     *     从 sample.metrics() 里取 "current" 字段（METRIC_CURRENT），
     *     更新 tvCurrentValue 文字。
     *     只有 CA:266 返回的样本才有 current 字段，其他样本会被静默忽略。

     * ③ CgmFileConsumer — 缓存数据写文件
     *     当用户在读缓存（device 回复 "Start Playback" → 多行历史数据 → "Playback all done"）时，
     *     把所有 cache 相关行（EVENT_CACHE_START / EVENT_CACHE_LINE / EVENT_CACHE_DONE）
     *     的原始文本追加写入 CGM_Cache_data.txt。
     *     实时测量数据不会触发这个消费者（event 不是 cache_*）。

     * ④ CgmRecorderConsumer — 录波写 JSONL
     *     如果 recorder.isRecording() == true，把 sample 转成 JSON 行，写入 JSONL 文件。
     *     如果没在录波（isRecording == false），直接跳过，不处理。
     *     JSON 行格式由 CgmJsonLineBuilder 生成。

     * ⑤ SampleChartBinder — 指标推图表（门控消费者）
     *     负责把 sample.metrics() 里的数值，推送到对应的图表。
     *     门控条件：context.recorder::isRecording — 只有开始录波后 gate 才打开，图表才更新。
     *     绑定关系：
     *       "primary"  metric → "cgm_primary" 图表（红色折线，显示主血糖值）
     *       "current" metric → "cgm_current" 图表（蓝色折线，显示实时血糖值）
     */
    @Override
    public void registerConsumers(
            @NonNull SampleConsumerRegistry consumers,
            @NonNull ProfileContext<FragmentMessageBinding> context
    ) {
        consumers
                .register(new CgmStatusConsumer(context.binding.tvStatus))
                .register(new CgmCurrentValueConsumer(context.binding.tvCurrentValue))
                .register(new CgmFileConsumer(context.runtime))
                .register(new CgmRecorderConsumer(context.runtime, context.recorder))
                .register(new SampleChartBinder(context.charts, context.recorder::isRecording)
                        .bind(CgmSample.METRIC_PRIMARY, CHART_PRIMARY)
                        .bind(CgmSample.METRIC_CURRENT, CHART_CURRENT));
    }

    // ══════════════════════════════════════════════════════════════
    // registerControls — 注册按钮
    // ══════════════════════════════════════════════════════════════

    /**
     * 注册按钮 → 动作的绑定关系。
     *
     * ControlRegistry 内部维护一张 Map<Integer, ControlAction>，
     * key 是按钮的 Android 资源 ID，value 是按下后要执行的动作。
     *
     * 这里的按钮都是 CGM 设备专用的命令按钮：
     *
     * ① btnStartMeasure — "开始测量"按钮
     *     按下 → sender.send(CgmCommandSet.startNow())
     *     CgmCommandSet.startNow() 返回：
     *       "TIME,2026,05,07,14,25,00\r\n"
     *     其中时间部分是当前系统时间，格式为 yyyy,MM,dd,HH,mm,ss。
     *     设备收到后开始实时测量。
     *
     * ② btnReadCache — "读取缓存"按钮
     *     按下 → sender.send(CgmCommandSet.readCache())
     *     CgmCommandSet.readCache() 返回："ALL\r\n"
     *     设备收到后开始回传内部存储的历史血糖记录。
     *     回传格式：Start Playback → 多行历史数据 → Playback all done。
     *     这些数据会被 CgmFileConsumer 写入 CGM_Cache_data.txt。
     *
     * ③ btnDeleteCache — "删除缓存"按钮
     *     按下 → sender.send(CgmCommandSet.deleteCache())
     *     CgmCommandSet.deleteCache() 返回："DELETE\r\n"
     *     设备收到后删除内部缓存数据。
     *
     * ④ btnParams — "设置参数"按钮
     *     按下 → CgmParameterDialog.show(runtime, rolePolicy, sender)
     *     弹出参数配置对话框（5 个输入框：controlRatio / extractionTime /
     *     highLevelTime / voltage / detectionTime）。
     *     用户点击"提交"后，把参数拼成二进制字符串命令，通过 sender.send() 发送给设备。
     *     rolePolicy.canEditParameters() 决定输入框是否可编辑（根据用户角色）。
     */
    @Override
    public void registerControls(
            @NonNull ControlRegistry controls,
            @NonNull ProfileContext<FragmentMessageBinding> context
    ) {
        controls
                .bind(context.binding.btnStartMeasure, () -> context.sender.send(CgmCommandSet.startNow()))
                .bind(context.binding.btnReadCache, () -> context.sender.send(CgmCommandSet.readCache()))
                .bind(context.binding.btnDeleteCache, () -> context.sender.send(CgmCommandSet.deleteCache()))
                .bind(context.binding.btnParams, () -> CgmParameterDialog.show(
                        context.runtime,
                        context.rolePolicy,
                        context.sender
                ));
    }
}
