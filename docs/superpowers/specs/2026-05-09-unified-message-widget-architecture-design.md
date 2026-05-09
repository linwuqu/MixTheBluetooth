# UnifiedMessage 第二阶段：清理旧模型与 WidgetSpec 组件化设计

日期：2026-05-09

## 背景

第一阶段已经把“通信页的设备差异”从 Fragment/ViewBinding 中抽离出来：

- `UnifiedMessageFragment` 负责 Android 生命周期、信道监听、视图绑定和销毁。
- `MessageController` 负责解释 `ProfileSpec`，动态创建按钮、指标和图表，并分发蓝牙数据。
- `Profiles.eis()` 负责声明 EIS 的 parser、chart、action 和 recorder formatter。
- Activity 通过统一通道桥接蓝牙通信：
  - `CMD_BT_POST`：Fragment/Controller -> Activity -> 蓝牙设备
  - `CH_BT_EVENT`：Activity -> Fragment/Controller

这个方向已经解决了 FM/FMN 之间 profile 与 view binding 互相缠绕的问题。但当前仍有三类遗留问题：

1. 项目树中还保留旧模型文件，例如 `DeviceProfile`、`SampleConsumer`、`EisProfile`、`EisProfileNew`、`FragmentMessageNew` 等。
2. `ChartSpec` 名义上有 `ChartType`，但 `MessageController` 实际写死了 `LineChart` 和 `RealtimeLineChart`。
3. 统一页目前只有粗粒度容器：action、indicator、chart、message、bottom，无法优雅表达圆环、统计卡、备注信息、dashboard 卡片等更复杂 UI。

第二阶段目标是把这个系统从“统一通信页”推进到“可注册、可复用、可美化的数据组件生态”。

## 目标

### 目标 1：清理旧模型

新路径只保留少量核心概念：

- `UnifiedMessageFragment`
- `Profiles`
- `BluetoothSample`
- `BluetoothSampleParser`
- `SampleRecorder`
- `MetricWidgets`

旧的 `DeviceProfile` / `SampleConsumer` 模型不再作为新架构的一部分。它们可以在迁移完成后删除，或在短期内仅作为 deprecated 对照存在。

### 目标 2：从 ChartSpec 升级到 WidgetSpec

当前 `ChartSpec` 只能描述折线图。后续应改为 `WidgetSpec`，用于描述所有由 sample 驱动的 UI 组件，例如：

- 折线图
- 圆环仪表
- 单值卡片
- 最大值/最小值/波动幅度统计卡
- 原始消息或调试面板
- 设备状态摘要卡

`WidgetSpec` 不直接认识 Android View，也不认识 ViewBinding。它只描述“需要什么组件、绑定哪个 metric、放在哪个区域、用什么样式”。

### 目标 3：用 Region/Slot 解决位置锚定

Widget 的位置不应该由 profile 直接操作 ViewBinding，也不应该由 widget 自己决定。

统一页面 XML 提供区域锚点，称为 `Region`：

- `ACTION`
- `SUMMARY`
- `MAIN`
- `SECONDARY`
- `DEBUG`
- `BOTTOM`

`ProfileSpec` 注册 widget 时声明目标区域：

```java
WidgetSpec.gauge("sweat_flow")
        .title("汗液流量")
        .metric("sweatFlow")
        .unit("uL")
        .region(Region.SUMMARY)
        .order(10);

WidgetSpec.line("ocp")
        .title("离子开路电位（OCP）")
        .metric("ocp")
        .unit("mV")
        .region(Region.MAIN)
        .order(20);

WidgetSpec.stats("ocp_stats")
        .metric("ocp")
        .region(Region.MAIN)
        .order(30);
```

`MessageController` 只根据 `region + order` 将组件插入对应容器：

```java
ViewGroup parent = host.region(widgetSpec.region);
MetricWidget widget = MetricWidgets.create(context, widgetSpec);
parent.addView(widget.view());
widgets.add(widget);
```

这样分工保持清晰：

- XML / Fragment：定义页面有哪些区域，以及区域本身如何布局。
- ProfileSpec：声明设备需要哪些组件，以及组件属于哪个区域。
- MetricWidget：负责组件内部怎么画。
- MessageController：负责按 spec 创建组件，并把 sample 分发给组件。

## 非目标

本阶段不做以下事情：

- 不把所有旧 Fragment 一次性迁移完成。
- 不重写整个 `FragmentCustom`。
- 不把所有 UI 都做成完全任意布局系统。
- 不引入 YAML/XML 外部配置文件。
- 不让 profile 重新依赖 ViewBinding。

