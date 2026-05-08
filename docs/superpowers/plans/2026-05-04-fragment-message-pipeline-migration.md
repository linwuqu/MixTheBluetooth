# FragmentMessage Pipeline Migration Implementation Plan

> **Superseded:** This plan is no longer the source of truth. It only wrapped legacy behavior behind a boundary, which is too conservative for the approved direction. Use `docs/superpowers/plans/2026-05-04-fragment-message-profile-unification.md` instead.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `FragmentMessage` so it uses the same readable host shape as `FragmentMessageNew`: list setup, pipeline setup, controls setup, and `onBtData -> pipeline`, while preserving old send, cache, status, and chart behavior.

**Architecture:** Do the migration in two small layers. First, give `FragmentMessage` a thin legacy receive pipeline and a reusable message-list controller so its outer flow looks like `FragmentMessageNew`. Second, move old CGM/cache parsing behind a named legacy processor boundary; do not force it into `BluetoothSampleRegistry` or `SampleRecorder` until the old behavior is stable behind that boundary.

**Tech Stack:** Android Java, ViewBinding, existing `BTFragment` channel callbacks, existing `BTPackage`, `BluetoothPayloadDecoder`, `MessageItemTools`, `MessageListController`, MPAndroidChart, Gradle debug Java compile.

---

## Current Execution Constraint

The user requested Inline Execution in the current workspace. Do not create a branch or commit unless the user asks for it.

The existing worktree may already contain refactor changes. Before each edit, read the target file and avoid reverting unrelated changes.

---

## Target Shape

After this plan, `FragmentMessage` should read like this at the top level:

```java
@Override
public void initAllImpl(View view, Context context) {
    initDependencies(context);
    initOptions();
    initRecycler();
    initLegacyProcessor();
    initPipeline();
    initEditView();
    initFoldLayout();
    initControls();
    initLimit();
}

@Override
protected void onBtData(BTPackage.BTData packet) {
    module = packet.module;
    legacyPipeline.onBtData(packet.module, packet.bytes);
}

@Override
public void onClickView(View view) {
    if (isCheck(viewBinding.sendMessageFragment)) {
        handleSendClick();
        return;
    }

    if (isCheck(viewBinding.sentStartFlag)) {
        sendRawCommand(buildStartTimeCommand());
        return;
    }

    if (isCheck(viewBinding.getAllData)) {
        sendRawCommand("ALL\n\r");
        return;
    }

    if (isCheck(viewBinding.deleteCacheData)) {
        sendRawCommand("DELETE\n\r");
        return;
    }

    if (isCheck(viewBinding.pullMessageFragment)) {
        showMessageOptions(view);
    }
}
```

The important change is not that every old method disappears. The important change is that the fragment becomes a host of named pieces instead of one long mixed control flow.

---

## File Structure

Create:

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/LegacyMessagePipelineController.java`
  - Decodes raw Bluetooth bytes for old `FragmentMessage`.
  - Counts received bytes through a callback.
  - Emits decoded text through a callback.
  - Knows nothing about charts, cache files, or UI widgets.

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/LegacyCgmMessageProcessor.java`
  - Owns the old CGM/cache text interpretation after text has already been decoded.
  - Calls back into the fragment for UI/chart/file side effects.
  - This is a boundary for legacy behavior, not a new sample registry.

Modify:

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessagePipelineController.java`
  - Keep it for `FragmentMessageNew`.
  - Fix broken comments or garbled comment/code joins if present.
  - Do not add old `FragmentMessage` behavior here.

- `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessageListController.java`
  - Add a constructor that accepts the old fragment's existing list and layout manager.
  - Add append-or-merge and outgoing-item methods needed by old `FragmentMessage`.

- `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`
  - Replace direct adapter/list mutation with `MessageListController`.
  - Replace `handleIncomingBytes -> addListData(bytes)` with `legacyPipeline.onBtData(...)`.
  - Keep send construction inside the fragment for this pass, but wrap click handlers into named methods.
  - Move CGM/cache parsing into `LegacyCgmMessageProcessor`.

Do not modify:

- `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
  - It is already mostly clean. `FragmentMessage` should become cleaner without adding new page-specific registration logic to the Activity.

