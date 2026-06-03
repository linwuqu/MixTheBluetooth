# CGM Cache Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-callback CGM replay handling with one compact buffer that synchronizes decoded text chunks, validates replay semantics, writes a validated txt, retries by re-sending `ALL`, and confirms cache deletion only after `DELETE` is pending and hardware text `Log Cleared` arrives.

**Architecture:** The workflow is function-driven, not state-machine-driven. `CgmCacheSyncBuffer` exposes three core groups: `acceptChunk(...)` for synchronization, `validate()` for semantics, and `writeTo(...)` plus `markDeleteSent()/acceptDeviceLine(...)` for persistence and deletion acknowledgement. A small `Phase` field exists only for observability and guarding dangerous actions.

**Tech Stack:** Java, Android XML/ViewBinding, AndroidX annotations, JUnit4, Gradle.

---

## Design Correction

The implementation should not be built around many `case` branches such as `READ_STARTED`, `LINE_APPENDED`, `REPLAY_VALID`, `DELETE_SENT`, and so on. Those names are useful when explaining logs, but they are not the right center of the code.

The real workflow is simpler:

```text
read cache command sent
  -> acceptChunk(raw decoded text)
  -> validate replay when end marker appears
  -> if invalid: retry ALL
  -> if valid: write txt and upload
  -> if upload ok: send DELETE and markDeleteSent()
  -> acceptDeviceLine(raw decoded text)
  -> if pending delete and Log Cleared appears: deletion confirmed
```

So `Phase` is an internal guard/debug value:

```java
enum Phase {
    IDLE,
    READING_CACHE,
    READY_TO_UPLOAD,
    UPLOADING,
    WAITING_DELETE_CONFIRM,
    DONE,
    ERROR
}
```

It should not become a large external event model. Callers should mostly care about:

- whether a retry command should be sent,
- whether upload should start,
- whether delete was confirmed,
- whether there is a user-visible error.

## File Structure

- Create `app/src/main/java/com/hc/mixthebluetooth/application/cgm/CgmCacheSyncBuffer.java`
  - One compact class for chunk synchronization, replay validation, txt writing, retry counter, and delete acknowledgement.
- Modify `app/src/main/java/com/hc/mixthebluetooth/application/cgm/DefaultCgmWorkflow.java`
  - Owns one buffer instance and coordinates upload.
- Modify `app/src/main/java/com/hc/mixthebluetooth/api/cgm/CgmWorkflow.java`
  - Add small workflow update objects so UI can send retry commands or observe delete confirmation without a large event enum.
- Modify `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/Codec.java`
  - Preserve raw newline boundaries for CGM workflow.
- Modify `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmController.java`
  - Pass raw decoded text and `DeviceModule` to CGM profile.
  - Add manual delete confirmation hook.
- Modify `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmProfile.java`
  - Notify workflow when `ALL` or `DELETE` is sent.
  - Send retry `ALL` if workflow asks for it.
  - Send automatic `DELETE` after upload success.
- Modify `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmFragment.java`
  - Implement delete confirmation dialog.
- Delete `app/src/main/java/com/hc/mixthebluetooth/application/cgm/CgmReplayCompletionDetector.java`
  - Its marker detection becomes part of the buffer.

## Core Class Shape

Implement `CgmCacheSyncBuffer` around these methods:

```java
public final class CgmCacheSyncBuffer {
    public enum Phase {
        IDLE,
        READING_CACHE,
        READY_TO_UPLOAD,
        UPLOADING,
        WAITING_DELETE_CONFIRM,
        DONE,
        ERROR
    }

    public static final class SyncResult {
        public final int appendedLines;
        public final boolean sawStart;
        public final boolean sawEnd;
    }

    public static final class ValidationResult {
        public final boolean valid;
        public final boolean retryable;
        public final String message;
    }

    public static final class DeleteResult {
        public final boolean confirmed;
        public final String message;
    }

    public void beginRead();

    public SyncResult acceptChunk(String chunk);

    public ValidationResult validate();

    public boolean canRetry();

    public void beginRetry();

    public File writeTo(FileRecorder recorder);

    public void markUploading();

    public void markDeleteSent();

    public DeleteResult acceptDeviceLine(String text);

    public Phase phase();

    public void reset();
}
```

