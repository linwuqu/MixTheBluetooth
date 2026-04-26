# MixTheBluetooth5.0 深度分析报告

> **时间**: 2026-04-26
> **分析者**: AI 代码审查助手
> **项目**: E:\AndroidStudioProject\MixTheBluetooth5.0

---

## 一、New Fragment 与 FragmentMessage / FragmentCustom 的体验差距与缺失

### 1.1 功能覆盖度对比

| 能力维度 | FragmentMessage | FragmentCustom | FragmentMessageNew | 差距分析 |
|---------|----------------|----------------|-------------------|---------|
| **蓝牙数据接收** | ✅ | ✅ | ✅ | 三者都已实现 |
| **发送数据到蓝牙** | ✅ 完整 | ✅ 完整 | ❌ **未实现** | New 缺失反向控制链路 |
| **RecyclerView 消息列表** | ✅ | ✅ | ✅ | |
| **图表展示** | ✅ LineChart (EIS/血糖) | ✅ 双 LineChart (OCP/EIS) | ✅ 双 LineChart (Ω/uS) | |
| **参数设置对话框** | ✅ 6参数弹出框 | ✅ 内部按钮控件 | ❌ 无 | New 没有参数控制 UI |
| **PopWindow 设置** | ✅ 6个选项 | ✅ | ❌ | New 没有 hex/换行等设置 |
| **循环发送** | ✅ Timer 定时发送 | ❌ | ❌ | |
| **文件发送** | ✅ SendFileActivity | ❌ | ❌ | |
| **CGM/离子分析** | ✅ 业务逻辑 | ✅ 业务逻辑 | ❌ 仅有 raw + Ω/uS | |
| **Recording 录制** | ❌ | ❌ | ✅ JSONL 本地保存 | 仅 New 有 |
| **Export 导出** | ✅ txt 文件 | ❌ | ✅ JSONL 文件 | |
| **连接状态管理** | ✅ 完整 | ✅ | ❌ | New 未处理断连重连 |
| **开发日志** | ✅ 内部 log | ✅ | ✅ 内置 logWarn | |

### 1.2 缺失部分清单

#### A. FragmentMessageNew 缺失的关键能力

| # | 缺失项 | 影响 | 优先级 |
|---|--------|------|--------|
| 1 | **发送数据到蓝牙模块** | 无法通过 New 页面主动控制设备，只能被动接收 | 🔴 高 |
| 2 | **PopWindow 设置（hex/换行/时间戳）** | 无法配置发送格式、接收格式 | 🟡 中 |
| 3 | **连接状态 UI（CONNECTED/CONNECTING/DISCONNECT）** | 用户不知道蓝牙是否在线 | 🟡 中 |
| 4 | **发送数据 HEX 模式** | 无法发送二进制指令 | 🟡 中 |
| 5 | **发送数据换行符追加** | 很多蓝牙设备依赖 `\r\n` 结尾命令 | 🟡 中 |
| 6 | **MTU 设置入口** | BLE 设备无法调整 MTU | 🟡 中 |
| 7 | **CGM / 离子浓度业务解析** | 仅展示原始 Ω/uS，缺少专业数据处理 | 🟢 低（可能故意保持纯净） |

#### B. Activity → Fragment 反向链路未完成

当前 `CommunicationActivity` 的 `initDataListener()` 中：

```java
// 已广播到所有订阅了 FRAGMENT_STATE_DATA 的 Fragment
sendDataToFragment(StaticConstants.FRAGMENT_STATE_DATA, new Object[]{modules.get(0), data});
```

但 New Fragment 缺少：
- 接收 `FRAGMENT_STATE_CONNECT_STATE` 显示连接状态
- 接收 `FRAGMENT_STATE_SERVICE_VELOCITY` 显示接收速度
- 接收 `FRAGMENT_STATE_NUMBER` 显示发送字节数

---

## 二、StaticConstants 信道设计问题分析

### 2.1 当前问题

#### 问题 1: 控制和数据信道未分离

```java
// FRAGMENT_STATE_DATA 被复用为两种完全不同性质的消息：
public static final String FRAGMENT_STATE_DATA = "FRAGMENT_STATE_DATA";
// Payload 类型:
//   1. DeviceModule (连接成功时)
//   2. Object[]{DeviceModule, byte[]} (接收蓝牙数据)
```

