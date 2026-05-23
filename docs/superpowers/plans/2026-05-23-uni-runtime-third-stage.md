# Uni Runtime Third Stage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前 `UnifiedMessageFragment` 的运行时收敛为 `UniFragment + uni/Controller + profile/eis + Widgets/Codec/Commands/Output`，在不接入 Retrofit 的前提下完成第三阶段结构重构。

**Architecture:** `UniFragment` 只保留 Android 页面壳职责；`uni.Controller` 成为运行时骨架，并集中承载 `ProfileSpec/ActionSpec/Region/Route/BuiltIn/HostView/Gateway` 等 controller 内需契约；`uni.profile.eis.EisProfile` 承载 EIS 设备协议；`uni.Widgets`、`uni.Codec`、`uni.Commands`、`uni.Output` 分别承载独立演化轴。保留一个 deprecated `UnifiedMessageFragment` 兼容壳，降低一次性 rename 风险。

**Tech Stack:** Android Java、ViewBinding、LiveEventBus 既有信道、MPAndroidChart、现有 `Analysis`/`SampleRecorder`、JUnit4、Gradle Android plugin。

---

## Current Workspace Note

当前工作区已有未提交改动：

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java`
- `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
- `docs/superpowers/specs/2026-05-23-uni-runtime-third-stage-design.md`

执行计划时不要回滚这些文件。涉及同一文件时，基于现场内容继续编辑。

## File Structure

### Create

- `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`
  - 新主 Fragment。继承 `BTFragment<FragmentUnifiedMessageBinding>`。
  - 只负责 binding、channel、host/gateway、controller 生命周期。

- `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`
  - 从当前 `UnifiedMessageFragment.MessageController` 迁移而来。
  - 内含 `Region`、`Route`、`BuiltIn`、`ActionSpec`、`ProfileSpec`、`HostView`、`Gateway`。
  - 内含 private `SystemStatusPresenter` 和 `MessageListPresenter`，不额外拆文件。

- `app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java`
  - 从当前 `activity/tool/chart/MetricWidgets.java` 迁移而来。
  - 保留 `WidgetSpec`、`MetricWidget`、内置 widget runtime。

- `app/src/main/java/com/hc/mixthebluetooth/uni/Codec.java`
  - 收敛蓝牙 payload -> text 的通用解码。
  - 复用 `Analysis.getByteToString(...)`。

- `app/src/main/java/com/hc/mixthebluetooth/uni/Commands.java`
  - 收敛设备命令语义。
  - 第三阶段只实现 legacy CGM 文本命令 helper。

- `app/src/main/java/com/hc/mixthebluetooth/uni/Output.java`
  - 包装现有 `SampleRecorder`，提供 session 输出边界。
  - 不接 Retrofit。

- `app/src/main/java/com/hc/mixthebluetooth/uni/Profiles.java`
  - profile registry，只暴露 `eis()`。

- `app/src/main/java/com/hc/mixthebluetooth/uni/profile/eis/EisProfile.java`
  - 承载 EIS profile、parser、sample、JSON formatter。

- `app/src/test/java/com/hc/mixthebluetooth/uni/CodecTest.java`
- `app/src/test/java/com/hc/mixthebluetooth/uni/CommandsTest.java`
- `app/src/test/java/com/hc/mixthebluetooth/uni/WidgetsTest.java`
- `app/src/test/java/com/hc/mixthebluetooth/uni/ProfilesTest.java`

### Modify

- `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
  - 改为 deprecated 兼容壳：`public class UnifiedMessageFragment extends UniFragment {}`。

- `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
  - 将 `new UnifiedMessageFragment()` 改为 `new UniFragment()`。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java`
  - 删除或改为 deprecated 代理到 `uni.Profiles`。本计划推荐先保留代理，避免外部引用断裂。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java`
  - 删除或改为 deprecated 代理。由于 nested 类型难以无痛代理，本计划推荐迁移测试和生产代码后删除。

- `app/src/test/java/com/hc/mixthebluetooth/activity/tool/ProfilesTest.java`
  - 迁移到 `app/src/test/java/com/hc/mixthebluetooth/uni/ProfilesTest.java` 后删除旧测试文件。

