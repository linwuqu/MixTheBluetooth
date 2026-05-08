# Unified Message/Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 `UnifiedMessageFragment + MessageController + ProfileSpec` 架构，让 profile 脱离 ViewBinding，并把新通信页收敛到 `CMD_BT_POST` / `CH_BT_EVENT` 两个主信道。

**Architecture:** `UnifiedMessageFragment` 继承 `BTFragment`，只负责 Android 生命周期、binding 容器和 gateway；`MessageController` 作为嵌套/同文件控制器，按 `ProfileSpec` 动态创建 actions、charts、indicators，并运行 producer/consumer 管道；profile 用 Java builder 声明 parser、charts、actions、record 格式，不碰 Fragment、Activity 或具体 View。

**Tech Stack:** Android Java, ViewBinding, LiveEventBus, MPAndroidChart, JUnit4, Gradle.

---

## 文件结构

第一版严格控制文件分散度。

- Create: `app/src/main/res/layout/fragment_unified_message.xml`
  - 统一通信页容器布局，只提供 `actionContainer`、`indicatorContainer`、`chartContainer`、`recyclerMessage`、`tvBottomInfo`。
- Create: `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
  - public `UnifiedMessageFragment`
  - 同文件嵌套/内部结构：`MessageController`、`ProfileSpec`、`Route`、`ActionSpec`、`ChartSpec`、`IndicatorSpec`、`HostView`、`BluetoothGateway`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java`
  - 扁平 profile 定义，第一阶段只放 EIS。
  - EIS parser、EIS JSON formatter 和 command/action 声明集中在一个文件。
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/single/StaticConstants.java`
  - 新增 `CMD_BT_POST`、`CH_BT_EVENT`。
  - 标记旧信道为迁移兼容路径。
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/single/BTPackage.java`
  - 增加统一事件/发送 payload 类型。
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
  - 同时支持旧 `CMD_SEND_BT_DATA` 和新 `CMD_BT_POST`。
  - 新页面使用 `CH_BT_EVENT`，旧页面继续收到旧信道。
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
  - 页面入口先把“新消息页”替换为 `UnifiedMessageFragment`，保留旧 FM 作为对照。
- Test: `app/src/test/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragmentTest.java`
  - 用 fake host/gateway 测 controller/spec 接线，不测 Android 真实渲染细节。

---

### Task 0: 保护当前工作区

**Files:**
- Inspect only: current git worktree

- [ ] **Step 1: 查看当前未提交修改**

Run:

```powershell
git status --short
```

Expected: 能看到已有临时实现改动和新增 spec 文档。不要执行 `git reset --hard`，不要回滚用户改动。

- [ ] **Step 2: 确认当前计划文件存在**

Run:

```powershell
Test-Path docs\superpowers\specs\2026-05-08-unified-message-profile-architecture-design.md
```

Expected: 输出 `True`。

- [ ] **Step 3: 记录实现原则**

During implementation, keep this rule visible:

```text
不把架构拆成大量文件。
第一版核心实现集中在 UnifiedMessageFragment.java 和 Profiles.java。
旧信道先兼容，新信道先跑通。
```

---

### Task 1: 增加两个主信道和统一 payload

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/single/StaticConstants.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/single/BTPackage.java`

- [ ] **Step 1: 在 `BTPackage` 中先写统一 payload 类型**

Modify `app/src/main/java/com/hc/mixthebluetooth/activity/single/BTPackage.java` by adding these nested classes before the final closing brace:

```java
    public static final class BTPost extends BTPackage {
        @NonNull
        public final DeviceModule module;

        @NonNull
        public final byte[] bytes;

        public BTPost(@NonNull DeviceModule module, @NonNull byte[] bytes) {
            this.module = module;
            this.bytes = bytes;
        }
    }

    public static final class ConnectState extends BTPackage {
        @NonNull
        public final String state;

        public ConnectState(@NonNull String state) {
            this.state = state;
        }
    }

    public static final class SentBytes extends BTPackage {
        public final int count;

        public SentBytes(int count) {
            this.count = count;
        }
    }

    public static final class StopLoopSend extends BTPackage {
        public static final StopLoopSend INSTANCE = new StopLoopSend();

        private StopLoopSend() {
        }
    }
