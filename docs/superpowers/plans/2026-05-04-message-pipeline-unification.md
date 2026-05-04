# Message Pipeline Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `FragmentMessageNew` onto a self-owned message pipeline and prepare `FragmentMessage` to converge on the same outer shape without changing legacy device behavior.

**Architecture:** Keep `CommunicationActivity` as the Bluetooth shell and move MessageNew-specific recording/control behavior into reusable helpers owned by the fragment. Add a small `MessagePipelineController` that wires decode, message list, sample parsing, chart binding, and recording callbacks. For `FragmentMessage`, first align the receive/send/list boundary with the same pipeline vocabulary, while keeping old CGM/cache/chart parsing isolated behind legacy methods.

**Tech Stack:** Android Java, ViewBinding, LiveEventBus through existing `BaseActivity`/`BaseFragment`, MPAndroidChart, JUnit 4 for pure helper tests, Gradle with JDK 17.

---

## File Structure

Create:

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java`
  - Owns recording state and `.jsonl` file writing.
  - Accepts already-built JSON lines so it does not depend on one sample type.

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessagePipelineController.java`
  - Owns the reusable receive path for clean message pages.
  - Decodes bytes, appends raw text to `MessageListController`, parses samples, updates charts, and emits parsed samples to a listener.

- `app/src/test/java/com/hc/mixthebluetooth/activity/tool/EisJsonLineBuilderTest.java`
  - Verifies JSON escaping used by the recorder path.

Modify:

- `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`
  - Replace activity command/event recording with local `SampleRecorder`.
  - Delegate byte handling to `MessagePipelineController`.

- `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
  - Remove `MessageNewCmd`, `CMD_MSG_NEW_CONTROL`, `EV_REC_SAMPLE`, and inner `Recorder` usage.
  - Continue handling only generic `CMD_SEND_BT_DATA`.

- `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`
  - Keep behavior stable.
  - Rename and isolate receive branches so the outer flow resembles the new pipeline.

- `docs/superpowers/specs/2026-05-04-message-pipeline-unification-design.md`
  - Add one sentence clarifying that `FragmentMessage` must eventually look like `FragmentMessageNew` at the outer structure level.

Deferred backlog:

- A future `DeviceProfile` layer can declare parsers, charts, recorder format, and command controls. This plan leaves that as a concrete backlog item and does not implement it.

---

## Task 1: Baseline Verification

**Files:**
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Check current worktree**

Run:

```powershell
git status --short
```

Expected: existing refactor files may already be dirty. Do not revert them. Note whether `FragmentMessageNew.java`, `CommunicationActivity.java`, and tool classes already have staged changes before editing.

- [ ] **Step 2: Compile current code before changing behavior**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run existing unit tests**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:testDebugUnitTest
```

Expected: existing tests pass. If they fail because of unrelated old sample tests, record the failing class and continue only after confirming compile still succeeds.

---

## Task 2: Test JSON Escaping For Recording Lines

**Files:**
- Create: `app/src/test/java/com/hc/mixthebluetooth/activity/tool/EisJsonLineBuilderTest.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisJsonLineBuilder.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/EisSample.java`

- [ ] **Step 1: Add focused unit tests**

Create `app/src/test/java/com/hc/mixthebluetooth/activity/tool/EisJsonLineBuilderTest.java`:

```java
package com.hc.mixthebluetooth.activity.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EisJsonLineBuilderTest {

    @Test
    public void escapeJsonEscapesControlCharacters() {
        String escaped = EisJsonLineBuilder.escapeJson("a\"b\\c\n\r\t");

        assertEquals("a\\\"b\\\\c\\n\\r\\t", escaped);
    }

    @Test
    public void buildIncludesSampleMetricsAndRawText() {
        EisSample sample = new EisSample(12.5f, 3.25f, "12.5惟,3.25uS");

        String json = EisJsonLineBuilder.build(null, sample);

        assertTrue(json.contains("\"mac\":\"\""));
        assertTrue(json.contains("\"name\":\"\""));
        assertTrue(json.contains("\"ohm\":12.5"));
        assertTrue(json.contains("\"us\":3.25"));
        assertTrue(json.contains("\"raw\":\"12.5惟,3.25uS\""));
    }
}
```

- [ ] **Step 2: Run the new test**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.activity.tool.EisJsonLineBuilderTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit the test-only checkpoint**

