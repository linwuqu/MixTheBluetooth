# Uni 第三阶段：运行时收敛、Profile 拆分与输出边界设计

日期：2026-05-23

## 1. 背景

前两阶段已经把旧通信页中的一部分逻辑迁移到 `UnifiedMessageFragment + MessageController + ProfileSpec + WidgetSpec` 架构中：

- Fragment 不再直接理解 EIS 数据格式。
- Controller 持有 `context/profile/hostView/gateway`，负责解释 profile、创建按钮和 widget、消费蓝牙事件。
- Profile 以声明式方式描述 actions、widgets、parser 和 record formatter。
- Widget 已经从单一 chart 发展为 gauge、value、line、stats 等组件。

这个方向是正确的，但目前仍有几个结构问题：

1. `ProfileSpec`、`ActionSpec`、`Region`、`Route`、`BuiltIn` 还作为 `UnifiedMessageFragment` 的内部类型存在，导致 profile 反向 import Fragment。
2. `activity/tool/` 目录承载了过多语义不一致的类，例如 `Profiles`、`Analysis`、`SampleRecorder`、`MetricWidgets`。
3. `MessageController` 内部混有若干不属于主编排职责的小工具函数，例如 byte decode、状态文本更新、dp 转换。
4. 旧 Fragment 中的 `ALL`、`DELETE`、`TIME,...` 等命令是设备协议命令，不是蓝牙 SDK 通用命令。它们不能继续散落在 Fragment 或 Controller 中。
5. 旧 txt 导出逻辑把解码、换行、设备状态、文件写入、图表更新和消息列表合并揉在同一个函数里。第三阶段需要先建立更清楚的输出边界，但不在本阶段完整接入服务器 API。

第三阶段目标不是新增完整注册登录和 Retrofit 上传功能，而是先把 Uni 运行时边界整理稳定。注册 Activity、Retrofit、mock、env 和真实文件上传放入第四阶段。

## 2. 设计原则

本阶段采用以下原则：

1. Controller 是 Uni 运行时骨架。Controller 内需类型可以集中在 `Controller.java`，不为每个 enum 或接口单独建文件。
2. Fragment 是 UI shell。Fragment 只负责 ViewBinding、生命周期、事件总线入口和蓝牙发送 gateway。
3. 具体设备 profile 按设备拆分。EIS、CGM 等协议不应该全部写在一个大 `Profiles.java` 中。
4. 只有独立演化的轴才拆文件：widgets、codec、commands、output、profile、api。
5. 第三阶段不提前实现大型协议系统。CMD 先建立语义命令和设备字符串之间的边界。
6. 第三阶段不直接实现 Retrofit 注册和上传。只在 `Output.java` 中预留本地 session 文件和后续上传出口。
7. 遵循约定优于配置。默认 UTF-8、默认 text line 输入、默认 JSONL 输出、默认通过 `CMD_BT_POST` 发送蓝牙 payload。

## 3. 非目标

第三阶段不做以下事情：

- 不实现注册页面。
- 不添加 Retrofit 依赖。
- 不接入 `/api/account/v1/register` 或 `/api/file/v1/upload`。
- 不实现 env.mock/env.dev/env.prod 的 Gradle 配置。
- 不重写所有旧 Fragment。
- 不实现无感设备发现和自动绑定设备。
- 不把 parser、sample、command、serializer 过早拆成大量小文件。

这些能力进入第四阶段或后续协议阶段。

## 4. 目标文件树

第三阶段完成后，核心结构应收敛为：

```text
app/src/main/java/com/hc/mixthebluetooth/
├── fragment/
│   ├── UniFragment.java
│   ├── FragmentCustom.java
│   ├── FragmentIonAnalysis.java
│   └── ...
│
├── uni/
│   ├── Controller.java
│   ├── Widgets.java
│   ├── Codec.java
│   ├── Commands.java
│   ├── Output.java
│   ├── Profiles.java
│   └── profile/
│       ├── eis/
│       │   └── EisProfile.java
│       └── cgm/
│           └── CgmProfile.java
│
└── activity/
    └── CommunicationActivity.java
```

