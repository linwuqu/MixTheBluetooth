# FragmentMessage Profile Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `FragmentMessage` 重构为和 `FragmentMessageNew` 同一套设计语言的 Profile 驱动消息页面，让设备差异通过 `schema/<device>` 和 `DeviceProfile` 注册表达。

**Architecture:** 先建立通用运行时、选项、发送、控制、sample consumer 和 profile 骨架，再把旧 FM 的 CGM 协议迁入 `schema/cgm`，最后把 `FragmentMessage` 改成只负责装配 runtime、profile、pipeline、controls、charts、recorder。旧 FM 的 `CA / EIS / RI / TIME / ALL / DELETE / Storage / 权限 / 文件写入` 不再散落在 Fragment，而是进入 profile、schema、sender、consumer 或 option store。

**Tech Stack:** Android Java, ViewBinding, existing `BTFragment`, existing `BTPackage`, LiveEventBus command path, MPAndroidChart, Gradle debug Java compile, JUnit 4 pure unit tests.

---

## 0. 执行边界

用户已明确要求在当前本地分支 Inline Execution。执行本计划时不要新建分支，不要提交，除非用户另行要求。

旧计划：

```text
docs/superpowers/plans/2026-05-04-fragment-message-pipeline-migration.md
```

已经标记为 superseded。执行时以本文件为准。

---

## 1. 文件结构

Create:

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/runtime/FragmentRuntime.java`
  - Fragment 的运行环境：context、Storage、FragmentParameter、连接状态、当前 module、toast、log、BT command sender。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessageOptions.java`
  - 消息页面的选项快照：显示自己发送、显示时间、发送 Hex、接收 Hex、自动清理、自动换行、编码。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessageOptionStore.java`
  - 从 `Storage` 和 `FragmentParameter` 读取 `MessageOptions`。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessageSender.java`
  - 统一处理命令发送、Hex 转换、换行追加、发送列表显示。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/ControlAction.java`
  - 一个按钮行为。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/ControlRegistry.java`
  - View 到 action 的注册和 dispatch。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/sample/SampleConsumer.java`
  - 一个 sample 消费者。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/sample/SampleConsumerRegistry.java`
  - 多个 sample consumer 的注册和分发。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/profile/DeviceProfile.java`
  - 设备 profile 接口。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/profile/ProfileContext.java`
  - profile 注册时的上下文。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/profile/UserRolePolicy.java`
  - 普通用户/管理员参数权限策略。

- `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmSample.java`
  - CGM 协议 sample。

- `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmParser.java`
  - 解析旧 FM 的 `Start Playback / Playback all done / CA / EIS / RI / CA:266`。

- `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmParameters.java`
  - 参数表单值。

- `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmCommandSet.java`
  - 构造 `TIME / ALL / DELETE / 参数` 命令。

- `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmJsonLineBuilder.java`
  - CGM sample JSONL 导出。

- `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmProfile.java`
  - 注册 CGM parser、charts、controls、consumers。

- `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmStatusConsumer.java`
  - 更新状态文本。

- `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmCurrentValueConsumer.java`
  - 更新 current value。

- `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmFileConsumer.java`
  - 写 `CGM_Cache_data.txt` 和 `CGM_data.txt`。

- `app/src/test/java/com/hc/mixthebluetooth/schema/cgm/CgmParserTest.java`
  - CGM parser 纯单元测试。

- `app/src/test/java/com/hc/mixthebluetooth/schema/cgm/CgmCommandSetTest.java`
  - CGM command 纯单元测试。

Modify:

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessageListController.java`
  - 支持外部 layout manager、外部 list、append/merge、outgoing item、clear 后 scroll。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessagePipelineController.java`
  - 支持 `MessageOptions` 和 `SampleConsumerRegistry`，同时保留 FMNew 现有构造方式。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/SampleChartBinder.java`
  - 实现 `SampleConsumer`，可选择只在 recording 时追加图表。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/sample/SampleRecorder.java`
  - 保持现有 API；必要时由 consumer 调用，不让 Fragment 直接判断 sample 类型。

- `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`
  - 重写为 profile host。

- `app/src/main/res/layout/fragment_message.xml`
  - 向 FMNew 布局靠拢，保留 CGM controls、status、current value、两张 chart、message list、bottom info。

---

## Task 1: Baseline Verification

**Files:**
- Read: `docs/superpowers/specs/2026-05-04-fragment-message-profile-unification-design.md`
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`

- [ ] **Step 1: Check current diff**

Run:

```powershell
git status --short
```

Expected: current docs may be dirty. Do not revert existing user changes.

- [ ] **Step 2: Compile before code changes**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run existing tests**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 2: Runtime And Message Options

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/runtime/FragmentRuntime.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessageOptions.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessageOptionStore.java`

- [ ] **Step 1: Create `FragmentRuntime`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.runtime;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

public final class FragmentRuntime {

    public interface CommandSink {
        void sendBtData(@NonNull FragmentMessageItem item);
    }

    public interface Notifier {
        void toast(@NonNull String message);
    }

    public interface Logger {
        void log(@NonNull String message);
    }

    private final Context context;
    private final Storage storage;
    private final FragmentParameter fragmentParameter;
    private final CommandSink commandSink;
    private final Notifier notifier;
    private final Logger logger;

    private DeviceModule module;
    private boolean connected;

    public FragmentRuntime(
            @NonNull Context context,
            @NonNull Storage storage,
            @NonNull FragmentParameter fragmentParameter,
            @NonNull CommandSink commandSink,
            @NonNull Notifier notifier,
            @NonNull Logger logger
    ) {
        this.context = context;
        this.storage = storage;
        this.fragmentParameter = fragmentParameter;
        this.commandSink = commandSink;
        this.notifier = notifier;
        this.logger = logger;
    }

    @NonNull
    public Context context() {
        return context;
    }

    @NonNull
    public Storage storage() {
        return storage;
    }

    @NonNull
    public FragmentParameter fragmentParameter() {
        return fragmentParameter;
    }

    @Nullable
    public DeviceModule module() {
        return module;
    }

    public void setModule(@Nullable DeviceModule module) {
        this.module = module;
    }

    public boolean connected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public void sendBtData(@NonNull FragmentMessageItem item) {
        commandSink.sendBtData(item);
    }

    public void toast(@NonNull String message) {
        notifier.toast(message);
    }

    public void log(@NonNull String message) {
        logger.log(message);
    }
}
```