每个使用 `FRAGMENT_STATE_DATA` 的 Fragment 都要写这个判断：

```java
if (StaticConstants.FRAGMENT_STATE_DATA.equals(sign)) {
    handleBluetoothPayload(o);  // o 可能是 DeviceModule 或 Object[]
}

private void handleBluetoothPayload(Object o) {
    if (o instanceof DeviceModule) { ... }       // 处理连接成功
    if (!(o instanceof Object[])) return;        // 防御性判断
    Object[] arr = (Object[]) o;
    if (arr.length < 2) return;
    if (!(arr[1] instanceof byte[])) return;      // 又一层类型判断
    // ...最终处理数据
}
```

这段判断逻辑在 **每个 Fragment 都重复写了一遍**。

#### 问题 2: DeviceModule 和 byte[] 的二次判断

```java
// Object[] 的结构是 [DeviceModule, byte[]]
// 但没有任何类型安全保证，依赖约定大于配置
Object[] arr = (Object[]) o;
DeviceModule module = (DeviceModule) arr[0];   // 如果顺序写反了，运行时才发现
byte[] data = (byte[]) arr[1];                 // 如果类型错了，ClassCastException
```

#### 问题 3: 信道命名不统一

| 信道名 | 用途 | 问题 |
|--------|------|------|
| `DATA_TO_MODULE` | Fragment → Activity 发送数据 | 名字含义模糊，"TO_MODULE" 是发给谁 |
| `FRAGMENT_STATE_*` | Activity → Fragment 状态 | "STATE" 和 "DATA" 混用 |
| `MESSAGE_NEW_*` | New Fragment 专用 | 与通用命名风格不一致 |
| `FRAGMENT_CUSTOM_*` | Custom 专用 | 仅用于一个 Fragment |
| `FRAGMENT_STATE_1` / `FRAGMENT_STATE_2` | 显示/隐藏速度 | 纯数字后缀，完全不可读 |

#### 问题 4: 订阅关系混乱

查看各 Fragment 的 `subscription()` 调用：

```
FragmentMessage 订阅: FRAGMENT_STATE_DATA, FRAGMENT_STATE_NUMBER,
                     FRAGMENT_STATE_SEND_SEND_TITLE, FRAGMENT_STATE_SERVICE_VELOCITY,
                     FRAGMENT_STATE_1, FRAGMENT_STATE_2, FRAGMENT_STATE_STOP_LOOP_SEND

FragmentMessageNew 订阅: FRAGMENT_STATE_DATA, MESSAGE_NEW_RECORD_STATE, MESSAGE_NEW_EXPORT_RESULT

FragmentCustom 订阅: FRAGMENT_STATE_DATA (需要验证)

FragmentThree 订阅: (需要验证)
```

没有统一管理，导致：
- 新 Fragment 不知道该订阅哪些信道
- 删除/新增信道时需要修改多个 Fragment
- 信道没有分类（蓝牙数据 / UI状态 / 控制命令 / 设置同步）

---

## 三、你的后期规划展开

你的规划分为以下几个方向，我逐一展开分析：

---

### 3.1 通信链路补全（Activity ↔ Fragment）

#### 3.1.1 现状

```
Activity → Fragment: FRAGMENT_STATE_DATA (Object[]{DeviceModule, byte[]})
Fragment → Activity: DATA_TO_MODULE (FragmentMessageItem)

New → Activity: MESSAGE_NEW_CONTROL (cmd)
New → Activity: MESSAGE_NEW_SAMPLE_JSONL (json string)
Activity → New: MESSAGE_NEW_RECORD_STATE / MESSAGE_NEW_EXPORT_RESULT
```

#### 3.1.2 补全方案

**目标**: New Fragment 能够完整控制蓝牙设备