- `app/src/test/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgetsTest.java`
  - 迁移到 `app/src/test/java/com/hc/mixthebluetooth/uni/WidgetsTest.java` 后删除旧测试文件。

### Keep

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Analysis.java`
  - `Codec` 复用它，不迁移。

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSample.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSampleParser.java`
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java`
  - 第三阶段保留这些小基础类型，避免无收益大搬家。

- `app/src/main/res/layout/fragment_unified_message.xml`
  - 第三阶段继续复用，不重命名 layout。

---

## Task 0: Preflight

**Files:**
- Read: `docs/superpowers/specs/2026-05-23-uni-runtime-third-stage-design.md`
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java`

- [ ] **Step 1: Inspect workspace**

Run:

```powershell
git status --short --branch
git diff -- app\src\main\java\com\hc\mixthebluetooth\activity\tool\SampleRecorder.java
git diff -- app\src\main\java\com\hc\mixthebluetooth\fragment\UnifiedMessageFragment.java
```

Expected:

```text
## dev-1.3
 M app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java
 M app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java
?? docs/superpowers/specs/2026-05-23-uni-runtime-third-stage-design.md
```

- [ ] **Step 2: Run baseline tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: either `BUILD SUCCESSFUL`, or an existing failure unrelated to this plan. If it fails, capture the failing test names before continuing.

- [ ] **Step 3: Run baseline compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: either `BUILD SUCCESSFUL`, or an existing compile failure. If it fails, capture the error before continuing.

---

## Task 1: Add Codec And Command Tests First

**Files:**
- Create: `app/src/test/java/com/hc/mixthebluetooth/uni/CodecTest.java`
- Create: `app/src/test/java/com/hc/mixthebluetooth/uni/CommandsTest.java`

- [ ] **Step 1: Create `CodecTest`**

Create `app/src/test/java/com/hc/mixthebluetooth/uni/CodecTest.java`:

```java
package com.hc.mixthebluetooth.uni;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.nio.charset.Charset;

public class CodecTest {

    @Test
    public void decodeReturnsNullForNullOrEmptyBytes() {
        Codec.Options options = new Codec.Options("UTF-8", false, false);

        assertNull(Codec.decode(null, options));
        assertNull(Codec.decode(new byte[0], options));
    }

    @Test
    public void decodeRemovesZeroCharactersAndTrims() {
        Codec.Options options = new Codec.Options("UTF-8", false, false);
        byte[] bytes = "  12.5\u0000Ω,3.2uS  ".getBytes(Charset.forName("UTF-8"));

        assertEquals("12.5Ω,3.2uS", Codec.decode(bytes, options));
    }

    @Test
    public void decodeUsesUtf8WhenCharsetIsNull() {
        Codec.Options options = new Codec.Options(null, false, false);
        byte[] bytes = "hello".getBytes(Charset.forName("UTF-8"));

        assertEquals("hello", Codec.decode(bytes, options));
    }
}
```

- [ ] **Step 2: Create `CommandsTest`**

Create `app/src/test/java/com/hc/mixthebluetooth/uni/CommandsTest.java`:

```java
package com.hc.mixthebluetooth.uni;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class CommandsTest {

    @Test
    public void legacyCgmReadCacheCommandIsAll() {
        assertEquals("ALL\n\r", Commands.LegacyCgm.readCache());
    }

    @Test
    public void legacyCgmDeleteCacheCommandIsDelete() {
        assertEquals("DELETE\n\r", Commands.LegacyCgm.deleteCache());
    }

    @Test
    public void legacyCgmSyncTimeUsesExpectedFormat() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(2026, Calendar.MAY, 23, 10, 20, 30);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date = calendar.getTime();

        assertEquals("TIME,2026,05,23,10,20,30\n\r", Commands.LegacyCgm.syncTime(date));
    }
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.CodecTest" --tests "com.hc.mixthebluetooth.uni.CommandsTest"
```

Expected: compile fails because `Codec` and `Commands` do not exist yet.

---