- [ ] **Step 2: Create `MessageOptions`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.message;

import androidx.annotation.NonNull;

public final class MessageOptions {
    public final boolean showOutgoing;
    public final boolean showTime;
    public final boolean sendHex;
    public final boolean readHex;
    public final boolean autoClear;
    public final boolean sendNewline;
    @NonNull
    public final String codeFormat;

    public MessageOptions(
            boolean showOutgoing,
            boolean showTime,
            boolean sendHex,
            boolean readHex,
            boolean autoClear,
            boolean sendNewline,
            @NonNull String codeFormat
    ) {
        this.showOutgoing = showOutgoing;
        this.showTime = showTime;
        this.sendHex = sendHex;
        this.readHex = readHex;
        this.autoClear = autoClear;
        this.sendNewline = sendNewline;
        this.codeFormat = codeFormat;
    }

    @NonNull
    public static MessageOptions defaults(@NonNull String codeFormat) {
        return new MessageOptions(false, false, false, false, false, false, codeFormat);
    }

    @NonNull
    public BluetoothPayloadDecoder.Options decoderOptions(boolean removeTrailingCrLf) {
        return new BluetoothPayloadDecoder.Options.Builder()
                .hex(readHex)
                .removeTrailingCrLf(removeTrailingCrLf)
                .cleanNull(true)
                .trim(true)
                .build();
    }
}
```

- [ ] **Step 3: Create `MessageOptionStore`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.message;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.customView.PopWindowFragment;
import com.hc.mixthebluetooth.storage.Storage;

public final class MessageOptionStore {

    private final FragmentRuntimeAccess runtime;

    public interface FragmentRuntimeAccess {
        @NonNull Storage storage();
        @NonNull FragmentParameter fragmentParameter();
        @NonNull android.content.Context context();
    }

    public MessageOptionStore(@NonNull FragmentRuntimeAccess runtime) {
        this.runtime = runtime;
    }

    @NonNull
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
}
```

- [ ] **Step 4: Make `FragmentRuntime` implement `MessageOptionStore.FragmentRuntimeAccess`**

Change class declaration:

```java
public final class FragmentRuntime implements MessageOptionStore.FragmentRuntimeAccess {
```

Add import:

```java
import com.hc.mixthebluetooth.activity.tool.message.MessageOptionStore;
```

- [ ] **Step 5: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 3: Message List, Sender, And Controls

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessageListController.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessageSender.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/ControlAction.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/ControlRegistry.java`

- [ ] **Step 1: Extend `MessageListController`**

Change the list field:

```java
private final List<FragmentMessageItem> dataList;
```

Make the current constructor delegate:

```java
public MessageListController(@NonNull Context context, @NonNull RecyclerView recyclerView, int itemLayout) {
    this(context, recyclerView, itemLayout, new ArrayList<>(), new LinearLayoutManager(context));
}
```

Add constructor:

```java
public MessageListController(
        @NonNull Context context,
        @NonNull RecyclerView recyclerView,
        int itemLayout,
        @NonNull List<FragmentMessageItem> dataList,
        @NonNull RecyclerView.LayoutManager layoutManager
) {
    this.context = context;
    this.recyclerView = recyclerView;
    this.dataList = dataList;
    this.adapter = new FragmentMessAdapter(context, dataList, itemLayout);
    recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(adapter);
}
```

Add methods:

```java
public void appendIncomingText(
        @NonNull String text,
        boolean endsWithLineBreak,
        @Nullable String time,
        @Nullable DeviceModule module,
        boolean showData
) {
    MessageItemTools.appendIncoming(dataList, text, endsWithLineBreak, time, module, showData);
}

public void appendOrMergeIncomingText(
        @NonNull String text,
        boolean endsWithLineBreak,
        @Nullable String time,
        @Nullable DeviceModule module,
        boolean showData
) {
    MessageItemTools.appendOrMergeIncoming(dataList, text, endsWithLineBreak, time, module, showData);
}

public void addOutgoingItem(@NonNull FragmentMessageItem item) {
    dataList.add(item);
    adapter.notifyItemInserted(dataList.size() - 1);
    scrollToBottom();
}