```
1. 添加 PopWindow 配置订阅
   Activity → New: POP_WINDOW_CONFIG (编码格式, HEX发送, HEX接收, 时间戳, 自动清屏, 换行)

2. 添加连接状态订阅
   Activity → New: FRAGMENT_STATE_CONNECT_STATE

3. 添加发送能力
   New → Activity: DATA_TO_MODULE (FragmentMessageItem)
   (复用现有的 FragmentMessageItem 发送通道)

4. 发送 HEX 模式支持
   - 需要在 buildByteData() 中增加 hex 解析逻辑
   - 参考 FragmentMessage 的 Analysis.changeHexString()
```

**代码示例**:

```java
// NewFragment 中添加订阅
subscription(
    StaticConstants.FRAGMENT_STATE_DATA,
    StaticConstants.MESSAGE_NEW_RECORD_STATE,
    StaticConstants.MESSAGE_NEW_EXPORT_RESULT,
    StaticConstants.FRAGMENT_STATE_CONNECT_STATE,    // 新增
    StaticConstants.POP_WINDOW_CONFIG                 // 新增
);

// updateState 中新增处理
if (StaticConstants.FRAGMENT_STATE_CONNECT_STATE.equals(sign)) {
    String state = (String) o;
    viewBinding.tvConnectionState.setText(state);
    // 可根据状态设置颜色: CONNECTED=绿, CONNECTING=黄, DISCONNECT=红
}
```

---

### 3.2 Storage + JSON + 通信传输链路

#### 3.2.1 现状分析

| 层级 | 当前状态 | 说明 |
|------|---------|------|
| **Storage** | `Storage.java` SharedPreferences 封装 | 只能存 key-value |
| **JSON 序列化** | FragmentMessageNew 手写 JSON 字符串拼接 | 无标准化 |
| **本地保存** | JSONL 文件写入 `getExternalFilesDir(DIRECTORY_DOCUMENTS)` | ✅ 已实现 |
| **PC 传输** | ❌ 未实现 | 目标：蓝牙/网络 → PC / 服务器 |

#### 3.2.2 完整链路设计

```
┌─────────────────────────────────────────────────────────────┐
│                        数据生产层                             │
│  Bluetooth Device → Fragment → (byte[])                     │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      数据解析层                               │
│  ByteParser / GbkDecoder / EisLineParser / CgmParser        │
│  输入: byte[] → 输出: DataPoint (结构化 POJO)                │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      数据路由层                               │
│  Router: 将 DataPoint 路由到多个消费者                        │
│  - LiveEventBus → Fragment (实时显示)                        │
│  - JsonRecorder → JSONL 本地存储                             │
│  - NetworkSender → 发送到 PC / 服务器                        │
└─────────────────────────┬───────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
     ┌─────────┐   ┌──────────┐   ┌────────────┐
     │ 实时图表  │   │ 本地JSONL │   │  网络/蓝牙   │
     │ Render  │   │  FileWriter│   │  发送模块    │
     └─────────┘   └──────────┘   └────────────┘
```

#### 3.2.3 JSON 数据模型标准化

当前 New 的 JSON 格式（片段）：

```json
{"tMs":1234567890,"mac":"AA:BB:CC:DD:EE:FF","name":"Device","ohm":123.45,"us":67.89,"raw":"...Ω, ...uS"}
```

**建议扩展为标准 Schema**:

```java
public class SampleRecord {
    public long timestampMs;       // 绝对时间戳
    public String mac;             // 设备 MAC
    public String deviceName;      // 设备名称
    public String dataType;        // "EIS" / "CGM" / "ION" / "SWT"
    public float[] values;         // 类型相关数据点
    public String raw;             // 原始字符串（用于调试）
    public String encoding;        // "GBK" / "UTF-8"
    public int signalStrength;      // RSSI
}
```

**编码/解码标准化**:

```java
// JsonCoder.java - 统一的 JSON 编解码
public class JsonCoder {
    private static final Gson gson = new Gson();

    public static String encode(SampleRecord record) {
        return gson.toJson(record);
    }

    public static SampleRecord decode(String json) {
        return gson.fromJson(json, SampleRecord.class);
    }

    // 批量写入 JSONL
    public static void writeJsonl(File file, List<SampleRecord> records) {
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            for (SampleRecord r : records) {
                w.write(encode(r));
                w.newLine();
            }
        }
    }
}
```

#### 3.2.4 PC/服务器传输链路