第一阶段只建立稳定组件生态。复杂 dashboard 可以逐步迁移。

## 核心架构

### ProfileSpec

`ProfileSpec` 仍然是设备协议说明书，但字段从：

```text
parsers
actions
charts
indicators
recordFormatter
```

升级为：

```text
parsers
actions
widgets
recordFormatter
```

其中 `indicators` 被视为一种 widget，不再单独作为顶层概念。

### WidgetSpec

`WidgetSpec` 是一个轻量声明对象：

```text
id          组件唯一 id
kind        组件类型，例如 LINE、GAUGE、VALUE、STATS
title       显示标题
metricKey   绑定的 sample metric
unit        单位
region      插入哪个页面区域
order       同一区域内排序
style       视觉预设，可选
options     类型专属配置，例如 y 轴范围、最大点数、仪表最大值
```

初期内置 `kind`：

- `LINE`
- `GAUGE`
- `VALUE`
- `STATS`

后续可以增加：

- `BAR`
- `PIE`
- `RAW_LOG`
- `DEVICE_CARD`

### MetricWidget

`MetricWidget` 是运行时对象，由 `WidgetSpec` 创建出来：

```java
interface MetricWidget {
    View view();
    void onSample(BluetoothSample sample);
    void reset();
}
```

不同 widget 内部可以使用不同 Android View：

- `LineMetricWidget` 使用 MPAndroidChart `LineChart`。
- `GaugeMetricWidget` 使用 `CircleProgressView`。
- `ValueMetricWidget` 使用普通 `TextView` 或小卡片布局。
- `StatsMetricWidget` 内部维护 max/min/amplitude，并更新一组 `TextView`。

`MessageController` 不再保存 `HashMap<String, RealtimeLineChart>`，而是保存：

```java
List<MetricWidget> widgets;
```

收到 sample 后统一调用：

```java
for (MetricWidget widget : widgets) {
    widget.onSample(sample);
}
```

这就是新架构下的“消费者”。它不需要继续保留旧的 `SampleConsumer` 接口。

### MetricWidgets

为了避免“一种 widget 一个文件”导致项目树发散，初期使用一个文件承载内置组件族：

```text
activity/tool/chart/MetricWidgets.java
```

这个文件可以包含：

- `MetricWidget` 接口
- `WidgetSpec` 相关 helper
- `LineMetricWidget`
- `GaugeMetricWidget`
- `ValueMetricWidget`
- `StatsMetricWidget`
- `MetricWidgets.create(...)`

如果后续某个 widget 复杂度明显增长，再单独拆文件。默认原则是小而精，不提前拆散。

## Region 与 XML

统一页 XML 不放具体业务控件，只放稳定区域：

```text
actionRegion
summaryRegion
mainRegion
secondaryRegion
debugRegion
bottomRegion
```

`UnifiedMessageFragment.BindingHost` 暴露：

```java
ViewGroup region(Region region);
RecyclerView messageList();
TextView bottomInfo();
```

如果某个布局不需要 debug 区域，可以让 `region(DEBUG)` 返回一个隐藏容器或空容器。

这使得后续可以出现不同 Fragment 壳：

- `UnifiedMessageFragment`：普通通信页，按钮 + 摘要 + 图表 + 消息列表。
- `FullScreenChartFragment`：重点显示 MAIN/SECONDARY，隐藏消息列表。
- `DebugConsoleFragment`：重点显示 DEBUG 和消息列表。
- `TabletDashboardFragment`：同样使用 Region，但 XML 把区域摆成平板布局。

这些 Fragment 可以复用同一个 `MessageController` 和同一个 `ProfileSpec`。

## Recorder 简化

当前 `SampleRecorder` 和 `SampleRecorderImpl` 可以约简。

原因：

- profile 不再直接拿 recorder。
- controller 是唯一使用者。
- 当前接口没有多实现，也没有测试替身需求。

建议把 recorder 收敛成一个 concrete class：

```text
activity/tool/SampleRecorder.java
```

原 `SampleRecorderImpl.java` 删除。类名可以直接叫 `SampleRecorder`。

`ProfileSpec.recordFormatter` 继续存在，负责把 sample 转成 json line：

```java
recordJson(Profiles::eisJson)
```

Controller 负责判断是否正在录制，并调用 recorder 写入。

## 旧文件清理策略

建议分两批清理：

### 第一批：新路径不再依赖

可以删除或迁移：

