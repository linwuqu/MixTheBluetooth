# FragmentMessage Profile 统一设计说明

## 1. 背景

当前工程已经完成了几条重要边界的拆分：

- chart 相关能力已经形成 `ChartRegistry`、`RealtimeLineChart`、`SampleChartBinder`。
- message 相关能力已经形成 `BluetoothPayloadDecoder`、`MessageItemTools`、`MessageListController`、`MessagePipelineController`。
- sample 相关能力已经形成 `BluetoothSample`、`BluetoothSampleParser`、`BluetoothSampleRegistry`、`SampleRecorder`。
- EIS 设备/协议数据已经移动到 `schema/eis`，表达为 `EisSample`、`EisParser`、`EisJsonLineBuilder`。

这些边界说明当前项目已经不再适合继续把设备逻辑写在 Fragment 里。`FragmentMessageNew` 已经表现出一个更好的样板：Fragment 只负责初始化列表、图表、记录器、pipeline 和按钮，设备数据通过 parser registry 进入统一管线。

但是 `FragmentMessage` 仍然是历史遗留形态。它同时负责：

- RecyclerView 列表和 adapter。
- Bluetooth bytes 解码。
- 发送消息构造。
- 读取/发送字节计数。
- 发送选项读取。
- 用户偏好读取。
- 管理员/普通用户权限控制。
- 参数弹窗。
- `TIME / ALL / DELETE` 命令。
- `Start Playback / Playback all done` 缓存回放。
- `CA / EIS / RI / CA:266` 文本解析。
- 图表绘制。
- 当前值显示。
- 文件写入。

这使得 `FragmentMessage` 很难复用，也很难迁移到“注册式设备能力”的新样式。

本设计的核心目标不是简单把旧逻辑包进 `LegacyProcessor`，而是让 `FragmentMessage` 最终和 `FragmentMessageNew` 使用同一套设计语言：Fragment 负责搭管线，设备差异通过 Profile 注册表达。

---

## 2. 总目标

把 `FragmentMessage` 从“巨型业务 Fragment”改造成“Profile 驱动的消息页面宿主”。

最终 `FragmentMessage` 应接近下面的形态：

```java
public class FragmentMessage extends BTFragment<FragmentMessageBinding> {

    private FragmentRuntime runtime;
    private MessageOptions options;
    private MessageOptionStore optionStore;
    private DeviceProfile profile;
    private MessageListController messageList;
    private ChartRegistry chartRegistry;
    private SampleRecorder recorder;
    private MessagePipelineController pipeline;
    private ControlRegistry controls;

    @Override
    protected void initAllImpl(View view, Context context) {
        initRuntime(context);
        initOptions();
        initProfile();
        initMessageList();
        initCharts();
        initRecorder();
        initPipeline();
        initControls();
    }

    @Override
    protected void onBtData(BTPackage.BTData data) {
        runtime.setModule(data.module);
        pipeline.onBtData(data.module, data.bytes);
    }

    @Override
    protected void onClickView(View view) {
        controls.dispatch(view);
    }
}
```

这里最重要的是：`FragmentMessage` 不再直接知道设备文本格式，也不再直接写 `CA / EIS / RI / TIME / ALL / DELETE`。

---

## 3. 非目标

这次设计不追求一次性删除所有旧 UI 和旧业务行为。

这些事情不作为第一阶段目标：

- 不要求立刻删除 `FragmentMessageNew`。
- 不要求立刻把所有 Fragment 都迁移到同一套 Profile。
- 不要求立刻把 `Analysis` 全部拆干净。
- 不要求立刻把旧 FM 的每一个 UI 元素完全重设计。
- 不要求 `CommunicationActivity` 感知设备 Profile。
- 不要求每个设备都已经有完整 schema。

第一阶段只要求：`FragmentMessage` 的主结构、数据管线、图表、记录、控制、选项、设备差异表达方式，全部向 Profile 注册式样板靠拢。

---

## 4. 设计原则