@SuppressLint("NotifyDataSetChanged")
public void notifyDataSetChangedAndScrollToBottom() {
    adapter.notifyDataSetChanged();
    scrollToBottom();
}
```

- [ ] **Step 2: Create `MessageSender`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

public final class MessageSender {

    private final FragmentRuntime runtime;
    private final MessageListController messageList;
    private MessageOptions options;

    public MessageSender(
            @NonNull FragmentRuntime runtime,
            @NonNull MessageListController messageList,
            @NonNull MessageOptions options
    ) {
        this.runtime = runtime;
        this.messageList = messageList;
        this.options = options;
    }

    public void updateOptions(@NonNull MessageOptions options) {
        this.options = options;
    }

    public boolean send(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            runtime.toast("不能发送空数据");
            return false;
        }

        if (!runtime.connected()) {
            runtime.toast("当前状态不可以发送数据");
            return false;
        }

        DeviceModule module = runtime.module();
        if (module == null) {
            runtime.toast("当前没有可发送的蓝牙模块");
            return false;
        }

        String text = prepare(raw);
        byte[] bytes = Analysis.getBytes(text, options.codeFormat, options.sendHex);
        FragmentMessageItem item = MessageItemTools.outgoing(
                options.sendHex,
                bytes,
                options.showTime ? Analysis.getTime() : null,
                module,
                options.showOutgoing
        );
        runtime.sendBtData(item);

        if (options.showOutgoing) {
            messageList.addOutgoingItem(item);
        }

        return true;
    }

    @NonNull
    public String prepare(@NonNull String raw) {
        String text = raw;
        if (options.sendNewline) {
            text += options.sendHex ? "0D0A" : "\r\n";
        }
        if (options.sendHex) {
            text = text.replaceAll(" ", "");
        }
        return text;
    }
}
```

- [ ] **Step 3: Create `ControlAction`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.message;

public interface ControlAction {
    void run();
}
```

- [ ] **Step 4: Create `ControlRegistry`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.message;

import android.view.View;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ControlRegistry {

    private final Map<Integer, ControlAction> actions = new LinkedHashMap<>();

    @NonNull
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

- [ ] **Step 5: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 4: Sample Consumers And Pipeline Upgrade

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/sample/SampleConsumer.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/sample/SampleConsumerRegistry.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/SampleChartBinder.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/message/MessagePipelineController.java`

- [ ] **Step 1: Create `SampleConsumer`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.NonNull;

public interface SampleConsumer {
    void consume(@NonNull BluetoothSample sample);
}
```

- [ ] **Step 2: Create `SampleConsumerRegistry`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.sample;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class SampleConsumerRegistry implements SampleConsumer {

    private final List<SampleConsumer> consumers = new ArrayList<>();

    @NonNull
    public SampleConsumerRegistry register(@NonNull SampleConsumer consumer) {
        consumers.add(consumer);
        return this;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        for (SampleConsumer consumer : consumers) {
            consumer.consume(sample);
        }
    }
}
```

- [ ] **Step 3: Make `SampleChartBinder` a `SampleConsumer`**

Add import:

```java
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;
```

Change class declaration:

```java
public final class SampleChartBinder implements SampleConsumer {
```

Change method:

```java
@Override
public void consume(@NonNull BluetoothSample sample) {
    append(sample);
}
```

Keep existing `append(...)` method for current FMNew callers.

- [ ] **Step 4: Upgrade `MessagePipelineController` fields**

Add fields:

```java
private MessageOptions options;
private final SampleConsumerRegistry sampleConsumers;
```

Keep the current constructor and delegate to the new one:

```java
public MessagePipelineController(
        @NonNull Context context,
        @NonNull MessageListController messageList,
        @NonNull BluetoothSampleRegistry sampleRegistry,
        @Nullable SampleListener sampleListener,
        @Nullable Logger logger
) {
    this(
            context,
            messageList,
            sampleRegistry,
            MessageOptions.defaults(FragmentParameter.getInstance().getCodeFormat(context)),
            null,
            sampleListener,
            logger
    );
}
```

Add new constructor:

```java
public MessagePipelineController(
        @NonNull Context context,
        @NonNull MessageListController messageList,
        @NonNull BluetoothSampleRegistry sampleRegistry,
        @NonNull MessageOptions options,
        @Nullable SampleConsumerRegistry sampleConsumers,
        @Nullable SampleListener sampleListener,
        @Nullable Logger logger
) {
    this.context = context;
    this.messageList = messageList;
    this.sampleRegistry = sampleRegistry;
    this.options = options;
    this.sampleConsumers = sampleConsumers;
    this.sampleListener = sampleListener;
    this.logger = logger;
}
```

Add:

```java
public void updateOptions(@NonNull MessageOptions options) {
    this.options = options;
}
```

- [ ] **Step 5: Decode with options**

Replace decode call in `onBtData(...)` with:

```java
BluetoothPayloadDecoder.Result result = BluetoothPayloadDecoder.decodeResult(
        bytes,
        options.codeFormat,
        options.decoderOptions(true)
);
```

- [ ] **Step 6: Dispatch sample consumers**

In `handleLine(...)`, after successful parse and before `sampleListener`, add:

```java
if (sampleConsumers != null) {
    sampleConsumers.consume(sample);
}
```

Keep existing `sampleListener` behavior for `FragmentMessageNew`.

- [ ] **Step 7: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 5: CGM Parser And Command Tests

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmSample.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmParser.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmParameters.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmCommandSet.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/schema/cgm/CgmParserTest.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/schema/cgm/CgmCommandSetTest.java`

- [ ] **Step 1: Write parser tests**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CgmParserTest {

    @Test
    public void parseCacheStart() {
        CgmSample sample = CgmParser.parseLine("Start Playback");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_CACHE_START, sample.event);
    }

    @Test
    public void parseCacheDone() {
        CgmSample sample = CgmParser.parseLine("Playback all done");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_CACHE_DONE, sample.event);
    }

    @Test
    public void parseCaPrimary() {
        CgmSample sample = CgmParser.parseLine("CA:1,12.5");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_CA, sample.event);
        assertEquals(12.5f, sample.metrics().get(CgmSample.METRIC_PRIMARY), 0.001f);
    }

    @Test
    public void parseCurrentValue() {
        CgmSample sample = CgmParser.parseLine("CA:266,8.8");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_CURRENT, sample.event);
        assertEquals(8.8f, sample.metrics().get(CgmSample.METRIC_CURRENT), 0.001f);
    }

    @Test
    public void parseEisPrimary() {
        CgmSample sample = CgmParser.parseLine("EIS:10,20,30.5");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_EIS, sample.event);
        assertEquals(30.5f, sample.metrics().get(CgmSample.METRIC_PRIMARY), 0.001f);
    }

    @Test
    public void parseRiStatus() {
        CgmSample sample = CgmParser.parseLine("RI:done");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_RI, sample.event);
        assertEquals("RI:done", sample.status);
    }

    @Test
    public void parseUnknownReturnsNull() {
        assertNull(CgmParser.parseLine("hello"));
    }
}
```

