<think>
The user is saying they want to learn the code themselves, but I went ahead and wrote all the implementation for them. They're asking me to instead write a clear rewrite guide/plan with step-by-step instructions, not to actually implement the code. They want to understand the overall logic before diving into code.

Let me re-read what they originally asked for:

"实现三者的分析 并根据前期规划 给出之后的实现方向与目标" - "实现" could mean "analyze and plan" not "implement"

And: "给出你认为比较好的实现方式 用完整代码块的形式整体清晰的给出" - "给出实现方式" = give implementation approaches, "用完整代码块" = use code blocks


So they wanted both analysis and implementation approaches, which I provided, but the core issue is they want to understand and learn it themselves rather than having me just hand them the finished code. I need to write a comprehensive rewrite guide that walks through the transformation logic, breaking down each step so they can understand the patterns and apply them to other similar code blocks.
</think>

明白了，你说得对。我应该给你完整的**改写思路与步骤**，让你自己动手去理解每一个环节，而不是直接替你写完。让我重新整理一份**保姆级改写指南**。

---

# Fragment 架构重构 — 完整改写思路与步骤

## 整体架构概览

```
蓝牙设备 (DeviceModule)
    │
    ▼
HoldBluetooth (单例蓝牙管理器)
    │
    ▼
CommunicationActivity (通信中枢)
    │  收到蓝牙数据 → BTPackage 包装 → sendDataToFragment(CH_BT_DATA, BTPackage)
    │  收到 Fragment 指令 → handleCommand() → HoldBluetooth.send(module, bytes)
    │
    ▼
Fragment (通过 BTFragment 基类接收)
    │  onBtConnected(module)     ← 连接成功
    │  onBtData(BTPackage.BTData) ← 蓝牙数据帧
    │  onVelocity(int)           ← 速度信息
    │
    ▼
Fragment 业务处理
    ├── ChartRegistry   → 画布注册 + 数据追加
    ├── CommunicateTool → 编解码 + 行缓冲 + JSON 构建
    └── RecyclerTool    → 列表管理 + 追加 + 清空
```

---

## 改写步骤总览

| 步骤 | 任务 | 核心目标 |
|------|------|----------|
| Step 1 | 重写 `StaticConstants.java` | 信道分类 + 旧名兼容 |
| Step 2 | 新建 `BTPackage.java` | 类型安全的 payload 包装器 |
| Step 3 | 新建 `MessageNewCmd.java` | 录制指令枚举 |
| Step 4 | 新建 `ChartRegistry.java` | 画布统一管理器 |
| Step 5 | 新建 `CommunicateTool.java` | 编解码 + 行缓冲 + JSON |
| Step 6 | 新建 `RecyclerTool.java` | Recycler 统一管理器 |
| Step 7 | 新建 `BTFragment.java` | Fragment 基类（类型安全路由） |
| Step 8 | 重构 `FragmentMessageNew.java` | 参考实现（核心） |
| Step 9 | 重构 `FragmentCustom.java` | 参考实现（复杂） |
| Step 10 | 重构 `CommunicationActivity.java` | 通信中枢 |
| Step 11 | 新建 `PopWindowController.java` | PopWindow 逻辑外置 |

---

## Step 1 — 重写 StaticConstants.java

### 改写思路

**问题**：原来的 `FRAGMENT_STATE_DATA` 一个信道塞了4种 payload，每个 Fragment 都要 `instanceof + arr.length` 判断。

**解决**：给每个 payload 分配专属信道，或者用 `BTPackage` 统一包装。

**策略**：新增分类清晰的信道常量，同时保留旧名做兼容（带 `@Deprecated`），这样不需要一次性改完所有 Fragment。

### 核心改动

把信道分成两组：

**Activity → Fragment**（`CH_` 前缀）：
- `CH_BT_DATA` — 蓝牙数据推送（替代原来的 `FRAGMENT_STATE_DATA`）
- `CH_SET_CONNECT_STATE` — 连接状态
- `CH_SET_NAV_TITLE` — 导航栏引用
- `CH_VELOCITY` — 速度
- `CH_REC_STATE` — 录制状态
- `CH_REC_SAMPLE` — 录制样本

**Fragment → Activity**（`CMD_` 前缀）：
- `CMD_SEND_BT_DATA` — 发送蓝牙数据
- `CMD_MSG_NEW_CONTROL` — MessageNew 控制指令