### 4.1 Fragment 只负责装配

Fragment 不应该负责具体协议。

Fragment 可以知道：

- 它使用哪个布局。
- 它有哪个 RecyclerView。
- 它有哪个图表容器。
- 它有哪个按钮区域。
- 它选择哪个 `DeviceProfile`。
- 它如何把 `BTFragment` 回调交给 pipeline。

Fragment 不应该知道：

- `CA:266` 是什么。
- `RI` 表示什么。
- `EIS` 的第几个字段该画图。
- 缓存回放该写哪个文件。
- 参数命令如何拼接。

### 4.2 设备差异通过 Profile 注册

不同设备、不同协议、不同参数，不应该靠复制 Fragment 实现。

理想表达是：

```java
profile.registerParsers(sampleRegistry);
profile.registerCharts(chartRegistry, binding);
profile.registerControls(controlRegistry);
profile.registerCommands(commandRegistry);
profile.registerConsumers(sampleConsumers);
```

也就是说，面对不同设备时，变化点应该集中在 `schema/<device>` 和 `profile`，而不是散落在 Fragment、Activity、Adapter、Chart 工具里。

### 4.3 Sample 是管线中间结果

管线应该形成稳定流向：

```text
bytes
  -> BluetoothPayloadDecoder
  -> raw text
  -> MessageListController 显示原始文本
  -> BluetoothSampleRegistry
  -> BluetoothSample
  -> SampleConsumer 列表
```

一个 sample 出来以后，可以被多个 consumer 消费：

- 图表 consumer。
- 状态栏 consumer。
- 当前值 consumer。
- 文件导出 consumer。
- 记录器 consumer。

这样图表、状态、记录、导出都不需要写进 Fragment。

### 4.4 用户偏好是页面样式的一部分

旧 FM 有很多 FMNew 没覆盖的能力，其中一部分是通用消息页面能力：

- 是否显示自己发送的数据。
- 是否显示时间。
- 发送是否按 Hex。
- 接收是否按 Hex。
- 是否自动清理消息。
- 是否自动追加换行。
- 当前编码格式。

这些不是 CGM 专属逻辑，也不是 Fragment 私有逻辑。它们应该进入统一的 message page 样式：

```text
Storage / FragmentParameter
  -> MessageOptionStore
  -> MessageOptions
  -> MessagePipelineController / MessageSender / MessageListController
```

### 4.5 权限控制是策略，不是 Fragment if-else

旧 FM 中 `initLimit()` 根据普通用户/管理员决定参数是否可编辑。这也是可复用能力。

它应该变成：

```text
UserRolePolicy
  -> apply(ParameterForm)
```

这样以后某个设备有参数区域，也可以复用角色策略，而不是每个 Fragment 重新写一遍。

---

## 5. 核心组件

### 5.1 FragmentRuntime

`FragmentRuntime` 是每个消息 Fragment 的运行环境。

它负责提供：

- `Context`
- 当前 `DeviceModule`
- 当前连接状态
- `Storage`
- `FragmentParameter`
- 日志能力
- toast 能力
- Activity command 发送能力

建议接口：

```java
public final class FragmentRuntime {
    Context context();
    Storage storage();
    FragmentParameter fragmentParameter();
    DeviceModule module();
    boolean connected();

    void setModule(DeviceModule module);
    void setConnected(boolean connected);

    void sendBtData(FragmentMessageItem item);
    void toast(String message);
    void log(String message);
}
```

它的意义是：Fragment 不再把 `Storage`、`FragmentParameter`、`sendDataToActivity(...)`、`toastShort(...)` 到处传。

---

### 5.2 MessageOptions

`MessageOptions` 是消息页面的显示、解码、发送选项。

建议字段：

```java
public final class MessageOptions {
    public final boolean showOutgoing;
    public final boolean showTime;
    public final boolean sendHex;
    public final boolean readHex;
    public final boolean autoClear;
    public final boolean sendNewline;
    public final String codeFormat;
}
```