Responsibilities:

- `acceptChunk(String chunk)`
  - Appends raw decoded text to an internal `StringBuilder`.
  - Splits complete lines on `\n`.
  - Keeps an unterminated tail for the next chunk.
  - Handles `Playback all done` even without a trailing newline.
  - Does not validate schema and does not write files.

- `validate()`
  - Checks the assembled replay document.
  - Requires `Start Playback`.
  - Requires `Playback all done`.
  - Requires at least one payload line between markers.
  - Requires marker order.
  - Applies a simple payload schema such as printable `key:value`/CSV-like text.
  - Returns `valid=false, retryable=true` when another `ALL` should be attempted.

- `writeTo(FileRecorder recorder)`
  - Allowed only after `validate().valid == true`.
  - Writes the assembled replay lines to txt.
  - Returns the finished file.

- `markDeleteSent()`
  - Sets phase to `WAITING_DELETE_CONFIRM`.

- `acceptDeviceLine(String text)`
  - Only confirms deletion when phase is `WAITING_DELETE_CONFIRM`.
  - If text contains `Log Cleared`, returns `confirmed=true` and sets phase to `DONE`.
  - If not pending delete, `Log Cleared` is ignored because it is not tied to a delete operation in this app session.

## Task 1: Preserve Raw Codec Output

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/Codec.java`
- Create: `app/src/test/java/com/hc/mixthebluetooth/ui/cgm/CodecTest.java`

- [ ] **Step 1: Add test for raw newline preservation**

Create `app/src/test/java/com/hc/mixthebluetooth/ui/cgm/CodecTest.java`:

```java
package com.hc.mixthebluetooth.ui.cgm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class CodecTest {
    @Test
    public void defaultDecodeTrimsForDisplay() {
        String text = Codec.decode(
                "Start Playback\n".getBytes(StandardCharsets.UTF_8),
                new Codec.Options("UTF-8", false, false)
        );

        assertEquals("Start Playback", text);
    }

    @Test
    public void rawDecodePreservesNewlinesForReplayBuffer() {
        String text = Codec.decode(
                "Start Playback\nEIS:1,1000,0.12\n".getBytes(StandardCharsets.UTF_8),
                new Codec.Options("UTF-8", false, false, false)
        );

        assertEquals("Start Playback\nEIS:1,1000,0.12\n", text);
    }
}
```

- [ ] **Step 2: Run failing test**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.hc.mixthebluetooth.ui.cgm.CodecTest
```

Expected: compile fails because the four-argument `Codec.Options` constructor does not exist.

- [ ] **Step 3: Add `trim` option**

In `Codec.Options`, add `trim` and a four-argument constructor:

```java
        public final boolean trim;

        public Options(@Nullable String charset, boolean hex, boolean checkNewline) {
            this(charset, hex, checkNewline, true);
        }

        public Options(@Nullable String charset, boolean hex, boolean checkNewline, boolean trim) {
            this.charset = charset;
            this.hex = hex;
            this.checkNewline = checkNewline;
            this.trim = trim;
        }
```

In `Codec.decode`, replace:

```java
        text = text.replace("\u0000", "").trim();
```

with:

```java
        text = text.replace("\u0000", "");
        if (options.trim) {
            text = text.trim();
        }
```