第四阶段再引入：

```text
app/src/main/java/com/hc/mixthebluetooth/
├── api/
│   ├── ApiClient.java
│   ├── ApiModels.java
│   ├── ApiEnvironment.java
│   ├── AuthApi.java
│   ├── FileApi.java
│   ├── AuthSessionStore.java
│   └── MockApi.java
│
└── activity/
    └── AccountRegisterActivity.java
```

第三阶段不要提前创建第四阶段文件，除非 implementation plan 中明确需要为编译迁移做最小占位。

## 5. 命名调整

当前：

```text
UnifiedMessageFragment
MessageController
Profiles
MetricWidgets
```

第三阶段建议改为：

```text
UniFragment
Controller
Profiles
Widgets
```

理由：

- 当前页面已经不只是 message list，而是统一通信运行时。
- `UniFragment` 比 `UnifiedMessageFragment` 短，也更符合后续 `uni/Controller.java` 的模块命名。
- `Controller` 位于 `uni` 包下，完整类名已经表达语义：`com.hc.mixthebluetooth.uni.Controller`。
- `Widgets` 位于 `uni` 包下，不需要再叫 `MetricWidgets`，但内部接口仍可叫 `MetricWidget`。

## 6. UniFragment

`UniFragment` 继承现有 `BTFragment<FragmentUnifiedMessageBinding>`。本阶段可以继续复用原 XML 文件 `fragment_unified_message.xml`，是否重命名 layout 可放到实现计划中评估。

`UniFragment` 负责：

- inflate ViewBinding。
- register `StaticConstants.CH_BT_EVENT`。
- 创建 `Controller`。
- 提供 `Controller.HostView` 实现。
- 提供 `Controller.Gateway` 实现。
- 在 `updateStateImpl` 中把 `CH_BT_EVENT` 转发给 controller。
- 在 `onDestroy` 中 release controller。

`UniFragment` 不负责：

- 设备命令字符串。
- sample parser。
- widget 内部创建逻辑。
- byte decode。
- session 文件写入。
- API 上传。

示意：

```java
public class UniFragment extends BTFragment<FragmentUnifiedMessageBinding> {
    private Controller controller;

    protected void initAllImpl(View view, Context context) {
        controller = new Controller(
                requireContext(),
                Profiles.eis(),
                new BindingHost(viewBinding),
                new FragmentGateway()
        );
        controller.init();
    }
}
```

## 7. Controller.java

`Controller.java` 是 Uni 运行时骨架。它集中承载 controller 内需的声明类型，不再把这些类型散落成多个文件。

建议结构：

```java
public final class Controller {
    public enum Region { ACTION, SUMMARY, MAIN, SECONDARY, DEBUG }
    public enum Route { POST, INNER }
    public enum BuiltIn { START_RECORD, STOP_RECORD, EXPORT, CLEAR_MESSAGES, RESET_WIDGETS }

    public interface HostView { ... }
    public interface Gateway { ... }
    public interface TextSupplier { ... }
    public interface RecordFormatter { ... }

    public static final class ProfileSpec { ... }
    public static final class ActionSpec { ... }

    public Controller(Context context, ProfileSpec spec, HostView host, Gateway gateway) { ... }
}
```

这样做的原因：

- Profile 是 Controller 可执行的配置，依赖 `Controller.ProfileSpec` 是合理的。
- 只要 profile 不依赖 Fragment/ViewBinding/Activity，就已经解决前两阶段最关键的问题。
- enum、host、gateway 都是 controller 运行时契约，单独拆文件会增加跳转成本。

Controller 保留的职责：

- 创建 action buttons。
- 创建 system indicators。
- 创建 widgets。
- 管理 message list adapter。
- 接收蓝牙事件。
- 调用 `Codec` 解码 bytes。
- 调用 profile parser 解析 sample。
- 分发 sample 到 widgets。
- 调用 `Output` 记录 sample。
- 执行 inner action。
- 对 post action 调用 gateway。