## Task 2: Implement Codec And Commands

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/uni/Codec.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/uni/Commands.java`

- [ ] **Step 1: Create `Codec.java`**

Create `app/src/main/java/com/hc/mixthebluetooth/uni/Codec.java`:

```java
package com.hc.mixthebluetooth.uni;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.Analysis;

public final class Codec {
    private Codec() {
    }

    public static final class Options {
        @Nullable
        public final String charset;
        public final boolean hex;
        public final boolean checkNewline;

        public Options(@Nullable String charset, boolean hex, boolean checkNewline) {
            this.charset = charset;
            this.hex = hex;
            this.checkNewline = checkNewline;
        }
    }

    @Nullable
    public static String decode(@Nullable byte[] bytes, @NonNull Options options) {
        if (bytes == null || bytes.length == 0) return null;
        String charset = options.charset != null ? options.charset : "UTF-8";
        String text = Analysis.getByteToString(bytes.clone(), charset, options.hex, options.checkNewline);
        if (text == null) return null;
        text = text.replace("\u0000", "").trim();
        return text.isEmpty() ? null : text;
    }
}
```

- [ ] **Step 2: Create `Commands.java`**

Create `app/src/main/java/com/hc/mixthebluetooth/uni/Commands.java`:

```java
package com.hc.mixthebluetooth.uni;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class Commands {
    private Commands() {
    }

    public enum Capability {
        SYNC_TIME,
        START_MEASURE,
        READ_CACHE,
        DELETE_CACHE,
        SET_PARAMS,
        STOP_MEASURE
    }

    public interface Encoder {
        @NonNull
        String encode(@NonNull Capability capability);
    }

    public static final class LegacyCgm {
        private LegacyCgm() {
        }

        @NonNull
        public static String syncTime(@NonNull Date date) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy,MM,dd,HH,mm,ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return "TIME," + sdf.format(date) + "\n\r";
        }

        @NonNull
        public static String readCache() {
            return "ALL\n\r";
        }

        @NonNull
        public static String deleteCache() {
            return "DELETE\n\r";
        }
    }
}
```

- [ ] **Step 3: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.CodecTest" --tests "com.hc.mixthebluetooth.uni.CommandsTest"
```

Expected: tests pass.

- [ ] **Step 4: Commit**

Run:

```powershell
git add app\src\main\java\com\hc\mixthebluetooth\uni\Codec.java app\src\main\java\com\hc\mixthebluetooth\uni\Commands.java app\src\test\java\com\hc\mixthebluetooth\uni\CodecTest.java app\src\test\java\com\hc\mixthebluetooth\uni\CommandsTest.java
git commit -m "feat: add uni codec and command helpers"
```

Expected: commit succeeds. If this workspace should not commit yet, skip this step and keep the files staged or unstaged according to the session owner preference.

---

