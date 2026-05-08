package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.basiclibrary.viewBasic.HomeApplication;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.message.ControlRegistry;
import com.hc.mixthebluetooth.activity.tool.message.MessageListController;
import com.hc.mixthebluetooth.activity.tool.message.MessageOptionStore;
import com.hc.mixthebluetooth.activity.tool.message.MessageOptions;
import com.hc.mixthebluetooth.activity.tool.message.MessagePipelineController;
import com.hc.mixthebluetooth.activity.tool.message.MessageSender;
import com.hc.mixthebluetooth.activity.tool.profile.DeviceProfile;
import com.hc.mixthebluetooth.activity.tool.profile.ProfileContext;
import com.hc.mixthebluetooth.activity.tool.profile.UserRolePolicy;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumerRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;
import com.hc.mixthebluetooth.databinding.FragmentMessageBinding;
import com.hc.mixthebluetooth.schema.eis.EisProfile;
import com.hc.mixthebluetooth.storage.Storage;

/**
 * FragmentMessage — 蓝牙消息界面的主控制器（老板角色）
 * <p>
 * 整体架构（策略模式 + 依赖注入）：
 * <p>
 * FragmentMessage（老板）负责创建所有子模块，并按顺序初始化它们。
 * 具体每个设备（CGM）有哪些按钮、按下去发什么命令、收到数据后怎么展示，
 * 全部由 DeviceProfile（设备档案）来描述，FragmentMessage 本身不写设备相关的具体逻辑。
 * <p>
 * 初始化顺序（不可随意调换，因为后面依赖前面）：
 * initRuntime()       — 创建运行时中介（发蓝牙、Toast、日志的统一入口）
 * initOptions()       — 加载用户的发送设置（HEX/字符串、换行符等）
 * initProfile()       — 创建设备档案，注册解析器
 * initMessageList()   — 初始化消息列表 RecyclerView
 * initCharts()        — 初始化实时折线图，注册到图表仓库
 * initRecorder()      — 初始化录波器（写 JSONL 文件）
 * initPipeline()      — 初始化数据处理流水线（发送路径 + 接收路径的核心枢纽）
 * initControls()       — 绑定按钮 → 命令的映射关系
 * <p>
 * 完整数据流：
 * <p>
 * 【发送路径】用户点按钮 → ControlRegistry.dispatch() → MessageSender.send() → runtime.sendBtData()
 * → sendDataToActivity(CMD_SEND_BT_DATA) → Activity 实际发送蓝牙数据
 * <p>
 * 【接收路径】蓝牙收到数据 → Activity 发布到 LiveEventBus → FM.onBtData()
 * → MessagePipelineController.onBtData()
 * → BluetoothPayloadDecoder 解码 → 文本行
 * → BluetoothSampleRegistry.parse() → BluetoothSample 对象
 * → SampleConsumerRegistry.consume() 广播给所有消费者
 * → 各个 Consumer 分别：更新 UI / 写缓存文件 / 录波 / 推图表
 */
public class FragmentMessage extends BTFragment<FragmentMessageBinding> {

    /**
     * 收到这么多字节后自动清空消息列表（防止内存泄漏）
     */
    private static final int AUTO_CLEAR_READ_BYTES = 400000;

    // ── 子模块声明（按初始化顺序排列）───────────────────────────────

    /**
     * 运行时中介：封装 Fragment 的三个基础能力（发蓝牙/Toast/日志），供子模块使用
     */
    private FragmentRuntime runtime;

    /**
     * 用户设置的发送选项（HEX/字符串、换行、编码格式等）
     */
    private MessageOptions options;

    /**
     * 发送选项的持久化存取器
     */
    private MessageOptionStore optionStore;

    /**
     * 设备档案：描述当前设备（CGM）的解析器、图表、消费者、按钮配置
     */
    private DeviceProfile<FragmentMessageBinding> profile;

    /**
     * 消息列表：管理 RecyclerView 的数据源，负责追加显示和自动滚动
     */
    private MessageListController messageList;

    /**
     * 图表仓库：管理多个实时折线图，按 key 推送数据或重置
     */
    private ChartRegistry chartRegistry;

    /**
     * 解析器仓库：按顺序遍历已注册的解析器，第一个能解析的赢
     */
    private BluetoothSampleRegistry sampleRegistry;

    /**
     * 消费者仓库（广播模式）：把一个样本分发给所有已注册的消费者
     */
    private SampleConsumerRegistry sampleConsumers;