- [ ] **Step 2: Write command tests**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CgmCommandSetTest {

    @Test
    public void readCacheCommand() {
        assertEquals("ALL\n\r", CgmCommandSet.readCache());
    }

    @Test
    public void deleteCacheCommand() {
        assertEquals("DELETE\n\r", CgmCommandSet.deleteCache());
    }

    @Test
    public void startCommandUsesProvidedTime() {
        assertEquals("TIME,2026,05,04,10,20,30\n\r", CgmCommandSet.startWithTime("2026,05,04,10,20,30"));
    }

    @Test
    public void parameterCommandMatchesLegacyJoin() {
        CgmParameters params = new CgmParameters("60", "10", "12", "5", "9");
        assertEquals("060001001201159", CgmCommandSet.buildParameters(params));
    }
}
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "*CgmParserTest" --tests "*CgmCommandSetTest"
```

Expected: tests fail because `CgmSample`, `CgmParser`, `CgmParameters`, and `CgmCommandSet` do not exist yet.

- [ ] **Step 4: Create `CgmSample`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CgmSample implements BluetoothSample {

    public static final String TYPE = "cgm";
    public static final String METRIC_PRIMARY = "primary";
    public static final String METRIC_CURRENT = "current";

    public static final String EVENT_CACHE_START = "cache_start";
    public static final String EVENT_CACHE_DONE = "cache_done";
    public static final String EVENT_CACHE_LINE = "cache_line";
    public static final String EVENT_CA = "ca";
    public static final String EVENT_EIS = "eis";
    public static final String EVENT_RI = "ri";
    public static final String EVENT_CURRENT = "current";

    @NonNull
    public final String event;
    @NonNull
    public final String raw;
    @Nullable
    public final String status;
    @NonNull
    private final Map<String, Float> metrics;

    public CgmSample(
            @NonNull String event,
            @NonNull String raw,
            @Nullable String status,
            @NonNull Map<String, Float> metrics
    ) {
        this.event = event;
        this.raw = raw;
        this.status = status;
        this.metrics = new LinkedHashMap<>(metrics);
    }

    @NonNull
    public static CgmSample event(@NonNull String event, @NonNull String raw) {
        return new CgmSample(event, raw, null, Collections.emptyMap());
    }

    @NonNull
    public static CgmSample status(@NonNull String event, @NonNull String raw, @NonNull String status) {
        return new CgmSample(event, raw, status, Collections.emptyMap());
    }

    @NonNull
    public static CgmSample metric(@NonNull String event, @NonNull String raw, @NonNull String key, float value) {
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(key, value);
        return new CgmSample(event, raw, event, values);
    }

    @NonNull
    public static CgmSample current(@NonNull String raw, float value) {
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(METRIC_PRIMARY, value);
        values.put(METRIC_CURRENT, value);
        return new CgmSample(EVENT_CURRENT, raw, EVENT_CURRENT, values);
    }

    @NonNull
    @Override
    public String type() {
        return TYPE;
    }

    @NonNull
    @Override
    public String raw() {
        return raw;
    }

    @NonNull
    @Override
    public Map<String, Float> metrics() {
        return Collections.unmodifiableMap(metrics);
    }
}
```

- [ ] **Step 5: Create `CgmParser`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleParser;

public final class CgmParser implements BluetoothSampleParser {

    @Nullable
    @Override
    public BluetoothSample parse(@Nullable String line) {
        return parseLine(line);
    }

    @Nullable
    public static CgmSample parseLine(@Nullable String line) {
        if (line == null) return null;

        String clean = line.replace("\u0000", "").trim();
        if (clean.isEmpty()) return null;

        if (clean.contains("Start Playback")) {
            return CgmSample.event(CgmSample.EVENT_CACHE_START, clean);
        }

        if (clean.contains("Playback all done")) {
            return CgmSample.event(CgmSample.EVENT_CACHE_DONE, clean);
        }

        if (clean.contains("RI")) {
            return CgmSample.status(CgmSample.EVENT_RI, clean, clean);
        }

        if (clean.contains("EIS")) {
            String[] values = valuesAfterColon(clean);
            if (values.length > 2) {
                return CgmSample.metric(CgmSample.EVENT_EIS, clean, CgmSample.METRIC_PRIMARY, Float.parseFloat(values[2]));
            }
            return null;
        }

        if (clean.contains("CA")) {
            String[] values = valuesAfterColon(clean);
            if (values.length > 1) {
                float value = Float.parseFloat(values[1]);
                if (clean.contains("CA:266")) {
                    return CgmSample.current(clean, value);
                }
                return CgmSample.metric(CgmSample.EVENT_CA, clean, CgmSample.METRIC_PRIMARY, value);
            }
        }

        return null;
    }