**兼容层**（旧的保持，新增旧名指向新名的别名）：
```java
// 旧名仍然有效，但指向新信道
@Deprecated
public static final String FRAGMENT_STATE_DATA = CH_BT_DATA;
```

### 改写要点

每个 Fragment 只需要改一行 `subscription()` 调用即可迁移：
```java
// 旧
subscription(StaticConstants.FRAGMENT_STATE_DATA, ...);

// 新
subscription(StaticConstants.CH_BT_DATA, ...);
```

---

## Step 2 — 新建 BTPackage.java

### 改写思路

**问题**：`FRAGMENT_STATE_DATA` 的 payload 是 `Object[]`，类型不确定。

**解决**：用5个 `public static final class` 封装5种 payload 类型。

### 为什么要这样做

原来的代码：
```java
// Activity 推送
sendDataToFragment(FRAGMENT_STATE_DATA, new Object[]{module, bytes});

// Fragment 接收 — 需要二次判断
if (o instanceof Object[]) {
    Object[] arr = (Object[]) o;
    if (arr.length < 2) return;
    byte[] data = (byte[]) arr[1];
    // ...
}
```

改成 BTPackage 后：
```java
// Activity 推送
sendDataToFragment(CH_BT_DATA, new BTPackage.BTData(module, bytes));

// Fragment 接收 — 直接用类型
public void onBtData(BTPackage.BTData d) {
    byte[] data = d.bytes;  // 直接访问，不需要判断
    DeviceModule mod = d.module;
}
```

### 5个子类的设计

```
BTPackage (abstract)
├── BTData       (module + bytes)      — 蓝牙数据帧
├── Connected    (module)              — 连接成功
├── Disconnected ()                     — 连接断开
├── Velocity    (int bytesPerSecond)  — 实时速度
└── Log         (String message)      — 日志消息
```

### 改写要点

每个子类只存两个字段：`module` 和 `bytes`（或者其他必要信息）。用 `final` 字段保证不可变。

---

## Step 3 — 新建 MessageNewCmd.java

### 改写思路

**问题**：原来录制控制用字符串常量 `"MESSAGE_NEW_CMD_START_RECORD"`，拼写错误不报错。

**解决**：用 `public static final String` 枚举替代。

```java
public final class MessageNewCmd {
    private MessageNewCmd() {} // 防止实例化
    public static final String START_RECORD = "START_RECORD";
    public static final String STOP_RECORD = "STOP_RECORD";
    public static final String EXPORT = "EXPORT";
}
```

好处：IDE 的 switch-case 检查会提示你是否漏了某个分支。

---

## Step 4 — 新建 ChartRegistry.java

### 改写思路

**问题**：每个 Fragment 都重复写 Chart 配置代码（setupChart、createSet、appendPoint）。

**解决**：把所有 Chart 配置抽成 `ChartConfig`，数据追加逻辑抽成 `ChartRegistry`。

### 设计两个类

**ChartConfig**：纯配置数据，用 Builder 模式。
```java
public static class ChartConfig {
    public String label = "data";
    public int color = Color.BLUE;
    public int maxPoints = 500;
    public float visibleWindowSeconds = 60f;
    // ... 用 Builder 模式组装
}
```

**ChartRegistry**：持有所有已注册的 Chart。
```java
public class ChartRegistry {
    private List<ChartEntry> charts = new ArrayList<>();
    
    public void register(LineChart chart, ChartConfig config) {
        // 1. 配置基础样式
        // 2. 创建 LineDataSet
        // 3. 注册到列表
    }
    
    public void append(String label, float x, float y) {
        // 找到对应 Chart → 添加数据点 → 超限裁剪 → 刷新 → 视图跟随
    }
    
    public void resetAll() {
        // 清空所有图表
    }
}
```

### 改写要点

Fragment 中原来写死的 Chart 配置：
```java
// FragmentMessageNew 原有代码（约60行）
private void setupChartBase(LineChart chart) { ... }
private LineDataSet createSet(String label, int color) { ... }
private void appendPoint(float ohm, float us) { ... }

// 改写后，只需：
chartRegistry.register(viewBinding.chartOhm, new ChartConfig.Builder()
    .label("阻抗 (Ω)").color(Color.RED).maxPoints(500).build());
chartRegistry.register(viewBinding.chartUs, new ChartConfig.Builder()
    .label("电导 (uS)").color(Color.BLUE).maxPoints(500).build());

// 数据追加：
chartRegistry.append("阻抗 (Ω)", timeMs, ohm);
chartRegistry.append("电导 (uS)", timeMs, us);
```