```

- [ ] **Step 2: 在 `StaticConstants` 增加新主信道**

Modify `app/src/main/java/com/hc/mixthebluetooth/activity/single/StaticConstants.java` near the top-level constants:

```java
    // Unified Message: Fragment/Controller -> Activity.
    public static final String CMD_BT_POST = "CMD_BT_POST";

    // Unified Message: Activity -> Fragment/Controller.
    public static final String CH_BT_EVENT = "CH_BT_EVENT";
```

- [ ] **Step 3: 把旧主路径标记成迁移兼容**

In `StaticConstants.java`, update comments around old constants to make direction explicit:

```java
    // Legacy Activity -> Fragment channels. New UnifiedMessage code should use CH_BT_EVENT.
    public static final String CH_BT_DATA = "CH_BT_DATA";

    // Legacy Fragment -> Activity command. New UnifiedMessage code should use CMD_BT_POST.
    public static final String CMD_SEND_BT_DATA = "CMD_SEND_BT_DATA";
```

Do not delete old constants.

- [ ] **Step 4: 编译验证 payload 类型**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/activity/single/StaticConstants.java app/src/main/java/com/hc/mixthebluetooth/activity/single/BTPackage.java
git commit -m "refactor: add unified bluetooth channels"
```

---

### Task 2: 让 Activity 同时支持新旧信道

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`

- [ ] **Step 1: 扩展 Activity 订阅**

In `initSubscription()`, replace:

```java
subscription(StaticConstants.CMD_SEND_BT_DATA);
```

with:

```java
subscription(
        StaticConstants.CMD_SEND_BT_DATA,
        StaticConstants.CMD_BT_POST
);
```

- [ ] **Step 2: 扩展 Activity command dispatch**

In `update(...)`, replace:

```java
if (sign.equals(StaticConstants.CMD_SEND_BT_DATA)) {
    onSendBtDataCommand(data);
} else {
    logWarn("Unknown activity command: " + sign);
}
```

with:

```java
if (sign.equals(StaticConstants.CMD_SEND_BT_DATA)) {
    onSendBtDataCommand(data);
} else if (sign.equals(StaticConstants.CMD_BT_POST)) {
    onBtPostCommand(data);
} else {
    logWarn("Unknown activity command: " + sign);
}
```

- [ ] **Step 3: 增加新 POST 处理函数**

Add below `onSendBtDataCommand(...)`:

```java
    private void onBtPostCommand(Object data) {
        if (!(data instanceof BTPackage.BTPost)) {
            logWarn("Ignore BT post command, payload is not BTPost: " + data);
            return;
        }

        BTPackage.BTPost post = (BTPackage.BTPost) data;
        mHoldBluetooth.sendData(post.module, post.bytes.clone());
    }