---

## Task 1: Baseline Compile And Immediate Compile Blockers

**Files:**
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessagePipelineController.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentSetting.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Check worktree state**

Run:

```powershell
git status --short
```

Expected: existing dirty files may appear. Keep them unless they are directly part of this migration.

- [ ] **Step 2: Compile once before migration**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: either `BUILD SUCCESSFUL` or a short list of current compile blockers.

- [ ] **Step 3: Fix the known ViewBinding rename blocker if it appears**

If compile says `FragmentThreeBinding` cannot be found in `FragmentSetting.java`, change only these references:

```java
import com.hc.mixthebluetooth.databinding.FragmentSettingBinding;

public class FragmentSetting extends BaseFragment<FragmentSettingBinding> {
    @Override
    protected FragmentSettingBinding getViewBinding() {
        return FragmentSettingBinding.inflate(getLayoutInflater());
    }
}
```

- [ ] **Step 4: Fix pipeline comment/code join if it appears**

In `MessagePipelineController.java`, make sure `onBtData` is a real method declaration and not swallowed by a comment. The method must look like this:

```java
public void onBtData(@Nullable DeviceModule module, @Nullable byte[] bytes) {
    BluetoothPayloadDecoder.Result result = BluetoothPayloadDecoder.decodeResult(context, bytes);
    if (result.isEmpty()) return;
    handleLine(module, result.text);
}
```

The comment above it can be:

```java
// Decode bytes before parsing text.
```

- [ ] **Step 5: Recompile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`. If another compile blocker appears, fix only the smallest syntax or rename issue needed to reach the baseline.

---

## Task 2: Upgrade MessageListController For Both Fragments

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessageListController.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessageItemTools.java`
- Read: `app/src/main/java/com/hc/mixthebluetooth/recyclerData/FragmentMessAdapter.java`

- [ ] **Step 1: Change the list field so it can use an external legacy list**

Replace:

```java
private final List<FragmentMessageItem> dataList = new ArrayList<>();
```

with:

```java
private final List<FragmentMessageItem> dataList;
```

- [ ] **Step 2: Make the current constructor delegate to a fuller constructor**

Replace the current constructor with:

```java
public MessageListController(@NonNull Context context, @NonNull RecyclerView recyclerView, int itemLayout) {
    this(context, recyclerView, itemLayout, new ArrayList<>(), new LinearLayoutManager(context));
}
```

Add this overload:

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

- [ ] **Step 3: Add methods old FragmentMessage needs**

Add these methods:

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

- [ ] **Step 4: Keep `FragmentMessageNew` behavior unchanged**

Make sure these existing methods still exist:

```java
public void addIncomingText(@NonNull String text, @Nullable DeviceModule module) {
    MessageItemTools.appendIncoming(dataList, text, false, null, module, false);
    adapter.notifyItemInserted(dataList.size() - 1);
    scrollToBottom();
}

public void addOutgoingText(@NonNull String text, @Nullable DeviceModule module) {
    dataList.add(MessageItemTools.outgoingText(text, null, module, true));
    adapter.notifyItemInserted(dataList.size() - 1);
    scrollToBottom();
}
```

- [ ] **Step 5: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 3: Move FragmentMessage List Mutations Behind MessageListController

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Add the controller field and import**

Add:

```java
import com.hc.mixthebluetooth.activity.tool.message.MessageListController;
```

Add field:

```java
private MessageListController messageList;
```

- [ ] **Step 2: Replace `initRecycler()`**

Replace the existing adapter setup with:

```java
private void initRecycler() {
    messageList = new MessageListController(
            requireContext(),
            viewBinding.recyclerMessageFragment,
            R.layout.item_message_fragment,
            mDataList,
            new FastScrollLinearLayoutManager(getContext())
    );
}
```

Keep `mDataList` during this task because old clear/count paths still reference it.

- [ ] **Step 3: Replace `refreshMessageList()`**

Replace the method body with:

```java
private void refreshMessageList() {
    messageList.notifyDataSetChangedAndScrollToBottom();
}
```

- [ ] **Step 4: Replace incoming append helpers**

Use this helper:

```java
private String currentMessageTime() {
    return isShowTime ? Analysis.getTime() : null;
}
```

Replace `appendIncomingItem(...)` with:

```java
private void appendIncomingItem(String text, boolean endsWithNewline) {
    messageList.appendIncomingText(text, endsWithNewline, currentMessageTime(), module, isShowMyData);
}
```

Replace `appendOrMergeIncomingItem(...)` with:

```java
private void appendOrMergeIncomingItem(String text, boolean endsWithNewline) {
    messageList.appendOrMergeIncomingText(text, endsWithNewline, currentMessageTime(), module, isShowMyData);
}
```

- [ ] **Step 5: Replace outgoing display mutation**

In `sendData(FragmentMessageItem item)`, replace direct `mDataList.add(...)`, `mAdapter.notifyDataSetChanged()`, and `smoothScrollToPosition(...)` with:

```java
if (isShowMyData) {
    messageList.addOutgoingItem(item);
}
```

- [ ] **Step 6: Replace popup clear mutation**

Inside `PopWindowFragment.DismissListener.clearRecycler()`, replace list and adapter operations with:

```java
messageList.clear();
viewBinding.sizeReadMessageFragment.setText(String.valueOf(0));
viewBinding.sizeSendMessageFragment.setText(String.valueOf(0));
mCacheByteNumber = 0;
```

- [ ] **Step 7: Remove the old adapter field if unused**

Run:

```powershell
Get-ChildItem .\app\src\main\java\com\hc\mixthebluetooth\fragment -Filter FragmentMessage.java |
  Select-String -Pattern 'mAdapter'
```

Expected after replacements: no real usage remains. Then remove:

```java
private FragmentMessAdapter mAdapter;
```

and remove:

```java
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
```

- [ ] **Step 8: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 4: Add A Thin LegacyMessagePipelineController

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/LegacyMessagePipelineController.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Create the legacy pipeline**

Create:

```java
package com.hc.mixthebluetooth.activity.tool;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;

public final class LegacyMessagePipelineController {

    public interface OptionsProvider {
        boolean readHex();

        boolean checkNewline();
    }

    public interface Listener {
        void onBytesReceived(int byteCount);

        void onDecodedText(@Nullable DeviceModule module, @NonNull BluetoothPayloadDecoder.Result result);
    }

    private final Context context;
    private final OptionsProvider optionsProvider;
    private final Listener listener;

    public LegacyMessagePipelineController(
            @NonNull Context context,
            @NonNull OptionsProvider optionsProvider,
            @NonNull Listener listener
    ) {
        this.context = context;
        this.optionsProvider = optionsProvider;
        this.listener = listener;
    }

    public void onBtData(@Nullable DeviceModule module, @Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;

        listener.onBytesReceived(bytes.length);

        BluetoothPayloadDecoder.Options options = new BluetoothPayloadDecoder.Options.Builder()
                .hex(optionsProvider.readHex())
                .removeTrailingCrLf(optionsProvider.checkNewline())
                .cleanNull(true)
                .trim(true)
                .build();

        String code = FragmentParameter.getInstance().getCodeFormat(context);
        BluetoothPayloadDecoder.Result result = BluetoothPayloadDecoder.decodeResult(bytes, code, options);
        if (result.isEmpty()) return;

        listener.onDecodedText(module, result);
    }
}
```

This controller intentionally does not call `BluetoothSampleRegistry.parse(...)`. Old `FragmentMessage` data is not sample-shaped yet.

- [ ] **Step 2: Add field and init method in FragmentMessage**

Add:

```java
import com.hc.mixthebluetooth.activity.tool.LegacyMessagePipelineController;
```

Add field:

```java
private LegacyMessagePipelineController legacyPipeline;
```

Add:

```java
private void initPipeline() {
    legacyPipeline = new LegacyMessagePipelineController(
            requireContext(),
            new LegacyMessagePipelineController.OptionsProvider() {
                @Override
                public boolean readHex() {
                    return isReadHex;
                }

                @Override
                public boolean checkNewline() {
                    return ModuleParameters.isCheckNewline();
                }
            },
            new LegacyMessagePipelineController.Listener() {
                @Override
                public void onBytesReceived(int byteCount) {
                    addReadBytes(byteCount);
                    setClearRecycler(byteCount);
                }

                @Override
                public void onDecodedText(DeviceModule module, BluetoothPayloadDecoder.Result result) {
                    FragmentMessage.this.module = module;
                    handleDecodedText(result);
                    refreshMessageList();
                }
            }
    );
}
```

- [ ] **Step 3: Replace `onBtData(...)`**

Replace:

```java
module = packet.module;
handleIncomingBytes(packet.bytes.clone());
```

with:

```java
module = packet.module;
if (legacyPipeline != null) {
    legacyPipeline.onBtData(packet.module, packet.bytes);
}
```

- [ ] **Step 4: Replace byte-based receive helpers with text-based helper**

Keep the old behavior, but make the input decoded text:

```java
private void handleDecodedText(BluetoothPayloadDecoder.Result result) {
    String dataString = result.text;

    if (!ModuleParameters.isCheckNewline()) {
        appendIncomingItem(dataString, false);
        return;
    }

    handleDecodedLegacyMessage(dataString, result.endsWithLineBreak);
}
```

After this compiles, remove these methods if unused:

```java
private void handleIncomingBytes(byte[] bytes)
private void appendIncomingMessage(byte[] bytes)
private void addListData(byte[] data)
private BluetoothPayloadDecoder.Result decodeIncomingBytes(byte[] data, boolean checkNewline)
```

- [ ] **Step 5: Call `initPipeline()` from initAllImpl**

The init sequence should be:

```java
mStorage = new Storage(context);
mFragmentParameter = FragmentParameter.getInstance();
setListState();
initRecycler();
initPipeline();
initEditView();
initFoldLayout();
initLimit();
```

- [ ] **Step 6: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 5: Put Legacy CGM/Cache Parsing Behind A Named Processor

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/LegacyCgmMessageProcessor.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Create the processor shell**

Create:

```java
package com.hc.mixthebluetooth.activity.tool;

import androidx.annotation.NonNull;

public final class LegacyCgmMessageProcessor {

    public interface Callback {
        void onLog(@NonNull String message);

        void onCacheText(@NonNull String text);

        void onStatusText(@NonNull String text);

        void onEisBackup(@NonNull String text);

        void onPrimaryChartReset();

        void onPrimaryChartValue(float value);

        void onCurrentChartValue(float value);

        void onCurrentFileValue(@NonNull String value);
    }

    private final Callback callback;
    private boolean readingCache;
    private boolean dataStarted;

    public LegacyCgmMessageProcessor(@NonNull Callback callback) {
        this.callback = callback;
    }

    public boolean process(@NonNull String text) {
        callback.onLog("legacy text: " + text);

        if (updateCacheState(text)) {
            callback.onCacheText(text);
            return true;
        }

        String[] lines = text.split("\n");
        for (String line : lines) {
            processLine(line);
        }

        return false;
    }

    private boolean updateCacheState(@NonNull String text) {
        if (text.contains("Start Playback")) {
            readingCache = true;
        } else if (text.contains("Playback all done")) {
            readingCache = false;
        }
        return readingCache;
    }

    private void processLine(@NonNull String line) {
        if (line.isEmpty()) return;

        if (line.contains("EIS")) {
            dataStarted = true;
            String textAfterColon = textAfterColon(line);
            String[] values = textAfterColon.split(",");
            if (values.length > 2) {
                callback.onEisBackup(textAfterColon);
                callback.onPrimaryChartValue(Float.parseFloat(values[2]));
            }
            callback.onStatusText("EIS");
            return;
        }

        if (line.contains("CA")) {
            dataStarted = true;
            String[] values = textAfterColon(line).split(",");
            if (values.length > 1) {
                int index = Integer.parseInt(values[0]);
                if (index == 1) {
                    callback.onPrimaryChartReset();
                }
                float value = Float.parseFloat(values[1]);
                callback.onPrimaryChartValue(value);
                if (line.contains("CA:266")) {
                    callback.onCurrentChartValue(value);
                    callback.onCurrentFileValue(String.valueOf(value));
                }
            }
            callback.onStatusText("CA");
            return;
        }

        if (line.contains("RI")) {
            dataStarted = false;
            callback.onStatusText(line);
            return;
        }

        if (dataStarted) {
            callback.onStatusText(line);
        }
    }

    private String textAfterColon(@NonNull String line) {
        String[] parts = line.split(":");
        if (parts.length < 2) return "";
        return parts[1];
    }
}
```

