# Chapter 2：入口 — FragmentMessage 生命周期

> 目标：理解 FragmentMessage 如何通过 9 个 init 方法把 Lego 块组装起来

---

## 2.1 整体初始化顺序

FragmentMessage 是一个**组装者**，它通过 `initAllImpl` 方法按顺序初始化各个组件：

```java
protected void initAllImpl(View view, Context context) {
    initRuntime(context);       // ① 建运行时环境
    initOptions();              // ② 建配置
    initProfile();              // ③ 建 Profile（Lego 入口）
    initMessageList();          // ④ 建 RecyclerView 封装
    initCharts();               // ⑤ 建图表注册表
    initRecorder();             // ⑥ 建录制器
    initPipeline();             // ⑦ 建管线大脑
    initControls();             // ⑧ 注册按钮事件
    setBottomInfo("Ready");
    updateByteCounters();
}
```

**为什么是这个顺序？** —— 依赖关系决定的：
1. Runtime 最先，因为其他组件都依赖它
2. Options 第二，因为 Pipeline 需要
3. Profile 第三，它定义了这个设备需要哪些 Lego
4. 其他组件按依赖链依次初始化

---

## 2.2 第一景：initRuntime

**作用：** 创建 Fragment 的"运行环境"

```java
private void initRuntime(Context context) {
    runtime = new FragmentRuntime(
            context,
            new Storage(context),
            FragmentParameter.getInstance(),
            item -> sendDataToActivity(StaticConstants.CMD_SEND_BT_DATA, item),  // CommandSink
            this::toastShort,  // Notifier
            this::logWarn      // Logger
    );
}
```

**FragmentRuntime 是什么？**

它是一个"瑞士军刀"——封装了 Fragment 需要用到的基础服务：

```java
public final class FragmentRuntime implements MessageOptionStore.FragmentRuntimeAccess {
    
    // 基础依赖
    private final Context context;
    private final Storage storage;
    private final FragmentParameter fragmentParameter;
    
    // 通信接口（三个回调）
    private final CommandSink commandSink;  // 发送数据到 Activity
    private final Notifier notifier;        // 弹 Toast
    private final Logger logger;            // 写日志
    
    // 状态
    private DeviceModule module;   // 当前连接的蓝牙设备
    private boolean connected;     // 连接状态
    
    // 三个核心方法
    public void sendBtData(@NonNull FragmentMessageItem item) { ... }
    public void toast(@NonNull String message) { ... }
    public void log(@NonNull String message) { ... }
}
```

**为什么要封装？**

原来 Fragment 直接调用 `toast()`、`sendData()`，现在通过 runtime：
- 方便测试（可以 mock runtime）
- 统一入口，日志/错误处理集中
- 依赖注入，不直接依赖 Context

---

## 2.3 第二景：initOptions

**作用：** 从 Storage 加载用户配置

```java
private void initOptions() {
    optionStore = new MessageOptionStore(runtime);
    options = optionStore.load();
}
```

**MessageOptions 是什么？**

一个**不可变配置对象**：

```java
public final class MessageOptions {
    public final boolean showOutgoing;   // 显示发送的消息
    public final boolean showTime;       // 显示时间戳
    public final boolean sendHex;        // 发送 HEX 格式
    public final boolean readHex;        // 读取 HEX 格式
    public final boolean autoClear;      // 自动清屏
    public final boolean sendNewline;    // 发送换行
    public final String codeFormat;      // 编码格式
}
```

**不可变的好处？**
- 线程安全
- 不会有意外的修改
- 可以放心传递

**MessageOptionStore 负责从 Storage 加载：**

```java
public MessageOptions load() {
    Storage storage = runtime.storage();
    String codeFormat = runtime.fragmentParameter().getCodeFormat(runtime.context());
    return new MessageOptions(
            storage.getData(PopWindowFragment.KEY_DATA),
            storage.getData(PopWindowFragment.KEY_TIME),
            storage.getData(PopWindowFragment.KEY_HEX_SEND),
            storage.getData(PopWindowFragment.KEY_HEX_READ),
            storage.getData(PopWindowFragment.KEY_CLEAR),
            storage.getData(PopWindowFragment.KEY_NEWLINE),
            codeFormat == null ? "" : codeFormat
    );
}
```

---

## 2.4 第三景：initProfile

**作用：** 创建设备 Profile（CGM 是第一个实现）

```java
private void initProfile() {
    profile = new CgmProfile();                      // ① 创建设备 Profile
    sampleRegistry = new BluetoothSampleRegistry();  // ② 创解析器注册表
    sampleConsumers = new SampleConsumerRegistry();   // ③ 创消费者注册表
    profile.registerSamples(sampleRegistry);          // ④ Profile 注册自己的解析器
}
```