Controller 不直接承担：

- 具体 bytes -> text 细节。
- 设备 command 字符串生成。
- widget 内部绘制。
- 具体文件上传。
- Retrofit API 调用。

### 7.1 Controller 内部可保留的小对象

以下对象可以作为 `Controller.java` 的 private static 内部类，不必单独拆文件：

- `SystemStatusPresenter`：管理 record state、byte counter、bottom info。
- `MessageListPresenter`：管理原始消息列表、自动清理和滚动。

这样可以把 `setRecordState()`、`updateByteCounter()`、`setBottomInfo()` 从 controller 主流程中抽出来，但不制造额外文件。

## 8. Widgets.java

`Widgets.java` 从现有 `activity/tool/chart/MetricWidgets.java` 迁移而来。

建议结构：

```java
public final class Widgets {
    public enum WidgetKind { LINE, GAUGE, VALUE, STATS }
    public enum WidgetStyle { CARD, HERO, COMPACT }

    public interface MetricWidget { ... }
    public static final class WidgetSpec { ... }

    public static MetricWidget create(Context context, WidgetSpec spec) { ... }
    public static ArrayList<WidgetSpec> orderedForDisplay(List<WidgetSpec> widgets) { ... }
}
```

本阶段可以继续把内置 widget 作为 private static class 放在 `Widgets.java` 中：

- `LineMetricWidget`
- `GaugeMetricWidget`
- `ValueMetricWidget`
- `StatsMetricWidget`
- `LineChartRuntime`

不拆成单独文件。等某个 widget 复杂度明显增加，再单独拆。

## 9. Codec.java

`Codec.java` 处理蓝牙 payload 到文本消息的约定。它不理解 EIS/CGM 业务协议。

职责：

- `byte[]` -> `String`
- 复用 `Analysis.getByteToString(...)`
- 读取或接收编码格式，例如 UTF-8/GBK
- 去掉 `\u0000`
- 处理 trim
- 处理是否按换行检查
- 后续可承接半包、多行拆分和 line buffer

建议第一阶段 API：

```java
public final class Codec {
    public static final class Options {
        public final String charset;
        public final boolean hex;
        public final boolean checkNewline;
    }

    @Nullable
    public static String decode(@Nullable byte[] bytes, @NonNull Options options) { ... }
}
```

Controller 不再直接写：

```java
Analysis.getByteToString(...)
FragmentParameter.getInstance().getCodeFormat(context)
replace("\u0000", "")
trim()
```

这些都由 `Codec` 收敛。

## 10. Commands.java

`Commands.java` 是设备 CMD 的语义边界。

当前旧代码中的：

```text
ALL\n\r
DELETE\n\r
TIME,yyyy,MM,dd,HH,mm,ss\n\r
```

不是 basiclibrary 或 bluetoothlibrary 的通用命令，而是某类设备的文本协议。蓝牙 SDK 只发送 bytes，不理解这些业务语义。

第三阶段先建立三层概念：

```text
Uni action:
  用户点击 READ_CACHE / DELETE_CACHE / SYNC_TIME

Device command:
  profile 或 command encoder 生成 ALL / DELETE / TIME

Transport:
  Gateway 通过 CMD_BT_POST 把 bytes 发给 Activity，再由蓝牙 SDK 发送
```

建议结构：

```java
public final class Commands {
    public enum Capability {
        SYNC_TIME,
        START_MEASURE,
        READ_CACHE,
        DELETE_CACHE,
        SET_PARAMS,
        STOP_MEASURE
    }

    public interface Encoder {
        String encode(Capability capability);
    }

    public static final class LegacyCgm {
        public static String syncTime(Date date) { ... }
        public static String readCache() { return "ALL\n\r"; }
        public static String deleteCache() { return "DELETE\n\r"; }
    }
}
```

第三阶段不要实现完整自动设备发现协议，只把以下事情做好：