## Task 3: Move Widgets Into `uni/Widgets.java`

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java`
- Create: `app/src/test/java/com/hc/mixthebluetooth/uni/WidgetsTest.java`
- Delete: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java`
- Delete: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgetsTest.java`

- [ ] **Step 1: Copy current widget implementation**

Create `app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java` by copying the full current content of:

```text
app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java
```

Then make these exact edits:

```java
package com.hc.mixthebluetooth.uni;
```

Replace class declaration:

```java
public final class MetricWidgets {
```

with:

```java
public final class Widgets {
```

Replace constructor:

```java
private MetricWidgets() {
}
```

with:

```java
private Widgets() {
}
```

Replace import:

```java
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Region;
```

with:

```java
import com.hc.mixthebluetooth.uni.Controller.Region;
```

- [ ] **Step 2: Move widget tests**

Create `app/src/test/java/com/hc/mixthebluetooth/uni/WidgetsTest.java` from the current `MetricWidgetsTest`, with these replacements:

```java
package com.hc.mixthebluetooth.uni;
```

Replace:

```java
MetricWidgets.WidgetSpec
MetricWidgets.WidgetKind
MetricWidgets.orderedForDisplay
```

with:

```java
Widgets.WidgetSpec
Widgets.WidgetKind
Widgets.orderedForDisplay
```

Replace:

```java
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment.Region;
```

with:

```java
import com.hc.mixthebluetooth.uni.Controller.Region;
```

- [ ] **Step 3: Keep old files until Controller exists**

Do not delete `MetricWidgets.java` yet if production code still imports it. Leave it in place temporarily.

- [ ] **Step 4: Run widget test after Controller exists**

This test depends on `Controller.Region`, so run it after Task 4 creates `Controller.java`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.WidgetsTest"
```

Expected after Task 4: PASS.

---

## Task 4: Extract `uni/Controller.java`

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`
- Modify later: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`

- [ ] **Step 1: Create Controller shell and nested contracts**

Create `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java` with the imports from current `UnifiedMessageFragment.java` that are used by `MessageController`, plus these contract types:

```java
package com.hc.mixthebluetooth.uni;

import android.annotation.SuppressLint;
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

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.BluetoothSampleParser;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.uni.Widgets.MetricWidget;
import com.hc.mixthebluetooth.uni.Widgets.WidgetSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class Controller {
    private static final int AUTO_CLEAR_BYTES = 400_000;

    public enum Region {
        ACTION,
        SUMMARY,
        MAIN,
        SECONDARY,
        DEBUG
    }

    public enum Route {
        POST,
        INNER
    }

    public enum BuiltIn {
        START_RECORD,
        STOP_RECORD,
        EXPORT,
        CLEAR_MESSAGES,
        RESET_WIDGETS
    }

    public interface HostView {
        ViewGroup region(@NonNull Region region);
        RecyclerView messageList();
        TextView bottomInfo();
    }

    public interface Gateway {
        void postText(@NonNull DeviceModule module, @NonNull String text);
    }

    public interface TextSupplier {
        @NonNull
        String get();
    }

    public interface RecordFormatter {
        @NonNull
        String format(@NonNull BluetoothSample sample);
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

    public static final class ProfileSpec {
        @NonNull public final String id;
        @NonNull public final List<BluetoothSampleParser> parsers;
        @NonNull public final List<ActionSpec> actions;
        @NonNull public final List<WidgetSpec> widgets;
        @Nullable public final RecordFormatter recordFormatter;

        private ProfileSpec(@NonNull Builder b) {
            id = b.id;
            parsers = new ArrayList<>(b.parsers);
            actions = new ArrayList<>(b.actions);
            widgets = new ArrayList<>(b.widgets);
            recordFormatter = b.recordFormatter;
        }

        public static Builder builder(@NonNull String id) {
            return new Builder(id);
        }

        public static final class Builder {
            @NonNull private final String id;
            private final List<BluetoothSampleParser> parsers = new ArrayList<>();
            private final List<ActionSpec> actions = new ArrayList<>();
            private final List<WidgetSpec> widgets = new ArrayList<>();
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

            public Builder widget(@NonNull WidgetSpec widget) {
                widgets.add(widget);
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
}
```

- [ ] **Step 2: Move current MessageController body into Controller**

From `UnifiedMessageFragment.java`, copy the fields, constructor, and methods of current `static final class MessageController` into `Controller.java`, directly inside `public final class Controller`.

Make these exact edits while copying:

Replace constructor name:

```java
MessageController(@NonNull Context context, @NonNull ProfileSpec spec, @NonNull HostView host, @NonNull BluetoothGateway gateway)
```

with:

```java
public Controller(@NonNull Context context, @NonNull ProfileSpec spec, @NonNull HostView host, @NonNull Gateway gateway)
```

Replace field type:

```java
private final BluetoothGateway gateway;
```

with:

```java
private final Gateway gateway;
```

Replace:

```java
MetricWidgets.orderedForDisplay
MetricWidgets.create
```

with:

```java
Widgets.orderedForDisplay
Widgets.create
```

Replace `private` package methods needed by `UniFragment`:

```java
void init()
void onEvent(@Nullable Object event)
void release()
```

with:

```java
public void init()
public void onEvent(@Nullable Object event)
public void release()
```

- [ ] **Step 3: Replace decode method with Codec call**

In `onBtData`, replace:

```java
String text = decode(data.bytes, FragmentParameter.getInstance().getCodeFormat(context));
```

with:

```java
Codec.Options options = new Codec.Options(
        FragmentParameter.getInstance().getCodeFormat(context),
        false,
        false
);
String text = Codec.decode(data.bytes, options);
```

Then remove the copied private `decode(...)` method from `Controller.java`.

- [ ] **Step 4: Keep status helpers in Controller for this task**

Do not yet extract `SystemStatusPresenter` or `MessageListPresenter`. The first implementation should compile with the current helper methods:

```java
resetWidgets()
setRecordState(boolean recording)
updateByteCounter()
setBottomInfo(@Nullable String text)
dp(int value)
```

Extraction can happen after compile is green, or remain as a later cleanup if the file stays readable.

- [ ] **Step 5: Run compile check**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: compile may still fail because `UniFragment` does not exist and old code is not rewired. Continue to Task 5.

---

## Task 5: Create `UniFragment` And Compatibility Wrapper

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`

- [ ] **Step 1: Create `UniFragment.java`**

Create `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`:

```java
package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.databinding.FragmentUnifiedMessageBinding;
import com.hc.mixthebluetooth.uni.Controller;
import com.hc.mixthebluetooth.uni.Profiles;

import java.nio.charset.StandardCharsets;

public class UniFragment extends BTFragment<FragmentUnifiedMessageBinding> {

