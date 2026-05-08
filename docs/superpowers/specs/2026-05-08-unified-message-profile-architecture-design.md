# Unified Message/Profile 架构设计

日期：2026-05-08

## 目标

建立一套小而精、可扩展的通信页架构：

- `UnifiedMessageFragment` 是 Android 页面壳。
- `MessageController` 是策略集合执行器。
- `ProfileSpec` 是设备协议说明书。
- `CommunicationActivity` 继续作为蓝牙通信通道。
- 设备 profile 不认识 `ViewBinding`、`Fragment`、`Activity`，也不认识具体的 `Button`、`TextView`、`LineChart`。
- 实现不能拆得很散。第一版要尽量把核心概念收拢在少量文件里。

这个设计要替代当前的 `DeviceProfile<B>` 模式。当前模式的问题是 profile 直接接收具体 binding，导致 `EisProfile`、`EisProfileNew` 和未来 profile 都被迫知道页面布局细节。

## 文件数量硬约束

第一版实现应尽量控制在 5 个自有源文件/核心类以内。

推荐结构：

1. `UnifiedMessageFragment.java`
   - public `UnifiedMessageFragment`
   - 嵌套类或 package-private `MessageController`
   - 嵌套类或 package-private `ProfileSpec`
   - 嵌套类或 package-private 小结构：`Route`、`ActionSpec`、`ChartSpec`、`IndicatorSpec`
2. `Profiles.java` 或现有 profile 文件
   - 放扁平化 profile 定义，例如 `eis()`、`cgm()`，或者第一阶段只迁移一个 profile
3. `BluetoothSample.java`
   - 如果现有接口可用，就继续保留
4. `RealtimeLineChart.java`
   - 继续作为第一版折线图渲染器
5. 必要时增加一个紧凑测试文件或小型支持文件

不要为每个 registry、action、route、renderer、gateway、context 单独创建文件。除非某个文件确实有存在价值，否则不要拆。设计语言要小、集中、容易读。

## 概念模型

架构分成四个角色：

```text
Profile
  说明设备协议能做什么。

Controller
  把 profile spec 变成运行时 UI、动作、解析器和消费者。

Fragment
  负责 Android 生命周期、binding 容器、通信出口。

Activity
  负责实际蓝牙发送和接收。
```

依赖方向必须是：

```text
UnifiedMessageFragment -> MessageController -> ProfileSpec
UnifiedMessageFragment -> CommunicationActivity，通过现有事件总线通信
```

不能变成：

```text
Profile -> FragmentBinding
Profile -> Activity
Profile -> 具体 Button/TextView/LineChart
```

## Route 模型

动作和事件应该按“方向”分类，而不是按模糊功能分类。

```java
enum Route {
    POST,       // Controller/Fragment -> Activity -> Bluetooth device
    SUBSCRIBE,  // Activity/Bluetooth -> Fragment/Controller
    INNER       // Controller 内部动作，不经过 Activity 或设备
}
```

`POST` 示例：

- 向设备发送 `"ALL\n\r"`。
- 向设备发送 `"DELETE\n\r"`。
- 向设备发送 `"TIME,yyyy,MM,dd,HH,mm,ss\n\r"`。
- 未来如果某设备需要二进制命令，也可以发送 bytes。

`SUBSCRIBE` 示例：

- 接收 `CH_BT_DATA`。
- 接收连接状态。
- 接收已发送字节数。

`INNER` 示例：

- 开始本地记录。
- 停止本地记录。
- 导出本地记录。
- 清空消息列表。
- 重置图表。
- 打开本地参数弹窗。

重要区别：

- `START` 如果存在，表示某个 profile 自己定义的设备命令。
- `START_RECORD` 表示 App 开始把解析后的 sample 保存到本地。

统一 Fragment 不能硬编码旧 CGM 命令，例如 `ALL`、`DELETE`、`TIME`。这些只属于需要它们的 profile。

## 信道收敛策略