- `SampleConsumer.java`
- `DeviceProfile.java`
- `EisProfile.java`
- `EisProfileNew.java`
- `FragmentMessageNew.java`

如果 `FragmentMessage` 仍在 tab 中作为旧通信页保留，可以暂时不删，但要明确它是 legacy。

### 第二批：协议迁移后再删

- `CgmProfile.java`
- `CgmSample.java`

CGM 后续应该迁移到 `Profiles.cgm()`，并按照 `WidgetSpec` 注册自己的 actions/widgets/parser。

## EIS 示例

迁移后 EIS profile 应类似：

```java
public static ProfileSpec eis() {
    return ProfileSpec.builder("eis")
            .parser(new EisParser())
            .action(ActionSpec.inner("start_record", "开始记录", BuiltIn.START_RECORD))
            .action(ActionSpec.inner("stop_record", "结束记录", BuiltIn.STOP_RECORD))
            .action(ActionSpec.inner("export", "导出", BuiltIn.EXPORT))
            .widget(WidgetSpec.line("eis_ohm")
                    .title("EIS Ohm")
                    .metric(EisSample.METRIC_OHM)
                    .unit("Ω")
                    .region(Region.MAIN)
                    .order(10))
            .widget(WidgetSpec.line("eis_us")
                    .title("EIS uS")
                    .metric(EisSample.METRIC_US)
                    .unit("uS")
                    .region(Region.MAIN)
                    .order(20))
            .recordJson(Profiles::eisJson)
            .build();
}
```

如果后续要接入汗液 dashboard，可以类似：

```java
.widget(WidgetSpec.gauge("sweat_flow")
        .title("汗液流量")
        .metric("sweatFlow")
        .unit("uL")
        .region(Region.SUMMARY)
        .order(10))
.widget(WidgetSpec.value("sodium")
        .title("钠钾离子浓度")
        .metric("ionConcentration")
        .unit("mmol/L")
        .region(Region.SUMMARY)
        .order(20))
.widget(WidgetSpec.stats("ocp_stats")
        .title("OCP 统计")
        .metric("ocp")
        .region(Region.MAIN)
        .order(30))
```

## 数据流

完整数据流保持不变：

```text
Activity 收到 byte[]
-> CH_BT_EVENT
-> UnifiedMessageFragment
-> MessageController.onEvent
-> parser.parse(text)
-> BluetoothSample
-> widgets.onSample(sample)
-> recorder.appendLine(recordFormatter.format(sample))
-> message list 显示原始收发
```

Fragment -> Activity 也保持统一：

```text
ActionSpec POST
-> gateway.postText/postBytes
-> CMD_BT_POST
-> Activity
-> HoldBluetooth.sendData
```

## UI 扩展原则

1. 需要新视觉组件时，优先新增 `WidgetSpec.kind` 和对应 `MetricWidget`。
2. 需要改变组件摆放时，优先调整 XML 的 Region 布局。
3. 需要复杂组合卡片时，再引入 `PanelSpec`，不要一开始就做完整布局 DSL。
4. profile 只声明“我需要什么”，不声明“我怎么 findViewById”。
5. controller 只解释 spec，不写具体 chart/gauge/text 的内部 UI。

## 测试策略

至少补三类测试：

1. `Profiles.eis()` 声明了两个 line widget，metric 分别为 `ohm` 和 `us`。
2. EIS parser 能把一行数据解析为包含两个 float metric 的 `BluetoothSample`。
3. WidgetSpec 的 region/order 排序稳定，例如 MAIN 区域内 order 小的先创建。

如果 recorder 合并为 concrete class，需要保留记录 JSON 行和 sample count 的测试。

## 验收标准

完成本阶段后应满足：

- 新统一页不再直接创建 `LineChart`。
- `MessageController` 只持有 `List<MetricWidget>`，不持有 `HashMap<String, RealtimeLineChart>`。
- EIS 的两个图表由 `WidgetSpec.line(...)` 注册。
- 旧 `SampleConsumer` 不再被新架构引用。
- recorder 文件从两个约简为一个。
- 工作区编译通过，单元测试通过。
- 后续要添加圆环、统计卡、数值卡时，不需要修改 `UnifiedMessageFragment` 生命周期逻辑。

## 自检

- 没有要求 profile 认识 ViewBinding。
- 没有让 widget 自己决定页面位置。
- 没有把所有历史页面一次性重写。
- 没有新增大量文件。
- Region/WidgetSpec 可以覆盖当前 EIS，也能逐步承接自定义按钮页里的圆环、统计卡和多图表布局。