    /**
     * 录波器：将血糖样本以 JSONL 格式写入文件，支持开始/停止/导出
     */
    private SampleRecorder recorder;

    /**
     * 接收流水线：收到蓝牙字节 → 解码 → 解析 → 广播给消费者
     */
    private MessagePipelineController pipeline;

    /**
     * 发送器：接收命令字符串 → 编码 → 包装 → 交给 runtime 发送，并在消息列表显示
     */
    private MessageSender sender;

    /**
     * 按钮调度器：存储 viewId → ControlAction 的映射，按下按钮时查表执行
     */
    private ControlRegistry controls;

    /**
     * 累计收发字节数（显示在界面上）
     */
    private int readBytes;
    private int sentBytes;

    // ── 生命周期 ────────────────────────────────────────────────

    @Override
    protected void initChannels() {
        // 向 LiveEventBus 注册需要订阅的事件通道。
        // Fragment 会在对应通道有消息时收到回调（如 onBtData、onSentBytesChanged 等）。
        register(
                StaticConstants.CH_BT_DATA,          // 蓝牙收到数据
                StaticConstants.CH_SET_CONNECT_STATE, // 连接状态变化
                StaticConstants.CH_SENT_BYTES,        // 发送字节数变化
                StaticConstants.CH_STOP_LOOP_SEND     // 停止循环发送
        );
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        // 严格按依赖顺序初始化所有子模块。
        // 顺序原则：被依赖的先初始化。
        initRuntime(context);      // ① 基础中介
        initOptions();      // ② 用户设置（runtime 依赖 options）
        initProfile();      // ③ 设备档案（解析器先注册）
        initMessageList();  // ④ 消息列表
        initCharts();       // ⑤ 图表（依赖 profile 描述和 binding）
        initRecorder();     // ⑥ 录波器
        initPipeline();     // ⑦ 流水线（依赖 sender、messageList、sampleRegistry、sampleConsumers）
        initControls();     // ⑧ 按钮绑定（依赖 sender、profile）
        setBottomInfo("Ready");
        updateByteCounters();
    }

    // ── 初始化子模块（一）运行时中介 ──────────────────────────────

    /**
     * 创建运行时中介 FragmentRuntime。
     * <p>
     * 为什么要单独抽象一个 Runtime？
     * Fragment 有三个基础能力：sendDataToActivity()、toastShort()、logWarn()。
     * 如果直接把 Fragment 引用传给子模块（MessageSender、MessagePipelineController 等），
     * 会导致 Fragment 被长期持有，生命周期复杂，容易内存泄漏。
     * <p>
     * 所以把三个能力"提取"成三个 Lambda，传给 Runtime：
     * - commandSink → sendDataToActivity()：发蓝牙数据
     * - notifier   → toastShort()：弹 Toast
     * - logger     → logWarn()：打日志
     * <p>
     * 子模块只持有 Runtime，不知道 Fragment 存在，实现了解耦。
     */
    private void initRuntime(Context context) {
        runtime = new FragmentRuntime(
                context,
                new Storage(context),
                FragmentParameter.getInstance(),
                item -> sendDataToActivity(StaticConstants.CMD_SEND_BT_DATA, item), // 发蓝牙
                this::toastShort,  // Toast
                this::logWarn     // 日志
        );
    }

    // ── 初始化子模块（二）发送选项 ────────────────────────────────

    /**
     * 从持久化存储加载用户上次的发送设置。
     * 包括：HEX/字符串模式、换行符类型、编码格式、是否显示时间戳等。
     * 这些选项会影响 MessageSender 的编码行为和 MessagePipelineController 的解码行为。
     */
    private void initOptions() {
        optionStore = new MessageOptionStore(runtime);
        options = optionStore.load();
    }

    // ── 初始化子模块（三）设备档案 ───────────────────────────────

    /**
     * 创建 CGM 设备档案，并将解析器注册到 sampleRegistry。
     * <p>
     * 策略模式的体现：
     * profile = new CgmProfile()  → 换设备只需要换成对应的 Profile 实现
     * 其他所有代码（initPipeline、initControls 等）保持不变
     * <p>
     * registerSamples() 由 Profile 自己调用，把自己特有的解析器注册进去。
     * 这样 Fragment 不需要知道"CGM 用什么解析器"，Profile 自己知道。
     */
    private void initProfile() {
        profile = new EisProfile();
        sampleRegistry = new BluetoothSampleRegistry();
        sampleConsumers = new SampleConsumerRegistry();
        profile.registerSamples(sampleRegistry); // 把 CgmParser 注册进去
    }