    private static String[] valuesAfterColon(String line) {
        String[] parts = line.split(":");
        if (parts.length < 2) return new String[0];
        return parts[1].split(",");
    }
}
```

- [ ] **Step 6: Create `CgmParameters`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;

public final class CgmParameters {
    @NonNull public final String controlRatio;
    @NonNull public final String extractionTime;
    @NonNull public final String highLevelTime;
    @NonNull public final String voltage;
    @NonNull public final String detectionTime;

    public CgmParameters(
            @NonNull String controlRatio,
            @NonNull String extractionTime,
            @NonNull String highLevelTime,
            @NonNull String voltage,
            @NonNull String detectionTime
    ) {
        this.controlRatio = controlRatio;
        this.extractionTime = extractionTime;
        this.highLevelTime = highLevelTime;
        this.voltage = voltage;
        this.detectionTime = detectionTime;
    }
}
```

- [ ] **Step 7: Create `CgmCommandSet`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CgmCommandSet {

    private CgmCommandSet() {
    }

    @NonNull
    public static String startNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy,MM,dd,HH,mm,ss", Locale.getDefault());
        return startWithTime(sdf.format(new Date()));
    }

    @NonNull
    public static String startWithTime(@NonNull String formattedTime) {
        return "TIME," + formattedTime + "\n\r";
    }

    @NonNull
    public static String readCache() {
        return "ALL\n\r";
    }

    @NonNull
    public static String deleteCache() {
        return "DELETE\n\r";
    }

    @NonNull
    public static String buildParameters(@NonNull CgmParameters params) {
        return padLeft(params.controlRatio, "000")
                + "0010" + params.extractionTime
                + padLeft(params.highLevelTime, "010")
                + "011" + params.voltage
                + padLeft(params.detectionTime, "100");
    }

    @NonNull
    static String padLeft(@NonNull String value, @NonNull String prefix) {
        String clean = value.trim();
        if (clean.length() >= prefix.length()) return clean;
        return prefix.substring(0, prefix.length() - clean.length()) + clean;
    }
}
```

- [ ] **Step 8: Run tests**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "*CgmParserTest" --tests "*CgmCommandSetTest"
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 6: Profile Context, Role Policy, And CGM Consumers

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/profile/DeviceProfile.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/profile/ProfileContext.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/profile/UserRolePolicy.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmJsonLineBuilder.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmStatusConsumer.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmCurrentValueConsumer.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmFileConsumer.java`

- [ ] **Step 1: Create `DeviceProfile`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.profile;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.message.ControlRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumerRegistry;

public interface DeviceProfile<B> {
    void registerSamples(@NonNull BluetoothSampleRegistry registry);
    void registerCharts(@NonNull ChartRegistry charts, @NonNull B binding);
    void registerConsumers(@NonNull SampleConsumerRegistry consumers, @NonNull ProfileContext<B> context);
    void registerControls(@NonNull ControlRegistry controls, @NonNull ProfileContext<B> context);
}
```

- [ ] **Step 2: Create `ProfileContext`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.profile;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.message.MessageSender;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;

public final class ProfileContext<B> {
    @NonNull public final B binding;
    @NonNull public final FragmentRuntime runtime;
    @NonNull public final ChartRegistry charts;
    @NonNull public final MessageSender sender;
    @NonNull public final SampleRecorder recorder;
    @NonNull public final UserRolePolicy rolePolicy;

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
```

- [ ] **Step 3: Create `UserRolePolicy`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool.profile;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.viewBasic.HomeApplication;

public final class UserRolePolicy {

    private final String role;

    public UserRolePolicy(@NonNull HomeApplication application) {
        this.role = application.getLimits();
    }

    public boolean canEditParameters() {
        return "admin".equals(role);
    }
}
```

- [ ] **Step 4: Create `CgmJsonLineBuilder`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;

public final class CgmJsonLineBuilder {

    private CgmJsonLineBuilder() {
    }

    @NonNull
    public static String build(@Nullable DeviceModule module, @NonNull CgmSample sample) {
        String mac = module != null ? module.getMac() : "";
        String name = module != null ? module.getName() : "";
        Float primary = sample.metrics().get(CgmSample.METRIC_PRIMARY);
        Float current = sample.metrics().get(CgmSample.METRIC_CURRENT);

        return "{"
                + "\"tMs\":" + System.currentTimeMillis()
                + ",\"mac\":\"" + escapeJson(mac) + "\""
                + ",\"name\":\"" + escapeJson(name) + "\""
                + ",\"event\":\"" + escapeJson(sample.event) + "\""
                + ",\"status\":\"" + escapeJson(sample.status) + "\""
                + ",\"primary\":" + (primary == null ? "null" : primary)
                + ",\"current\":" + (current == null ? "null" : current)
                + ",\"raw\":\"" + escapeJson(sample.raw) + "\""
                + "}";
    }

    @NonNull
    public static String escapeJson(@Nullable String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
```

- [ ] **Step 5: Create `CgmStatusConsumer`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;
import android.widget.TextView;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

public final class CgmStatusConsumer implements SampleConsumer {

    private final TextView statusView;

    public CgmStatusConsumer(@NonNull TextView statusView) {
        this.statusView = statusView;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!(sample instanceof CgmSample)) return;
        CgmSample cgm = (CgmSample) sample;
        if (cgm.status != null && !cgm.status.isEmpty()) {
            statusView.setText(cgm.status);
        } else {
            statusView.setText(cgm.event);
        }
    }
}
```

- [ ] **Step 6: Create `CgmCurrentValueConsumer`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;
import android.widget.TextView;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

public final class CgmCurrentValueConsumer implements SampleConsumer {

    private final TextView currentValueView;

    public CgmCurrentValueConsumer(@NonNull TextView currentValueView) {
        this.currentValueView = currentValueView;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!(sample instanceof CgmSample)) return;
        Float value = sample.metrics().get(CgmSample.METRIC_CURRENT);
        if (value != null) {
            currentValueView.setText(String.valueOf(value));
        }
    }
}
```