- [ ] **Step 4: Run codec test**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.hc.mixthebluetooth.ui.cgm.CodecTest
```

Expected: tests pass.

## Task 2: Test The Buffer As Three Function Groups

**Files:**
- Create: `app/src/test/java/com/hc/mixthebluetooth/application/cgm/CgmCacheSyncBufferTest.java`

- [ ] **Step 1: Create tests for synchronization, validation, writing, delete ACK**

Create `app/src/test/java/com/hc/mixthebluetooth/application/cgm/CgmCacheSyncBufferTest.java`:

```java
package com.hc.mixthebluetooth.application.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.driver.capability.FileRecorder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CgmCacheSyncBufferTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptChunkReassemblesSplitLines() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();

        CgmCacheSyncBuffer.SyncResult first = buffer.acceptChunk("Start Play");
        CgmCacheSyncBuffer.SyncResult second = buffer.acceptChunk("back\nEIS:1,1000,0.12\n");

        assertEquals(0, first.appendedLines);
        assertEquals(2, second.appendedLines);
        assertTrue(second.sawStart);
        assertFalse(second.sawEnd);
    }

    @Test
    public void acceptChunkHandlesEndMarkerWithoutTrailingNewline() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();

        CgmCacheSyncBuffer.SyncResult result =
                buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\nPlayback all done");

        assertEquals(3, result.appendedLines);
        assertTrue(result.sawStart);
        assertTrue(result.sawEnd);
    }

    @Test
    public void validateAcceptsCompleteReplay() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\nPlayback all done\n");

        CgmCacheSyncBuffer.ValidationResult result = buffer.validate();

        assertTrue(result.valid);
        assertFalse(result.retryable);
        assertEquals(CgmCacheSyncBuffer.Phase.READY_TO_UPLOAD, buffer.phase());
    }

    @Test
    public void validateRejectsMissingEndAndAllowsRetry() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\n");

        CgmCacheSyncBuffer.ValidationResult result = buffer.validate();

        assertFalse(result.valid);
        assertTrue(result.retryable);
        assertTrue(result.message.contains("missing end marker"));
        assertTrue(buffer.canRetry());
    }

    @Test
    public void validateRejectsInvalidPayloadAndAllowsRetry() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\n@@@\nPlayback all done\n");

        CgmCacheSyncBuffer.ValidationResult result = buffer.validate();

        assertFalse(result.valid);
        assertTrue(result.retryable);
        assertTrue(result.message.contains("invalid payload line"));
    }

    @Test
    public void beginRetryClearsCurrentAttemptButKeepsRetryBudget() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer(2);
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\n");

        buffer.beginRetry();
        CgmCacheSyncBuffer.SyncResult result =
                buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\nPlayback all done\n");

        assertEquals(3, result.appendedLines);
        assertTrue(buffer.validate().valid);
        assertFalse(buffer.canRetry());
    }

    @Test
    public void writeToWritesOnlyValidatedReplay() throws Exception {
        File file = temporaryFolder.newFile("CGM_Cache_data.txt");
        FakeFileRecorder recorder = new FakeFileRecorder(file);
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();
        buffer.beginRead();
        buffer.acceptChunk("Start Playback\nEIS:1,1000,0.12\nPlayback all done\n");
        assertTrue(buffer.validate().valid);

        File written = buffer.writeTo(recorder);

        assertSame(file, written);
        assertEquals(1, recorder.startCount);
        assertTrue(recorder.finished);
        assertEquals("Start Playback", recorder.lines.get(0));
        assertEquals("Playback all done", recorder.lines.get(2));
    }

    @Test
    public void logClearedOnlyConfirmsWhenDeleteIsPending() {
        CgmCacheSyncBuffer buffer = new CgmCacheSyncBuffer();

        assertFalse(buffer.acceptDeviceLine("Log Cleared\n").confirmed);

        buffer.markDeleteSent();
        CgmCacheSyncBuffer.DeleteResult result = buffer.acceptDeviceLine("Log Cleared\n");

        assertTrue(result.confirmed);
        assertEquals(CgmCacheSyncBuffer.Phase.DONE, buffer.phase());
    }

    private static final class FakeFileRecorder implements FileRecorder {
        final File file;
        final List<String> lines = new ArrayList<>();
        int startCount;
        boolean finished;

        FakeFileRecorder(File file) {
            this.file = file;
        }

        @Override
        public void start() {
            startCount++;
        }

        @Override
        public void appendLine(@NonNull String line) {
            lines.add(line);
        }

        @NonNull
        @Override
        public File finish() {
            finished = true;
            return file;
        }

        @Override
        public void reset() {
            lines.clear();
        }
    }
}
```

- [ ] **Step 2: Run failing buffer tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.hc.mixthebluetooth.application.cgm.CgmCacheSyncBufferTest
```