    private Controller controller;

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_EVENT);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        controller = new Controller(
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

    private static final class BindingHost implements Controller.HostView {
        private final FragmentUnifiedMessageBinding binding;

        BindingHost(@NonNull FragmentUnifiedMessageBinding binding) {
            this.binding = binding;
        }

        @Override
        public ViewGroup region(@NonNull Controller.Region region) {
            if (region == Controller.Region.ACTION) return binding.actionRegion;
            if (region == Controller.Region.SUMMARY) return binding.summaryRegion;
            if (region == Controller.Region.MAIN) return binding.mainRegion;
            if (region == Controller.Region.SECONDARY) return binding.secondaryRegion;
            if (region == Controller.Region.DEBUG) return binding.debugRegion;
            return binding.contentRoot;
        }

        @Override
        public RecyclerView messageList() {
            return binding.recyclerMessage;
        }

        @Override
        public TextView bottomInfo() {
            return binding.tvBottomInfo;
        }
    }

    private final class FragmentGateway implements Controller.Gateway {
        @Override
        public void postText(@NonNull DeviceModule module, @NonNull String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            sendDataToActivity(
                    StaticConstants.CMD_BT_POST,
                    new BTPackage.BTPost(module, bytes)
            );
        }
    }
}
```

- [ ] **Step 2: Replace `UnifiedMessageFragment.java` with wrapper**

Replace the full content of `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java` with:

```java
package com.hc.mixthebluetooth.fragment;

/**
 * @deprecated Use {@link UniFragment}. Kept as a compatibility wrapper while
 * old imports and docs are migrated.
 */
@Deprecated
public class UnifiedMessageFragment extends UniFragment {
}
```

- [ ] **Step 3: Update `CommunicationActivity`**

In `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`, replace:

```java
import com.hc.mixthebluetooth.fragment.UnifiedMessageFragment;
```

with:

```java
import com.hc.mixthebluetooth.fragment.UniFragment;
```

Replace:

```java
viewPagerManage.addFragment(new UnifiedMessageFragment());
```

with:

```java
viewPagerManage.addFragment(new UniFragment());
```

- [ ] **Step 4: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: compile may still fail because `Profiles` still references old nested types. Continue to Task 6.

---

## Task 6: Move EIS Profile To `uni/profile/eis`

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/uni/Profiles.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/uni/profile/eis/EisProfile.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java`
- Create: `app/src/test/java/com/hc/mixthebluetooth/uni/ProfilesTest.java`
- Delete after migration: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/ProfilesTest.java`

- [ ] **Step 1: Create registry**

Create `app/src/main/java/com/hc/mixthebluetooth/uni/Profiles.java`:

```java
package com.hc.mixthebluetooth.uni;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.uni.profile.eis.EisProfile;

public final class Profiles {
    private Profiles() {
    }