    // ── 初始化子模块（四）消息列表 ───────────────────────────────

    /**
     * 初始化消息列表 RecyclerView。
     * messageList 负责：追加显示收到的蓝牙数据 / 发送的指令，并在列表底部自动滚动。
     * 本身不负责解析或格式化，只负责"展示数据"这件事。
     */
    private void initMessageList() {
        messageList = new MessageListController(
                requireContext(),
                viewBinding.recyclerMessage,
                R.layout.item_message_fragment
        );
    }

    // ── 初始化子模块（五）图表 ──────────────────────────────────

    /**
     * 初始化实时折线图，注册到图表仓库。
     * <p>
     * 初始化实时折线图，注册到图表仓库。
     * 然后调用 profile.registerCharts()，让 DeviceProfile 把自己的图表注册进来。
     * 注册结果：
     * EisProfile:  "eis"  → 绑定阻抗值
     */
    private void initCharts() {
        chartRegistry = new ChartRegistry();
        profile.registerCharts(chartRegistry, viewBinding);
    }

    // ── 初始化子模块（六）录波器 ────────────────────────────────

    /**
     * 初始化录波器。
     * <p>
     * 录波器的作用：将每次收到的血糖样本，以 JSON 行格式写入文件，
     * 方便后期导出和分析（每行一条 JSON，类似日志文件的格式）。
     * <p>
     * 回调说明：
     * onRecordStateChanged  → 录波开始/停止时，更新界面文字
     * onRecordExported      → 导出完成时，Toast 显示文件路径
     * onRecordError         → 写文件出错时，显示错误信息
     * <p>
     * 录波是在后台线程（单线程 ExecutorService）执行的，不阻塞主线程。
     */
    private void initRecorder() {
        recorder = new SampleRecorder(new SampleRecorder.Callback() {
            @Override
            public void onRecordStateChanged(boolean recording) {
                viewBinding.tvRecordState.setText(recording ? "Record: ON" : "Record: OFF");
                setBottomInfo(recording ? "Recording started" : "Recording stopped");
            }

            @Override
            public void onRecordExported(@NonNull String path) {
                setBottomInfo(path);
                toastShort(path);
            }

            @Override
            public void onRecordError(@NonNull String message) {
                setBottomInfo(message);
                logError(message);
            }
        });
    }

    // ── 初始化子模块（七）数据处理流水线 ────────────────────────

    /**
     * 初始化接收流水线。
     * <p>
     * 这是整个 Fragment 最核心的初始化：把之前创建的所有子模块组装在一起。
     * <p>
     * 步骤：
     * ① new MessageSender() — sender 依赖 runtime、messageList、options
     * ② new ProfileContext() — 把所有基础工具打包成一个上下文
     * ③ profile.registerConsumers() — 让 DeviceProfile 把消费者注册进来
     * ④ new MessagePipelineController() — 把流水线所需的依赖全部注入
     * <p>
     * 注意：ProfileContext 被创建了两次（initPipeline 和 initControls），
     * 这是因为 initPipeline 需要 sender（所以 sender 要先 new 出来），
     * 而 initControls 也需要 sender，所以两个初始化阶段都要有 context。
     */
    private void initPipeline() {
        // sender 依赖 runtime、messageList、options，此时三个都已就绪
        sender = new MessageSender(runtime, messageList, options);

        // 把所有子模块打包成上下文，传给 Profile
        UserRolePolicy rolePolicy = new UserRolePolicy((HomeApplication) requireActivity().getApplication());
        ProfileContext<FragmentMessageBinding> profileContext = new ProfileContext<>(
                viewBinding,    // 界面绑定
                runtime,       // 运行时中介
                chartRegistry, // 图表仓库
                sender,        // 发送器（刚 new 出来）
                recorder,      // 录波器
                rolePolicy     // 用户角色策略
        );

        // 让 DeviceProfile 把消费者注册进来
        // 注册后 sampleConsumers 包含：SampleChartBinder + EisRecorderConsumer
        profile.registerConsumers(sampleConsumers, profileContext);

        // 创建流水线核心
        // 参数顺序：context、messageList（显示收到的消息）、sampleRegistry（解析字节）、
        //          options（解码设置）、sampleConsumers（广播给消费者）、null（不用额外监听器）、日志
        pipeline = new MessagePipelineController(
                requireContext(),
                messageList,
                sampleRegistry,
                options,
                sampleConsumers,
                null,
                this::logWarn
        );
    }