这些字段来自旧 FM 的：

```text
isShowMyData
isShowTime
isSendHex
isReadHex
isAutoClear
isSendNewline
mFragmentParameter.getCodeFormat(...)
```

之后 pipeline、sender、message list 都使用 `MessageOptions`，而不是直接读 Fragment 字段。

---

### 5.3 MessageOptionStore

`MessageOptionStore` 负责从 `Storage` 和 `FragmentParameter` 读取/保存选项。

建议职责：

```java
public final class MessageOptionStore {
    MessageOptions load();
    void refresh();
}
```

它会读取：

- `PopWindowFragment.KEY_DATA`
- `PopWindowFragment.KEY_TIME`
- `PopWindowFragment.KEY_HEX_SEND`
- `PopWindowFragment.KEY_HEX_READ`
- `PopWindowFragment.KEY_CLEAR`
- `PopWindowFragment.KEY_NEWLINE`
- `FragmentParameter.getCodeFormat(context)`

这样旧 FM 的 `setListState()` 可以迁移为：

```java
options = optionStore.load();
pipeline.updateOptions(options);
sender.updateOptions(options);
```

---

### 5.4 DeviceProfile

`DeviceProfile` 是本设计的核心。

它代表一种设备或协议页面需要注册的能力。

建议接口：

```java
public interface DeviceProfile<B> {
    void registerSamples(BluetoothSampleRegistry registry);
    void registerCharts(ChartRegistry chartRegistry, B binding);
    void registerControls(ControlRegistry controls, B binding);
    void registerConsumers(SampleConsumerRegistry consumers, ProfileContext context);
}
```

其中 `B` 是 ViewBinding 类型，例如：

```java
DeviceProfile<FragmentMessageBinding>
```

不同设备只需要实现不同 Profile。

---

### 5.5 ProfileContext

`ProfileContext` 是 Profile 注册时可用的上下文。

建议包含：

- `FragmentRuntime`
- `MessageOptions`
- `SampleRecorder`
- `ChartRegistry`
- `MessageSender`
- `FileExporter`
- `UserRolePolicy`

Profile 不应该直接持有 Fragment。它只能通过 context 使用必要能力。

---

### 5.6 ControlRegistry

`ControlRegistry` 负责按钮和行为绑定。

旧 FM 的按钮逻辑：

```text
sendMessageFragment -> 参数弹窗 / 参数提交
sentStartFlag -> TIME
getAllData -> ALL
deleteCacheData -> DELETE
pullMessageFragment -> 选项弹窗
```

应该变成注册：

```java
controls.bind(binding.sentStartFlag, profile.commands().start());
controls.bind(binding.getAllData, profile.commands().readCache());
controls.bind(binding.deleteCacheData, profile.commands().deleteCache());
controls.bind(binding.sendMessageFragment, profile.commands().submitParams());
controls.bind(binding.pullMessageFragment, commonControls.openMessageOptions());
```

Fragment 的 `onClickView` 只需要：

```java
controls.dispatch(view);
```

---

### 5.7 MessageSender

`MessageSender` 负责把文本命令转成 `FragmentMessageItem` 并发送给 Activity。

它使用：

- `FragmentRuntime`
- `MessageOptions`
- `MessageItemTools`
- `Analysis.getBytes(...)`

它处理：

- 未连接不可发送。
- 当前 module 为空不可发送。
- 空命令不可发送。
- Hex 发送。
- 自动追加换行。
- 是否显示自己发送的数据。

Fragment 不再自己写 `canSend()`、`prepareSendText()`、`createOutgoingItem()`。

---

### 5.8 SampleConsumerRegistry

sample 出来以后，不应该只交给一个 listener。

建议：

```java
public final class SampleConsumerRegistry {
    void register(SampleConsumer consumer);
    void consume(BluetoothSample sample);
}
```

Consumer 可以包括：

- `SampleChartBinder`
- `SampleRecorderConsumer`
- `SampleStatusBinder`
- `SampleCurrentValueBinder`
- `SampleFileExporter`