当前项目里 `CH_*`、`CMD_*`、`EV_*` 信道较多，历史语义混杂，阅读和迁移成本都高。Unified Message 新架构应把跨组件通信收敛到少量主信道。

第一版推荐两个主信道：

```text
CMD_BT_POST
  Fragment/Controller -> Activity
  页面请求 Activity 向设备发送数据或执行蓝牙相关命令。

CH_BT_EVENT
  Activity -> Fragment/Controller
  Activity 把蓝牙数据、连接状态、发送字节统计等事件推给页面。
```

`INNER` 动作不走信道，直接由 `MessageController` 执行。

`EV_*` 不进入 Unified Message 主干。旧 `EV_*` 可以保留给旧代码兼容，但新架构默认不新增 `EV_*`。

新架构不是“永远只能有两个信道”。如果后续确实需要新增信道，必须满足三个条件：

1. 功能稳健：新增信道表达的是稳定功能边界，而不是临时绕路。
2. 类型纯净：同一个信道里的 payload 类型明确，不能把互不相关的数据混在一起。
3. 归并重复：新增前先确认不能复用或扩展现有主信道，避免重复表达同一个方向。

旧信道迁移策略：

```text
CH_BT_DATA
CH_SENT_BYTES
CH_SET_CONNECT_STATE
CH_STOP_LOOP_SEND
CH_LOG_MESSAGE
CMD_SEND_BT_DATA
EV_REC_SAMPLE
CMD_MSG_NEW_CONTROL
...
```

这些旧常量应逐步标记为 deprecated，并迁移到：

```text
POST      -> CMD_BT_POST
SUBSCRIBE -> CH_BT_EVENT
INNER     -> 无信道
```

迁移时不要一次性删除旧信道。先让 `UnifiedMessageFragment` 使用新主信道；旧 Fragment 和旧 Activity 分支继续兼容。等旧路径被替换后，再清理 deprecated 常量。

## ProfileSpec

`ProfileSpec` 是 profile 返回的紧凑声明。第一版用 Java builder，不引入 YAML/XML。

原因：

- Java 有类型检查。
- parser 和复杂回调本来就要写代码。
- builder 写法已经接近声明式配置。
- 等结构稳定后，再考虑 YAML/XML loader。

EIS 示例：

```java
ProfileSpec.builder("eis")
        .parser(line -> parseEis(line))
        .chart(ChartSpec.line("ohm", "EIS Ohm").metric("ohm"))
        .chart(ChartSpec.line("us", "EIS uS").metric("us"))
        .action(ActionSpec.inner("start_record", "开始记录", BuiltIn.START_RECORD))
        .action(ActionSpec.inner("stop_record", "结束记录", BuiltIn.STOP_RECORD))
        .action(ActionSpec.inner("export", "导出", BuiltIn.EXPORT))
        .recordJson(sample -> eisJson(sample))
        .build();
```

CGM 示例：

```java
ProfileSpec.builder("cgm")
        .parser(line -> parseCgm(line))
        .chart(ChartSpec.line("primary", "CGM").metric("primary"))
        .chart(ChartSpec.line("current", "Current").metric("current"))
        .indicator(IndicatorSpec.metric("current", "Current", "current"))
        .indicator(IndicatorSpec.status("status", "Status"))
        .action(ActionSpec.postText("start_measure", "Start", () -> CgmCommands.startNow()))
        .action(ActionSpec.postText("read_cache", "Read", () -> "ALL\n\r"))
        .action(ActionSpec.postText("delete_cache", "Delete", () -> "DELETE\n\r"))
        .action(ActionSpec.inner("start_record", "开始记录", BuiltIn.START_RECORD))
        .action(ActionSpec.inner("stop_record", "结束记录", BuiltIn.STOP_RECORD))
        .action(ActionSpec.inner("export", "导出", BuiltIn.EXPORT))
        .recordJson(sample -> cgmJson(sample))
        .build();
```

Profile 可以写在一个类或一个文件里。parser、sample factory、命令字符串、JSON 格式化都可以作为内部类或静态方法放在同一个 profile 文件里，保持设备协议细节集中。