**方案 A: 蓝牙 SPP 直接传输**
```
手机蓝牙 SPP → PC 蓝牙串口 → TCP Socket → 服务器
```
- 优点: 无需网络，蓝牙本身可用
- 缺点: PC 需要蓝牙 SPP 支持，距离受限

**方案 B: WiFi / TCP 直接传输**
```
手机 WiFi Direct / USB Tethering → PC → 服务器
```
- 优点: 速度快，适合大数据量
- 缺点: 需要网络配置

**方案 C: HTTP POST 到服务器**
```
手机 WiFi/流量 → HTTPS → 服务器 API → 数据库/文件存储
```
- 优点: 标准化，易扩展，支持远程
- 缺点: 需要服务器端开发

**推荐**: 先实现 **方案 C (HTTP POST)** 作为 MVP，保留 A/B 作为离线备选。

**传输接口设计**:

```java
public interface DataTransport {
    // 同步发送单条记录
    CompletableFuture<Boolean> send(SampleRecord record);

    // 批量发送（减少网络开销）
    CompletableFuture<Integer> batchSend(List<SampleRecord> records);

    // 离线队列管理（网络不可用时本地缓存）
    void enqueue(SampleRecord record);
    List<SampleRecord> getPendingRecords();
    void markSent(List<Long> timestamps);
}
```

---

### 3.3 Fragment init 架构重构

#### 3.3.1 当前 init 的问题

FragmentMessage 的 init (部分):

```java
// initAll 中有 20+ 个初始化方法调用
initRecycler();
initData();
initBluetooth();
initViewPager();
initChart();
initButtons();
initParameter();
initPopWindow();
initStorage();
initTimer();
initLog();
initDebug();
init...
```

每加一个功能就加一个 init 方法，导致 `initAll()` 越来越长。

#### 3.3.2 目标: 清晰的 4 阶段 init

```
┌────────────────────────────────────────────────────────┐
│                  Fragment.initAll()                    │
├────────────────────────────────────────────────────────┤
│ 1️⃣ 注册阶段 (Registry)                                 │
│    - registerDataChannel()   // 订阅蓝牙数据           │
│    - registerControlChannel() // 订阅控制命令           │
│    - registerRenderTarget()  // 注册图表/画布           │
│    - registerRecycler()      // 注册列表               │
├────────────────────────────────────────────────────────┤
│ 2️⃣ 初始化阶段 (Init)                                   │
│    - initChart()             // 图表基本配置           │
│    - initRecycler()          // 列表配置               │
│    - initControls()          // 按钮绑定               │
│    - initStorage()           // 读取本地配置           │
├────────────────────────────────────────────────────────┤
│ 3️⃣ 连接阶段 (Connect)                                  │
│    - connectBluetooth()      // 发送初始命令           │
│    - requestInitialData()     // 请求初始数据           │
├────────────────────────────────────────────────────────┤
│ 4️⃣ 状态同步阶段 (Sync)                                 │
│    - syncConnectionState()    // 同步连接状态          │
│    - syncPopWindowConfig()    // 同步 PopWindow 配置   │
│    - syncDeviceInfo()        // 同步设备信息          │
└────────────────────────────────────────────────────────┘
```

#### 3.3.3 Fragment 架构抽象设计

**核心接口**:

```java
// 1. 数据信道注册器
public interface DataChannelRegistry {
    // 注册蓝牙数据消费者（接收 byte[]）
    void registerDataChannel(DataChannel channel);
    // 注册控制命令消费者（接收 String cmd / Object config）
    void registerControlChannel(ControlChannel channel);
}

// 2. 蓝牙数据通道
public interface DataChannel {
    // 返回该通道关心的数据类型标识（用于路由）
    String getDataType();  // "EIS" / "CGM" / "ION" / "RAW"

    // 处理接收到的蓝牙数据
    void onDataReceived(byte[] raw, DeviceModule module);
}

// 3. 控制命令通道
public interface ControlChannel {
    // 处理 Activity 发来的控制命令
    void onControlReceived(String command, Object payload);
}

// 4. 画布注册器（统一图表管理）
public interface RenderRegistry {
    // 注册图表
    void registerChart(String name, LineChart chart, ChartConfig config);
    // 追加数据点
    void appendPoint(String chartName, float x, float y);
    // 清空图表
    void resetChart(String chartName);
    // 批量更新（减少 UI 刷新）
    void flush();
}

// 5. 图表配置
public class ChartConfig {
    public String label;
    public int color;
    public int maxPoints = 500;
    public float visibleRangeSec = 60f;
    public String xAxisFormat = "HH:mm:ss";
    public ValueFormatter customFormatter;
}
```