这样可以表达：

```text
sample -> 图表
sample -> 记录
sample -> 状态文本
sample -> 当前值
sample -> 文件
```

---

### 5.9 UserRolePolicy

旧 FM 的管理员/普通用户逻辑不应该留在 Fragment。

建议：

```java
public final class UserRolePolicy {
    boolean canEditParameters();
    void apply(ParameterForm form);
}
```

它从 `HomeApplication.getLimits()` 读取角色。

CGM 参数表单注册时调用：

```java
rolePolicy.apply(parameterForm);
```

---

## 6. CGM Profile 设计

旧 FM 实际包含一个 CGM 风格协议，虽然代码中同时出现 `CA`、`EIS`、`RI`。

建议新增：

```text
schema/cgm/
  CgmSample
  CgmParser
  CgmCommandSet
  CgmJsonLineBuilder
  CgmProfile
```

### 6.1 CgmSample

CGM sample 不一定只有一个指标。可以先支持这些类型：

```text
CA primary point
EIS primary point
RI status
current value from CA:266
cache playback text
```

建议字段：

```java
public final class CgmSample implements BluetoothSample {
    public static final String TYPE = "cgm";
    public static final String METRIC_PRIMARY = "primary";
    public static final String METRIC_CURRENT = "current";

    public final String event;
    public final String status;
    public final String raw;
    public final Map<String, Float> metrics;
}
```

其中 `event` 可以是：

```text
CA
EIS
RI
CACHE_START
CACHE_LINE
CACHE_DONE
CURRENT
```

### 6.2 CgmParser

旧逻辑迁移方向：

```text
Start Playback -> CgmSample(event=CACHE_START)
Playback all done -> CgmSample(event=CACHE_DONE)
EIS:x,y,z -> CgmSample(event=EIS, primary=z)
CA:i,v -> CgmSample(event=CA, primary=v)
CA:266,v -> CgmSample(event=CURRENT, primary=v, current=v)
RI... -> CgmSample(event=RI, status=raw)
```

这里要注意：旧 FM 中 `EIS` 和 `CA` 都更新主图，`CA:266` 更新 current 图和文件。

### 6.3 CgmCommandSet

旧 FM 命令迁移：

```java
public final class CgmCommandSet {
    String startWithCurrentTime();
    String readCache();
    String deleteCache();
    String buildParameters(CgmParameters params);
}
```

对应旧命令：

```text
TIME,yyyy,MM,dd,HH,mm,ss\n\r
ALL\n\r
DELETE\n\r
参数拼接字符串
```

### 6.4 CgmProfile

`CgmProfile` 负责注册：

- `CgmParser`
- 主图 chart
- current chart
- 状态文本 consumer
- current value consumer
- 文件写入 consumer
- CGM 按钮命令
- 参数表单
- 角色权限策略

理想形态：

```java
public final class CgmProfile implements DeviceProfile<FragmentMessageBinding> {
    @Override
    public void registerSamples(BluetoothSampleRegistry registry) {
        registry.register(new CgmParser());
    }

    @Override
    public void registerCharts(ChartRegistry charts, FragmentMessageBinding binding) {
        charts.register("primary", binding.chartPrimary, primaryConfig());
        charts.register("current", binding.chartCurrent, currentConfig());
    }

    @Override
    public void registerConsumers(SampleConsumerRegistry consumers, ProfileContext context) {
        consumers.register(new SampleChartBinder(context.chartRegistry())
                .bind(CgmSample.METRIC_PRIMARY, "primary")
                .bind(CgmSample.METRIC_CURRENT, "current"));
        consumers.register(new CgmStatusBinder(context.binding()));
        consumers.register(new CgmFileExporter(context.runtime()));
    }

    @Override
    public void registerControls(ControlRegistry controls, FragmentMessageBinding binding) {
        controls.bind(binding.sentStartFlag, () -> sender.send(commandSet.startWithCurrentTime()));
        controls.bind(binding.getAllData, () -> sender.send(commandSet.readCache()));
        controls.bind(binding.deleteCacheData, () -> sender.send(commandSet.deleteCache()));
        controls.bind(binding.sendMessageFragment, () -> showParameterForm());
    }
}
```