Expected: compile fails because `CgmCacheSyncBuffer` does not exist.

## Task 3: Implement The Compact Buffer

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/application/cgm/CgmCacheSyncBuffer.java`

- [ ] **Step 1: Implement the buffer around the three function groups**

Create `app/src/main/java/com/hc/mixthebluetooth/application/cgm/CgmCacheSyncBuffer.java`.

Implementation requirements:

- Constants:

```java
static final String START_MARKER = "Start Playback";
static final String END_MARKER = "Playback all done";
static final String DELETE_ACK = "Log Cleared";
```

- Fields:

```java
private final int maxAttempts;
private final StringBuilder pending = new StringBuilder();
private final ArrayList<String> lines = new ArrayList<>();
private Phase phase = Phase.IDLE;
private int attempt;
```

- `beginRead()`:

```java
attempt = 1;
pending.setLength(0);
lines.clear();
phase = Phase.READING_CACHE;
```

- `acceptChunk(String chunk)`:

```java
pending.append(chunk.replace("\r", ""));
int appended = 0;
boolean sawStart = false;
boolean sawEnd = false;

while (true) {
    int newline = pending.indexOf("\n");
    if (newline < 0) break;
    String line = pending.substring(0, newline).trim();
    pending.delete(0, newline + 1);
    if (!line.isEmpty()) {
        lines.add(line);
        appended++;
        sawStart |= line.contains(START_MARKER);
        sawEnd |= line.contains(END_MARKER);
    }
}

if (pending.indexOf(END_MARKER) >= 0) {
    String line = pending.toString().trim();
    pending.setLength(0);
    if (!line.isEmpty()) {
        lines.add(line);
        appended++;
        sawStart |= line.contains(START_MARKER);
        sawEnd = true;
    }
}

return new SyncResult(appended, sawStart, sawEnd);
```

- `validate()`:
  - find first line containing `START_MARKER`;
  - find first line after start containing `END_MARKER`;
  - fail with `"missing start marker"` if absent;
  - fail with `"missing end marker"` if absent;
  - fail with `"missing payload lines"` if there is no line between start and end;
  - fail with `"invalid payload line"` if a payload line does not match `[A-Za-z0-9_:\\-.,;=\\s]+`;
  - on success set `phase = READY_TO_UPLOAD`.

- `canRetry()`:

```java
return attempt < maxAttempts;
```

- `beginRetry()`:

```java
attempt++;
pending.setLength(0);
lines.clear();
phase = Phase.READING_CACHE;
```

- `writeTo(FileRecorder recorder)`:
  - throw `IllegalStateException` unless `phase == READY_TO_UPLOAD`;
  - call `recorder.reset()`, `recorder.start()`, append every buffered line, then `recorder.finish()`.

- `markDeleteSent()`:

```java
phase = Phase.WAITING_DELETE_CONFIRM;
```

- `acceptDeviceLine(String text)`:

```java
if (phase == Phase.WAITING_DELETE_CONFIRM && text.contains(DELETE_ACK)) {
    phase = Phase.DONE;
    return new DeleteResult(true, "cache delete confirmed");
}
return new DeleteResult(false, "delete not confirmed");
```

- [ ] **Step 2: Run buffer tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.hc.mixthebluetooth.application.cgm.CgmCacheSyncBufferTest
```

Expected: tests pass.

## Task 4: Keep CgmWorkflow Small

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/api/cgm/CgmWorkflow.java`

- [ ] **Step 1: Replace line-only workflow API with small update object**

Use this shape:

```java
public interface CgmWorkflow {
    @NonNull
    Update onReadCacheSent();