**NewFragment 重构示例**:

```java
public class FragmentMessageNew extends BaseFragment<FragmentMessageNewBinding>
        implements DataChannel, ControlChannel {

    // ===== 1️⃣ 注册阶段 =====
    @Override
    protected void initAll(View view, Context context) {
        initData();    // 注册信道
        initCharts();  // 注册画布
        initRecycler();
        initControls();
        setBottomInfo("Ready");
    }

    private void initData() {
        // 注册数据通道: 接收 EIS 格式蓝牙数据
        subscription(StaticConstants.FRAGMENT_STATE_DATA,
                     StaticConstants.MESSAGE_NEW_RECORD_STATE,
                     StaticConstants.MESSAGE_NEW_EXPORT_RESULT,
                     StaticConstants.FRAGMENT_STATE_CONNECT_STATE);
    }

    private void initCharts() {
        RenderRegistry registry = RenderTool.create(this);

        registry.registerChart("ohm", viewBinding.chartOhm,
            ChartConfig.create("阻抗 (Ω)", Color.RED)
                .maxPoints(500)
                .visibleRange(60f)
        );

        registry.registerChart("us", viewBinding.chartUs,
            ChartConfig.create("电导 (uS)", Color.BLUE)
                .maxPoints(500)
                .visibleRange(60f)
        );
    }

    // ===== 2️⃣ 数据通道实现 =====
    @Override
    public void handleBluetoothPayload(Object o) {
        if (o instanceof DeviceModule) {
            module = (DeviceModule) o;
            return;
        }
        if (!(o instanceof Object[])) return;
        Object[] arr = (Object[]) o;
        if (arr.length < 2 || !(arr[1] instanceof byte[])) return;

        if (arr[0] instanceof DeviceModule) module = (DeviceModule) arr[0];
        byte[] bytes = (byte[]) arr[1];

        String line = CommunicateTool.decodePayload(bytes, getContext());
        float[] v = CommunicateTool.parseEisLine(line);
        if (v == null) return;

        // 渲染到图表
        RenderRegistry.get(this).appendPoint("ohm", v[0]);
        RenderRegistry.get(this).appendPoint("us", v[1]);

        if (mIsRecording) {
            String json = JsonTool.buildEisRecord(line, v[0], v[1], module);
            sendDataToActivity(StaticConstants.MESSAGE_NEW_SAMPLE_JSONL, json);
        }
    }

    // ===== 3️⃣ 控制通道实现 =====
    @Override
    public void onControlReceived(String command, Object payload) {
        switch (command) {
            case StaticConstants.MESSAGE_NEW_RECORD_STATE:
                mIsRecording = (Boolean) payload;
                break;
            case StaticConstants.MESSAGE_NEW_EXPORT_RESULT:
                handleExportResult((String) payload);
                break;
            case StaticConstants.FRAGMENT_STATE_CONNECT_STATE:
                updateConnectionUI((String) payload);
                break;
        }
    }
}
```

---

### 3.4 工具类拆分 (Communicate / Render / Json)

#### 3.4.1 CommunicateTool — 通信工具