```

- [ ] **Step 4: 双发蓝牙数据事件**

In the method that currently publishes `CH_BT_DATA` with `new BTPackage.BTData(module, data)`, keep old publish and add new publish:

```java
sendDataToFragment(
        StaticConstants.CH_BT_DATA,
        new BTPackage.BTData(module, data)
);
sendDataToFragment(
        StaticConstants.CH_BT_EVENT,
        new BTPackage.BTData(module, data)
);
```

- [ ] **Step 5: 双发连接事件**

In `publishBtConnected(DeviceModule module)`, keep old publish and add:

```java
sendDataToFragment(
        StaticConstants.CH_BT_EVENT,
        new BTPackage.Connected(module)
);
```

In `publishBtDisconnected()`, keep old publish and add:

```java
sendDataToFragment(
        StaticConstants.CH_BT_EVENT,
        BTPackage.Disconnected.INSTANCE
);
```

- [ ] **Step 6: 双发状态和字节事件**

In `publishConnectState(String state)`, keep old publish and add:

```java
sendDataToFragment(
        StaticConstants.CH_BT_EVENT,
        new BTPackage.ConnectState(state)
);
```

In `publishSentBytes(int number)`, keep old publish and add:

```java
sendDataToFragment(
        StaticConstants.CH_BT_EVENT,
        new BTPackage.SentBytes(number)
);
```

In `publishStopLoopSend()`, keep old publish and add:

```java
sendDataToFragment(
        StaticConstants.CH_BT_EVENT,
        BTPackage.StopLoopSend.INSTANCE
);
```

- [ ] **Step 7: 编译验证 Activity 兼容**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`。旧 Fragment 仍然编译，因为旧信道没有删除。

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java
git commit -m "refactor: bridge activity to unified bluetooth event channel"
```

---

### Task 3: 创建统一通信页 layout

**Files:**
- Create: `app/src/main/res/layout/fragment_unified_message.xml`

- [ ] **Step 1: 创建统一 layout**

Create `app/src/main/res/layout/fragment_unified_message.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:keepScreenOn="true">

    <LinearLayout
        android:id="@+id/actionContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <LinearLayout
        android:id="@+id/indicatorContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:paddingBottom="6dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/actionContainer" />

    <ScrollView
        android:id="@+id/chartScroll"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:fillViewport="false"
        app:layout_constraintBottom_toTopOf="@id/recyclerMessage"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/indicatorContainer">

        <LinearLayout
            android:id="@+id/chartContainer"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingStart="10dp"
            android:paddingTop="4dp"
            android:paddingEnd="10dp"
            android:paddingBottom="8dp" />
    </ScrollView>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerMessage"
        android:layout_width="0dp"
        android:layout_height="120dp"
        android:clipToPadding="false"
        android:paddingBottom="10dp"
        app:layout_constraintBottom_toTopOf="@id/tvBottomInfo"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

    <TextView
        android:id="@+id/tvBottomInfo"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="@drawable/window_back"
        android:padding="10dp"
        android:text="Ready"
        android:textColor="@color/black"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 2: 生成 ViewBinding 验证**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `FragmentUnifiedMessageBinding` generated and `BUILD SUCCESSFUL`。

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/res/layout/fragment_unified_message.xml
git commit -m "feat: add unified message layout"
```

---

### Task 4: 写 UnifiedMessageFragment 骨架和 spec 类型

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`

- [ ] **Step 1: 创建最小 Fragment 骨架**

Create `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`:

```java
package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.BluetoothSampleParser;
import com.hc.mixthebluetooth.activity.tool.Profiles;
import com.hc.mixthebluetooth.activity.tool.SampleRecorder;
import com.hc.mixthebluetooth.activity.tool.SampleRecorderImpl;
import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.databinding.FragmentUnifiedMessageBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnifiedMessageFragment extends BTFragment<FragmentUnifiedMessageBinding> {

    private MessageController controller;

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_EVENT);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        controller = new MessageController(
                requireContext(),
                Profiles.eis(),
                new BindingHost(viewBinding),
                new FragmentGateway()
        );
        controller.init();
    }

    @Override
    protected void updateStateImpl(String sign, Object data) {
        if (StaticConstants.CH_BT_EVENT.equals(sign) && controller != null) {
            controller.onEvent(data);
        }
    }

    @Override
    protected FragmentUnifiedMessageBinding getViewBinding() {
        return FragmentUnifiedMessageBinding.inflate(getLayoutInflater());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (controller != null) {
            controller.release();
        }
    }
```

Keep the class open; following steps add nested structures before the final closing brace.

- [ ] **Step 2: 添加 Host 和 Gateway**

Append inside `UnifiedMessageFragment`:

```java
    interface HostView {
        ViewGroup actionContainer();
        ViewGroup chartContainer();
        ViewGroup indicatorContainer();
        RecyclerView messageList();
        TextView bottomInfo();
    }

    interface BluetoothGateway {
        void postText(@NonNull DeviceModule module, @NonNull String text);
        void postBytes(@NonNull DeviceModule module, @NonNull byte[] bytes);
    }

    private static final class BindingHost implements HostView {
        private final FragmentUnifiedMessageBinding binding;

        BindingHost(@NonNull FragmentUnifiedMessageBinding binding) {
            this.binding = binding;
        }

        @Override public ViewGroup actionContainer() { return binding.actionContainer; }
        @Override public ViewGroup chartContainer() { return binding.chartContainer; }
        @Override public ViewGroup indicatorContainer() { return binding.indicatorContainer; }
        @Override public RecyclerView messageList() { return binding.recyclerMessage; }
        @Override public TextView bottomInfo() { return binding.tvBottomInfo; }
    }

    private final class FragmentGateway implements BluetoothGateway {
        @Override
        public void postText(@NonNull DeviceModule module, @NonNull String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            sendDataToActivity(StaticConstants.CMD_BT_POST, new BTPackage.BTPost(module, bytes));
        }

        @Override
        public void postBytes(@NonNull DeviceModule module, @NonNull byte[] bytes) {
            sendDataToActivity(StaticConstants.CMD_BT_POST, new BTPackage.BTPost(module, bytes.clone()));
        }
    }
```

