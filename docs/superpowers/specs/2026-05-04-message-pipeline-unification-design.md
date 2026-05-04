# Message Pipeline Unification Design

## Goal

Unify `FragmentMessage` and `FragmentMessageNew` around one small, repeatable Bluetooth message pipeline without doing a large device-profile rewrite yet.

This phase should prove the recommended route:

- `FragmentMessageNew` becomes the clean reference fragment.
- `FragmentMessage` adopts only the safe shared parts first: message list and send flow.
- `FragmentMessage` must ultimately read like the same kind of pipeline host as `FragmentMessageNew`: declare channels, initialize shared list/send/pipeline pieces, then delegate legacy device-specific parsing behind a named processor.
- `CommunicationActivity` stops owning `FragmentMessageNew`-specific record/control logic.
- The future `DeviceProfile` direction is documented as deferred backlog, not implemented now.

## Current Shape

`BTFragment` already gives every Bluetooth fragment the same event entry point:

- Fragments declare channels in `initChannels()`.
- Activity pushes typed `BTPackage` payloads through `CH_BT_DATA`.
- Fragments override typed callbacks such as `onBtData`, `onBtConnected`, and `onRecStateChanged`.

`FragmentMessageNew` is close to the intended model because it registers:

- parsers through `BluetoothSampleRegistry`
- charts through `ChartRegistry`
- metric-to-chart binding through `SampleChartBinder`

The remaining asymmetry is that `CommunicationActivity` still knows about `FragmentMessageNew` control and recording:

- `CMD_MSG_NEW_CONTROL`
- `EV_REC_SAMPLE`
- the inner `Recorder`
- `CH_REC_STATE`
- `CH_REC_EXPORT_RESULT`

That makes `FragmentMessageNew` a special case instead of one normal `BTFragment`.

## Non-Goals

Do not implement a full `DeviceProfile` registry in this phase.

Do not merge `FragmentMessage` and `FragmentMessageNew` into one class yet.

Do not move the old CGM/CA/EIS/RI parsing logic into the new parser registry yet. It should first be isolated behind named methods or a legacy processor so behavior remains reviewable.

Do not move Bluetooth connection lifecycle out of `CommunicationActivity`. It is timing-sensitive and already central to the screen.

## Recommended Architecture

### 1. `MessagePipelineController`

Introduce a small controller used first by `FragmentMessageNew`.

Responsibilities:

- decode incoming `BTPackage.BTData` bytes into text
- append text to `MessageListController`
- parse text with a `BluetoothSampleRegistry`
- append parsed samples to charts through `SampleChartBinder`
- notify an optional recorder when a parsed sample should be recorded

It should not know about Android buttons, tabs, connection title, or Bluetooth connection retry.

### 2. `SampleRecorder`

Extract the current `CommunicationActivity.Recorder` into a reusable helper owned by the fragment that needs recording.

Responsibilities:

- start a new `.jsonl` file
- stop recording
- export/report the current path
- append sample JSON lines on a single background executor
- expose recording state back to the owning fragment through a callback

The fragment owns UI updates such as `tvRecordState`, `tvBottomInfo`, and toast text.

### 3. `FragmentMessageNew` as Reference Fragment

After this phase, `FragmentMessageNew` should read roughly as:

- register channels
- init message list
- register parsers
- register charts
- create `MessagePipelineController`
- create `SampleRecorder`
- handle three buttons: start, stop, export
- handle Bluetooth callbacks by delegating to the pipeline

It should no longer send `CMD_MSG_NEW_CONTROL` or `EV_REC_SAMPLE` to `CommunicationActivity`.

### 4. `CommunicationActivity` as Generic Shell

After this phase, `CommunicationActivity` should still own:

- Bluetooth connection setup and retry
- real Bluetooth sending from `CMD_SEND_BT_DATA`
- broadcasting `BTPackage` data/connect/disconnect events
- tabs, title menu, MTU, send-file navigation

It should no longer own:

- `CMD_MSG_NEW_CONTROL`
- `EV_REC_SAMPLE`
- `Recorder`
- `MessageNewCmd`

### 5. `FragmentMessage` Safe Adoption

`FragmentMessage` should not be fully migrated yet. First pass only:

- keep it as a `BTFragment`
- keep its existing legacy device behavior
- use shared message item/list/send helpers where behavior is already equivalent
- isolate legacy CA/EIS/RI/cache parsing behind named methods or a small `LegacyMessageProcessor`

The important result is that the main receive flow becomes readable:

```text
bytes -> decode -> message list -> legacy measurement/cache branch -> optional chart/file side effects
```

## Data Flow

### FragmentMessageNew

```text
CommunicationActivity
  -> CH_BT_DATA / BTPackage.BTData
  -> BTFragment.onBtData
  -> MessagePipelineController
  -> MessageListController
  -> BluetoothSampleRegistry
  -> SampleChartBinder
  -> SampleRecorder when recording
```

### FragmentMessage

```text
CommunicationActivity
  -> CH_BT_DATA / BTPackage.BTData
  -> BTFragment.onBtData
  -> existing receive flow
  -> shared message helpers where safe
  -> legacy CGM/cache/chart logic remains isolated
```

## Error Handling

Parsing failure should not crash the fragment. The pipeline should log the raw line and keep the message visible in the list.

Recording failure should log the exception and notify the fragment through a callback. It should not break receiving or charting.

Unknown samples should still appear in the message list even when no parser handles them.

`SampleRecorder` should tolerate export before start by returning an empty path.

## Testing And Verification

Compile after each small extraction:

```powershell
$env:JAVA_HOME='E:\AndroidStudio\jbr'; .\gradlew.bat :app:compileDebugJavaWithJavac
```

Manual smoke path:

1. Open `CommunicationActivity`.
2. Confirm default page still opens on `FragmentMessageNew`.
3. Connect a device.
4. Confirm raw text appears in MessageNew list.
5. Confirm EIS samples still update both charts.
6. Start recording and confirm charting continues.
7. Stop recording and export path.
8. Switch to old Message page and confirm existing send/receive behavior still works.
9. Disconnect and confirm reconnect/loop-send behavior still belongs to Activity.

## Deferred Backlog For Later Aggressive Route

After the recommended route is proven, consider a `DeviceProfile` layer:

- profile declares sample parsers
- profile declares chart registrations
- profile declares recorder/export format
- profile declares command buttons and labels
- fragments become thin hosts for one selected profile
- migrate the isolated `FragmentMessage` legacy processor into a real profile once its behavior is covered by compile checks and manual device smoke tests

This is intentionally deferred because the current old CGM logic is still too behavior-heavy to migrate safely in one pass.
