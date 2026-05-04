# Project Code Organization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Bluetooth page and fragment code easier to read by extracting stable reusable pieces while keeping the current new event-channel flow as the only target path.

**Architecture:** Keep `CommunicationActivity` as the screen shell, keep `BTFragment` as the fragment event router, and move reusable parsing/list/chart/command behavior into small tool classes. Refactor one behavior boundary at a time and compile after each task so the app never drifts far from a runnable state.

**Tech Stack:** Android Java, ViewBinding, existing event bus from `BaseActivity`/`BaseFragment`, MPAndroidChart, Gradle 6.5 with JDK 17.

---

## File Structure

Current stable pieces:

- `app/src/main/java/com/hc/mixthebluetooth/entity/StaticConstants.java`: channel and command names.
- `app/src/main/java/com/hc/mixthebluetooth/entity/BTPackage.java`: typed Bluetooth payloads.
- `app/src/main/java/com/hc/mixthebluetooth/fragment/BTFragment.java`: shared channel subscription and typed callback routing.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothPayloadDecoder.java`: byte-to-text decoding policy.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessageItemTools.java`: `FragmentMessageItem` construction and merge rules.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessageListController.java`: RecyclerView adapter wrapper for simple message pages.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/LineBuffer.java`: split incomplete Bluetooth text chunks into complete lines.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisParser.java`: parse `ohm,uS` sample lines.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/RealtimeLineChart.java`: one real-time chart.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/ChartRegistry.java`: multiple chart registry.

Planned new pieces:

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/IncomingMessageProcessor.java`: pure helper that decodes incoming bytes and decides whether to append or merge a message item.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSample.java`: common interface for parsed device samples.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSampleParser.java`: one parser per device/protocol format.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSampleRegistry.java`: ordered parser registry, similar in spirit to `ChartRegistry`.
- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleChartBinder.java`: maps sample metric keys to registered charts.
- `app/src/main/java/com/hc/mixthebluetooth/activity/communication/FragmentEventPublisher.java`: activity-side wrapper for `sendDataToFragment(...)`.
- `app/src/main/java/com/hc/mixthebluetooth/activity/communication/CommunicationPages.java`: ViewPager and tab page setup.
- `app/src/main/java/com/hc/mixthebluetooth/activity/communication/CommunicationPopupMenu.java`: title menu actions.
- `app/src/main/java/com/hc/mixthebluetooth/activity/communication/BluetoothConnectionUiState.java`: small value object for `CONNECTED / CONNECTING / DISCONNECT` text handling.

The first pass should not move Bluetooth connection callbacks out of `CommunicationActivity`; that part has real device timing risk. Extract publishing, page setup, menu setup, and data processing first.

---

## Task 1: Baseline Verification

**Files:**
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`

- [ ] **Step 1: Check current diff**

Run:

```powershell
git status --short
```

Expected: only the current refactor files are dirty; no unexpected unrelated files.

- [ ] **Step 2: Compile before touching the next code block**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Note current manual smoke path**

Manual app check:

```text
1. Open CommunicationActivity.
2. Connect BT24-S.
3. Confirm MessageNew receives "ohm,uS" samples.
4. Confirm charts update.
5. Confirm Log tab appears in development mode.
6. Disconnect and confirm loop send stops during reconnect delay.
```

---

## Task 2: Make Incoming Message Processing Reusable

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/IncomingMessageProcessor.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

Reason: `FragmentMessage.addListData(...)` still mixes byte decoding, newline policy, cache playback, measurement parsing, and RecyclerView item mutation. First extract the simplest safe slice: decode plus append/merge decision.

- [ ] **Step 1: Create the helper skeleton**

Create:

```java
package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class IncomingMessageProcessor {

    public static final class Result {
        @NonNull public final String text;
        public final boolean completeLine;
        public final boolean empty;

        private Result(@NonNull String text, boolean completeLine) {
            this.text = text;
            this.completeLine = completeLine;
            this.empty = text.length() == 0;
        }
    }

    private IncomingMessageProcessor() {
    }

    @NonNull
    public static Result decode(
            @Nullable byte[] data,
            @Nullable String code,
            boolean readHex,
            boolean checkNewline
    ) {
        BluetoothPayloadDecoder.Result decoded = BluetoothPayloadDecoder.decodeResult(
                data,
                code,
                new BluetoothPayloadDecoder.Options.Builder()
                        .hex(readHex)
                        .removeTrailingCrLf(checkNewline)
                        .cleanNull(true)
                        .trim(false)
                        .build()
        );
        boolean completeLine = data != null && BluetoothPayloadDecoder.endsWithCrLf(data);
        return new Result(decoded.text, completeLine);
    }
}
```

- [ ] **Step 2: Use it in `FragmentMessage.addListData(...)`**

Replace the local `decodeIncomingBytes(...)` call with:

```java
IncomingMessageProcessor.Result incoming = IncomingMessageProcessor.decode(
        data,
        FragmentParameter.getInstance().getCodeFormat(getContext()),
        isReadHex,
        checkNewline
);
String dataString = incoming.text;
```

Use `incoming.completeLine` where the method currently calculates `newline`.

- [ ] **Step 3: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 3: Introduce Registered Bluetooth Samples

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSample.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSampleParser.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSampleRegistry.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleChartBinder.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisSample.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisParser.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`