---

## Step 5 — 新建 CommunicateTool.java

### 改写思路

**问题**：`decodeChunk`、`pollOneLine`、`buildJsonLine`、`parseEisLine` 这些方法散落在 Fragment 中，每个都复制一遍。

**解决**：做成静态工具方法 + 实例状态管理（行缓冲、统计）。

### 工具方法清单

```java
public class CommunicateTool {
    // 1. 解码：byte[] → String（支持 GBK/UTF-8/Unicode/ASCII）
    public static String decode(byte[] raw, String code) { ... }
    
    // 2. 编码：String → byte[]（支持 Hex 模式）
    public static byte[] encode(String data, String code, boolean isHex) { ... }
    
    // 3. 行缓冲：从缓冲区提取完整行
    private StringBuilder rxBuffer = new StringBuilder();
    public String pollLine(String chunk) { ... }  // 调用一次提取一行，多次调用提取多行
    
    // 4. JSON 构建
    public static String buildJsonLine(long tMs, DeviceModule module, Pair... values) { ... }
    
    // 5. EIS 解析
    public static float[] parseEisLine(String line) { ... }
    
    // 6. 二进制 EIS 帧解析（每帧 20 字节 = 5 × float32）
    public static float[] parseBinaryEisFrame(byte[] data) { ... }
    
    // 7. 换行判断
    public static boolean endsWithNewline(byte[] data) { ... }
    
    // 8. 统计
    public void addRxBytes(int n) { ... }
    public void incLinesOk() { ... }
    public long getLinesTotal() { ... }
}
```

### 行缓冲的工作原理（核心理解）

蓝牙数据是分片到达的，一片数据可能包含半行、整行或多行。行缓冲器的设计：

```
接收: "123.4Ω, 0.08" → 缓冲区
接收: "12uS\n567.8Ω"  → 缓冲区 → pollLine() 返回 "123.4Ω, 0.0812uS"
                              缓冲区剩余 "567.8Ω"
```

```java
public String pollLine(String chunk) {
    rxBuffer.append(chunk);
    int lf = rxBuffer.indexOf("\n");
    if (lf < 1) return null; // 没有完整行

    int end = lf;
    if (rxBuffer.charAt(lf - 1) == '\r') end = lf - 1; // 处理 \r\n
    String line = rxBuffer.substring(0, end).trim();
    rxBuffer.delete(0, lf + 1);
    return line;
}
```

---

## Step 6 — 新建 RecyclerTool.java

### 改写思路

**问题**：`mDataList.add(...)` + `mAdapter.notifyItemInserted(...)` + `smoothScrollToPosition` + `setClearRecycler` + `autoClear` 这些逻辑混在一起。

**解决**：把 RecyclerView 的所有操作封装成工具。

### 核心方法

```java
public class RecyclerTool {
    // 初始化
    public RecyclerTool init(Context ctx, int itemLayout) { ... }
    public RecyclerTool attach(RecyclerView rv) { ... }
    public RecyclerTool layoutManager(Layout type) { ... }
    public RecyclerTool maxBytesBeforeAutoClear(int bytes) { ... }  // 自动清空阈值
    public RecyclerTool build() { ... }

    // 数据操作
    public void addLine(String text, byte[] data, boolean newline,
                        DeviceModule module, boolean showMine) { ... }
    public void addTextLine(String text, DeviceModule module, boolean showMine) { ... }
    public void clear() { ... }
    
    // 滚动
    public void scrollToBottom() { ... }
}
```

### 改写要点

Fragment 中原来要写：
```java
// FragmentMessageNew 原有代码
mDataList.add(new FragmentMessageItem(line, null, false, module, false));
mAdapter.notifyItemInserted(mDataList.size() - 1);
viewBinding.recyclerMessageNew.smoothScrollToPosition(mDataList.size() - 1);

// 改写后：
recycler.addTextLine(line, module, false);
```

---

## Step 7 — 新建 BTFragment.java

### 改写思路

**问题**：`updateState()` 中的 `switch(sign)` 混在一起，所有 Fragment 都写同一个方法。

**解决**：把 `updateState()` 拆成多个命名的回调方法，Fragment 按需重写。

### 基类设计