    // ── 初始化子模块（八）按钮绑定 ───────────────────────────────

    /**
     * 初始化按钮调度器，绑定按钮 ID → 命令/动作。
     * <p>
     * 这里有两层绑定：
     * <p>
     * 第一层 — profile.registerControls()：
     * 由 DeviceProfile 描述"设备有哪几个按钮、按下去发什么命令"。
     * EisProfile 暂无专用按钮。
     * <p>
     * 第一层 — Profile 注册：
     * 这几个按钮不是 CGM 设备专用的，是所有设备共用的界面按钮（录波、导出等），
     * 所以直接在 FM 里绑定，不走 Profile。
     * btnStartRecord → startRecording()
     * btnStopRecord  → stopRecording()
     * btnExport      → exportRecording()
     * btnOptions     → refreshOptions()
     * <p>
     * 最终，所有按钮都会通过 bindOnClickListener() 注册到 Android 的点击监听器，
     * 点击时统一走到 onClickView() → controls.dispatch(view) → 找到对应的动作执行。
     */
    private void initControls() {
        controls = new ControlRegistry();
        UserRolePolicy rolePolicy = new UserRolePolicy((HomeApplication) requireActivity().getApplication());
        ProfileContext<FragmentMessageBinding> profileContext = new ProfileContext<>(
                viewBinding,
                runtime,
                chartRegistry,
                sender,
                recorder,
                rolePolicy
        );

        // 第一层：让 DeviceProfile 绑定设备专用的按钮
        profile.registerControls(controls, profileContext);

        // 第二层：FM 直接绑定共用的界面按钮（录波、导出、设置）
        controls
                .bind(viewBinding.btnStartRecord, this::startRecording)
                .bind(viewBinding.btnStopRecord, this::stopRecording)
                .bind(viewBinding.btnExport, this::exportRecording)
                .bind(viewBinding.btnOptions, this::refreshOptions);

        // 把所有按钮注册到 Android 点击监听器，事件统一到 onClickView()
        bindOnClickListener(
                viewBinding.btnStartMeasure,
                viewBinding.btnReadCache,
                viewBinding.btnDeleteCache,
                viewBinding.btnParams,
                viewBinding.btnStartRecord,
                viewBinding.btnStopRecord,
                viewBinding.btnExport,
                viewBinding.btnOptions
        );
    }

    // ── 按钮动作实现 ─────────────────────────────────────────────

    /**
     * 开始录波。
     * 先重置所有图表（清除历史数据，重新计时），
     * 然后让 recorder 开始接收样本。
     * 录波开始后，SampleChartBinder 的 gate 打开（recorder::isRecording == true），
     * 图表才会开始接收数据。
     */
    private void startRecording() {
        chartRegistry.resetAll();
        recorder.start(requireContext(), "message_cgm");
    }

    /**
     * 停止录波。
     * 停止后，recorder.isRecording() 返回 false，SampleChartBinder 的 gate 关闭，
     * 图表停止更新。
     */
    private void stopRecording() {
        recorder.stop();
        setBottomInfo("Samples: " + recorder.getSampleCount());
    }

    /**
     * 导出录波文件。
     * 触发录音文件路径的 Toast 提示（实际路径由 recorder.onRecordExported 回调返回）。
     */
    private void exportRecording() {
        recorder.exportPath();
    }

    /**
     * 刷新发送选项。
     * 从持久化存储重新加载用户设置，并同步到 sender 和 pipeline。
     * sender 更新后会按新的 HEX/字符串编码方式发送，
     * pipeline 更新后会按新的解码方式解析收到的数据。
     */
    private void refreshOptions() {
        options = optionStore.load();
        sender.updateOptions(options);
        pipeline.updateOptions(options);
        setBottomInfo("Options refreshed");
    }

    // ── 蓝牙生命周期回调 ─────────────────────────────────────────

    /**
     * 蓝牙连接成功。
     * 通知 runtime 记录当前连接的设备模块，
     * runtime.connected() 会返回 true，MessageSender 才能正常发送数据。
     */
    @Override
    protected void onBtConnected(DeviceModule module) {
        runtime.setModule(module);
        runtime.setConnected(true);
    }