Reason: `EisSample` should not be the common model for every Bluetooth device. Different devices produce different text formats and different metric sets, so the reusable shape is `BluetoothSample` plus registered parsers. A fragment should decode text once, ask the registry to parse it, and then bind the parsed metrics to charts/recording without knowing every device protocol.

- [x] **Step 1: Create the common sample interface**

Create:

```java
package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

import java.util.Map;

public interface BluetoothSample {
    @NonNull
    String type();

    @NonNull
    String raw();

    @NonNull
    Map<String, Float> metrics();
}
```

- [x] **Step 2: Create the parser interface**

Create:

```java
package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.Nullable;

public interface BluetoothSampleParser {
    @Nullable
    BluetoothSample parse(@Nullable String line);
}
```

- [x] **Step 3: Make `EisSample` implement `BluetoothSample`**

Replace `EisSample` with:

```java
package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EisSample implements BluetoothSample {

    public static final String TYPE = "eis";
    public static final String METRIC_OHM = "ohm";
    public static final String METRIC_US = "us";

    public final float ohm;
    public final float us;
    public final String raw;

    public EisSample(float ohm, float us, String raw) {
        this.ohm = ohm;
        this.us = us;
        this.raw = raw;
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
        Map<String, Float> values = new LinkedHashMap<>();
        values.put(METRIC_OHM, ohm);
        values.put(METRIC_US, us);
        return values;
    }
}
```

- [x] **Step 4: Make `EisParser` a parser implementation**

Change the class declaration:

```java
public final class EisParser implements BluetoothSampleParser {
```

Keep the existing static `parse(...)` for old callers, and add:

```java
@Nullable
@Override
public BluetoothSample parse(@Nullable String line) {
    return parseLine(line);
}
```

Then rename the current static method body to:

```java
@Nullable
public static EisSample parseLine(@Nullable String line) {
    // current EisParser.parse(...) body
}
```

Existing code may still call `EisParser.parseLine(line)` during the migration.

- [x] **Step 5: Create `BluetoothSampleRegistry`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BluetoothSampleRegistry {

    private final List<BluetoothSampleParser> parsers = new ArrayList<>();

    @NonNull
    public BluetoothSampleRegistry register(@NonNull BluetoothSampleParser parser) {
        parsers.add(parser);
        return this;
    }

    @Nullable
    public BluetoothSample parse(@Nullable String line) {
        for (BluetoothSampleParser parser : parsers) {
            BluetoothSample sample = parser.parse(line);
            if (sample != null) return sample;
        }
        return null;
    }
}
```

- [x] **Step 6: Create `SampleChartBinder`**

Create:

```java
package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SampleChartBinder {

    private final ChartRegistry chartRegistry;
    private final Map<String, String> metricToChart = new LinkedHashMap<>();

    public SampleChartBinder(@NonNull ChartRegistry chartRegistry) {
        this.chartRegistry = chartRegistry;
    }

    @NonNull
    public SampleChartBinder bind(@NonNull String metricKey, @NonNull String chartKey) {
        metricToChart.put(metricKey, chartKey);
        return this;
    }

    public void append(@NonNull BluetoothSample sample) {
        Map<String, Float> metrics = sample.metrics();
        for (Map.Entry<String, String> entry : metricToChart.entrySet()) {
            Float value = metrics.get(entry.getKey());
            if (value != null) {
                chartRegistry.append(entry.getValue(), value);
            }
        }
    }
}
```

- [x] **Step 7: Route `FragmentMessageNew` through the registry**

Add fields:

```java
private final BluetoothSampleRegistry sampleRegistry = new BluetoothSampleRegistry()
        .register(new EisParser());