- [ ] **Step 7: Create `CgmFileConsumer`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import android.os.Environment;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;

public final class CgmFileConsumer implements SampleConsumer {

    private final FragmentRuntime runtime;

    public CgmFileConsumer(@NonNull FragmentRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!(sample instanceof CgmSample)) return;
        CgmSample cgm = (CgmSample) sample;
        String dir = String.valueOf(runtime.context().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));

        if (CgmSample.EVENT_CACHE_START.equals(cgm.event)
                || CgmSample.EVENT_CACHE_LINE.equals(cgm.event)
                || CgmSample.EVENT_CACHE_DONE.equals(cgm.event)) {
            Analysis.IO_input_data(cgm.raw, dir, "CGM_Cache_data.txt");
            return;
        }

        if (CgmSample.EVENT_CURRENT.equals(cgm.event)) {
            Analysis.IO_input_data(CgmJsonLineBuilder.build(runtime.module(), cgm) + "\n", dir, "CGM_data.txt");
        }
    }
}
```

- [ ] **Step 8: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 7: CgmProfile

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmProfile.java`

- [ ] **Step 1: Create `CgmProfile`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import android.graphics.Color;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.chart.ChartRegistry;
import com.hc.mixthebluetooth.activity.tool.chart.RealtimeLineChart;
import com.hc.mixthebluetooth.activity.tool.chart.SampleChartBinder;
import com.hc.mixthebluetooth.activity.tool.message.ControlRegistry;
import com.hc.mixthebluetooth.activity.tool.profile.DeviceProfile;
import com.hc.mixthebluetooth.activity.tool.profile.ProfileContext;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleRegistry;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumerRegistry;
import com.hc.mixthebluetooth.databinding.FragmentMessageBinding;

public final class CgmProfile implements DeviceProfile<FragmentMessageBinding> {

    public static final String CHART_PRIMARY = "cgm_primary";
    public static final String CHART_CURRENT = "cgm_current";
    private static final int MAX_POINTS = 500;

    @Override
    public void registerSamples(@NonNull BluetoothSampleRegistry registry) {
        registry.register(new CgmParser());
    }

    @Override
    public void registerCharts(@NonNull ChartRegistry charts, @NonNull FragmentMessageBinding binding) {
        charts
                .register(
                        CHART_PRIMARY,
                        binding.chartPrimary,
                        new RealtimeLineChart.Config.Builder()
                                .label("CGM")
                                .color(Color.RED)
                                .maxPoints(MAX_POINTS)
                                .visibleWindowSeconds(60f)
                                .build()
                )
                .register(
                        CHART_CURRENT,
                        binding.chartCurrent,
                        new RealtimeLineChart.Config.Builder()
                                .label("Current")
                                .color(Color.BLUE)
                                .maxPoints(MAX_POINTS)
                                .visibleWindowSeconds(60f)
                                .build()
                );
    }

    @Override
    public void registerConsumers(
            @NonNull SampleConsumerRegistry consumers,
            @NonNull ProfileContext<FragmentMessageBinding> context
    ) {
        consumers
                .register(new CgmStatusConsumer(context.binding.tvStatus))
                .register(new CgmCurrentValueConsumer(context.binding.tvCurrentValue))
                .register(new CgmFileConsumer(context.runtime))
                .register(new SampleChartBinder(context.charts)
                        .bind(CgmSample.METRIC_PRIMARY, CHART_PRIMARY)
                        .bind(CgmSample.METRIC_CURRENT, CHART_CURRENT));
    }

    @Override
    public void registerControls(
            @NonNull ControlRegistry controls,
            @NonNull ProfileContext<FragmentMessageBinding> context
    ) {
        controls
                .bind(context.binding.btnStartMeasure, () -> context.sender.send(CgmCommandSet.startNow()))
                .bind(context.binding.btnReadCache, () -> context.sender.send(CgmCommandSet.readCache()))
                .bind(context.binding.btnDeleteCache, () -> context.sender.send(CgmCommandSet.deleteCache()));
    }
}
```

- [ ] **Step 2: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: this can fail until Task 8 because `fragment_message.xml` does not yet expose `chartPrimary`, `chartCurrent`, `tvStatus`, `tvCurrentValue`, `btnStartMeasure`, `btnReadCache`, and `btnDeleteCache`.

If it fails only for missing generated binding fields, continue to Task 8. If it fails for package/type errors, fix those before moving on.

---

## Task 8: Replace FragmentMessage Layout With Profile Host Layout

**Files:**
- Modify: `app/src/main/res/layout/fragment_message.xml`

- [ ] **Step 1: Replace layout content**

Replace the full file with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:keepScreenOn="true">

    <LinearLayout
        android:id="@+id/controlsContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <Button
            android:id="@+id/btnStartMeasure"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Start" />

        <Button
            android:id="@+id/btnReadCache"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Read" />

        <Button
            android:id="@+id/btnDeleteCache"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Delete" />

        <Button
            android:id="@+id/btnOptions"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Options" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/recordControlsContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingStart="8dp"
        android:paddingEnd="8dp"
        android:paddingBottom="6dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/controlsContainer">

        <Button
            android:id="@+id/btnStartRecord"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/start_record" />

        <Button
            android:id="@+id/btnStopRecord"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/stop_record" />

        <Button
            android:id="@+id/btnExport"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/export" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/statusContainer"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingStart="12dp"
        android:paddingEnd="12dp"
        android:paddingBottom="6dp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/recordControlsContainer">

        <TextView
            android:id="@+id/tvRecordState"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Record: OFF" />

        <TextView
            android:id="@+id/tvStatus"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Status: Ready" />

        <TextView
            android:id="@+id/tvCurrentValue"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Current: 0.00" />

        <TextView
            android:id="@+id/tvByteCounters"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Read: 0 B    Sent: 0 B" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/chartPrimaryContainer"
        android:layout_width="0dp"
        android:layout_height="160dp"
        android:layout_marginStart="10dp"
        android:layout_marginTop="4dp"
        android:layout_marginEnd="10dp"
        android:background="@drawable/window_back"
        android:elevation="8dp"
        android:orientation="vertical"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/statusContainer">

        <com.github.mikephil.charting.charts.LineChart
            android:id="@+id/chartPrimary"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/chartCurrentContainer"
        android:layout_width="0dp"
        android:layout_height="140dp"
        android:layout_marginStart="10dp"
        android:layout_marginTop="8dp"
        android:layout_marginEnd="10dp"
        android:background="@drawable/window_back"
        android:elevation="8dp"
        android:orientation="vertical"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/chartPrimaryContainer">

        <com.github.mikephil.charting.charts.LineChart
            android:id="@+id/chartCurrent"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </LinearLayout>

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

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerMessage"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:paddingBottom="10dp"
        app:layout_constraintBottom_toTopOf="@id/tvBottomInfo"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/chartCurrentContainer" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

- [ ] **Step 2: Compile to regenerate binding**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: compile fails in `FragmentMessage.java` because old binding ids no longer exist. Continue to Task 9.

---

## Task 9: Rewrite FragmentMessage As Profile Host

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Replace FragmentMessage with host implementation**

Replace the full class with:

```java
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
import com.hc.mixthebluetooth.schema.cgm.CgmProfile;
import com.hc.mixthebluetooth.storage.Storage;