    @NonNull
    public static Controller.ProfileSpec eis() {
        return EisProfile.create();
    }
}
```

- [ ] **Step 2: Create `EisProfile.java`**

Create `app/src/main/java/com/hc/mixthebluetooth/uni/profile/eis/EisProfile.java` by moving the current EIS implementation out of `activity/tool/Profiles.java`.

The file should use this package and imports:

```java
package com.hc.mixthebluetooth.uni.profile.eis;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.BluetoothSampleParser;
import com.hc.mixthebluetooth.uni.Controller;
import com.hc.mixthebluetooth.uni.Widgets.WidgetSpec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
```

Use this public shape:

```java
public final class EisProfile {
    private EisProfile() {
    }

    @NonNull
    public static Controller.ProfileSpec create() {
        return Controller.ProfileSpec.builder("eis")
                .parser(new EisParser())
                .action(Controller.ActionSpec.inner("start_record", "开始记录", Controller.BuiltIn.START_RECORD))
                .action(Controller.ActionSpec.inner("stop_record", "结束记录", Controller.BuiltIn.STOP_RECORD))
                .action(Controller.ActionSpec.inner("export", "导出", Controller.BuiltIn.EXPORT))
                .widget(WidgetSpec.gauge("eis_conductance_gauge")
                        .title("电导率")
                        .metric(EisSample.METRIC_US)
                        .unit("uS")
                        .region(Controller.Region.SUMMARY)
                        .order(10)
                        .gaugeMax(10f)
                        .lineColor(0xFF4EE097)
                        .build())
                .widget(WidgetSpec.value("eis_ohm_value")
                        .title("阻抗")
                        .metric(EisSample.METRIC_OHM)
                        .unit("Ω")
                        .region(Controller.Region.SUMMARY)
                        .order(20)
                        .build())
                .widget(WidgetSpec.line("eis_ohm_line")
                        .title("电化学交流阻抗（EIS）")
                        .metric(EisSample.METRIC_OHM)
                        .unit("Ω")
                        .region(Controller.Region.MAIN)
                        .order(10)
                        .lineColor(0xFF4285F4)
                        .build())
                .widget(WidgetSpec.line("eis_us_line")
                        .title("电导率（uS）")
                        .metric(EisSample.METRIC_US)
                        .unit("uS")
                        .region(Controller.Region.MAIN)
                        .order(20)
                        .lineColor(0xFFFBBC05)
                        .build())
                .widget(WidgetSpec.stats("eis_ohm_stats")
                        .title("阻抗统计")
                        .metric(EisSample.METRIC_OHM)
                        .unit("Ω")
                        .region(Controller.Region.MAIN)
                        .order(30)
                        .build())
                .recordJson(EisProfile::eisJson)
                .build();
    }
}
```

Then move the current `EisParser`, `EisSample`, `eisJson(...)`, and `esc(...)` implementations into the same file as package-private or public static nested types, preserving behavior.

- [ ] **Step 3: Add compatibility proxy for old `Profiles`**

Replace `app/src/main/java/com/hc/mixthebluetooth/activity/tool/Profiles.java` with:

```java
package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.uni.Controller;

/**
 * @deprecated Use {@link com.hc.mixthebluetooth.uni.Profiles}.
 */
@Deprecated
public final class Profiles {
    private Profiles() {
    }