This first version mirrors the old line markers. It is deliberately not a general parser registry.

- [ ] **Step 2: Add processor field and init method in FragmentMessage**

Add:

```java
import androidx.annotation.NonNull;
import com.hc.mixthebluetooth.activity.tool.LegacyCgmMessageProcessor;
```

Add field:

```java
private LegacyCgmMessageProcessor legacyProcessor;
```

Add:

```java
private void initLegacyProcessor() {
    legacyProcessor = new LegacyCgmMessageProcessor(new LegacyCgmMessageProcessor.Callback() {
        @Override
        public void onLog(@NonNull String message) {
            logWarn(message);
        }

        @Override
        public void onCacheText(@NonNull String text) {
            writeLegacyCache(text);
        }

        @Override
        public void onStatusText(@NonNull String text) {
            viewBinding.status.setText(text);
        }

        @Override
        public void onEisBackup(@NonNull String text) {
            dataInIOList.add(text);
        }

        @Override
        public void onPrimaryChartReset() {
            dataFloatList.clear();
            values.clear();
        }

        @Override
        public void onPrimaryChartValue(float value) {
            appendLegacyChartPoint(value);
        }

        @Override
        public void onCurrentChartValue(float value) {
            appendLegacyCurrentPoint(value);
        }

        @Override
        public void onCurrentFileValue(@NonNull String value) {
            writeLegacyCurrentResult(value);
        }
    });
}
```

- [ ] **Step 3: Call the processor from decoded legacy handling**

Replace `handleDecodedLegacyMessage(...)` with:

```java
private void handleDecodedLegacyMessage(String dataString, boolean endsWithLineBreak) {
    if (legacyProcessor != null && legacyProcessor.process(dataString)) {
        return;
    }

    appendOrMergeIncomingItem(dataString, endsWithLineBreak);
}
```

- [ ] **Step 4: Add value-based side-effect helpers in FragmentMessage**

Add:

```java
private void appendLegacyChartPoint(float value) {
    if (dataFloatList.size() > 94) {
        dataFloatList.clear();
        values.clear();
    }
    dataFloatList.add(value);
    LineChart lineChart = requireActivity().findViewById(R.id.lineChart);
    getLineChart(dataFloatList, lineChart);
}

private void appendLegacyCurrentPoint(float value) {
    viewBinding.currentData.setText(String.valueOf(value));
    currentDataFloatList.add(value);
    LineChart lineChart = requireActivity().findViewById(R.id.lineChart_current);
    getLineBloodChart(currentDataFloatList, lineChart);
}

private void writeLegacyCache(String text) {
    String path = String.valueOf(requireActivity().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
    Analysis.IO_input_data(text, path, "CGM_Cache_data.txt");
}

private void writeLegacyCurrentResult(String value) {
    LocalTime currentTime = LocalTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    String timeCurrent = "time: " + currentTime.format(formatter) + " " + value;
    String path = String.valueOf(requireActivity().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS));
    dataInIOList.add(timeCurrent);
    Analysis.IO_input_data(dataInIOList.toString() + "\n", path, "CGM_data.txt");
    dataInIOList.clear();
}
```

- [ ] **Step 5: Remove old mixed parsing helpers after compile**

After the new processor compiles, remove these methods if no references remain:

```java
private boolean handlePlaybackCache(String dataString)
private void handleLegacyMeasurementText(String dataString)
private void handleLegacyMeasurementLine(String[] dataFloatString, int i)
private void appendLegacyChartPoint(String line)
private void appendLegacyCurrentPoint(String line)
```

- [ ] **Step 6: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 6: Make Controls Read Like FragmentMessageNew

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Add `initControls()`**

Add:

```java
private void initControls() {
    bindOnClickListener(
            viewBinding.sendMessageFragment,
            viewBinding.sentStartFlag,
            viewBinding.getAllData,
            viewBinding.deleteCacheData,
            viewBinding.pullMessageFragment
    );
}
```

Call it from `initAllImpl(...)` after `initFoldLayout()`.

- [ ] **Step 2: Split `onClickView(...)` into named handlers**

Replace the method body with:

```java
public void onClickView(View view) {
    if (isCheck(viewBinding.sendMessageFragment)) {
        handleSendClick();
        return;
    }

    if (isCheck(viewBinding.sentStartFlag)) {
        sendRawCommand(buildStartTimeCommand());
        return;
    }

    if (isCheck(viewBinding.getAllData)) {
        sendRawCommand("ALL\n\r");
        return;
    }

    if (isCheck(viewBinding.deleteCacheData)) {
        sendRawCommand("DELETE\n\r");
        return;
    }

    if (isCheck(viewBinding.pullMessageFragment)) {
        showMessageOptions(view);
    }
}
```

Add:

```java
private void handleSendClick() {
    if (!isSetParam) {
        setParameter();
    }
}

private String buildStartTimeCommand() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy,MM,dd,HH,mm,ss", Locale.getDefault());
    return "TIME," + sdf.format(new Date()) + "\n\r";
}

private void sendRawCommand(String command) {
    data = command;
    setSendData();
}
```

- [ ] **Step 3: Move popup code into `showMessageOptions(...)`**

Add:

```java
private void showMessageOptions(View view) {
    viewBinding.pullMessageFragment.setImageResource(R.drawable.pull_up);
    new PopWindowFragment(view, getActivity(), new PopWindowFragment.DismissListener() {
        @Override
        public void onDismissListener() {
            viewBinding.pullMessageFragment.setImageResource(R.drawable.pull_down);
            setListState();
        }

        @Override
        public void clearRecycler() {
            messageList.clear();
            viewBinding.sizeReadMessageFragment.setText(String.valueOf(0));
            viewBinding.sizeSendMessageFragment.setText(String.valueOf(0));
            mCacheByteNumber = 0;
        }
    });
}
```

- [ ] **Step 4: Remove old command wrappers if unused**

Run:

```powershell
Get-ChildItem .\app\src\main\java\com\hc\mixthebluetooth\fragment -Filter FragmentMessage.java |
  Select-String -Pattern 'getBufferData|deleteBufferData|setStartFlag'
```

Expected: no usage after the split. Remove these methods if the search confirms they are unused:

```java
private void getBufferData()
private void deleteBufferData()
private void setStartFlag()
```

- [ ] **Step 5: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 7: Reorder FragmentMessage Into The Same Reading Pattern As New

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`

- [ ] **Step 1: Reorder init methods**

Make `initAllImpl(...)` exactly:

```java
@Override
public void initAllImpl(View view, Context context) {
    initDependencies(context);
    initOptions();
    initRecycler();
    initLegacyProcessor();
    initPipeline();
    initEditView();
    initFoldLayout();
    initControls();
    initLimit();
}

private void initDependencies(Context context) {
    mStorage = new Storage(context);
    mFragmentParameter = FragmentParameter.getInstance();
}

private void initOptions() {
    setListState();
}
```

- [ ] **Step 2: Reorder method sections without changing behavior**

Use this order:

```text
fields
initChannels
initAllImpl and init helpers
Bluetooth callbacks
pipeline decoded-text handler
click handlers
send helpers
legacy processor callback helpers
message-list helpers
chart rendering methods
ViewBinding
destroy/cleanup
```

- [ ] **Step 3: Remove only confirmed-unused legacy fields**

Run:

```powershell
Get-ChildItem .\app\src\main\java\com\hc\mixthebluetooth\fragment -Filter FragmentMessage.java |
  Select-String -Pattern 'mTimeHandler|decimalFormat|mediaPlayer|floatSum|bloodSum|detectionString'