## 动态 UI 创建

动态创建的意思是：profile 只声明 spec 列表，controller 根据 spec 创建真实 Android View。

### Actions

Profile 声明：

```text
actions: List<ActionSpec>
```

Controller 创建按钮：

```java
for (ActionSpec action : spec.actions) {
    Button button = new Button(context);
    button.setText(action.label);
    button.setOnClickListener(v -> handleAction(action));
    host.actionContainer().addView(button);
}
```

点击后：

```java
if (action.route == Route.POST) {
    gateway.postText(action.resolveText());
}

if (action.route == Route.INNER) {
    runBuiltInOrCallback(action);
}
```

也就是说，profile 不绑定按钮。profile 只声明动作。controller 创建按钮，并把点击事件绑定到这个动作上。

### Charts

Profile 声明：

```text
charts: List<ChartSpec>
```

Controller 创建图表：

```java
for (ChartSpec chart : spec.charts) {
    View chartView = createChartView(chart);
    host.chartContainer().addView(chartView);
    chartRenderers.put(chart.metricKey, renderer);
}
```

收到 sample 后：

```java
for (Map.Entry<String, Float> metric : sample.metrics().entrySet()) {
    ChartRenderer renderer = chartRenderers.get(metric.getKey());
    if (renderer != null) {
        renderer.append(metric.getValue());
    }
}
```

第一版只需要折线图。但 `ChartSpec.type` 应该保留，这样未来能增加 bar、pie、gauge 等渲染器，而不用改 profile 和 fragment 边界。

### Indicators

Profile 声明：

```text
indicators: List<IndicatorSpec>
```

Controller 在 `indicatorContainer` 里创建 `TextView` 行。

示例：

- record state：系统自带，表示本地记录状态。
- byte counter：系统自带，表示收发字节数。
- device status：profile 可选，由 sample/status 更新。
- current value：profile 可选，由某个 metric 更新。

Profile 不应该知道 `tvStatus`、`tvCurrentValue`、`tvBottomInfo`。

## Producer/Consumer 模型

生产者消费者思想保留。`ProfileSpec` 不替代它。

`ProfileSpec` 声明管道。`MessageController` 根据 spec 创建和连接管道。运行时仍然走 producer/consumer。

输入管道：

```text
Activity 收到蓝牙数据
-> UnifiedMessageFragment 收到 SUBSCRIBE 事件
-> MessageController 处理 raw event
-> Profile parser 生产 BluetoothSample
-> SampleDispatcher 分发 sample
-> chart / record / indicator / message-list consumers 执行
```

动作管道：

```text
Controller 根据 ActionSpec 创建按钮
-> 用户点击按钮
-> Button 生产 ActionEvent
-> Controller 消费 ActionEvent
-> Route.POST 发送到 Activity/device
-> Route.INNER 执行本地 controller 动作
```

职责划分：

- Parser 只负责理解数据。
- Consumer 各自负责一种副作用。
- Controller 负责把 parser 输出接到 consumers。
- Profile 负责声明应该有哪些东西。

## UnifiedMessageFragment

`UnifiedMessageFragment` 应该继承 `BTFragment`。

它负责：

- Android 生命周期。
- ViewBinding。
- 通过 `BTFragment` 订阅事件总线。
- `sendDataToActivity(...)` 这类 gateway 调用。
- 把生命周期和数据回调转发给 `MessageController`。

它不负责：

- profile 特定命令字符串。
- profile 特定 parser。
- 设备专属图表/文本更新规则。

Fragment 方法应该很薄：

```java
initAllImpl(...) -> controller.init(...)
onBtData(...) -> controller.onBtData(...)
onSentBytesChanged(...) -> controller.onSentBytesChanged(...)
onConnectStateChanged(...) -> controller.onConnectStateChanged(...)
onDestroy(...) -> controller.release()
```

## MessageController

`MessageController` 是策略执行器。

它负责：