```java
// activity/tool/CommunicateTool.java
public class CommunicateTool {

    // ===== 编码解码 =====
    // byte[] → String（支持 GBK/UTF-8/HEX）
    public static String decodePayload(byte[] raw, Context ctx) { ... }

    // String → byte[]（支持 GBK/UTF-8/HEX）
    public static byte[] encodePayload(String text, Context ctx, boolean isHex, boolean appendNewline) { ... }

    // ===== 数据解析 =====
    // 解析 EIS 格式: "123.45Ω, 67.89uS"
    public static float[] parseEisLine(String line) { ... }

    // 解析 CGM 格式: 血糖数据
    public static float parseCgmLine(String line) { ... }

    // 解析离子浓度格式
    public static float[] parseIonLine(String line) { ... }

    // ===== 通用解析器注册表 =====
    // 支持动态添加新的数据格式解析器
    private static final Map<String, LineParser> parsers = new HashMap<>();

    public static void registerParser(String type, LineParser parser) {
        parsers.put(type, parser);
    }

    public static LineParser getParser(String type) {
        return parsers.get(type);
    }

    public interface LineParser {
        float[] parse(String line);
    }
}
```

#### 3.4.2 RenderTool — 渲染工具

```java
// activity/tool/RenderTool.java
public class RenderTool {

    // 每个 Fragment 维护自己的渲染器实例
    private static final Map<Fragment, RenderRegistry> registries = new WeakHashMap<>();

    public static RenderRegistry create(Fragment fragment) {
        RenderRegistry registry = registries.get(fragment);
        if (registry == null) {
            registry = new RenderRegistryImpl(fragment);
            registries.put(fragment, registry);
        }
        return registry;
    }

    public static RenderRegistry get(Fragment fragment) {
        return registries.get(fragment);
    }

    // ===== 预置图表工厂 =====
    // 一行代码创建标准 LineChart
    public static LineChart createEisChart(Context ctx, String label, int color) { ... }
    public static LineChart createCgmChart(Context ctx, String label, int color) { ... }

    // ===== 预设渲染模式 =====
    public static RenderMode REALTIME = new RealtimeRenderMode();  // 实时滚动
    public static RenderMode BATCH = new BatchRenderMode();        // 批量更新
    public static RenderMode STATIC = new StaticRenderMode();      // 静态图表
}
```

#### 3.4.3 JsonTool — JSON 工具

```java
// storage/JsonTool.java
public class JsonTool {

    private static final Gson gson = new GsonBuilder()
        .serializeNulls()
        .create();

    // ===== EIS 数据 =====
    public static String buildEisRecord(String rawLine, float ohm, float us, DeviceModule module) {
        return gson.toJson(new EispRecord(rawLine, ohm, us, module));
    }

    // ===== CGM 数据 =====
    public static String buildCgmRecord(float glucose, DeviceModule module) { ... }

    // ===== 批量写入 JSONL =====
    public static void appendJsonl(File file, List<String> lines) { ... }

    // ===== 读取 JSONL =====
    public static List<SampleRecord> readJsonl(File file) { ... }

    // ===== 字段 =====
    private static class EispRecord {
        long tMs;
        String mac;
        String name;
        float ohm;
        float us;
        String raw;
    }
}
```

#### 3.4.4 与现有 Analysis.java 的关系

| Analysis 中的工具 | 对应新工具 | 迁移方式 |
|------------------|-----------|---------|
| `getByteToString()` | `CommunicateTool.decodePayload()` | 直接迁移 |
| `getBytes()` | `CommunicateTool.encodePayload()` | 直接迁移 |
| `changeHexString()` | `CommunicateTool.hexToString()` / `stringToHex()` | 直接迁移 |
| `hexString2ByteArray()` | `CommunicateTool.hexToBytes()` | 直接迁移 |
| `analysis()` (按分隔符取子串) | 保留在 `CommunicateTool` | 分析后迁移 |
| `getSpeed()` | `CommunicateTool.calculateSpeed()` | 迁移 |
| `IO_input_data()` | `JsonTool.appendJsonl()` | 功能合并 |
| `readFileDate()` | `JsonTool.readJsonl()` | 功能合并 |
| GPS/View 动画相关 | 不迁移，超出蓝牙通信范畴 | 删除或移到其他工具 |

---

### 3.5 PopWindow 重构

#### 3.5.1 当前问题

PopWindowFragment 的逻辑分散在多个地方：

```
PopWindowFragment.java       ←→  SharedPreferences (6个 key)
       ↓
FragmentMessage.java         ←→  读取配置 + 应用到 UI
       ↓
HoldBluetooth (间接)         ←→  编码格式传递给蓝牙发送
```