- [ ] **Step 3: 添加 Route 和 Spec 小结构**

Append inside `UnifiedMessageFragment`:

```java
    public enum Route {
        POST,
        SUBSCRIBE,
        INNER
    }

    public enum BuiltIn {
        START_RECORD,
        STOP_RECORD,
        EXPORT,
        CLEAR_MESSAGES,
        RESET_CHARTS
    }

    public enum ChartType {
        LINE
    }

    public static final class ActionSpec {
        @NonNull public final String id;
        @NonNull public final String label;
        @NonNull public final Route route;
        @Nullable public final TextSupplier textSupplier;
        @Nullable public final BuiltIn builtIn;

        private ActionSpec(
                @NonNull String id,
                @NonNull String label,
                @NonNull Route route,
                @Nullable TextSupplier textSupplier,
                @Nullable BuiltIn builtIn
        ) {
            this.id = id;
            this.label = label;
            this.route = route;
            this.textSupplier = textSupplier;
            this.builtIn = builtIn;
        }

        public static ActionSpec postText(@NonNull String id, @NonNull String label, @NonNull TextSupplier textSupplier) {
            return new ActionSpec(id, label, Route.POST, textSupplier, null);
        }

        public static ActionSpec inner(@NonNull String id, @NonNull String label, @NonNull BuiltIn builtIn) {
            return new ActionSpec(id, label, Route.INNER, null, builtIn);
        }
    }

    public static final class ChartSpec {
        @NonNull public final String id;
        @NonNull public final String title;
        @NonNull public final String metricKey;
        @NonNull public final ChartType type;
        public final int color;

        private ChartSpec(@NonNull String id, @NonNull String title, @NonNull String metricKey, @NonNull ChartType type, int color) {
            this.id = id;
            this.title = title;
            this.metricKey = metricKey;
            this.type = type;
            this.color = color;
        }

        public static ChartSpec line(@NonNull String id, @NonNull String title, @NonNull String metricKey, int color) {
            return new ChartSpec(id, title, metricKey, ChartType.LINE, color);
        }
    }

    public static final class IndicatorSpec {
        @NonNull public final String id;
        @NonNull public final String label;
        @Nullable public final String metricKey;

        private IndicatorSpec(@NonNull String id, @NonNull String label, @Nullable String metricKey) {
            this.id = id;
            this.label = label;
            this.metricKey = metricKey;
        }

        public static IndicatorSpec metric(@NonNull String id, @NonNull String label, @NonNull String metricKey) {
            return new IndicatorSpec(id, label, metricKey);
        }
    }

    public interface TextSupplier {
        @NonNull String get();
    }

    public interface RecordFormatter {
        @NonNull String format(@NonNull BluetoothSample sample);
    }
```

- [ ] **Step 4: 添加 ProfileSpec builder**

Append inside `UnifiedMessageFragment`:

```java
    public static final class ProfileSpec {
        @NonNull public final String id;
        @NonNull public final List<BluetoothSampleParser> parsers;
        @NonNull public final List<ActionSpec> actions;
        @NonNull public final List<ChartSpec> charts;
        @NonNull public final List<IndicatorSpec> indicators;
        @Nullable public final RecordFormatter recordFormatter;

        private ProfileSpec(@NonNull Builder b) {
            this.id = b.id;
            this.parsers = new ArrayList<>(b.parsers);
            this.actions = new ArrayList<>(b.actions);
            this.charts = new ArrayList<>(b.charts);
            this.indicators = new ArrayList<>(b.indicators);
            this.recordFormatter = b.recordFormatter;
        }

        public static Builder builder(@NonNull String id) {
            return new Builder(id);
        }

        public static final class Builder {
            @NonNull private final String id;
            private final List<BluetoothSampleParser> parsers = new ArrayList<>();
            private final List<ActionSpec> actions = new ArrayList<>();
            private final List<ChartSpec> charts = new ArrayList<>();
            private final List<IndicatorSpec> indicators = new ArrayList<>();
            @Nullable private RecordFormatter recordFormatter;

            private Builder(@NonNull String id) {
                this.id = id;
            }

            public Builder parser(@NonNull BluetoothSampleParser parser) {
                parsers.add(parser);
                return this;
            }

            public Builder action(@NonNull ActionSpec action) {
                actions.add(action);
                return this;
            }

            public Builder chart(@NonNull ChartSpec chart) {
                charts.add(chart);
                return this;
            }

            public Builder indicator(@NonNull IndicatorSpec indicator) {
                indicators.add(indicator);
                return this;
            }

            public Builder recordJson(@NonNull RecordFormatter formatter) {
                recordFormatter = formatter;
                return this;
            }

            public ProfileSpec build() {
                return new ProfileSpec(this);
            }
        }
    }
```