- 读取 `ProfileSpec`。
- 创建 action buttons。
- 创建 chart views。
- 创建 indicator views。
- 管理消息列表 adapter。
- 管理 recorder。
- 管理 byte counters。
- 解析输入数据。
- 分发 sample 给 runtime consumers。
- 对 `POST` action 调用 gateway。
- 对 `INNER` action 执行内置动作。

它可以创建 Android View，因为它是 UI controller；但它不能要求 profile 知道这些 view。

## Host View 和 Gateway

可以使用很小的 host interface，避免 controller 直接依赖具体 binding。

```java
interface HostView {
    ViewGroup actionContainer();
    ViewGroup chartContainer();
    ViewGroup indicatorContainer();
    RecyclerView messageList();
    TextView bottomInfo();
}
```

使用很小的 gateway 作为设备发送出口：

```java
interface BluetoothGateway {
    void postText(String text);
    void postBytes(byte[] bytes);
}
```

Fragment 实现或提供这两个接口。为了避免文件分散，这些接口可以作为 `UnifiedMessageFragment.java` 内部结构存在。

## FM 和 FMN 迁移

FM 和 FMN 不应该继续作为两套分裂逻辑存在。

目标：

```text
FragmentMessage -> 删除，或变成 UnifiedMessageFragment 的薄包装
FragmentMessageNew -> 删除，或变成 UnifiedMessageFragment 的薄包装
EisProfileNew -> 删除
EisProfile -> 设备独立 ProfileSpec
```

如果之后确实需要两种视觉模式，应作为 layout/view mode：

- 普通通信布局
- compact/debug/fullscreen 布局

不能因为 layout view id 不同，就创建一个新的 profile。

## Android 初学者阅读路径

理解最终架构时按这个顺序看：

1. 先看统一 XML layout，找到容器：
   - `actionContainer`
   - `indicatorContainer`
   - `chartContainer`
   - `recyclerMessage`
   - `bottomInfo`
2. 再看 `UnifiedMessageFragment`。
   - 看 binding 如何 inflate。
   - 看 controller 如何接收生命周期和数据回调。
3. 再看 `ProfileSpec` builder 使用。
   - 看某个设备声明了哪些 actions、charts、indicators。
4. 搜索 action id 或 metric key。
   - 例如 `read_cache`、`ohm`、`current`
5. 跟运行时流：
   - 按钮点击 -> route -> gateway
   - 蓝牙数据 -> parser -> sample -> consumers

## 测试策略

测试要聚焦架构行为：

- EIS parser 能生产 `ohm` 和 `us` 两个 metric。
- 如果迁移 CGM，CGM parser 保留 `CA`、`EIS`、`RI`、`Start Playback`、`Playback all done` 语义。
- Controller 能根据 profile 创建正确数量的 action/chart/indicator。
- Controller 能把 `POST` action 路由到 gateway。
- Controller 能把 `INNER` 录制动作路由到 recorder。
- Sample dispatch 能按 metric key 更新对应 chart renderer。

不要在单测里过度测试 Android 渲染细节。controller/spec 接线可以用 fake 测；ViewBinding 通过 app compile 和手工 smoke test 验证。

## 非目标

- 第一版不做 YAML/XML profile loader。
- 不把每个 spec type 拆成单独文件。
- 不立刻支持所有 chart 类型。
- 除非出现真实阻塞，否则不重做 `CommunicationActivity`。
- 不保留 FM/FMN 两套分裂实现。

## 待定决策

1. 第一阶段先迁移 EIS，还是 EIS 和 CGM 一起迁移？
2. 当前工作区里上一轮临时修复，是丢弃后按新架构重写，还是改造成新架构的一部分？
3. 动态 chart view 第一版用 Java 直接创建，还是用 XML row template inflate？

推荐答案：

1. 先迁移 EIS。EIS 数据简单，只有两个 metric，最适合验证 profile 和 viewbinding 解耦。
2. 不在临时修复上继续叠补丁，而是把可用部分改造成新架构。
3. 第一版用 Java 直接创建；如果后续需要 UI 打磨，再引入 XML row template。