每增加一个 PopWindow 选项：
1. 在 Storage 中添加 key
2. 在 PopWindowFragment 中添加 CheckBox
3. 在 FragmentMessage 中读取并应用
4. 在 HoldBluetooth 中使用

#### 3.5.2 统一配置管理

```java
// customView/PopWindowConfig.java
public class PopWindowConfig {
    public boolean hexSend = false;
    public boolean hexRead = false;
    public boolean showOwnData = true;
    public boolean showTimestamp = false;
    public boolean autoClear = false;
    public String newline = "\r\n";  // "\r\n" / "\n" / "\r" / ""
    public String encoding = "GBK";  // "GBK" / "UTF-8"

    // 从 SharedPreferences 加载
    public static PopWindowConfig load(Context ctx) { ... }

    // 保存到 SharedPreferences
    public void save(Context ctx) { ... }
}

// Activity 统一广播配置
public class PopWindowConfigChannel {
    public static final String KEY = "POP_WINDOW_CONFIG";

    // Activity 收到 PopWindowFragment 的配置变更后，广播给所有需要的 Fragment
    public static void broadcastConfig(Activity activity, PopWindowConfig config) {
        activity.sendDataToFragment(KEY, config);
    }
}
```

**各 Fragment 的处理**:

```java
// NewFragment 中
if (StaticConstants.POP_WINDOW_CONFIG.equals(sign)) {
    PopWindowConfig config = (PopWindowConfig) o;
    this.mEncoding = config.encoding;
    this.mHexSend = config.hexSend;
    this.mNewline = config.newline;
    // 更新发送按钮的可用状态等
}
```

#### 3.5.3 PopWindow 按钮 → Fragment 控制

当前: 按钮逻辑写在 Activity 的 `onClickView()` 中

```
Activity.onClickView()
    ↓
viewBinding.one → ViewPager.setCurrentItem(0)  // FragmentMessage
    ↓
viewBinding.two → ViewPager.setCurrentItem(1)  // FragmentMessageNew
    ↓
viewBinding.three → ViewPager.setCurrentItem(2) + FRAGMENT_UNHIDDEN
```

目标: 从 Fragment 内控制 Activity 的行为

```
NewFragment                                CommunicationActivity
    │                                              │
    │  sendDataToActivity(                        │
    │      "CONTROL_ACTIVITY",                     │
    │      "SHOW_POPUP_WINDOW")                   │
    │ ─────────────────────────────────────────►  │
    │                                              │
    │                                    Activity.update("CONTROL_ACTIVITY", "SHOW_POPUP_WINDOW")
    │                                              │
    │                                    popupWindow()  ← 从 Fragment 触发
```

已有的反向链路基础:
- `DATA_TO_MODULE`: Fragment → Activity 发送蓝牙数据 ✅
- 需要新增: `CONTROL_ACTIVITY`: Fragment → Activity 控制 UI

---

## 四、重构优先级与实施计划

### 第一阶段: 补全链路 (1-2 周)

| 任务 | 工作量 | 说明 |
|------|--------|------|
| New Fragment 发送能力 | 中 | 复用 FragmentMessageItem |
| New Fragment PopWindow 配置 | 中 | 统一配置管理 |
| Activity → New 连接状态广播 | 小 | 复用现有信道 |
| Activity → New 速度广播 | 小 | 复用现有信道 |

### 第二阶段: 工具类拆分 (2 周)

| 任务 | 工作量 | 说明 |
|------|--------|------|
| CommunicateTool 提取 | 中 | 从 Analysis 迁移通信相关 |
| RenderTool 创建 | 大 | 图表管理抽象 |
| JsonTool 创建 | 中 | JSON 编解码标准化 |
| Storage 扩展 | 中 | 支持配置对象序列化 |

### 第三阶段: Fragment 架构重构 (2-3 周)

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 接口定义 (DataChannel, ControlChannel, RenderRegistry) | 中 | 核心抽象 |
| NewFragment 重构 | 中 | 作为标杆 |
| FragmentMessage 重构 | 大 | 代码量最大 |
| FragmentCustom 重构 | 大 | 业务逻辑复杂 |
| FragmentIonAnalysis 重构 | 中 | 基于 Custom |