---

## 7. FragmentMessage UI 方向

用户已确认：允许 `FragmentMessage` 的界面结构向 `FragmentMessageNew` 靠拢。

新版 FM UI 应该保留旧功能，但重新组织：

```text
顶部 controls 区
  Start
  Read Cache
  Delete Cache
  Params
  Record Start
  Record Stop
  Export
  Options

状态区
  connection/status text
  read bytes
  sent bytes
  record state
  current value

图表区
  primary chart
  current chart

消息区
  RecyclerView 原始消息列表

底部信息区
  export path / warning / ready
```

相比旧布局，主要变化是：

- 不再使用小尺寸固定 `330dp` 卡片式布局。
- 图表成为第一等区域。
- controls 以统一按钮区呈现。
- message list 和 chart 同时存在，但职责清晰。
- 参数弹窗可以先保留，但触发和命令构造交给 `CgmProfile`。

---

## 8. 数据流

### 8.1 接收数据

```text
BTFragment.onBtData
  -> FragmentMessage.pipeline.onBtData(module, bytes)
  -> BluetoothPayloadDecoder.decodeResult(options)
  -> MessageListController.addIncomingText(rawText)
  -> BluetoothSampleRegistry.parse(rawText)
  -> SampleConsumerRegistry.consume(sample)
       -> chart
       -> recorder
       -> status
       -> current value
       -> file exporter
```

### 8.2 发送命令

```text
Button click
  -> ControlRegistry.dispatch(view)
  -> CgmCommandSet builds text command
  -> MessageSender.send(command)
  -> MessageItemTools.outgoing(...)
  -> FragmentRuntime.sendBtData(item)
  -> CommunicationActivity sends through HoldBluetooth
```

### 8.3 选项变化

```text
Options button
  -> old PopWindowFragment or new options panel
  -> Storage updated
  -> MessageOptionStore.load()
  -> pipeline.updateOptions(options)
  -> sender.updateOptions(options)
  -> messageList.updateOptions(options)
```

---

## 9. Recorder 行为

`FragmentMessageNew` 当前已经调整为：只有 recording 开启时，sample 才进入图表和记录。

新版 FM 也应采用同样语义：

```text
recording OFF:
  原始消息列表继续显示
  sample 可以更新状态提示
  图表不追加数据点
  文件记录不追加样本

recording ON:
  sample 追加图表
  sample 追加 recorder
  sample 可以导出
```

如果 CGM 有“缓存回放必须写文件”的特殊需求，应通过 `CgmProfile` 注册一个明确的 `CgmCacheExporter`，不要让 Fragment 自己写文件。

---

## 10. 迁移策略

这次不再采用“把旧逻辑包进 LegacyProcessor 就结束”的策略。

建议分阶段：

### 阶段一：建立通用骨架

- `FragmentRuntime`
- `MessageOptions`
- `MessageOptionStore`
- `MessageSender`
- `ControlRegistry`
- `SampleConsumerRegistry`
- `DeviceProfile`
- `ProfileContext`

这一阶段先不大改 UI，只让 `FragmentMessage` 能使用这些组件。

### 阶段二：建立 CGM schema/profile

- `CgmSample`
- `CgmParser`
- `CgmCommandSet`
- `CgmProfile`
- `CgmStatusBinder`
- `CgmFileExporter`

这一阶段把 `CA / EIS / RI / TIME / ALL / DELETE / CA:266` 从 Fragment 移走。

### 阶段三：重塑 FragmentMessage

- `FragmentMessage` 改成类似 `FragmentMessageNew` 的初始化顺序。
- `onBtData` 只转发给 pipeline。
- `onClickView` 只转发给 controls。
- 删除直接 adapter/list mutation。
- 删除直接 chart 绘制。
- 删除直接 file writing。
- 删除直接 command building。

