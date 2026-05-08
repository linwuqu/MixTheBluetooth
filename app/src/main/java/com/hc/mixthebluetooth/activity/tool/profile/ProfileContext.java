package com.hc.mixthebluetooth.activity.tool.profile;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.message.MessageSender;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;

/**
 * ProfileContext — Profile 与 Fragment 之间共享的上下文容器
 *
 * 作用：
 *   当 FragmentMessage 调用 profile.register*(registry, context) 时，
 *   需要把 Fragment 里已经 new 好的各种工具（runtime、charts、sender 等）传给 Profile，
 *   Profile 再用这些工具注册消费者和按钮。
 *
 *   ProfileContext 就是这个"打包好的工具箱"——
 *   它把 Fragment 里所有可能用到的工具都装在一个对象里，一次性传给 Profile。
 *
 * 为什么不用一堆独立参数，而是打包成一个 Context 对象？
 *   ① 如果不用 Context，每次 register* 方法的参数列表会很长（6-7 个参数），代码难读
 *   ② 以后加新工具（比如新增一个工具），只需要在 Context 里加一个字段，
 *      register* 方法签名不变，所有调用方不用改
 *   ③ Profile 内部需要共享同一份 runtime/charts/recorder 实例，打包确保引用一致
 *
 * 各字段说明：
 *
 *   binding
 *     → 界面绑定对象（FragmentMessageBinding），包含所有界面控件引用。
 *     → 消费者和图表注册时需要直接访问控件（如 tvStatus、chartPrimary）。
 *
 *   runtime
 *     → FragmentRuntime，封装了 Fragment 的三个基础能力。
 *     → 消费者（如 CgmFileConsumer）需要用它来获取 Context（写文件用）
 *       或弹 Toast。CgmRecorderConsumer 需要用它来获取 module 信息。
 *
 *   charts
 *     → ChartRegistry，图表仓库。
 *     → SampleChartBinder 需要往里面查图表、推数据点。
 *
 *   sender
 *     → MessageSender，发送命令的人。
 *     → registerControls 绑定的按钮动作里要调用 sender.send()。
 *
 *   recorder
 *     → SampleRecorder，录波器。
 *     → CgmRecorderConsumer 需要用它来判断是否在录波，以及追加数据。
 *     → SampleChartBinder 用 recorder::isRecording 作为 gate（门控）。
 *
 *   rolePolicy
 *     → UserRolePolicy，用户角色策略。
 *     → CgmParameterDialog 用它来判断当前用户是否有权限编辑参数。
 */
public final class ProfileContext<B> {
    @NonNull
    public final B binding;

    @NonNull
    public final FragmentRuntime runtime;

    @NonNull
    public final ChartRegistry charts;

    @NonNull
    public final MessageSender sender;

    @NonNull
    public final SampleRecorder recorder;

    @NonNull
    public final UserRolePolicy rolePolicy;

    public ProfileContext(
            @NonNull B binding,
            @NonNull FragmentRuntime runtime,
            @NonNull ChartRegistry charts,
            @NonNull MessageSender sender,
            @NonNull SampleRecorder recorder,
            @NonNull UserRolePolicy rolePolicy
    ) {
        this.binding = binding;
        this.runtime = runtime;
        this.charts = charts;
        this.sender = sender;
        this.recorder = recorder;
        this.rolePolicy = rolePolicy;
    }
}