**DeviceProfile 接口：**

```java
public interface DeviceProfile<B> {
    void registerSamples(@NonNull BluetoothSampleRegistry registry);
    void registerCharts(@NonNull ChartRegistry charts, @NonNull B binding);
    void registerConsumers(@NonNull SampleConsumerRegistry consumers, @NonNull ProfileContext<B> context);
    void registerControls(@NonNull ControlRegistry controls, @NonNull ProfileContext<B> context);
}
```

**为什么叫 Profile？**
- 它定义了"这个设备"需要哪些 Lego 块
- 换设备 = 换 Profile，不动 Fragment 代码

---

## 2.5 第四景：initMessageList

**作用：** 初始化 RecyclerView 的封装

```java
private void initMessageList() {
    messageList = new MessageListController(
            requireContext(),
            viewBinding.recyclerMessage,
            R.layout.item_message_fragment
    );
}
```

**MessageListController 封装了什么？**
- 维护数据列表
- 提供 `addIncomingText()`、`addOutgoingText()` 等方法
- 自动滚动到底部
- 自动 `notifyItemInserted()`

**Fragment 不再直接操作 adapter**，只调用 controller。

---

## 2.6 第五景：initCharts

**作用：** 初始化图表注册表，并让 Profile 注册自己的图表

```java
private void initCharts() {
    chartRegistry = new ChartRegistry();
    profile.registerCharts(chartRegistry, viewBinding);
}
```

**ChartRegistry 是什么？**

一个"图表仓库"，按名字索引：

```java
chartRegistry.register("cgm_primary", binding.chartPrimary, config);
chartRegistry.register("cgm_current", binding.chartCurrent, config);

// 之后通过名字访问
chartRegistry.get("cgm_primary").addPoint(value);
```

**谁定义了注册哪些图表？** — CgmProfile！

```java
// CgmProfile.registerCharts()
charts.register(CHART_PRIMARY, binding.chartPrimary, config)
     .register(CHART_CURRENT, binding.chartCurrent, config);
```

---

## 2.7 第六景：initRecorder

**作用：** 创建录制器（用于录制样本到文件）

```java
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
```

**SampleRecorder 做什么？**
- `start()` 开始录制
- `stop()` 停止录制
- `addSample(sample)` 添加样本
- `exportPath()` 导出到文件

**回调的作用？**
- 录制状态变化时更新 UI
- 导出完成时显示路径
- 出错时显示错误

---

## 2.8 第七景：initPipeline（核心）

**作用：** 创建管线大脑，连接所有组件

```java
private void initPipeline() {
    sender = new MessageSender(runtime, messageList, options);  // ① 发消息
    
    // ② 建 ProfileContext（运行时依赖容器）
    UserRolePolicy rolePolicy = new UserRolePolicy((HomeApplication) requireActivity().getApplication());
    ProfileContext<FragmentMessageBinding> profileContext = new ProfileContext<>(
            viewBinding,    // UI binding
            runtime,        // 运行环境
            chartRegistry,  // 图表
            sender,        // 发消息
            recorder,      // 录制器
            rolePolicy     // 权限
    );
    
    // ③ Profile 注册消费者
    profile.registerConsumers(sampleConsumers, profileContext);
    
    // ④ 建管线控制器
    pipeline = new MessagePipelineController(
            requireContext(),
            messageList,
            sampleRegistry,
            options,
            sampleConsumers,
            null,  // sampleListener（不需要）
            this::logWarn
    );
}
```

**ProfileContext 为什么存在？**

它是"依赖容器"，把消费者需要的依赖都打包在一起：

```java
public final class ProfileContext<B> {
    public final B binding;           // UI 绑定
    public final FragmentRuntime runtime;
    public final ChartRegistry charts;
    public final MessageSender sender;
    public final SampleRecorder recorder;
    public final UserRolePolicy rolePolicy;
}
```

**然后 CgmProfile 用这些依赖注册消费者：**

```java
// CgmProfile.registerConsumers()
consumers
    .register(new CgmStatusConsumer(context.binding.tvStatus))
    .register(new CgmCurrentValueConsumer(context.binding.tvCurrentValue))
    .register(new CgmFileConsumer(context.runtime))
    .register(new CgmRecorderConsumer(context.runtime, context.recorder))
    .register(new SampleChartBinder(context.charts, context.recorder::isRecording)
        .bind(CgmSample.METRIC_PRIMARY, CHART_PRIMARY)
        .bind(CgmSample.METRIC_CURRENT, CHART_CURRENT));
```

---