public class FragmentMessage extends BTFragment<FragmentMessageBinding> {

    private FragmentRuntime runtime;
    private MessageOptions options;
    private MessageOptionStore optionStore;
    private DeviceProfile<FragmentMessageBinding> profile;
    private MessageListController messageList;
    private ChartRegistry chartRegistry;
    private BluetoothSampleRegistry sampleRegistry;
    private SampleConsumerRegistry sampleConsumers;
    private SampleRecorder recorder;
    private MessagePipelineController pipeline;
    private MessageSender sender;
    private ControlRegistry controls;

    private int readBytes;
    private int sentBytes;

    @Override
    protected void initChannels() {
        register(
                StaticConstants.CH_BT_DATA,
                StaticConstants.CH_SET_CONNECT_STATE,
                StaticConstants.CH_SENT_BYTES,
                StaticConstants.CH_STOP_LOOP_SEND
        );
    }

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
        setBottomInfo("Ready");
        updateByteCounters();
    }

    private void initRuntime(Context context) {
        runtime = new FragmentRuntime(
                context,
                new Storage(context),
                FragmentParameter.getInstance(),
                item -> sendDataToActivity(StaticConstants.CMD_SEND_BT_DATA, item),
                this::toastShort,
                this::logWarn
        );
    }

    private void initOptions() {
        optionStore = new MessageOptionStore(runtime);
        options = optionStore.load();
    }

    private void initProfile() {
        profile = new CgmProfile();
        sampleRegistry = new BluetoothSampleRegistry();
        sampleConsumers = new SampleConsumerRegistry();
        profile.registerSamples(sampleRegistry);
    }

    private void initMessageList() {
        messageList = new MessageListController(
                requireContext(),
                viewBinding.recyclerMessage,
                R.layout.item_message_fragment
        );
    }

    private void initCharts() {
        chartRegistry = new ChartRegistry();
        profile.registerCharts(chartRegistry, viewBinding);
    }

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

    private void initPipeline() {
        sender = new MessageSender(runtime, messageList, options);
        UserRolePolicy rolePolicy = new UserRolePolicy((HomeApplication) requireActivity().getApplication());
        ProfileContext<FragmentMessageBinding> profileContext = new ProfileContext<>(
                viewBinding,
                runtime,
                chartRegistry,
                sender,
                recorder,
                rolePolicy
        );
        profile.registerConsumers(sampleConsumers, profileContext);
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
        profile.registerControls(controls, profileContext);
        controls
                .bind(viewBinding.btnStartRecord, this::startRecording)
                .bind(viewBinding.btnStopRecord, this::stopRecording)
                .bind(viewBinding.btnExport, this::exportRecording)
                .bind(viewBinding.btnOptions, this::refreshOptions);
        bindOnClickListener(
                viewBinding.btnStartMeasure,
                viewBinding.btnReadCache,
                viewBinding.btnDeleteCache,
                viewBinding.btnStartRecord,
                viewBinding.btnStopRecord,
                viewBinding.btnExport,
                viewBinding.btnOptions
        );
    }

    private void startRecording() {
        chartRegistry.resetAll();
        recorder.start(requireContext(), "message_cgm");
    }

    private void stopRecording() {
        recorder.stop();
        setBottomInfo("Samples: " + recorder.getSampleCount());
    }

    private void exportRecording() {
        recorder.exportPath();
    }

    private void refreshOptions() {
        options = optionStore.load();
        sender.updateOptions(options);
        pipeline.updateOptions(options);
        setBottomInfo("Options refreshed");
    }

    @Override
    protected void onBtConnected(DeviceModule module) {
        runtime.setModule(module);
        runtime.setConnected(true);
    }

    @Override
    protected void onBtDisconnected() {
        runtime.setModule(null);
        runtime.setConnected(false);
    }

    @Override
    protected void onConnectStateChanged(String state) {
        boolean connected = "已连接".equals(state);
        runtime.setConnected(connected);
    }

    @Override
    protected void onBtData(BTPackage.BTData data) {
        runtime.setModule(data.module);
        if (pipeline != null) {
            pipeline.onBtData(data.module, data.bytes);
            if (data.bytes != null) {
                readBytes += data.bytes.length;
                updateByteCounters();
            }
        }
    }

    @Override
    protected void onSentBytesChanged(int number) {
        sentBytes += number;
        updateByteCounters();
    }

    @Override
    protected void onStopLoopSend() {
        HoldBluetooth.getInstance().stopSend(runtime.module(), null);
    }

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

    private void updateByteCounters() {
        if (viewBinding != null) {
            viewBinding.tvByteCounters.setText("Read: " + readBytes + " B    Sent: " + sentBytes + " B");
        }
    }

    private void setBottomInfo(@Nullable String text) {
        if (viewBinding != null) {
            viewBinding.tvBottomInfo.setText(text == null ? "" : text);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (recorder != null) {
            recorder.release();
        }
        HoldBluetooth.getInstance().stopSend(runtime == null ? null : runtime.module(), null);
    }
}
```

- [ ] **Step 2: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 10: Recorder Consumer For CGM JSONL

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmRecorderConsumer.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/schema/cgm/CgmProfile.java`