    @NonNull
    public static Controller.ProfileSpec eis() {
        return com.hc.mixthebluetooth.uni.Profiles.eis();
    }
}
```

- [ ] **Step 4: Move profile tests**

Create `app/src/test/java/com/hc/mixthebluetooth/uni/ProfilesTest.java` from current `ProfilesTest`, replacing imports and names:

```java
package com.hc.mixthebluetooth.uni;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.activity.tool.BluetoothSample;
import com.hc.mixthebluetooth.uni.profile.eis.EisProfile;
import com.hc.mixthebluetooth.uni.profile.eis.EisProfile.EisSample;

import org.junit.Test;
```

Replace old type references:

```java
ProfileSpec -> Controller.ProfileSpec
BuiltIn -> Controller.BuiltIn
Route -> Controller.Route
Region -> Controller.Region
MetricWidgets -> Widgets
Profiles.EisSample -> EisSample
Profiles.eisJson(sample) -> EisProfile.eisJson(sample)
```

- [ ] **Step 5: Run focused profile tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.ProfilesTest"
```

Expected: PASS.

- [ ] **Step 6: Delete old profile test after new test passes**

Delete:

```text
app/src/test/java/com/hc/mixthebluetooth/activity/tool/ProfilesTest.java
```

---

## Task 7: Add Output Wrapper Around SampleRecorder

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/uni/Output.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`

- [ ] **Step 1: Create `Output.java`**

Create `app/src/main/java/com/hc/mixthebluetooth/uni/Output.java`:

```java
package com.hc.mixthebluetooth.uni;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.SampleRecorder;

public final class Output {
    private final SampleRecorder recorder = new SampleRecorder();

    public void start(@NonNull Context context, @NonNull String prefix) {
        recorder.start(context, prefix);
    }

    public void stop() {
        recorder.stop();
    }

    public boolean isRecording() {
        return recorder.isRecording();
    }

    public int sampleCount() {
        return recorder.getSampleCount();
    }

    @NonNull
    public String exportPath() {
        return recorder.exportPath();
    }

    public void appendJsonLine(@Nullable String json) {
        recorder.appendLine(json);
    }