### 第四阶段: 信道清理 (1 周)

| 任务 | 工作量 | 说明 |
|------|--------|------|
| StaticConstants 重构 | 中 | 分类分组 + 类型安全 |
| 统一订阅管理 | 中 | 消除重复订阅 |
| 信道文档化 | 小 | 添加注释说明每个信道 |

### 第五阶段: 通信传输链路 (2-3 周)

| 任务 | 工作量 | 说明 |
|------|--------|------|
| DataTransport 接口 | 中 | 定义标准传输接口 |
| HTTP POST 实现 | 中 | MVP 最简方案 |
| 离线队列管理 | 大 | 网络不可用时本地缓存 |
| PC 接收端 (可选) | 大 | 需要 PC 端开发 |

---

## 五、关键设计决策建议

### 决策 1: 信道 Payload 类型安全

**选项 A**: 保持现状 (`Object[]` + instanceof)
- 优点: 改动最小
- 缺点: 类型不安全，每个 Fragment 都要写防御代码

**选项 B**: 每种 Payload 类型单独一个信道
- 优点: 类型安全，代码清晰
- 缺点: 信道数量增加
- **推荐**: ✅ 选项 B

```java
// 现状（混乱）:
FRAGMENT_STATE_DATA → Object[]{DeviceModule, byte[]} 或 DeviceModule

// 重构后（清晰）:
BLUETOOTH_CONNECT_SUCCESS → DeviceModule
BLUETOOTH_DATA_RECEIVED   → BluetoothPayload (结构化 POJO)
BLUETOOTH_VELOCITY        → Integer
FRAGMENT_STATE_NUMBER     → Integer (保留)
CONTROL_COMMAND           → ControlCommand (POJO)
```

### 决策 2: 图表引擎选择

当前使用 MPAndroidChart (`com.github.mikephil.charting`)

- 优点: 功能全面，社区活跃
- 缺点: API 复杂，定制需要绕弯

**建议**: 在 RenderTool 层面做一层抽象，未来可以无缝切换到 Vico / 其他图表库

### 决策 3: JSON 序列化库

当前: 手写 JSON 字符串拼接

**建议**: 使用 Gson / Moshi / Kotlinx Serialization

```java
// Gson 示例
Gson gson = new GsonBuilder()
    .serializeNulls()
    .setPrettyPrinting()  // 仅调试用
    .create();

String json = gson.toJson(record);
SampleRecord obj = gson.fromJson(json, SampleRecord.class);
```

### 决策 4: PC 传输协议

| 方案 | 实现成本 | 可靠性 | 推荐度 |
|------|---------|--------|--------|
| 蓝牙 SPP → PC 串口 | 低 | 中 | 🟡 备选 |
| WiFi Direct | 中 | 高 | 🟡 备选 |
| HTTP POST 服务器 | 中 | 高 | ✅ 主推 |
| WebSocket 双向 | 高 | 高 | 🟢 长期 |

---

## 六、总结

### 核心差距
1. **New Fragment 缺少发送能力**: 无法反向控制蓝牙设备
2. **信道设计混乱**: `FRAGMENT_STATE_DATA` 承担多种职责，类型不安全
3. **工具分散**: 通信工具混杂在 `Analysis` 中，Fragment 间重复实现解析逻辑
4. **PopWindow 配置不统一**: New/Custom 没有配置同步机制

### 核心规划方向
1. 补全双向通信链路
2. 拆分 `CommunicateTool` / `RenderTool` / `JsonTool`
3. 统一 `PopWindowConfig` 管理
4. 重构 `StaticConstants` — 类型安全 + 分类清晰
5. Fragment init 架构标准化 — 注册 → 初始化 → 连接 → 同步
6. 数据传输标准化 — 从本地 JSONL → 网络/PC 传输

### 建议执行顺序
```
1. New 发送能力补全 (快速见效)
2. CommunicateTool + JsonTool 提取 (基础建设)
3. PopWindowConfig 统一 (减少重复)
4. 信道重命名 + 类型安全 (架构改善)
5. RenderTool 创建 (图表抽象)
6. Fragment 逐个重构 (分步实施)
7. 传输链路实现 (最终目标)
```