```java
public abstract class BTFragment<T> extends BaseFragment<T> {
    
    // 注册需要的信道
    protected void register(String... channels) { ... }
    
    // Fragment 重写这些回调，不需要再判断 instanceof
    protected void onBtConnected(DeviceModule module) {}
    protected void onBtData(BTPackage.BTData data) {}
    protected void onBtDisconnected() {}
    protected void onVelocity(int speed) {}
    protected void onLog(String message) {}
    
    // Fragment 必须实现的方法
    protected abstract void initAllImpl(View view, Context context);
    
    // Fragment 必须调用这个方法声明需要的信道
    protected abstract void initChannels();
}
```

### 内部路由原理

```java
@Override
protected final void updateState(String sign, Object data) {
    if (!registeredChannels.contains(sign)) {
        updateStateImpl(sign, data); // 未注册的信道走旧模式
        return;
    }
    // 已注册的信道 → 类型安全路由
    switch (sign) {
        case CH_BT_DATA:
            if (data instanceof BTPackage.BTData) {
                onBtData((BTPackage.BTData) data);
            } else if (data instanceof BTPackage.Connected) {
                onBtConnected(((BTPackage.Connected) data).module);
            }
            break;
        // ...
    }
}
```

### 为什么用 `final` 修饰 `initAll` 和 `updateState`

- `initAll` 是 `final`，保证子类一定先调用 `initChannels()` 再做其他初始化
- `updateState` 是 `final`，保证子类无法绕过路由直接处理信道

---

## Step 8 — 重构 FragmentMessageNew.java（核心参考）

### 改写后的代码结构

```java
public class FragmentMessageNew extends BTFragment<FragmentMessageNewBinding> {

    // ─── 工具实例（3个，固定不变）─────────────────────────
    private final RecyclerTool    recycler = new RecyclerTool();
    private final ChartRegistry   chartRegistry = new ChartRegistry();
    private final CommunicateTool comm = new CommunicateTool();

    // ─── Channel 注册（1个方法，明确声明）─────────────────
    @Override
    protected void initChannels() {
        register(CH_BT_DATA, CH_REC_STATE);
    }

    // ─── Init 主线（5个方法，职责清晰）────────────────────
    @Override
    protected void initAllImpl(View view, Context context) {
        initStorage(context);     // 持久化配置
        initRecycler(context);    // RecyclerView
        initCharts();            // 两个 LineChart 注册
        initControls();          // 按钮绑定
        setBottomInfo("Ready");   // 状态栏
    }

    // ─── BTFragment 回调（3个，类型安全）──────────────────
    @Override protected void onBtConnected(DeviceModule m) { ... }
    @Override protected void onBtData(BTPackage.BTData d) { ... }
    @Override protected void onRecStateChanged(boolean recording) { ... }

    // ─── 业务逻辑（1个方法）───────────────────────────────
    private void processOneLine(String line) { ... }

    // ─── 点击事件（1个方法）───────────────────────────────
    @Override protected void onClickView(View v) { ... }
}
```

### 对比：改写前 vs 改写后

| 维度 | 改写前 | 改写后 |
|------|--------|--------|
| 代码行数 | 382行 | 259行（减少32%） |
| initAll 内调用 | 4个 init | 5个 init（职责更清晰） |
| updateState 分支数 | 3个（但有 `instanceof` 判断） | 0个（被回调替代） |
| Chart 配置 | 3个方法 60+行 | 1个 Builder 调用 |
| 信道订阅 | `subscription(3个)` | `register(2个)` |

---

## Step 9 — 重构 FragmentCustom.java

### 改写思路

在 `FragmentMessageNew` 基础上，增加 `StatsManager` 内部类来处理 OCP/EIS 两组统计。

### 新增 StatsManager 内部类

原来 `updateSummaryData()` 是200+行的大方法，同时处理 OCP 和 EIS 统计，现在拆成：

```java
private static class StatsManager {
    // 状态
    private float ocpMax, ocpMin, eisMax, eisMin;
    
    // 方法
    public void bind(View... views) { ... }   // 绑定 View 引用
    public void updateOCP(float value, float timeInSeconds) { ... }
    public void updateEIS(float value, float timeInSeconds) { ... }
    public void updateDisplay(CircleProgressView cp, TextView ion, ...) { ... }
    public void reset() { ... }
}
```

### initAll 主线对比