    public void release() {
        recorder.release();
    }
}
```

- [ ] **Step 2: Replace SampleRecorder usage in Controller**

In `Controller.java`, replace:

```java
private final SampleRecorder recorder = new SampleRecorder();
```

with:

```java
private final Output output = new Output();
```

Replace:

```java
recorder.release();
recorder.start(context, "unified_" + spec.id);
recorder.stop();
recorder.getSampleCount();
recorder.exportPath();
recorder.isRecording();
recorder.appendLine(spec.recordFormatter.format(sample));
```

with:

```java
output.release();
output.start(context, "uni_" + spec.id);
output.stop();
output.sampleCount();
output.exportPath();
output.isRecording();
output.appendJsonLine(spec.recordFormatter.format(sample));
```

- [ ] **Step 3: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: compile succeeds or fails only because old `MetricWidgets` imports remain. Continue to Task 8.

---

## Task 8: Rewire Imports And Remove Old Widget File

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/profile/eis/EisProfile.java`
- Delete: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java`
- Delete: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgetsTest.java`

- [ ] **Step 1: Search old widget references**

Run:

```powershell
rg -n "MetricWidgets|activity\\.tool\\.chart" app\src\main\java app\src\test\java
```

Expected: references only in old `MetricWidgets.java`, old `MetricWidgetsTest.java`, or files already being edited.

- [ ] **Step 2: Replace old references**

Replace production imports:

```java
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets;
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets.MetricWidget;
import com.hc.mixthebluetooth.activity.tool.chart.MetricWidgets.WidgetSpec;
```

with:

```java
import com.hc.mixthebluetooth.uni.Widgets;
import com.hc.mixthebluetooth.uni.Widgets.MetricWidget;
import com.hc.mixthebluetooth.uni.Widgets.WidgetSpec;
```

Replace type prefixes:

```java
MetricWidgets.
```

with:

```java
Widgets.
```

- [ ] **Step 3: Delete old widget files**

Delete:

```text
app/src/main/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgets.java
app/src/test/java/com/hc/mixthebluetooth/activity/tool/chart/MetricWidgetsTest.java
```

- [ ] **Step 4: Run widget tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.WidgetsTest"
```

Expected: PASS.

---

## Task 9: Compile And Fix Remaining Imports

**Files:**
- Modify as needed:
  - `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`
  - `app/src/main/java/com/hc/mixthebluetooth/fragment/UnifiedMessageFragment.java`
  - `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`
  - `app/src/main/java/com/hc/mixthebluetooth/uni/Profiles.java`
  - `app/src/main/java/com/hc/mixthebluetooth/uni/profile/eis/EisProfile.java`
  - `app/src/test/java/com/hc/mixthebluetooth/uni/*.java`

- [ ] **Step 1: Search old Fragment nested type references**

Run:

```powershell
rg -n "UnifiedMessageFragment\\.(ActionSpec|BuiltIn|ProfileSpec|Region|Route)|fragment\\.UnifiedMessageFragment\\.(ActionSpec|BuiltIn|ProfileSpec|Region|Route)" app\src\main\java app\src\test\java
```

Expected: no results.

- [ ] **Step 2: Search old Profiles references**

Run:

```powershell
rg -n "activity\\.tool\\.Profiles|Profiles\\.EisSample|Profiles\\.eisJson" app\src\main\java app\src\test\java
```

Expected: no results outside deprecated proxy file, if proxy remains.

- [ ] **Step 3: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 10: Final Cleanup And Documentation Sync

**Files:**
- Modify: `docs/superpowers/specs/2026-05-23-uni-runtime-third-stage-design.md` if implementation chooses a compatibility wrapper or leaves a legacy proxy.
- Maybe modify: `docs/superpowers/plans/2026-05-23-uni-runtime-third-stage.md` only if execution reveals a plan correction.

- [ ] **Step 1: Verify final file tree**

Run:

```powershell
Get-ChildItem -Path app\src\main\java\com\hc\mixthebluetooth\uni -Recurse -File | ForEach-Object { $_.FullName.Replace((Get-Location).Path + '\','') }
```

Expected includes:

```text
app\src\main\java\com\hc\mixthebluetooth\uni\Controller.java
app\src\main\java\com\hc\mixthebluetooth\uni\Widgets.java
app\src\main\java\com\hc\mixthebluetooth\uni\Codec.java
app\src\main\java\com\hc\mixthebluetooth\uni\Commands.java
app\src\main\java\com\hc\mixthebluetooth\uni\Output.java
app\src\main\java\com\hc\mixthebluetooth\uni\Profiles.java
app\src\main\java\com\hc\mixthebluetooth\uni\profile\eis\EisProfile.java
```

- [ ] **Step 2: Verify old command strings are contained**

Run:

```powershell
rg -n '"ALL\\n\\r"|"DELETE\\n\\r"|TIME,' app\src\main\java\com\hc\mixthebluetooth
```

Expected: command strings appear only in `uni/Commands.java`, tests, docs, or legacy old Fragment files not touched by this plan.

- [ ] **Step 3: Verify no old widget package remains**

Run:

```powershell
rg -n "activity\\.tool\\.chart|MetricWidgets" app\src\main\java app\src\test\java
```

Expected: no results.

- [ ] **Step 4: Final verification**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:testDebugUnitTest
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```powershell
git add app\src\main\java\com\hc\mixthebluetooth app\src\test\java\com\hc\mixthebluetooth docs\superpowers\specs\2026-05-23-uni-runtime-third-stage-design.md docs\superpowers\plans\2026-05-23-uni-runtime-third-stage.md
git commit -m "refactor: extract uni runtime structure"
```

Expected: commit succeeds. If the session owner wants to review before commit, leave changes uncommitted and report status.

---

## Self-Review

### Spec Coverage

- UniFragment shell: covered by Task 5.
- Controller as runtime skeleton with nested contracts: covered by Task 4.
- Widgets independent file: covered by Tasks 3 and 8.
- Codec boundary: covered by Tasks 1 and 2.
- Commands boundary: covered by Tasks 1 and 2.
- Output boundary without Retrofit: covered by Task 7.
- EIS profile split: covered by Task 6.
- API/register deferred to fourth stage: no implementation task by design.

### Placeholder Scan

The plan intentionally avoids third-stage Retrofit implementation. The only deferred items are explicitly marked as fourth-stage scope in the spec, not as incomplete third-stage work.

### Type Consistency

- `Controller.ProfileSpec`, `Controller.ActionSpec`, `Controller.Region`, `Controller.Route`, and `Controller.BuiltIn` are the single contract types for profile and controller.
- `Widgets.WidgetSpec` is used by `Controller.ProfileSpec`.
- `Codec.Options` is used only by `Controller`.
- `Output` wraps existing `SampleRecorder` and does not change recorder storage policy.