- `ALL/DELETE/TIME` 只存在于 `Commands` 或 `CgmProfile` 中。
- `Controller` 不硬编码任何设备命令字符串。
- `Profile` 通过 `ActionSpec.postText(...)` 或 `Commands.LegacyCgm.*` 声明设备命令。

未来设备发现阶段再扩展：

```text
scan filter -> connect -> read protocol info -> select profile -> select command encoder
```

## 11. Output.java

`Output.java` 是 sample 输出边界。第三阶段只建立本地 session 输出和后续上传入口，不接 Retrofit。

职责：

- sample -> JSON line
- sample -> TXT line
- session start/stop
- 创建 app 内部临时输出文件
- 返回 export path
- 为第四阶段 FileApi 上传准备文件对象

建议结构：

```java
public final class Output {
    public interface Serializer {
        String toJsonLine(BluetoothSample sample);
        String toTextLine(BluetoothSample sample);
    }

    public static final class SessionWriter {
        void start(Context context, String prefix);
        void appendJsonLine(String line);
        void appendTextLine(String line);
        void stop();
        File file();
    }
}
```

本阶段可以把现有 `SampleRecorder` 的能力迁移进 `Output.SessionWriter`，也可以先让 `Output` 包装 `SampleRecorder`。实现计划中应选择改动更小的路径。

旧 `addListData()` 的大函数应被拆成管道：

```text
BTPackage.BTData
-> Codec.decode(...)
-> MessageListPresenter.addIncoming(...)
-> profile.parser.parse(...)
-> Controller.consume(sample)
-> Widgets.onSample(sample)
-> Output.append(sample)
```

第四阶段再扩展：

```text
Output.SessionWriter.file()
-> api.FileApi.upload(...)
-> success 后删除临时文件
```

## 12. Profiles.java 与设备 profile

`Profiles.java` 是 profile registry，不承载具体设备细节。

建议：

```java
public final class Profiles {
    public static Controller.ProfileSpec eis() {
        return EisProfile.create();
    }

    public static Controller.ProfileSpec cgm() {
        return CgmProfile.create();
    }
}
```

`uni/profile/eis/EisProfile.java` 可以集中放：

- `create()`
- `EisParser`
- `EisSample`
- `eisJson`
- EIS 专属 widget/action 声明

`uni/profile/cgm/CgmProfile.java` 后续集中放：

- `create()`
- `CgmParser`
- `CgmSample`
- CGM command actions
- CGM text/json formatter

不在第三阶段把 parser/sample 拆成单独文件。只有当单个 profile 文件明显膨胀时再拆。

## 13. API 与注册页面的阶段划分

用户提出的注册 Activity、Retrofit、mock、env、文件上传是合理需求，但它们不是第三阶段的最小闭环。

建议第四阶段处理：

```text
api/
├── ApiClient.java
├── ApiModels.java
├── ApiEnvironment.java
├── AuthApi.java
├── FileApi.java
├── AuthSessionStore.java
└── MockApi.java

activity/
└── AccountRegisterActivity.java
```

第四阶段目标：

- 添加 Retrofit/Gson 依赖。
- 根据接口文档实现 `AuthApi` 和 `FileApi`。
- 用 `JsonData<T>` 匹配后端 `{ code, data, msg, success }` 响应格式。
- 用 product flavor 或 properties 生成 `BuildConfig.API_BASE_URL`、`BuildConfig.API_ENV`、`BuildConfig.USE_MOCK_API`。
- 支持 mock 注册、mock 登录、mock 文件上传。
- 注册页 first version 只负责最小字段提交和 session 保存。
- `Output` 通过 `FileApi` 上传第三阶段生成的 session 文件。

第四阶段不应该反向污染 Uni Controller。Uni 只知道 `Output`，不知道 Retrofit 注解。

## 14. 数据流

### 14.1 接收数据