private SampleChartBinder chartBinder;
```

After chart registration in `initCharts()`:

```java
chartBinder = new SampleChartBinder(chartRegistry)
        .bind(EisSample.METRIC_OHM, CHART_OHM)
        .bind(EisSample.METRIC_US, CHART_US);
```

Change line handling to:

```java
BluetoothSample sample = sampleRegistry.parse(line);
if (sample == null) {
    logWarn("MessageNew parse failed, raw: " + line);
    return;
}

chartBinder.append(sample);

if (isRecording()) {
    recordSample(sample);
}
```

Adjust `recordSample(...)` to accept `BluetoothSample`. If `EisJsonLineBuilder` still only supports EIS, keep a temporary `instanceof EisSample` branch there and move generic JSON export into a later task.

- [ ] **Step 8: In `FragmentMessage`, isolate the existing CA/RI/EIS branches behind named methods**

Before moving logic out, reshape `addListData(...)` into:

```java
if (handlePlaybackCache(dataString)) return;
if (handleMeasurementText(dataString)) return;
appendOrMergeIncomingItem(dataString, incoming.completeLine);
```

Each helper may still contain the old logic. This step is only naming the branches, not changing behavior.

- [x] **Step 9: Compile and manually check MessageNew charts**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Manual check: connect BT24-S and confirm chart points still append.

---

## Task 4: Wrap Activity-to-Fragment Publishing

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/communication/FragmentEventPublisher.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`

Reason: `CommunicationActivity` should say “publish BT connected” instead of repeatedly knowing channel names and package classes.

- [ ] **Step 1: Create publisher**

Create:

```java
package com.hc.mixthebluetooth.activity.communication;

import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.entity.BTPackage;
import com.hc.mixthebluetooth.entity.StaticConstants;

public final class FragmentEventPublisher {

    public interface Sink {
        void send(String channel, @Nullable Object payload);
    }

    private final Sink sink;

    public FragmentEventPublisher(Sink sink) {
        this.sink = sink;
    }

    public void btData(DeviceModule module, byte[] data) {
        sink.send(StaticConstants.CH_BT_DATA, new BTPackage.BTData(module, data));
    }

    public void btConnected(DeviceModule module) {
        sink.send(StaticConstants.CH_BT_DATA, new BTPackage.Connected(module));
    }

    public void btDisconnected() {
        sink.send(StaticConstants.CH_BT_DATA, BTPackage.Disconnected.INSTANCE);
    }

    public void connectState(String state) {
        sink.send(StaticConstants.CH_SET_CONNECT_STATE, state);
    }

    public void stopLoopSend() {
        sink.send(StaticConstants.CH_STOP_LOOP_SEND, null);
    }
}
```

- [ ] **Step 2: Replace only five existing publish methods first**

In `CommunicationActivity`, add:

```java
private FragmentEventPublisher fragmentEvents;
```

Initialize:

```java
fragmentEvents = new FragmentEventPublisher(this::sendDataToFragment);
```

Then make existing methods delegate:

```java
private void publishBtDisconnected() {
    fragmentEvents.btDisconnected();
}
```

Keep the method names in `CommunicationActivity` for now. This makes the diff easy to review.

- [ ] **Step 3: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

---