### 阶段四：重塑 UI

- 让 `fragment_message.xml` 向 `fragment_message_new.xml` 靠拢。
- 保留 CGM 所需状态区、current 值、参数入口。
- 图表、记录、导出、消息列表使用统一布局语言。

### 阶段五：让 FMNew 也吃到统一骨架

当 FM 完成后，反过来把 FMNew 也接入 `FragmentRuntime`、`MessageOptions`、`DeviceProfile`。

这时 FM 和 FMNew 的差异会变成：

```text
FragmentMessage -> CgmProfile
FragmentMessageNew -> EisProfile
```

---

## 11. 成功标准

### 11.1 代码结构

`FragmentMessage` 中不再出现：

```text
CA:
EIS
RI
Start Playback
Playback all done
TIME,
ALL
DELETE
Analysis.IO_input_data(...)
getLineChart(...)
getLineBloodChart(...)
mAdapter.notifyDataSetChanged()
mDataList.add(...)
```

这些逻辑应迁移到 profile、schema、sender、consumer 或 controller 中。

### 11.2 Fragment 体量

`FragmentMessage` 不要求第一阶段立刻少于 50 行，但最终应接近：

```text
字段
initChannels
initAllImpl
onBt callbacks
onClickView
getViewBinding
onDestroy
```

理想行数应控制在 80 到 150 行内。

### 11.3 扩展方式

新增设备时，不应该复制一个巨型 Fragment。

新增设备应该主要新增：

```text
schema/<device>/*
<Device>Profile
必要的 layout binding 注册
```

### 11.4 行为

旧 FM 的关键行为不能丢：

- 原始消息列表显示。
- 读/发字节计数。
- start 命令。
- read cache 命令。
- delete cache 命令。
- 参数提交。
- 普通用户/管理员参数权限。
- CGM 主图。
- current 图。
- current 值显示。
- cache 文件输出。
- sample 记录和导出。

---

## 12. 旧计划处理

旧计划：

```text
docs/superpowers/plans/2026-05-04-fragment-message-pipeline-migration.md
```

方向偏保守，只适合“把 legacy 逻辑包进边界”，不适合当前目标。

新版实施计划应另起文件，旧计划应标记为 superseded 或删除，避免后续施工误用。

---

## 13. 风险与约束

### 13.1 风险：一次性改太多导致行为不可验证

应对方式：

- 先建立骨架。
- 再迁移 CGM parser。
- 再迁移 command。
- 再迁移 chart/file/status consumer。
- 每阶段都跑编译。

### 13.2 风险：CGM 协议理解不完整

应对方式：

- 第一版 `CgmParser` 严格复刻旧 FM 的判断逻辑。
- 不擅自改 `CA / EIS / RI / CA:266` 语义。
- 后续再做协议命名和字段校正。

### 13.3 风险：UI 重塑影响用户操作习惯

应对方式：

- 功能入口保留。
- 名称可以更清晰。
- 参数弹窗第一阶段可以继续使用旧 `parameter_setting.xml`。
- 布局向 FMNew 靠拢，但不强迫一次性视觉重做。

### 13.4 风险：Storage 和 FragmentParameter 继续到处传

应对方式：

- 所有新 controller 都从 `FragmentRuntime` 或 `MessageOptions` 取依赖。
- 新代码不直接依赖 Fragment 字段。

---

## 14. 推荐结论

采用 Profile 驱动迁移。

不要继续沿用旧计划的 `LegacyMessagePipelineController / LegacyCgmMessageProcessor` 作为最终目标。可以在实现中短暂使用过渡类，但最终形态必须是：

```text
FragmentMessage
  -> FragmentRuntime
  -> MessageOptions
  -> CgmProfile
  -> MessagePipelineController
  -> SampleConsumerRegistry
```

这样 `FragmentMessage` 才能真正和 `FragmentMessageNew` 站到同一套设计语言里。