```text
CommunicationActivity 收到蓝牙数据
-> CH_BT_EVENT
-> UniFragment.updateStateImpl
-> Controller.onEvent
-> Codec.decode(bytes, options)
-> MessageListPresenter.addIncomingText
-> profile parser parse(text)
-> BluetoothSample
-> Widgets.onSample(sample)
-> Output append sample
```

### 14.2 发送设备命令

```text
Profile declares ActionSpec
-> Controller creates Button
-> user taps button
-> Controller.handleAction
-> ActionSpec.textSupplier / Commands generates text
-> Gateway.postText(module, text)
-> CMD_BT_POST
-> CommunicationActivity
-> HoldBluetooth.sendData(module, bytes)
```

### 14.3 本地输出

```text
BuiltIn.START_RECORD
-> Output.SessionWriter.start

sample consumed while recording
-> Serializer.toJsonLine(sample)
-> SessionWriter.appendJsonLine

BuiltIn.STOP_RECORD
-> SessionWriter.stop

BuiltIn.EXPORT
-> bottom info shows path
```

第四阶段上传：

```text
SessionWriter.file()
-> FileApi.upload(file)
-> success
-> delete temp file if policy requires
```

## 15. 迁移策略

建议按低风险顺序迁移：

1. 新建 `uni/` 目录和目标类文件。
2. 将 `UnifiedMessageFragment` 重命名或薄包装为 `UniFragment`。
3. 将当前 `MessageController` 迁移到 `uni/Controller.java`。
4. 将 `ProfileSpec/ActionSpec/Region/Route/BuiltIn` 一并迁入 `Controller.java`。
5. 将 `MetricWidgets` 迁移到 `uni/Widgets.java`。
6. 将 `Profiles.eis()` 迁移到 `uni/profile/eis/EisProfile.java`，`uni/Profiles.java` 只保留入口。
7. 添加 `Codec.java`，替换 controller 内部 decode。
8. 添加 `Commands.java`，先不改变 EIS 行为，只为 CGM/旧命令预留边界。
9. 添加 `Output.java`，选择包装或迁移 `SampleRecorder`。
10. 更新 tests 的 import 和断言。
11. 编译和单测验证。

## 16. 测试策略

第三阶段至少保留或新增以下测试：

1. `Profiles.eis()` 返回的 profile id、actions、widgets 与迁移前一致。
2. `EisProfile` parser 能解析 `ohm/us`。
3. `Widgets.orderedForDisplay(...)` 排序稳定。
4. `Codec.decode(...)` 能去掉 `\u0000` 并 trim。
5. `Commands.LegacyCgm.readCache()` 返回 `"ALL\n\r"`。
6. `Commands.LegacyCgm.deleteCache()` 返回 `"DELETE\n\r"`。
7. `Commands.LegacyCgm.syncTime(...)` 返回 `TIME,yyyy,MM,dd,HH,mm,ss\n\r` 格式。

如果 `Output` 迁移 `SampleRecorder`，需要验证：

- start 后有 export path。
- append 后 sample count 增加。
- stop 后不继续写入。

## 17. 验收标准

完成第三阶段后应满足：

- `Profiles` 和具体 profile 不再 import `fragment.UnifiedMessageFragment.*`。
- `UniFragment` 不包含 profile/action/widget spec 定义。
- `Controller.java` 是 Uni 运行时唯一主入口。
- Controller 内部不再直接写 byte decode 细节。
- `Widgets.java` 承载 widget spec 和内置 widget runtime。
- 设备命令字符串不散落在 Fragment/Controller。
- EIS 当前页面行为保持不变。
- 当前单元测试通过。
- app debug compile 通过。
- 第四阶段可以在不重改 Controller 的情况下接入 `api/FileApi` 和注册页。

## 18. 自检

- 本设计没有为每个 enum 单独建文件。
- 本设计没有把所有 profile 塞进一个大文件。
- 本设计没有让 profile 依赖 Fragment。
- 本设计没有提前实现 Retrofit、注册页面和 env。
- 本设计保留 Controller 作为中间层骨架。
- 本设计只拆独立演化轴：widgets、codec、commands、output、profile。