- [ ] **Step 5: 添加 MessageController**

Append inside `UnifiedMessageFragment`, then close the class:

```java
    static final class MessageController {
        private static final int AUTO_CLEAR_BYTES = 400_000;

        private final Context context;
        private final ProfileSpec spec;
        private final HostView host;
        private final BluetoothGateway gateway;
        private final SampleRecorder recorder = new SampleRecorderImpl();
        private final ArrayList<FragmentMessageItem> messages = new ArrayList<>();
        private final HashMap<String, RealtimeLineChart> charts = new HashMap<>();
        private final HashMap<String, TextView> indicators = new HashMap<>();

        private FragmentMessAdapter adapter;
        private DeviceModule module;
        private int readBytes;
        private int sentBytes;

        MessageController(@NonNull Context context, @NonNull ProfileSpec spec, @NonNull HostView host, @NonNull BluetoothGateway gateway) {
            this.context = context;
            this.spec = spec;
            this.host = host;
            this.gateway = gateway;
        }

        void init() {
            adapter = new FragmentMessAdapter(context, messages, R.layout.item_message_fragment);
            host.messageList().setLayoutManager(new LinearLayoutManager(context));
            host.messageList().setAdapter(adapter);
            createSystemIndicators();
            createIndicators();
            createActions();
            createCharts();
            setBottomInfo("Ready");
        }

        void onEvent(@Nullable Object event) {
            if (event instanceof BTPackage.BTData) {
                onBtData((BTPackage.BTData) event);
            } else if (event instanceof BTPackage.Connected) {
                module = ((BTPackage.Connected) event).module;
            } else if (event instanceof BTPackage.Disconnected) {
                module = null;
            } else if (event instanceof BTPackage.SentBytes) {
                sentBytes += ((BTPackage.SentBytes) event).count;
                updateByteCounter();
            } else if (event instanceof BTPackage.ConnectState) {
                setBottomInfo(((BTPackage.ConnectState) event).state);
            }
        }

        void release() {
            recorder.release();
        }

        private void createSystemIndicators() {
            addIndicatorText("record_state", "Record: OFF");
            addIndicatorText("byte_counter", "Read: 0 B    Sent: 0 B");
        }

        private void createIndicators() {
            for (IndicatorSpec indicator : spec.indicators) {
                TextView tv = addIndicatorText(indicator.id, indicator.label + ": --");
                indicators.put(indicator.id, tv);
            }
        }

        private TextView addIndicatorText(@NonNull String id, @NonNull String text) {
            TextView tv = new TextView(context);
            tv.setText(text);
            host.indicatorContainer().addView(tv);
            indicators.put(id, tv);
            return tv;
        }

        private void createActions() {
            for (ActionSpec action : spec.actions) {
                Button button = new Button(context);
                button.setText(action.label);
                button.setOnClickListener(v -> handleAction(action));
                host.actionContainer().addView(button);
            }
        }

        private void createCharts() {
            for (ChartSpec chartSpec : spec.charts) {
                LinearLayout box = new LinearLayout(context);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setBackgroundResource(R.drawable.window_back);
                LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(170)
                );
                boxParams.setMargins(0, dp(6), 0, dp(8));
                host.chartContainer().addView(box, boxParams);

                LineChart chartView = new LineChart(context);
                box.addView(chartView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                RealtimeLineChart chart = new RealtimeLineChart(
                        chartView,
                        new RealtimeLineChart.Config.Builder()
                                .label(chartSpec.title)
                                .color(chartSpec.color)
                                .maxPoints(500)
                                .visibleWindowSeconds(60f)
                                .build()
                );
                charts.put(chartSpec.metricKey, chart);
            }
        }

        private void handleAction(@NonNull ActionSpec action) {
            if (action.route == Route.POST && action.textSupplier != null && module != null) {
                gateway.postText(module, action.textSupplier.get());
                return;
            }
            if (action.route == Route.INNER && action.builtIn != null) {
                runBuiltIn(action.builtIn);
            }
        }

        private void runBuiltIn(@NonNull BuiltIn builtIn) {
            if (builtIn == BuiltIn.START_RECORD) {
                resetCharts();
                recorder.start(context, "unified_" + spec.id);
                setRecordState(true);
                setBottomInfo("Recording started");
            } else if (builtIn == BuiltIn.STOP_RECORD) {
                recorder.stop();
                setRecordState(false);
                setBottomInfo("Samples: " + recorder.getSampleCount());
            } else if (builtIn == BuiltIn.EXPORT) {
                setBottomInfo(recorder.exportPath());
            } else if (builtIn == BuiltIn.CLEAR_MESSAGES) {
                messages.clear();
                adapter.notifyDataSetChanged();
                readBytes = 0;
                updateByteCounter();
            } else if (builtIn == BuiltIn.RESET_CHARTS) {
                resetCharts();
            }
        }

        private void onBtData(@NonNull BTPackage.BTData data) {
            module = data.module;
            readBytes += data.bytes.length;
            updateByteCounter();

            String text = decode(data.bytes, FragmentParameter.getInstance().getCodeFormat(context));
            if (text == null || text.isEmpty()) {
                return;
            }

            FragmentMessageItem item = new FragmentMessageItem(text, Analysis.getTime(), false, data.module, false);
            item.setDataEndNewline(true);
            messages.add(item);
            adapter.notifyItemInserted(messages.size() - 1);
            host.messageList().smoothScrollToPosition(messages.size() - 1);

            BluetoothSample sample = parse(text);
            if (sample != null) {
                consume(sample);
            }

            if (readBytes > AUTO_CLEAR_BYTES) {
                messages.clear();
                adapter.notifyDataSetChanged();
                readBytes = 0;
                setBottomInfo("Auto cleared");
                updateByteCounter();
            }
        }

        @Nullable
        private BluetoothSample parse(@NonNull String text) {
            for (BluetoothSampleParser parser : spec.parsers) {
                BluetoothSample sample = parser.parse(text);
                if (sample != null) {
                    return sample;
                }
            }
            return null;
        }

        private void consume(@NonNull BluetoothSample sample) {
            for (Map.Entry<String, Float> e : sample.metrics().entrySet()) {
                RealtimeLineChart chart = charts.get(e.getKey());
                if (chart != null && recorder.isRecording()) {
                    chart.append(e.getValue());
                }
                for (IndicatorSpec indicator : spec.indicators) {
                    if (e.getKey().equals(indicator.metricKey)) {
                        TextView tv = indicators.get(indicator.id);
                        if (tv != null) {
                            tv.setText(indicator.label + ": " + e.getValue());
                        }
                    }
                }
            }
            if (recorder.isRecording() && spec.recordFormatter != null) {
                recorder.appendLine(spec.recordFormatter.format(sample));
            }
        }

        private void resetCharts() {
            for (RealtimeLineChart chart : charts.values()) {
                chart.reset();
            }
        }

        private void setRecordState(boolean recording) {
            TextView tv = indicators.get("record_state");
            if (tv != null) {
                tv.setText(recording ? "Record: ON" : "Record: OFF");
            }
        }

        private void updateByteCounter() {
            TextView tv = indicators.get("byte_counter");
            if (tv != null) {
                tv.setText("Read: " + readBytes + " B    Sent: " + sentBytes + " B");
            }
        }

        private void setBottomInfo(@Nullable String text) {
            host.bottomInfo().setText(text == null ? "" : text);
        }

        private int dp(int value) {
            return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
        }

        @Nullable
        private String decode(@Nullable byte[] bytes, @Nullable String code) {
            if (bytes == null || bytes.length == 0) return null;
            String text = Analysis.getByteToString(bytes.clone(), code != null ? code : "UTF-8", false, false);
            if (text == null) return null;
            text = text.replace("\u0000", "").trim();
            return text.isEmpty() ? null : text;
        }
    }
}
```