    @NonNull
    Update onDeleteCacheSent();

    @NonNull
    Update onDeviceText(@NonNull String text, @NonNull ApiCallback<CallResult<CgmResult>> callback);

    void reset();

    final class Update {
        @Nullable
        public final String message;
        @Nullable
        public final String commandText;
        public final boolean uploadStarted;
        public final boolean deleteConfirmed;
        public final boolean error;

        private Update(@Nullable String message,
                       @Nullable String commandText,
                       boolean uploadStarted,
                       boolean deleteConfirmed,
                       boolean error) {
            this.message = message;
            this.commandText = commandText;
            this.uploadStarted = uploadStarted;
            this.deleteConfirmed = deleteConfirmed;
            this.error = error;
        }

        @NonNull
        public static Update message(@NonNull String message) {
            return new Update(message, null, false, false, false);
        }

        @NonNull
        public static Update command(@NonNull String message, @NonNull String commandText) {
            return new Update(message, commandText, false, false, false);
        }

        @NonNull
        public static Update uploadStarted(@NonNull String message) {
            return new Update(message, null, true, false, false);
        }

        @NonNull
        public static Update deleteConfirmed(@NonNull String message) {
            return new Update(message, null, false, true, false);
        }

        @NonNull
        public static Update error(@NonNull String message) {
            return new Update(message, null, false, false, true);
        }
    }
}
```

This keeps the external API simple:

- `commandText != null` means caller should send that command, usually retry `ALL`.
- `uploadStarted == true` means workflow accepted a valid replay and started upload.
- `deleteConfirmed == true` means hardware returned `Log Cleared` while delete was pending.
- `error == true` means show/log the message.

## Task 5: Refactor DefaultCgmWorkflow Around The Three Blocks

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/application/cgm/DefaultCgmWorkflow.java`
- Modify: `app/src/test/java/com/hc/mixthebluetooth/application/cgm/DefaultCgmWorkflowTest.java`

- [ ] **Step 1: Update workflow tests**

Tests should assert these behaviors:

```text
onReadCacheSent() calls buffer.beginRead().
onDeviceText(valid full replay) validates, writes txt, starts upload, returns uploadStarted.
onDeviceText(invalid replay) returns commandText == ALL\n\r when retry remains.
onDeviceText(invalid replay after retry budget) returns error and does not upload.
upload success is forwarded to callback.
upload failure sets buffer phase ERROR and does not send DELETE.
onDeleteCacheSent() calls markDeleteSent().
onDeviceText(Log Cleared) returns deleteConfirmed only after delete is pending.
```

- [ ] **Step 2: Implement workflow**

Core workflow logic should look like this:

```java
public synchronized Update onDeviceText(String text, ApiCallback<CallResult<CgmResult>> callback) {
    CgmCacheSyncBuffer.DeleteResult delete = buffer.acceptDeviceLine(text);
    if (delete.confirmed) {
        logger.text(OWNER, API_REPLAY, "delete", delete.message);
        return Update.deleteConfirmed(delete.message);
    }

    CgmCacheSyncBuffer.SyncResult sync = buffer.acceptChunk(text);
    if (!sync.sawEnd) {
        return Update.message("cache text accepted");
    }

    CgmCacheSyncBuffer.ValidationResult validation = buffer.validate();
    if (!validation.valid) {
        if (validation.retryable && buffer.canRetry()) {
            buffer.beginRetry();
            return Update.command(validation.message, CgmCommands.LegacyCgm.readCache());
        }
        callback.onResult(CallResult.error(CallResult.DEVICE_REPLAY_INCOMPLETE, validation.message, null));
        return Update.error(validation.message);
    }

    return uploadValidatedReplay(callback);
}
```

If `DefaultCgmWorkflow` cannot import `ui/cgm/CgmCommands` because that would create an application-to-UI dependency, put the string constant `ALL\n\r` in `CgmCacheSyncBuffer` and return that.

- [ ] **Step 3: Delete old detector**

Delete:

```text
app/src/main/java/com/hc/mixthebluetooth/application/cgm/CgmReplayCompletionDetector.java
app/src/test/java/com/hc/mixthebluetooth/application/cgm/CgmReplayCompletionDetectorTest.java
```

## Task 6: Wire UI Without Turning It Into A State Machine

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmController.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmProfile.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmFragment.java`

- [ ] **Step 1: Pass raw text to workflow**

In `CgmController.onBtData`, decode once with `trim=false` for workflow:

```java
String rawText = Codec.decode(data.bytes, new Codec.Options(
        AppApi.settingsStore().textEncoding(),
        false,
        false,
        false
));
```

Use `rawText.trim()` only for UI message display and sample parsing.

- [ ] **Step 2: Let raw consumer send retry commands**

In `CgmProfile.CgmRawLineConsumer`, after calling workflow:

```java
CgmWorkflow.Update update = AppApi.cgmWorkflow().onDeviceText(text, result -> {
    gateway.onCgmWorkflowResult(result);
    if (result.isOk()) {
        gateway.postText(module, CgmCommands.LegacyCgm.deleteCache());
        gateway.onCgmWorkflowUpdate(AppApi.cgmWorkflow().onDeleteCacheSent());
    }
});

if (update.commandText != null) {
    gateway.postText(module, update.commandText);
}
gateway.onCgmWorkflowUpdate(update);
```

- [ ] **Step 3: Notify workflow when read/delete buttons send commands**

When `read_cache` is sent:

```java
gateway.onCgmWorkflowUpdate(AppApi.cgmWorkflow().onReadCacheSent());
```

When confirmed `delete_cache` is sent:

```java
gateway.onCgmWorkflowUpdate(AppApi.cgmWorkflow().onDeleteCacheSent());
```

- [ ] **Step 4: Add manual delete confirmation**

In `CgmFragment.FragmentGateway.confirmDeleteCache`:

```java
new AlertDialog.Builder(requireContext())
        .setTitle("确认删除缓存")
        .setMessage("设备缓存删除后无法恢复。请确认本次数据已经保存或上传成功。")
        .setNegativeButton("取消", null)
        .setPositiveButton("删除", (dialog, which) -> onConfirm.run())
        .show();
```

## Task 7: Verification

**Files:**
- No planned source edits.

- [ ] **Step 1: Run focused tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.hc.mixthebluetooth.ui.cgm.CodecTest --tests com.hc.mixthebluetooth.application.cgm.CgmCacheSyncBufferTest --tests com.hc.mixthebluetooth.application.cgm.DefaultCgmWorkflowTest
```

Expected: selected tests pass.

- [ ] **Step 2: Run app unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all unit tests pass.

- [ ] **Step 3: Build debug APK**

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Manual Logcat check**

Use Android Studio Logcat filter:

```text
tag:BioAI.Http package:com.hc.mixthebluetooth
```

Expected flow:

```text
BT_SEND read_cache payload=ALL
CGM_REPLAY read started
BT_RECV raw text contains Start Playback
BT_RECV raw text contains Playback all done
CGM_REPLAY validation passed
CGM_UPLOAD_POLL file=<txt path>
CGM_REPLAY upload succeeded
BT_SEND delete_cache payload=DELETE
BT_RECV raw text contains Log Cleared
CGM_REPLAY cache delete confirmed
```

## Acceptance Criteria

- The buffer API is centered on `acceptChunk`, `validate`, `writeTo`, `markDeleteSent`, and `acceptDeviceLine`.
- There is no large workflow event enum driving the implementation.
- `Phase` is used only as a guard/debug value.
- Split chunks and multi-line chunks are handled.
- `Playback all done` without trailing newline is handled.
- Invalid replay triggers whole-cache retry via `ALL\n\r`.
- Txt is written only after validation passes.
- Upload starts only after txt write succeeds.
- Automatic delete is sent only after upload success.
- `Log Cleared` confirms delete only when delete is pending.
- Manual delete requires a confirmation dialog.