Run:

```powershell
git add app/src/test/java/com/hc/mixthebluetooth/activity/tool/EisJsonLineBuilderTest.java
git commit -m "test: cover EIS record JSON lines"
```

Expected: commit succeeds. If the current branch already has unrelated staged changes, unstage only unrelated paths first with `git restore --staged <path>` for those paths, never discarding file contents.

---

## Task 3: Add `SampleRecorder`

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java`

- [ ] **Step 1: Create the recorder helper**

Create `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java`:

```java
package com.hc.mixthebluetooth.activity.tool;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Records parsed sample JSON lines to one file per recording session.
 */
public final class SampleRecorder {

    public interface Callback {
        void onRecordStateChanged(boolean recording);

        void onRecordExported(@NonNull String path);

        void onRecordError(@NonNull String message);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Callback callback;

    private volatile boolean recording = false;
    private volatile File recordFile = null;
    private int sampleCount = 0;

    public SampleRecorder(@Nullable Callback callback) {
        this.callback = callback;
    }

    public void start(@NonNull Context context, @NonNull String filePrefix) {
        recordFile = createRecordFile(context, filePrefix);
        recording = true;
        sampleCount = 0;
        notifyStateChanged(true);
    }

    public void stop() {
        recording = false;
        notifyStateChanged(false);
    }

    public boolean isRecording() {
        return recording;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    @NonNull
    public String exportPath() {
        String path = getRecordPath();
        if (callback != null) {
            callback.onRecordExported(path);
        }
        return path;
    }

    public void appendLine(@Nullable String jsonLine) {
        if (!canAppend(jsonLine)) return;

        File file = recordFile;
        String safeLine = jsonLine.trim();
        sampleCount++;
        io.execute(() -> appendUtf8Line(file, safeLine));
    }

    public void release() {
        io.shutdown();
    }

    private boolean canAppend(@Nullable String jsonLine) {
        if (!recording) return false;
        if (recordFile == null) return false;
        return jsonLine != null && !jsonLine.trim().isEmpty();
    }

    @NonNull
    private String getRecordPath() {
        return recordFile != null ? recordFile.getAbsolutePath() : "";
    }

    private void notifyStateChanged(boolean recording) {
        if (callback != null) {
            callback.onRecordStateChanged(recording);
        }
    }

    @NonNull
    private File createRecordFile(@NonNull Context context, @NonNull String filePrefix) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(dir, filePrefix + "_" + ts + ".jsonl");

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && callback != null) {
            callback.onRecordError("Create record directory failed: " + parent.getAbsolutePath());
        }
        return file;
    }