```

For each field, remove it only if it has no read or write usage after the refactor.

- [ ] **Step 4: Compile**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

---

## Task 8: Final Verification

**Files:**
- Verify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessage.java`
- Verify: `app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentMessageNew.java`
- Verify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/MessageListController.java`
- Verify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/LegacyMessagePipelineController.java`
- Verify: `app/src/main/java/com/hc/mixthebluetooth/activity/tool/LegacyCgmMessageProcessor.java`

- [ ] **Step 1: Compile app**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run unit tests**

Run:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Search for old direct list/adapter mutation in FragmentMessage**

Run:

```powershell
Get-ChildItem .\app\src\main\java\com\hc\mixthebluetooth\fragment -Filter FragmentMessage.java |
  Select-String -Pattern 'mAdapter|mDataList\.add|mDataList\.clear|notifyDataSetChanged'
```

Expected: no direct adapter mutation remains. `mDataList` may still exist only as the backing list passed into `MessageListController`.

- [ ] **Step 4: Search for Activity-specific page migration leaks**

Run:

```powershell
Get-ChildItem .\app\src\main\java\com\hc\mixthebluetooth\activity -Filter CommunicationActivity.java |
  Select-String -Pattern 'FragmentMessageNew|LegacyMessagePipelineController|LegacyCgmMessageProcessor|MessagePipelineController'
```

Expected: no new registration or pipeline logic appears in `CommunicationActivity`.

- [ ] **Step 5: Manual Bluetooth smoke test**

Manual path:

```text
1. Open CommunicationActivity.
2. Connect the target Bluetooth device.
3. Open FragmentMessage.
4. Confirm incoming raw text still appears in the message list.
5. Confirm the read byte counter increases.
6. Confirm Start sends a TIME command.
7. Confirm ALL sends the cache-read command.
8. Confirm DELETE sends the cache-delete command.
9. Confirm the parameter send button still opens the parameter dialog and sends once.
10. Confirm cache playback still writes CGM_Cache_data.txt.
11. Confirm CA, EIS, and RI lines still update status and charts.
12. Confirm FragmentMessageNew still receives text, parses EIS samples, and only updates charts while recording.
```

Expected: user-visible behavior remains stable, while `FragmentMessage` now has the same outer style as `FragmentMessageNew`.

---

## SampleRecorder Position

Do not add `SampleRecorder` to `FragmentMessage` in this migration.

Current relationship:

```text
FragmentMessageNew
  onBtData
    -> MessagePipelineController
    -> BluetoothSampleRegistry
    -> BluetoothSample
    -> if SampleRecorder is recording:
         chart update
         record line

FragmentMessage after this plan
  onBtData
    -> LegacyMessagePipelineController
    -> decoded text
    -> LegacyCgmMessageProcessor
    -> old chart/cache/status behavior
```

The reason is simple: `SampleRecorder` records structured `BluetoothSample` data, while old `FragmentMessage` still has legacy line markers and direct chart side effects. Once `LegacyCgmMessageProcessor` is stable, a future pass can convert CA/EIS/RI text into real sample objects and then reuse `BluetoothSampleRegistry`, `SampleChartBinder`, and `SampleRecorder`.

---

## Deferred Backlog

These are intentionally outside the current small-step migration:

- Convert legacy CA/EIS/RI lines into `BluetoothSample` implementations.
- Replace `LegacyCgmMessageProcessor` with registered parsers.
- Make `FragmentMessage` charts use `ChartRegistry` and `SampleChartBinder`.
- Add recording controls to old `FragmentMessage` only if the product wants old Message to behave like New.
- Remove `mDataList` entirely after `MessageListController` owns all list state.
- Normalize Chinese UI strings and comments after the structural migration is compile-clean.