## Task 5: Extract Page and Tab Setup

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/communication/CommunicationPages.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`

Reason: page setup is stable UI wiring. It does not need to sit between Bluetooth connection callbacks and event publishing.

- [ ] **Step 1: Move page constants and setup into a helper**

Create helper with responsibilities:

```java
public final class CommunicationPages {
    public static final int PAGE_MESSAGE = 0;
    public static final int PAGE_MESSAGE_NEW = 1;
    public static final int PAGE_CUSTOM = 2;
    public static final int PAGE_ION_ANALYSIS = 3;
    public static final int PAGE_THREE = 4;
    public static final int PAGE_LOG = 5;
}
```

Add setup methods after the constants in the same class:

```java
public ViewPagerManage setup(ViewPager viewPager, boolean showLogPage) {
    ViewPagerManage manage = new ViewPagerManage(viewPager);
    manage.addFragment(new FragmentMessage());
    manage.addFragment(new FragmentMessageNew());
    manage.addFragment(new FragmentCustom());
    manage.addFragment(new FragmentIonAnalysis());
    manage.addFragment(new FragmentThree());
    if (showLogPage) {
        manage.addFragment(new FragmentLog());
    }
    viewPager.setAdapter(manage.getAdapter());
    viewPager.setOffscreenPageLimit(showLogPage ? 5 : 4);
    return manage;
}
```

- [ ] **Step 2: Keep tab selection in Activity for one more commit**

Only replace `initPages()` internals. Do not move `updateTabSelection()` yet. This avoids mixing adapter setup and tab state bugs.

- [ ] **Step 3: Compile and manually click all visible tabs**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Manual check: Message, New, Custom, Ion Analysis, Three, and Log if visible.

---

## Task 6: Extract Popup Menu Actions

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/communication/CommunicationPopupMenu.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`

Reason: title menu behavior is separate from Bluetooth data flow. Extracting it will make `CommunicationActivity` mostly read as lifecycle + Bluetooth callbacks.

- [ ] **Step 1: Create callback interface**

```java
public interface Actions {
    boolean isConnected();
    DeviceModule getCurrentModule();
    void stopLoopSend();
    void openSendFilePage();
    void openMtuDialog(DeviceModule module);
    void showConnectRequiredToast();
}
```

- [ ] **Step 2: Move only popup view binding code**

Move `updateMtuMenuText(...)`, `bindPopupActions(...)`, and `showPopupWindow(...)` into `CommunicationPopupMenu`.

- [ ] **Step 3: Leave real operations in Activity**

Activity still owns:

```java
private void openSendFilePage()
private void openMtuDialog(DeviceModule module)
private DeviceModule getCurrentModule()
```

- [ ] **Step 4: Compile and manually open the menu**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Manual check: menu opens, disconnected operations still toast, MTU text still reflects module MTU when connected.

---

## Task 7: Migrate Next Fragments to `BTFragment`

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentCustom.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentIonAnalysis.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentThree.java`

Reason: after the common tools are stable, migrate page by page. Do not preserve old channel compatibility unless a page still cannot compile without it.

- [ ] **Step 1: For each fragment, change base class**

Pattern:

```java
public class FragmentCustom extends BTFragment<FragmentCustomBinding> {
    @Override
    protected void initChannels() {
        register(StaticConstants.CH_BT_DATA, StaticConstants.CH_SET_CONNECT_STATE);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        // old initAll body
    }
}
```

- [ ] **Step 2: Replace `updateState(...)` object inspection**

Pattern:

```java
@Override
protected void onBtData(BTPackage.BTData data) {
    // old byte[] handling
}

@Override
protected void onBtConnected(DeviceModule module) {
    // old DeviceModule handling
}
```

- [ ] **Step 3: Compile after each fragment**

Run after each page:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

---

## Task 8: Remove Dead Compatibility Names

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/entity/StaticConstants.java`
- Search all Java files under: `app/src/main/java`

Reason: after all target fragments use the new channel path, deprecated aliases become noise.

- [ ] **Step 1: Search deprecated old names**

Run:

```powershell
Get-ChildItem .\app\src\main\java -Recurse -Filter *.java |
  Select-String -Pattern 'FRAGMENT_STATE_|MESSAGE_NEW_RECORD_STATE|MESSAGE_NEW_EXPORT_RESULT|MESSAGE_NEW_CONTROL'
```

Expected: no production usage outside comments or deprecated aliases.

- [ ] **Step 2: Remove aliases only after search is clean**

Remove deprecated constants from `StaticConstants.java`.

- [ ] **Step 3: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

---

## Completion Checklist

- [ ] `CommunicationActivity` reads in this order: fields, lifecycle, init, subscription commands, Bluetooth callbacks, connection state, menu callbacks, tiny publish delegates.
- [ ] `FragmentMessage.addListData(...)` is no longer the place where every parsing policy lives.
- [ ] `FragmentMessageNew` still works as the clean sample page.
- [ ] Shared logic lives in `activity/tool` or `activity/communication`, not copied per fragment.
- [ ] All migrated fragments use `BTFragment.initChannels()` and `initAllImpl()`.
- [ ] Gradle compile passes with JDK 17.
- [ ] Manual BT24-S receive/chart/log smoke path passes.