    private void appendUtf8Line(@NonNull File file, @NonNull String line) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
        } catch (Exception e) {
            if (callback != null) {
                callback.onRecordError("Record write failed: " + e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

Run:

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java
git commit -m "feat: add reusable sample recorder"
```

Expected: commit succeeds.

---

## Task 4: Add `MessagePipelineController`

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessagePipelineController.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothPayloadDecoder.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessageListController.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/BluetoothSampleRegistry.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleChartBinder.java`

- [ ] **Step 1: Create the pipeline controller**

Create `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessagePipelineController.java`:

```java
package com.hc.mixthebluetooth.activity.tool;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;

/**
 * Reusable receive pipeline for message fragments that parse samples and update charts.
 */
public final class MessagePipelineController {

    public interface SampleListener {
        void onSample(@NonNull BluetoothSample sample);
    }

    public interface Logger {
        void warn(@NonNull String message);
    }

    private final Context context;
    private final MessageListController messageList;
    private final BluetoothSampleRegistry sampleRegistry;
    private final SampleChartBinder chartBinder;
    private final SampleListener sampleListener;
    private final Logger logger;

    public MessagePipelineController(
            @NonNull Context context,
            @NonNull MessageListController messageList,
            @NonNull BluetoothSampleRegistry sampleRegistry,
            @Nullable SampleChartBinder chartBinder,
            @Nullable SampleListener sampleListener,
            @Nullable Logger logger
    ) {
        this.context = context;
        this.messageList = messageList;
        this.sampleRegistry = sampleRegistry;
        this.chartBinder = chartBinder;
        this.sampleListener = sampleListener;
        this.logger = logger;
    }

    public void onBtData(@Nullable DeviceModule module, @Nullable byte[] bytes) {
        BluetoothPayloadDecoder.Result result = BluetoothPayloadDecoder.decodeResult(context, bytes);
        if (result.isEmpty()) return;

        handleLine(module, result.text);
    }

    public void handleLine(@Nullable DeviceModule module, @NonNull String line) {
        messageList.addIncomingText(line, module);

        BluetoothSample sample = sampleRegistry.parse(line);
        if (sample == null) {
            warn("Pipeline parse failed, raw: " + line);
            return;
        }

        if (chartBinder != null) {
            chartBinder.append(sample);
        }

        if (sampleListener != null) {
            sampleListener.onSample(sample);
        }
    }

    private void warn(@NonNull String message) {
        if (logger != null) {
            logger.warn(message);
        }
    }
}
```

- [ ] **Step 2: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

Run:

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessagePipelineController.java
git commit -m "feat: add message pipeline controller"
```

Expected: commit succeeds.

---

## Task 5: Move `FragmentMessageNew` Onto Local Pipeline And Recorder

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessagePipelineController.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/SampleRecorder.java`

- [ ] **Step 1: Replace imports**

In `FragmentMessageNew.java`, remove:

```java
import com.hc.mixthebluetooth.activity.single.MessageNewCmd;
import com.hc.mixthebluetooth.activity.tool.message.BluetoothPayloadDecoder;
```

Add:

```java
import com.hc.mixthebluetooth.activity.tool.message.MessagePipelineController;
import com.hc.mixthebluetooth.activity.tool.sample.SampleRecorder;
```

- [ ] **Step 2: Replace fields**

Replace:

```java
private MessageListController messageList;
private DeviceModule module;

private boolean mIsRecording = false;
```

with:

```java
private MessageListController messageList;
private MessagePipelineController messagePipeline;
private SampleRecorder sampleRecorder;
private DeviceModule module;
```

- [ ] **Step 3: Narrow registered channels**

Replace `initChannels()` with:

```java
@Override
protected void initChannels() {
    register(StaticConstants.CH_BT_DATA);
}
```

- [ ] **Step 4: Initialize recorder and pipeline**

In `initAllImpl(...)`, replace:

```java
initRecycler();
initCharts();
initControls();
setBottomInfo("Ready");
```

with:

```java
initRecycler();
initCharts();
initRecorder();
initPipeline();
initControls();
setBottomInfo("Ready");
```

Add these methods:

```java
private void initRecorder() {
    sampleRecorder = new SampleRecorder(new SampleRecorder.Callback() {
        @Override
        public void onRecordStateChanged(boolean recording) {
            updateRecordStateView(recording);
            logWarn("MessageNew record state: " + (recording ? "ON" : "OFF"));
        }

        @Override
        public void onRecordExported(@NonNull String path) {
            showExportResult(path);
        }

        @Override
        public void onRecordError(@NonNull String message) {
            logError(message);
            setBottomInfo(message);
        }
    });
}

private void initPipeline() {
    messagePipeline = new MessagePipelineController(
            requireContext(),
            messageList,
            sampleRegistry,
            chartBinder,
            this::recordSample,
            this::logWarn
    );
}
```

Also add this import:

```java
import androidx.annotation.NonNull;
```

- [ ] **Step 5: Remove old recording state helpers**

Delete these methods from `FragmentMessageNew.java`:

```java
private boolean isRecording() {
    return mIsRecording;
}

private void sendControl(String cmd) {
    sendDataToActivity(StaticConstants.CMD_MSG_NEW_CONTROL, cmd);
}

private void setRecording(boolean recording) {
    mIsRecording = recording;
    logWarn("MessageNew record state: " + (recording ? "ON" : "OFF"));
}
```

Delete overrides:

```java
@Override
protected void onRecStateChanged(boolean recording) {
    setRecording(recording);
    updateRecordStateView(recording);

    if (recording) {
        resetChartsForNewSession();
    }
}

@Override
protected void onRecExportResult(@Nullable String path) {
    String msg = path == null ? "Export: (empty)" : ("Export: " + path);
    toastShort(msg);
    logWarn("MessageNew export result: " + path);
    setBottomInfo(msg);
}
```

- [ ] **Step 6: Add local record/export methods**

Add:

```java
private void startRecording() {
    resetChartsForNewSession();
    sampleRecorder.start(requireContext(), "message_new");
    toastShort("MessageNew record: ON");
}

private void stopRecording() {
    sampleRecorder.stop();
    toastShort("MessageNew record: OFF");
    logWarn("MessageNew samples: " + sampleRecorder.getSampleCount());
}

private void exportRecording() {
    sampleRecorder.exportPath();
}

private void showExportResult(@Nullable String path) {
    String msg = path == null || path.isEmpty() ? "Export: (empty)" : ("Export: " + path);
    toastShort(msg);
    logWarn("MessageNew export result: " + path);
    setBottomInfo(msg);
}
```

- [ ] **Step 7: Delegate Bluetooth bytes to pipeline**

Replace `onBtData(...)` with:

```java
@Override
protected void onBtData(BTPackage.BTData data) {
    updateCurrentModule(data.module);

    if (messagePipeline != null) {
        messagePipeline.onBtData(data.module, data.bytes);
    }
}
```

Delete the old `handleBluetoothLine(...)` method. Keep `appendSampleToCharts(...)` only if it is still used; after this change it should be unused and should be deleted.

- [ ] **Step 8: Record only when recorder is active**

Replace `recordSample(...)` with:

```java
private void recordSample(BluetoothSample sample) {
    if (sampleRecorder == null || !sampleRecorder.isRecording()) return;

    if (!(sample instanceof EisSample)) {
        logWarn("MessageNew skip record, unsupported sample type: " + sample.type());
        return;
    }

    sampleRecorder.appendLine(EisJsonLineBuilder.build(module, (EisSample) sample));
}
```

- [ ] **Step 9: Replace button commands**

In `onClickView(...)`, replace:

```java
sendControl(MessageNewCmd.START_RECORD);
```

with:

```java
startRecording();
```

Replace:

```java
sendControl(MessageNewCmd.STOP_RECORD);
```

with:

```java
stopRecording();
```

Replace:

```java
sendControl(MessageNewCmd.EXPORT);
```

with:

```java
exportRecording();
```

- [ ] **Step 10: Release recorder**

Add:

```java
@Override
public void onDestroy() {
    super.onDestroy();
    if (sampleRecorder != null) {
        sampleRecorder.release();
    }
}
```

- [ ] **Step 11: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`. If the compiler reports unused imports, remove only those imports.

- [ ] **Step 12: Run JSON unit test**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.activity.tool.EisJsonLineBuilderTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 13: Commit**

Run:

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java
git commit -m "refactor: move MessageNew onto local pipeline"
```

Expected: commit succeeds.

---

## Task 6: Remove MessageNew Special Handling From Activity

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`

- [ ] **Step 1: Remove unused imports**

Remove these imports:

```java
import android.os.Environment;
import com.hc.mixthebluetooth.activity.single.MessageNewCmd;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
```

Keep `SimpleDateFormat`, `Date`, or `Locale` only if another method in `CommunicationActivity` still uses them after deleting `Recorder`.

- [ ] **Step 2: Remove recorder field**

Delete:

```java
private final Recorder messageNewRecorder = new Recorder();
```

- [ ] **Step 3: Narrow activity subscriptions**

Replace:

```java
subscription(
        StaticConstants.CMD_SEND_BT_DATA,
        StaticConstants.CMD_MSG_NEW_CONTROL,
        StaticConstants.EV_REC_SAMPLE
);
```

with:

```java
subscription(StaticConstants.CMD_SEND_BT_DATA);
```

- [ ] **Step 4: Narrow `update(...)`**

Replace the `switch` in `update(...)` with:

```java
switch (sign) {
    case StaticConstants.CMD_SEND_BT_DATA:
        onSendBtDataCommand(data);
        break;

    default:
        logWarn("Unknown activity command: " + sign);
        break;
}
```

- [ ] **Step 5: Delete MessageNew command handlers**

Delete:

```java
private void onMessageNewControlCommand(Object data) {
    String cmd = data != null ? data.toString() : "";
    logWarn("MessageNew control: " + cmd);

    switch (cmd) {
        case MessageNewCmd.START_RECORD:
            messageNewRecorder.start();
            return;
        case MessageNewCmd.STOP_RECORD:
            messageNewRecorder.stop();
            return;
        case MessageNewCmd.EXPORT:
            messageNewRecorder.export();
            break;
    }

}

private void onMessageNewSampleEvent(Object data) {
    messageNewRecorder.appendSample(data);
}
```

- [ ] **Step 6: Delete the inner `Recorder` class**

Delete the entire inner class beginning with:

```java
private class Recorder {
```

and ending at its matching closing brace before the final `CommunicationActivity` closing brace.

- [ ] **Step 7: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

Run:

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java
git commit -m "refactor: remove MessageNew activity special case"
```

Expected: commit succeeds.

---

## Task 7: Align `FragmentMessage` Receive Flow With Pipeline Shape

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Replace the body of `addListData(...)` with a named pipeline shape**

In `FragmentMessage.java`, replace the first part of `addListData(byte[] data)` from:

```java
if (data == null || data.length == 0) return;

boolean checkNewline = ModuleParameters.isCheckNewline();
BluetoothPayloadDecoder.Result decoded = decodeIncomingBytes(data, checkNewline);
String dataString = decoded.text;

if (!checkNewline) {
    appendIncomingItem(dataString, false);
    return;
}

boolean newline = BluetoothPayloadDecoder.endsWithCrLf(data);
logWarn("dataString:" + dataString);
```

with:

```java
if (data == null || data.length == 0) return;

boolean checkNewline = ModuleParameters.isCheckNewline();
BluetoothPayloadDecoder.Result decoded = decodeIncomingBytes(data, checkNewline);
String dataString = decoded.text;

if (!checkNewline) {
    appendIncomingItem(dataString, false);
    return;
}

handleDecodedLegacyMessage(dataString, decoded.endsWithLineBreak);
```

Then move the remaining old code from after `logWarn("dataString:" + dataString);` through `appendOrMergeIncomingItem(dataString, newline);` into a new method:

```java
private void handleDecodedLegacyMessage(String dataString, boolean endsWithLineBreak) {
    logWarn("dataString:" + dataString);

    if (handlePlaybackCache(dataString)) {
        return;
    }

    handleLegacyMeasurementText(dataString);
    appendOrMergeIncomingItem(dataString, endsWithLineBreak);
}
```

- [ ] **Step 2: Extract playback cache branch**

Add this method and move only the cache branch into it:

```java
private boolean handlePlaybackCache(String dataString) {
    if (dataString.contains("Start Playback")) {
        logWarn("Start playback");
        readCache = true;
    } else if (dataString.contains("Playback all done")) {
        logWarn("Playback all done");
        readCache = false;
    }

    if (!readCache) return false;

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        String strFilePath = String.valueOf(this.getActivity().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
        logWarn("strFilePath：" + strFilePath);
        Analysis.IO_input_data(dataString, strFilePath, "CGM_Cache_data.txt");
    }
    return true;
}
```

Remove the original duplicated cache branch from `addListData(...)`.

- [ ] **Step 3: Extract measurement branch**

Add this method and move the old `String[] dataFloatString = dataString.split("\n");` loop into it:

```java
private void handleLegacyMeasurementText(String dataString) {
    String[] dataFloatString = dataString.split("\n");

    logWarn("dataFloatStringLength:" + dataFloatString.length);
    for (int i = 0; i < dataFloatString.length; i++) {
        handleLegacyMeasurementLine(dataFloatString, i);
    }
}
```

Then add:

```java
private void handleLegacyMeasurementLine(String[] dataFloatString, int i) {
    String line = dataFloatString[i];

    if (line.contains("EIS") || dataFloatString[0].contains("CA")) {
        getDataStart = true;
        if (line.contains("EIS")) {
            dataInIOList.add(line.split(":")[1]);
        }
    } else if (line.contains("RI")) {
        getDataStart = false;
    }

    viewBinding.status.setText(line.split(":")[0]);
    if (line.contains("RI")) {
        viewBinding.status.setText(line);
    }

    if (getDataStart) {
        appendLegacyChartPoint(line);
    }

    if (line.contains("CA:266")) {
        appendLegacyCurrentPoint(line);
    }
}
```

- [ ] **Step 4: Extract chart side effects**

Add:

```java
private void appendLegacyChartPoint(String line) {
    logWarn("chart_data:" + line.split(":")[1].split(",")[1]);
    if (line.contains("EIS")) {
        if (dataFloatList.size() > 94) {
            dataFloatList.clear();
            values.clear();
        }
        dataFloatList.add(Float.parseFloat(line.split(":")[1].split(",")[2]));
    } else if (line.contains("CA")) {
        int index = Integer.parseInt(line.split(":")[1].split(",")[0]);
        logWarn("currentIndex:" + index);
        if (index == 1) {
            dataFloatList.clear();
            values.clear();
        }
        dataFloatList.add(Float.parseFloat(line.split(":")[1].split(",")[1]));
    }

    logWarn("dataFloatList:" + dataFloatList.toString());
    LineChart lineChart = this.getActivity().findViewById(R.id.lineChart);
    getLineChart(dataFloatList, lineChart);
}
```

Add:

```java
private void appendLegacyCurrentPoint(String line) {
    viewBinding.currentData.setText(line.split(":")[1].split(",")[1]);
    currentDataFloatList.add(Float.valueOf(line.split(":")[1].split(",")[1]));
    LineChart lineChart = this.getActivity().findViewById(R.id.lineChart_current);
    getLineBloodChart(currentDataFloatList, lineChart);

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        LocalTime currentTime = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String currentTimeString = currentTime.format(formatter);
        String timeCurrent = "time：" + currentTimeString + " " + line.split(":")[1].split(",")[1];
        String strFilePath = String.valueOf(this.getActivity().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
        dataInIOList.add(timeCurrent);
        logWarn("写入信息：" + dataInIOList.toString());
        Analysis.IO_input_data(dataInIOList.toString() + "\n", strFilePath, "的CGM_data.txt");
        dataInIOList.clear();
    }
}
```

Use the exact string constants from the existing file if the source file currently contains mojibake strings. Do not change user-facing text in this task unless the compiler requires escaping.

- [ ] **Step 5: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

Run:

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java
git commit -m "refactor: shape legacy Message receive pipeline"
```

Expected: commit succeeds.

---

## Task 8: Record The Convergence Requirement In The Spec

**Files:**
- Modify: `docs/superpowers/specs/2026-05-04-message-pipeline-unification-design.md`

- [ ] **Step 1: Add explicit convergence requirement**

In the `Goal` section, after:

```markdown
- `FragmentMessage` adopts only the safe shared parts first: message list and send flow.
```

add:

```markdown
- `FragmentMessage` must ultimately read like the same kind of pipeline host as `FragmentMessageNew`: declare channels, initialize shared list/send/pipeline pieces, then delegate legacy device-specific parsing behind a named processor.
```

- [ ] **Step 2: Add deferred backlog item**

In the `Deferred Backlog` section of this implementation plan, preserve this concrete future item in the design document under `Deferred Backlog For Later Aggressive Route` if that heading already exists:

```markdown
- migrate the isolated `FragmentMessage` legacy processor into a real profile once its behavior is covered by compile checks and manual device smoke tests
```

- [ ] **Step 3: Commit docs**

Run:

```powershell
git add docs/superpowers/specs/2026-05-04-message-pipeline-unification-design.md docs/superpowers/plans/2026-05-04-message-pipeline-unification.md
git commit -m "docs: plan message pipeline unification"
```

Expected: commit succeeds.

---

## Final Verification

- [ ] **Step 1: Compile app**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run focused unit test**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.activity.tool.EisJsonLineBuilderTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Search for removed MessageNew activity path**

Run:

```powershell
Get-ChildItem .\app\src\main\java -Recurse -Filter *.java |
  Select-String -Pattern 'CMD_MSG_NEW_CONTROL','EV_REC_SAMPLE','MessageNewCmd','messageNewRecorder'
```

Expected: no usage in `CommunicationActivity.java` or `FragmentMessageNew.java`. Deprecated constants in `StaticConstants.java` may remain for compatibility.

- [ ] **Step 4: Manual device smoke**

Manual path:

```text
1. Open CommunicationActivity.
2. Confirm FragmentMessageNew is still the default page.
3. Connect the target Bluetooth device.
4. Confirm MessageNew raw text appears in the list.
5. Confirm parsed EIS samples update both charts.
6. Tap Start Record and confirm charting continues.
7. Tap Stop Record and confirm state text changes.
8. Tap Export and confirm a non-empty .jsonl path appears after recording.
9. Switch to FragmentMessage.
10. Confirm old receive list, send button, cache commands, and charts still behave as before.
11. Disconnect and confirm reconnect/loop-send behavior still belongs to CommunicationActivity.
```

Expected: New behaves as the reference pipeline page; old Message keeps behavior while its method structure now points toward the same pattern.