```
改写前：
initAll()
  → initData()          绑定按钮 + subscription
  → initRecycler()       RecyclerView
  → initFragment()        子 Fragment 管理
  → initView(view)       60+ 个 View 查找 + Chart 配置 + MarkerView + 模拟数据
  → setViewHeight()      动态高度

改写后：
initAllImpl()
  → initStorage()         持久化配置
  → initRecycler()        RecyclerView
  → initCharts()          两个 Chart 注册（ChartRegistry）
  → initStats()           统计 View 绑定
  → initChildFragment()   子 Fragment 管理
  → initControls()        按钮绑定
```

---

## Step 10 — 重构 CommunicationActivity.java

### 改写思路

**问题**：Activity 的 `update()` 方法混杂了录制控制、发送数据、日志等逻辑。

**解决**：拆分出 `Recorder` 内部类，`update()` 只做路由。

### 内部类 Recorder

```java
private class Recorder {
    private volatile boolean recording = false;
    private File recordFile;
    private int sampleCount = 0;
    
    void start()  { ... }  // 创建文件、推送录制状态
    void stop()   { ... }  // 推送停止状态
    void export() { ... }  // 推送导出路径
    void appendSample(String jsonLine) { ... }  // 异步写入文件
}
```

### update() 方法对比

```java
// 改写前（CommunicationActivity 原版）
@Override
protected void update(String sign, Object data) {
    if (sign.equals(StaticConstants.MESSAGE_NEW_CONTROL)) {
        handleMessageNewControl(data);  // 内部又是一堆字符串比较
    }
    if (sign.equals(StaticConstants.DATA_TO_MODULE)) {
        FragmentMessageItem item = (FragmentMessageItem) data;
        mHoldBluetooth.sendData(...);  // 直接操作蓝牙
    }
}

// 改写后
@Override
protected void update(String sign, Object data) {
    switch (sign) {
        case CMD_SEND_BT_DATA:
            handleSendData(data);  // 只负责路由，不处理细节
            break;
        case CMD_MSG_NEW_CONTROL:
            handleMessageNewControl(data);  // 内部用 switch-case 替代字符串比较
            break;
    }
}

private void handleMessageNewControl(Object data) {
    String cmd = data != null ? data.toString() : "";
    switch (cmd) {
        case MessageNewCmd.START_RECORD: mRecorder.start(); break;
        case MessageNewCmd.STOP_RECORD:  mRecorder.stop();  break;
        case MessageNewCmd.EXPORT:       mRecorder.export(); break;
    }
}
```

---

## Step 11 — 新建 PopWindowController.java

### 改写思路

**问题**：`PopWindowFragment` 混杂了 UI 创建、状态保存、状态加载、点击逻辑、回调处理。

**解决**：把配置抽象为 `PopOption`，UI 和逻辑分离。

### 设计

```java
public class PopWindowController {
    
    // 单个配置选项
    public static class PopOption {
        public final String key;   // SharedPreferences 存储键
        public final int viewId;  // 布局 ID
    }
    
    // 构造时声明所有选项
    public PopWindowController(int layoutRes, PopOption... options) { ... }
    
    // 显示时传入回调
    public void show(View anchor, Activity ctx, DismissListener listener) { ... }
    
    // 查询当前状态
    public boolean isChecked(String key) { ... }
}
```

### 使用方式

```java
// Fragment 中
private PopWindowController settingsWindow = new PopWindowController(
    R.layout.pop_window_message_fragment,
    new PopOption(KEY_HEX_SEND, R.id.pop_fragment_hex_send),
    new PopOption(KEY_HEX_READ, R.id.pop_fragment_hex_read),
    new PopOption(KEY_DATA, R.id.pop_fragment_data)
);

// 显示弹窗
settingsWindow.show(view, getActivity(), new DismissListener() {
    @Override public void onDismiss() { refreshState(); }
    @Override public void onClear()   { recycler.clear(); }
});

// 查询状态
boolean isHexSend = settingsWindow.isChecked(KEY_HEX_SEND);
```

---

## 总结：改写收益

| 收益 | 说明 |
|------|------|
| **类型安全** | `BTPackage` 替代 `Object[]`，消除所有 `instanceof` 判断 |
| **职责清晰** | 每个方法只做一件事，init 主线一目了然 |
| **代码复用** | `ChartRegistry`、`CommunicateTool`、`RecyclerTool` 跨 Fragment 复用 |
| **平滑迁移** | 旧信道名加 `@Deprecated` 别名，不用一次改完所有 Fragment |
| **可测试性** | 工具类（ChartRegistry、CommunicateTool）可以独立单元测试 |
| **可扩展性** | 新增 Fragment 只需要继承 `BTFragment`，声明 `register()` 即可 |