- [ ] **Step 1: Create `CgmRecorderConsumer`**

Create:

```java
package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.SampleConsumer;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;

public final class CgmRecorderConsumer implements SampleConsumer {

    private final FragmentRuntime runtime;
    private final SampleRecorder recorder;

    public CgmRecorderConsumer(@NonNull FragmentRuntime runtime, @NonNull SampleRecorder recorder) {
        this.runtime = runtime;
        this.recorder = recorder;
    }

    @Override
    public void consume(@NonNull BluetoothSample sample) {
        if (!recorder.isRecording()) return;
        if (!(sample instanceof CgmSample)) return;
        recorder.appendLine(CgmJsonLineBuilder.build(runtime.module(), (CgmSample) sample));
    }
}
```

- [ ] **Step 2: Register recorder consumer**

In `CgmProfile.registerConsumers(...)`, add:

```java
.register(new CgmRecorderConsumer(context.runtime, context.recorder))
```

after `new CgmFileConsumer(...)`.

- [ ] **Step 3: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 11: Remove Dead Legacy From FragmentMessage

**Files:**
- Verify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Search forbidden legacy strings**

Run:

```powershell
Get-ChildItem .\app\src\main\java\com\hc\mixthebluetooth\fragment -Filter FragmentMessage.java |
  Select-String -Pattern 'CA:|EIS|RI|Start Playback|Playback all done|TIME,|ALL|DELETE|Analysis.IO_input_data|getLineChart|getLineBloodChart|mAdapter|mDataList'
```

Expected: no matches.

- [ ] **Step 2: Search FragmentMessage binding old ids**

Run:

```powershell
Get-ChildItem .\app\src\main\java\com\hc\mixthebluetooth -Recurse -Filter *.java |
  Select-String -Pattern 'sizeReadMessageFragment|sizeSendMessageFragment|sentStartFlag|getAllData|deleteCacheData|currentData|recyclerMessageFragment|pullMessageFragment|sendMessageFragment|editMessageFragment'
```

Expected: no matches in `FragmentMessage.java`. Matches in unrelated old files must be inspected before changing.

- [ ] **Step 3: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 12: Final Verification

**Files:**
- Verify: all changed Java and XML files.

- [ ] **Step 1: Run compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run tests**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Search FragmentMessage forbidden direct responsibilities**

Run:

```powershell
Get-ChildItem .\app\src\main\java\com\hc\mixthebluetooth\fragment -Filter FragmentMessage.java |
  Select-String -Pattern 'CA:|EIS|RI|TIME,|ALL|DELETE|Analysis.IO_input_data|MessageItemTools|BluetoothPayloadDecoder|LineChart|FragmentMessAdapter|Storage|FragmentParameter'
```

Expected: no matches except imports or runtime construction for `Storage` and `FragmentParameter` if they remain in `initRuntime(...)`.

- [ ] **Step 4: Search profile ownership**

Run:

```powershell
Get-ChildItem .\app\src\main\java\com\hc\mixthebluetooth\schema\cgm -Filter *.java |
  Select-String -Pattern 'CA:|EIS|RI|TIME,|ALL|DELETE|CGM_Cache_data|CGM_data'
```

Expected: matches are present in `schema/cgm`, proving CGM-specific behavior moved out of Fragment.

- [ ] **Step 5: Manual smoke**

Manual path:

```text
1. Open CommunicationActivity.
2. Connect the CGM target device.
3. Open FragmentMessage.
4. Confirm raw incoming text appears in the message list.
5. Confirm read byte counter increases.
6. Tap Start and confirm TIME command reaches device.
7. Tap Read and confirm ALL command reaches device.
8. Tap Delete and confirm DELETE command reaches device.
9. Start recording and confirm charts update only while recording.
10. Stop recording and confirm charts stop adding new points.
11. Export recording and confirm export path appears.
12. Send CA/EIS/RI/CA:266 data and confirm status/current/chart behavior.
13. Trigger cache playback and confirm CGM_Cache_data.txt is written.
14. Confirm FragmentMessageNew still compiles and opens.
```

Expected: FM is now a Profile-driven host and visible CGM behavior remains present.

---

## 13. 本计划结束后的下一步

本计划只要求让 `FragmentMessage` 完成 Profile 化。

当 FM 稳定后，下一份计划再处理：

- 将 `FragmentMessageNew` 迁移成 `EisProfile`。
- 将 `FragmentMessage` 和 `FragmentMessageNew` 的重复 host 逻辑进一步收进基类或 `ProfileMessageFragment`。
- 将 `Analysis` 中仍被多个模块使用的能力继续拆小。