    /**
     * 蓝牙断开连接。清除设备模块，sender 将无法发送数据。
     */
    @Override
    protected void onBtDisconnected() {
        runtime.setModule(null);
        runtime.setConnected(false);
    }

    /**
     * 连接状态文字变化时（如"已连接"），同步更新 runtime 的连接状态。
     */
    @Override
    protected void onConnectStateChanged(String state) {
        runtime.setConnected("已连接".equals(state));
    }

    /**
     * 收到蓝牙数据（核心接收路径入口）。
     * <p>
     * 完整链路：
     * onBtData(data.module, data.bytes)
     * → pipeline.onBtData(module, bytes)            // 交给流水线
     * → BluetoothPayloadDecoder.decodeResult()   // 字节 → 文本行
     * → messageList.appendIncomingText()        // 消息列表追加显示
     * → sampleRegistry.parse(line)              // 文本 → BluetoothSample
     * → sampleConsumers.consume(sample)         // 广播给所有消费者
     * ├→ CgmStatusConsumer        更新 tvStatus
     * ├→ CgmCurrentValueConsumer   更新 tvCurrentValue
     * ├→ CgmFileConsumer           缓存数据写文件
     * ├→ CgmRecorderConsumer       录波写 JSONL
     * └→ SampleChartBinder         指标推图表
     */
    @Override
    protected void onBtData(BTPackage.BTData data) {
        runtime.setModule(data.module);
        if (pipeline != null) {
            pipeline.onBtData(data.module, data.bytes);
            if (data.bytes != null) {
                readBytes += data.bytes.length;
                autoClearIfNeeded();
                updateByteCounters();
            }
        }
    }

    /**
     * 发送字节数变化，更新界面显示。
     */
    @Override
    protected void onSentBytesChanged(int number) {
        sentBytes += number;
        updateByteCounters();
    }

    /**
     * 收到停止循环发送的命令。
     */
    @Override
    protected void onStopLoopSend() {
        HoldBluetooth.getInstance().stopSend(runtime.module(), null);
    }

    // ── 按钮点击分发 ─────────────────────────────────────────────

    /**
     * 所有按钮的点击统一入口。
     * <p>
     * Android 点击事件 → onClickView(view)
     * → controls.dispatch(view)：用按钮的 viewId 查 Map，找对应的动作执行
     * 如果找到（CGM 专用按钮 / 共用界面按钮）：执行绑定的动作，返回 true
     * 如果没找到：打印警告日志
     * <p>
     * 这样设计的好处：
     * 新增设备类型时，不需要改动 FM 的 onClickView()，
     * 只需要在对应 Profile 的 registerControls() 里 bind 即可。
     */
    @Override
    protected void onClickView(View view) {
        if (controls != null && controls.dispatch(view)) {
            return;
        }
        logWarn("FragmentMessage unhandled click: " + view.getId());
    }

    @Override
    protected FragmentMessageBinding getViewBinding() {
        return FragmentMessageBinding.inflate(getLayoutInflater());
    }

    // ── 辅助方法 ────────────────────────────────────────────────

    /**
     * 在界面底部显示文本信息（如"Ready"、"Options refreshed"）。
     */
    private void setBottomInfo(@Nullable String text) {
        if (viewBinding != null) {
            viewBinding.tvBottomInfo.setText(text == null ? "" : text);
        }
    }

    /**
     * 更新收发字节计数器的显示。
     */
    private void updateByteCounters() {
        if (viewBinding != null) {
            viewBinding.tvByteCounters.setText("Read: " + readBytes + " B    Sent: " + sentBytes + " B");
        }
    }

    /**
     * 如果收到超过 AUTO_CLEAR_READ_BYTES 字节，自动清空消息列表。
     * 防止 RecyclerView 的 item 过多导致内存和渲染性能下降。
     */
    private void autoClearIfNeeded() {
        if (options != null && options.autoClear && readBytes > AUTO_CLEAR_READ_BYTES) {
            messageList.clear();
            readBytes = 0;
            setBottomInfo("Auto cleared");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 释放录波器的后台线程资源
        if (recorder != null) {
            recorder.release();
        }
        // 断开连接时，确保停止可能还在运行的循环发送
        HoldBluetooth.getInstance().stopSend(runtime == null ? null : runtime.module(), null);
    }
}