- [ ] **Step 6: 编译验证骨架**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: compile may fail only because `Profiles.eis()` does not exist. If it fails for syntax errors in `UnifiedMessageFragment`, fix those before continuing.

Do not commit until `Profiles.eis()` is added in the next task.

---

### Task 5: 添加扁平 EIS profile

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java`

- [ ] **Step 1: 创建 `Profiles.java`**

Create `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java`:

```java
package com.hc.mixthebluetooth.activity.tool;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ActionSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.BuiltIn;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ChartSpec;
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.ProfileSpec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Profiles {
    private Profiles() {
    }

    @NonNull
    public static ProfileSpec eis() {
        return ProfileSpec.builder("eis")
                .parser(new EisParser())
                .chart(ChartSpec.line(EisSample.METRIC_OHM, "EIS Ohm", EisSample.METRIC_OHM, Color.RED))
                .chart(ChartSpec.line(EisSample.METRIC_US, "EIS uS", EisSample.METRIC_US, Color.BLUE))
                .action(ActionSpec.inner("start_record", "开始记录", BuiltIn.START_RECORD))
                .action(ActionSpec.inner("stop_record", "结束记录", BuiltIn.STOP_RECORD))
                .action(ActionSpec.inner("export", "导出", BuiltIn.EXPORT))
                .recordJson(Profiles::eisJson)
                .build();
    }

    static final class EisParser implements BluetoothSampleParser {
        private static final Pattern P = Pattern.compile(
                "\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*\\u03A9\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*(?:uS|\\u03BCS|\\u78C1S)\\s*",
                Pattern.CASE_INSENSITIVE
        );

        @Nullable
        @Override
        public BluetoothSample parse(@Nullable String line) {
            if (line == null) return null;
            String clean = line;
            int idx = clean.lastIndexOf("dataString:");
            if (idx >= 0) clean = clean.substring(idx + "dataString:".length());
            clean = clean.replace("\u0000", "").trim();
            Matcher m = P.matcher(clean);
            if (!m.find()) return null;
            try {
                float ohm = Float.parseFloat(m.group(1));
                float us = Float.parseFloat(m.group(2));
                return new EisSample(ohm, us, clean);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    @NonNull
    static String eisJson(@NonNull BluetoothSample sample) {
        if (!(sample instanceof EisSample)) {
            return "{\"tMs\":" + System.currentTimeMillis() + ",\"raw\":\"" + esc(sample.raw()) + "\"}";
        }
        EisSample e = (EisSample) sample;
        return "{\"tMs\":" + System.currentTimeMillis()
                + ",\"ohm\":" + e.ohm
                + ",\"us\":" + e.us
                + ",\"raw\":\"" + esc(e.raw) + "\"}";
    }

    @NonNull
    private static String esc(@Nullable String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
```

- [ ] **Step 2: 编译验证 Unified + EIS profile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java
git commit -m "feat: add unified message fragment with eis profile"
```

---

### Task 6: 接入新页面入口

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`

- [ ] **Step 1: 替换新消息页 Fragment**

In `CommunicationActivity.java`, add import:

```java
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment;
```

In `initPages()`, replace:

```java
viewPagerManage.addFragment(new FragmentMessageNew());
```

with:

```java
viewPagerManage.addFragment(new UnifiedMessageFragment());
```

Keep `FragmentMessage` in the first tab for comparison during this migration.

- [ ] **Step 2: 编译验证入口**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 手动 smoke test**

Run app from Android Studio or install debug APK. In the app:

```text
1. 打开“新消息页”。
2. 确认能看到动态创建的“开始记录 / 结束记录 / 导出”按钮。
3. 确认能看到 Record 和 Read/Sent 指标。
4. 连接 EIS 设备后，开始记录。
5. 收到类似 670258.375Ω,1.492uS 的数据后，两张折线图应追加点。
6. 停止记录后，底部显示 Samples: N。
```

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java
git commit -m "feat: route new message tab to unified fragment"
```

---

### Task 7: 添加 controller/spec 单测

**Files:**
- Create: `app/src/test/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragmentTest.java`

- [ ] **Step 1: 写 EIS profile spec 测试**

Create `app/src/test/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragmentTest.java`:

```java
package com.hc.mixthebluetooth.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.EisSample;
import com.hc.mixthebluetooth.activity.tool.Profiles;

import org.junit.Test;

public class UnifiedMessageFragmentTest {

    @Test
    public void eisProfileDeclaresTwoChartsAndRecordActions() {
        UnifiedMessageFragment.ProfileSpec spec = Profiles.eis();

        assertEquals("eis", spec.id);
        assertEquals(2, spec.charts.size());
        assertEquals(EisSample.METRIC_OHM, spec.charts.get(0).metricKey);
        assertEquals(EisSample.METRIC_US, spec.charts.get(1).metricKey);
        assertEquals(3, spec.actions.size());
        assertEquals(UnifiedMessageFragment.Route.INNER, spec.actions.get(0).route);
    }

    @Test
    public void eisParserProducesOhmAndUsMetrics() {
        UnifiedMessageFragment.ProfileSpec spec = Profiles.eis();

        BluetoothSample sample = spec.parsers.get(0).parse("670258.375Ω,1.492uS");

        assertNotNull(sample);
        assertEquals(670258.375f, sample.metrics().get(EisSample.METRIC_OHM), 0.001f);
        assertEquals(1.492f, sample.metrics().get(EisSample.METRIC_US), 0.001f);
    }
}
```

- [ ] **Step 2: 运行测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.fragment.UnifiedMessageFragmentTest"
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: Commit**

```powershell
git add app/src/test/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragmentTest.java
git commit -m "test: cover unified eis profile spec"
```

---

### Task 8: 清理旧 EIS/FMN 分裂点

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisProfile.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisProfileNew.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`

- [ ] **Step 1: 标记旧 EIS Profile**

At top of `EisProfile.java` class declaration, add:

```java
/**
 * @deprecated Use Profiles.eis() with UnifiedMessageFragment.
 */
@Deprecated
```

- [ ] **Step 2: 标记旧 EIS ProfileNew**

At top of `EisProfileNew.java` class declaration, add:

```java
/**
 * @deprecated Layout-specific profile is replaced by Profiles.eis().
 */
@Deprecated
```

- [ ] **Step 3: 标记旧 FragmentMessageNew**

At top of `FragmentMessageNew.java` class declaration, add:

```java
/**
 * @deprecated New message tab now uses UnifiedMessageFragment.
 */
@Deprecated
```

- [ ] **Step 4: 编译验证 deprecated 标记**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`，允许出现 deprecation note。

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisProfile.java app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisProfileNew.java app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java
git commit -m "chore: deprecate layout-bound eis message path"
```

---

### Task 9: 全量验证

**Files:**
- No source changes

- [ ] **Step 1: 运行 app 编译**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2: 运行单测**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 检查文件数量约束**

Run:

```powershell
git diff --name-only HEAD~7..HEAD
```

Expected: 核心新增文件集中在:

```text
app/src/main/res/layout/fragment_unified_message.xml
app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java
app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java
app/src/test/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragmentTest.java
```

If many extra architecture files appear, pause and merge them back into `UnifiedMessageFragment.java` or `Profiles.java` unless a file has a clear responsibility.

- [ ] **Step 4: 检查旧信道仍兼容**

Run:

```powershell
rg -n "CMD_BT_POST|CH_BT_EVENT|CMD_SEND_BT_DATA|CH_BT_DATA|EV_REC_SAMPLE" app\src\main\java\com\hc\mixthebluetooth
```

Expected:

```text
CMD_BT_POST and CH_BT_EVENT appear in UnifiedMessage path.
Old CMD_SEND_BT_DATA and CH_BT_DATA still exist for legacy fragments.
EV_REC_SAMPLE is not used by UnifiedMessageFragment.
```

- [ ] **Step 5: Commit verification notes if docs changed**

If implementation updates the spec or plan with verified findings:

```powershell
git add docs/superpowers/specs/2026-05-08-unified-message-profile-architecture-design.md docs/superpowers/plans/2026-05-08-unified-message-profile-architecture.md
git commit -m "docs: update unified message architecture notes"
```

If docs did not change, do not create an empty commit.