## 2.9 第八景：initControls

**作用：** 注册按钮事件

```java
private void initControls() {
    controls = new ControlRegistry();
    
    // ① Profile 注册设备相关按钮
    UserRolePolicy rolePolicy = new UserRolePolicy((HomeApplication) requireActivity().getApplication());
    ProfileContext<FragmentMessageBinding> profileContext = new ProfileContext<>(...);
    profile.registerControls(controls, profileContext);
    
    // ② Fragment 注册自己的通用按钮
    controls
        .bind(viewBinding.btnStartRecord, this::startRecording)
        .bind(viewBinding.btnStopRecord, this::stopRecording)
        .bind(viewBinding.btnExport, this::exportRecording)
        .bind(viewBinding.btnOptions, this::refreshOptions);
    
    // ③ 绑定点击监听
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
```

**ControlRegistry 是什么？**

一个"按钮事件表"：

```java
public final class ControlRegistry {
    private final Map<Integer, ControlAction> actions = new LinkedHashMap<>();
    
    public ControlRegistry bind(@NonNull View view, @NonNull ControlAction action) {
        actions.put(view.getId(), action);
        return this;
    }
    
    public boolean dispatch(@NonNull View view) {
        ControlAction action = actions.get(view.getId());
        if (action == null) return false;
        action.run();
        return true;
    }
}
```

**点击分发流程：**

```
onClickView(view)
    ↓
controls.dispatch(view)  // 按 view.getId() 找 action
    ↓
action.run()              // 执行对应的动作
```

**CgmProfile 注册了哪些按钮？**

```java
// CgmProfile.registerControls()
controls
    .bind(context.binding.btnStartMeasure, () -> context.sender.send(CgmCommandSet.startNow()))
    .bind(context.binding.btnReadCache, () -> context.sender.send(CgmCommandSet.readCache()))
    .bind(context.binding.btnDeleteCache, () -> context.sender.send(CgmCommandSet.deleteCache()))
    .bind(context.binding.btnParams, () -> CgmParameterDialog.show(...));
```

---

## 2.10 数据流：蓝牙数据进来

```java
@Override
protected void onBtData(BTPackage.BTData data) {
    runtime.setModule(data.module);
    if (pipeline != null) {
        pipeline.onBtData(data.module, data.bytes);  // 扔给管线
        if (data.bytes != null) {
            readBytes += data.bytes.length;
            autoClearIfNeeded();
            updateByteCounters();
        }
    }
}
```

**管线处理流程（MessagePipelineController）：**

```
onBtData(bytes)
    ↓
BluetoothPayloadDecoder.decode(bytes)  // 字节 → 文本
    ↓
handleLine(text)                       // 处理一行
    ↓
messageList.appendIncomingText(text)   // 显示到列表
    ↓
sampleRegistry.parse(text)            // 解析成 CgmSample
    ↓
sampleConsumers.consume(sample)        // 分发给所有消费者
    ↓
    ├── CgmStatusConsumer      → 更新状态 UI
    ├── CgmCurrentValueConsumer → 更新数值 UI
    ├── CgmRecorderConsumer   → 写入文件（如果正在录制）
    ├── CgmFileConsumer        → 缓存处理
    └── SampleChartBinder      → 更新图表
```

---

## 2.11 数据流：按钮点击进来

```java
@Override
protected void onClickView(View view) {
    if (controls != null && controls.dispatch(view)) {
        return;  // 找到就处理完了
    }
    logWarn("FragmentMessage unhandled click: " + view.getId());
}
```

**事件分发流程：**

```
onClickView(btnStartMeasure)
    ↓
controls.dispatch(btnStartMeasure)  // 找按钮 ID
    ↓
CgmProfile 注册的 action.run()
    ↓
context.sender.send(CgmCommandSet.startNow())  // 发送命令
```

---

## 2.12 总结：FragmentMessage 的角色

| 原来 | 现在 |
|------|------|
| 直接写业务逻辑 | 只负责组装 |
| 130+ 字段 | 9 个核心字段 |
| 直接调用 Chart、Dialog、IO | 通过 Consumer 接口 |
| CGM 逻辑硬编码 | Profile 抽象 |

**FragmentMessage 现在做的事：**
1. 创建依赖对象
2. 按顺序初始化
3. 处理 UI 回调（`onClickView`、`onBtData`）
4. 生命周期管理（`onDestroy`）

**FragmentMessage 不做的事：**
- 不解析蓝牙数据
- 不更新图表
- 不发送命令
- 不写文件

---

## 2.13 下一景：Chapter 3

我们将深入看 **activity/tool/** 目录下的通用 Lego 块，理解每个组件的职责和设计。
