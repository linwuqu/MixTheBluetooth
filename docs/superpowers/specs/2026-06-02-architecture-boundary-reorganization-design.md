# Architecture Boundary Reorganization Design

Date: 2026-06-02

Status: Draft for user review.

## 1. Background

The current Android app has working pieces, but the project tree mixes several generations of implementation:

- `activity/`, `fragment/`, `uni/`, `customView/`, and `recyclerData/` all contain UI or UI-adjacent behavior.
- `api/`, `impl/`, `remote/`, `local/`, `storage/`, and `staticdata/` contain service contracts, business logic, HTTP details, persistence, file recording, and static fixtures.
- The CGM path is currently split across `CgmProfile`, `UniFragment`, `DefaultDeviceDataService`, `DeviceDataRecorder`, and `DefaultCgmService`.
- Old screens and fragments still expose useful capabilities, but their UI containers are no longer part of the intended CGM flow.

The goal is not cosmetic package renaming. The goal is to make each file answer one clear question:

- What does the upper layer promise?
- What business use case is implemented?
- What raw driver capability is required?
- Which Android, Retrofit, file, or persistence implementation supplies that capability?
- Which UI screen renders or triggers the workflow?

## 2. Goals

- Reorganize the app into a tree that matches real responsibilities:
  - `runtime/`
  - `api/`
  - `application/`
  - `driver/`
  - `persistence/`
  - `ui/`
- Keep capability contracts separate from implementation details.
- Keep the CGM feature readable as one vertical flow.
- Remove app-internal static service implementations. Static fixed responses are now handled by the Python LAN static backend.
- Keep only two build environments:
  - `static-lan`: real HTTP to a LAN backend with fixed responses.
  - `dev`: real HTTP to the development backend that performs actual computation.
- Replace plain SharedPreferences session storage with encrypted persistence.
- Delete old UI shells that are no longer part of the intended product flow, while preserving useful capabilities behind cleaner contracts.

## 3. Non-Goals

- Do not rewrite the Bluetooth library module.
- Do not change backend endpoint shapes in this reorganization.
- Do not add a new mock UI flow.
- Do not keep the app-internal static branch as a runtime mode.
- Do not preserve old fragments merely because they exist. Preserve capabilities, not stale UI shells.
- Do not let UI classes call Retrofit endpoints, SharedPreferences, or Android Bluetooth library classes directly after the migration.

## 4. Final Top-Level Tree

The final app package should use this shape:

```text
com.hc.mixthebluetooth/
  runtime/
  api/
  application/
  driver/
  persistence/
  ui/
```

### 4.1 runtime

`runtime/` owns app startup and dependency installation.

```text
runtime/
  MixBluetoothApplication.java
  AppApiBootstrap.java
  AppContainer.java
  EnvConfig.java
```

Responsibilities:

- Read build-time environment config.
- Create implementations for API contracts.
- Wire business use cases to driver capabilities.
- Expose a single app container or service locator used by UI.
- Keep environment selection out of UI and business classes.

Old files:

- `MixBluetoothApplication.java` moves to `runtime/MixBluetoothApplication.java`.
- `impl/AppApiBootstrap.java` moves to `runtime/AppApiBootstrap.java`.
- `api/EnvConfig.java` moves to `runtime/EnvConfig.java`.
- `api/AppApi.java` is replaced or reduced into `runtime/AppContainer.java`.

### 4.2 api

`api/` owns contracts and domain-facing models. It must not depend on Retrofit, Android SDK persistence details, or concrete Bluetooth classes.

```text
api/
  core/
    ApiCallback.java
    CallResult.java

  auth/
    AuthService.java
    AuthUser.java

  file/
    FileUploadService.java
    UploadedFile.java

  cgm/
    CgmWorkflow.java
    CgmJobService.java
    CgmResult.java
    CgmPoint.java
    CgmSummary.java

  device/
    DeviceGateway.java
    DeviceInfo.java
    DeviceEvent.java
    CgmCommandEncoder.java

  persistence/
    SessionStore.java
    SettingsStore.java

  codec/
    TextCodec.java

  log/
    AppLogger.java
```

Responsibilities:

- Define what upper layers can call.
- Define domain-facing data models.
- Hide Retrofit DTOs and Android Bluetooth library types from UI and application code.
- Keep `CallResult` as the shared success, pending, and error envelope for this reorganization.

Old files:

- `api/ApiCallback.java` -> `api/core/ApiCallback.java`.
- `api/CallResult.java` -> `api/core/CallResult.java`.
- `api/auth/AuthService.java` -> remains in `api/auth`.
- `api/auth/AuthUser.java` -> remains in `api/auth`.
- `api/file/FileService.java` -> rename to `api/file/FileUploadService.java`.
- `api/file/UploadedFile.java` -> remains in `api/file`.
- `api/cgm/CgmService.java` -> split:
  - `api/cgm/CgmJobService.java` for upload and poll job operations.
  - `api/cgm/CgmWorkflow.java` for the full CGM device line to result workflow.
- `api/device/DeviceDataService.java` -> replaced by `api/cgm/CgmWorkflow` plus lower-level recorder contracts.

### 4.3 application

`application/` owns business use case implementations. It depends on `api/` contracts and `driver/capability/` contracts, not on Android implementation classes directly.

```text
application/
  auth/
    DefaultAuthService.java

  file/
    DefaultFileUploadService.java

  cgm/
    DefaultCgmWorkflow.java
    DefaultCgmJobService.java

  device/
    DefaultDeviceGateway.java
```

Responsibilities:

- Implement service contracts from `api/`.
- Convert raw driver results into domain results.
- Keep business rules in one readable vertical path.
- Keep the CGM flow together:

```text
Bluetooth raw line
  -> CgmWorkflow.consumeDeviceLine(...)
  -> FileRecorder detects Start Playback and Playback all done
  -> CgmJobService uploads txt
  -> CgmJobService polls generated result
  -> CgmWorkflow returns CgmResult
  -> UI renders result
```

Old files:

- `impl/auth/DefaultAuthService.java` -> `application/auth/DefaultAuthService.java`.
- `impl/file/DefaultFileService.java` -> `application/file/DefaultFileUploadService.java`.
- `impl/cgm/DefaultCgmService.java` -> split:
  - upload/poll details become `application/cgm/DefaultCgmJobService.java`.
  - orchestration moves into `application/cgm/DefaultCgmWorkflow.java`.
- `impl/device/DefaultDeviceDataService.java` -> removed as a standalone service; its useful flow is absorbed into `DefaultCgmWorkflow` and driver recorder contracts.
- `impl/auth/StaticAuthService.java` and `impl/cgm/StaticCgmService.java` are deleted.
- `impl/log/ApiTraceLogger.java` becomes an implementation behind `api/log/AppLogger`.

### 4.4 driver

`driver/` is explicitly split into capability contracts and implementation.

```text
driver/
  capability/
    BluetoothTransport.java
    HttpTransport.java
    FileRecorder.java
    ReplaySource.java
    PreferencesStore.java
    Clock.java

  implementation/
    bluetooth/
      AndroidBluetoothTransport.java
      BluetoothLibraryDeviceMapper.java

    http/
      RetrofitHttpTransport.java
      endpoint/
        BioAiEndpoints.java
      dto/
        ServerResponse.java
        ServerDtos.java

    file/
      AndroidFileRecorder.java
      AssetReplaySource.java

    codec/
      AnalysisTextCodec.java

    log/
      AndroidAppLogger.java

    time/
      SystemClock.java
```

Responsibilities:

- `driver/capability/` defines raw capabilities needed by application services.
- `driver/implementation/` adapts Android SDK, Retrofit, OkHttp, local files, assets, logging, and legacy utilities.
- UI and application layers should not import `driver/implementation/*` directly.
- Retrofit endpoints stay in the driver implementation because they are transport declarations, not business APIs.

Old files:

- `activity/single/HoldBluetooth.java` -> `driver/implementation/bluetooth/AndroidBluetoothTransport.java`.
- `activity/single/BTPackage.java` -> replaced by `api/device/DeviceEvent.java` and implementation event mapping.
- `remote/ServerClient.java` -> `driver/implementation/http/RetrofitHttpTransport.java`.
- `remote/ServerEndpoints.java` -> `driver/implementation/http/endpoint/BioAiEndpoints.java`.
- `remote/ServerResponse.java` -> `driver/implementation/http/dto/ServerResponse.java`.
- `remote/ServerModels.java` -> `driver/implementation/http/dto/ServerDtos.java`.
- `local/DeviceDataRecorder.java` -> `driver/implementation/file/AndroidFileRecorder.java`.
- `local/DeviceReplaySample.java` -> `driver/implementation/file/AssetReplaySource.java`.
- `uni/Codec.java` and reusable parts of `activity/tool/Analysis.java` -> `driver/implementation/codec/AnalysisTextCodec.java`.
- `impl/log/ApiTraceLogger.java` -> `driver/implementation/log/AndroidAppLogger.java`.

### 4.5 persistence

`persistence/` owns encrypted persistence implementations. The public contracts live in `api/persistence/`.

```text
persistence/
  EncryptedPreferencesStore.java
  EncryptedSessionStore.java
  EncryptedSettingsStore.java
```

Responsibilities:

- Store session data and UI settings through a single persistence family.
- Encrypt session data.
- Keep the implementation testable through `api/persistence/SessionStore`, `api/persistence/SettingsStore`, and `driver/capability/PreferencesStore`.
- Prevent UI from constructing SharedPreferences directly.

Old files:

- `local/SessionStore.java`:
  - interface shape moves to `api/persistence/SessionStore.java`.
  - encrypted implementation moves to `persistence/EncryptedSessionStore.java`.
- `storage/Storage.java`:
  - settings contract moves to `api/persistence/SettingsStore.java`.
  - encrypted implementation moves to `persistence/EncryptedSettingsStore.java`.
- `activity/single/FragmentParameter.java` is deleted and replaced by `SettingsStore` plus `TextCodec`.

Settings covered by `SettingsStore` include:

- encoding format
- first-run flags
- device filter options
- any remaining UI preferences that were previously stored by `Storage`

### 4.6 ui

`ui/` owns Android screens, fragments, adapters, dialogs, and custom views. It can depend on `api/`, but it should not know Retrofit, SharedPreferences, or raw Bluetooth implementation details.

```text
ui/
  auth/
    LoginActivity.java
    RegisterActivity.java

  intro/
    IntroActivity.java

  main/
    MainActivity.java
    DeviceListAdapter.java
    DeviceFilterDialog.java
    CollectDeviceDialog.java

  cgm/
    CgmActivity.java
    CgmFragment.java
    CgmController.java
    CgmProfile.java
    CgmWidgets.java
    MessageAdapter.java
    MessageItem.java

  shared/
    view/
      CircleProgressView.java
      UnderlineTextView.java
```

Responsibilities:

- Render screens.
- Forward user actions to API contracts.
- Render workflow outputs.
- Own only UI models and view state.
- Avoid carrying old debugging and generic Bluetooth-helper screens into the CGM product flow.

Old files:

- `activity/LoginActivity.java` -> `ui/auth/LoginActivity.java`.
- `activity/AccountRegisterActivity.java` -> `ui/auth/RegisterActivity.java`.
- `activity/IntroActivity.java` -> `ui/intro/IntroActivity.java`.
- `activity/MainActivity.java` -> `ui/main/MainActivity.java`.
- `activity/CommunicationActivity.java` -> `ui/cgm/CgmActivity.java`.
- `fragment/UniFragment.java` -> `ui/cgm/CgmFragment.java`.
- `uni/Controller.java` -> `ui/cgm/CgmController.java`.
- `uni/profile/cgm/CgmProfile.java` -> `ui/cgm/CgmProfile.java`.
- `uni/Widgets.java` -> `ui/cgm/CgmWidgets.java`.
- `uni/Commands.java`:
  - CGM command encoding moves to `api/device/CgmCommandEncoder.java` and implementation.
  - UI labels and action list stay in `ui/cgm/CgmProfile.java`.
- `recyclerData/MainRecyclerAdapter.java` -> `ui/main/DeviceListAdapter.java`.
- `recyclerData/FragmentMessAdapter.java` -> `ui/cgm/MessageAdapter.java`.
- `recyclerData/itemHolder/FragmentMessageItem.java` -> `ui/cgm/MessageItem.java`.
- `customView/CircleProgressView.java` -> `ui/shared/view/CircleProgressView.java`.
- `customView/UnderlineTextView.java` -> `ui/shared/view/UnderlineTextView.java`.
- `customView/PopWindowMain.java` -> `ui/main/DeviceFilterDialog.java`.
- `customView/dialog/CollectBluetooth.java` -> `ui/main/CollectDeviceDialog.java`.

Deleted UI files:

- `activity/DebugActivity.java`
- `activity/SendFileActivity.java`
- `activity/HIDActivity.java`
- `fragment/FragmentCustom.java`
- `fragment/FragmentCustomGroup.java`
- `fragment/FragmentCustomDirection.java`
- `fragment/FragmentIonAnalysis.java`
- `fragment/FragmentSetting.java`
- `fragment/FragmentLog.java`
- `fragment/UnifiedMessageFragment.java`
- `recyclerData/FileRecyclerAdapter.java`
- `recyclerData/FragmentLogAdapter.java`
- `recyclerData/itemHolder/FragmentLogItem.java`
- old dialogs used only by deleted screens:
  - `SetMtu`
  - `SetButton`
  - `PermissionHint`
  - `InvalidHint`
  - `HintHID`
- old custom views used only by deleted screens:
  - `CheckBoxSample`
  - `NumPickView`
  - `CustomButtonView`

`ChartMarkerView` depends on chart usage. If CGM widgets still need MPAndroidChart markers, it moves to `ui/cgm/ChartMarkerView.java`. If the new CGM widget path does not use it, it is deleted with the old ion-analysis UI.

## 5. Environment Model

Only two build environments remain:

```text
static-lan
dev
```

`static-lan`:

- Android uses real Retrofit and OkHttp.
- The base URL points to the Python LAN static backend.
- The backend returns deterministic fixed responses.
- This validates real HTTP request, upload, polling, parsing, and rendering.

`dev`:

- Android uses real Retrofit and OkHttp.
- The base URL points to the real development backend.
- Backend performs actual computation.

Files:

```text
app/config/env.static-lan.properties
app/config/env.dev.properties
```

Deleted config files:

```text
app/config/env.static.properties
app/config/env.lan.properties
app/config/env.prod.properties
```

`staticdata/StaticBioAiFixtures.java` is deleted because fixed CGM responses are served by the Python backend, not Android code.

## 6. Dependency Rules

Allowed:

```text
ui -> api
application -> api
application -> driver/capability
driver/implementation -> driver/capability
driver/implementation -> Android SDK / Retrofit / OkHttp / bluetoothlibrary
persistence -> api/persistence
persistence -> driver/capability
runtime -> api
runtime -> application
runtime -> driver/implementation
runtime -> persistence
```

Disallowed:

```text
ui -> remote / Retrofit endpoint / ServerDtos
ui -> SharedPreferences
ui -> AllBluetoothManage / HoldBluetooth / DeviceModule
application -> Android Activity / Fragment / View
application -> Retrofit annotations
api -> Android SDK implementation details
api -> Retrofit / OkHttp
driver/capability -> driver/implementation
```

The runtime layer is the composition root. It is allowed to know many concrete classes because its only job is wiring.

## 7. CGM Workflow Design

The new `CgmWorkflow` is the main clarity improvement.

Current split:

```text
CgmProfile
  -> AppApi.deviceData().consumeLine
  -> DeviceDataRecorder
  -> UniFragment.onCacheFileReady
  -> AppApi.cgm().uploadAndPoll
  -> DefaultCgmService
  -> Controller.onCgmResult
```

Target split:

```text
CgmFragment / CgmController
  -> CgmWorkflow.consumeDeviceLine(line)
  -> CgmWorkflow returns pending or generated result
  -> CgmController renders generated result
```

`DefaultCgmWorkflow` dependencies:

- `FileRecorder`
- `CgmJobService`
- `AppLogger`
- `Clock` if timestamps are needed

`DefaultCgmJobService` dependencies:

- `HttpTransport` or a narrow Retrofit-backed remote implementation
- `AppLogger`

The workflow owns the entire CGM chain:

1. Receive decoded raw line.
2. Pass line to recorder.
3. If recorder is pending, return pending.
4. If recorder finishes a cache file, upload it.
5. Extract job id.
6. Poll until generated or timeout.
7. Return `CgmResult`.
8. UI renders result.

This prevents UI from handling intermediate file-ready events.

## 8. Driver Capability Design

`BluetoothTransport` exposes raw device operations:

```text
scan
stopScan
connect
disconnect
sendBytes
setMtu
observeEvents
```

`DeviceGateway` in `api/device/` is the upper-level device contract used by UI and CGM. It should work with `DeviceInfo` and `DeviceEvent`, not `DeviceModule`.

`AndroidBluetoothTransport` adapts the existing `bluetoothlibrary` and maps:

```text
DeviceModule -> DeviceInfo
IBluetooth callbacks -> DeviceEvent
sendData -> sendBytes
setMTU -> setMtu
```

`HttpTransport` exposes request-level behavior or is kept narrow by specific remotes. Retrofit endpoint declarations live only in `driver/implementation/http/endpoint`.

`FileRecorder` handles line recording and file completion detection:

```text
consumeLine(line) -> pending or completed file
currentFile()
```

`PreferencesStore` is the low-level encrypted key-value capability used by persistence implementations.

## 9. UI Deletion And Capability Preservation

The old UI shells are deleted, but their useful capabilities are preserved:

| Old UI behavior | Target capability |
|---|---|
| encoding selection in `FragmentSetting` | `SettingsStore` and `TextCodec` |
| custom text or bytes sending in old fragments | preserve only as `DeviceGateway.sendText` / `DeviceGateway.sendBytes`; old custom-send UI is deleted |
| MTU setting dialog | delete the dialog; preserve only `BluetoothTransport.setMtu` as a driver capability |
| debug mode flag | delete the debug UI flag; logging remains controlled by build/debug logging policy |
| file sending screen | deleted, not part of CGM product flow |
| HID help screen | deleted |
| log fragment | deleted; logging remains in `AppLogger` and logcat |

This avoids coupling driver capability to legacy UI controls.

## 10. Manifest And Resource Cleanup

The manifest should eventually register only retained activities:

```text
runtime.MixBluetoothApplication
ui.intro.IntroActivity
ui.auth.LoginActivity
ui.auth.RegisterActivity
ui.main.MainActivity
ui.cgm.CgmActivity
```

Deleted activity registrations:

```text
DebugActivity
SendFileActivity
HIDActivity
```

Layouts and drawables used only by deleted screens should be removed after references are gone. The cleanup should be compiler-driven:

1. Remove Java references.
2. Remove manifest entries.
3. Remove layouts that are no longer referenced.
4. Remove drawables only referenced by deleted layouts.
5. Run compile/tests after each cleanup stage.

## 11. Migration Strategy

This reorganization is large enough to require staged migration. The implementation should not attempt one giant move.

### Stage 1: Environment and static removal

- Add `env.static-lan.properties`.
- Keep `env.dev.properties`.
- Remove `static`, `lan`, and `prod` configs.
- Remove Android internal static service branch.
- Delete `StaticAuthService`, `StaticCgmService`, and `StaticBioAiFixtures`.
- Ensure both `static-lan` and `dev` use real HTTP services.

### Stage 2: Persistence

- Create `api/persistence/SessionStore`.
- Create `api/persistence/SettingsStore`.
- Create encrypted persistence implementations.
- Replace `local/SessionStore` direct SharedPreferences usage.
- Replace `storage/Storage` and `FragmentParameter` usage gradually through `SettingsStore`.

### Stage 3: Driver capability contracts

- Create `driver/capability` contracts.
- Wrap `HoldBluetooth` behavior in `AndroidBluetoothTransport`.
- Create `DeviceInfo` and `DeviceEvent` so UI no longer depends on `DeviceModule`.
- Wrap text codec behavior behind `TextCodec`.
- Move `DeviceDataRecorder` into `driver/implementation/file`.

### Stage 4: CGM workflow

- Introduce `api/cgm/CgmWorkflow`.
- Introduce `application/cgm/DefaultCgmWorkflow`.
- Split upload/poll into `CgmJobService`.
- Move file-ready upload logic out of `CgmFragment`.
- Make `CgmController` consume generated workflow results.

### Stage 5: UI slimming

- Rename and move:
  - `CommunicationActivity` -> `ui/cgm/CgmActivity`
  - `UniFragment` -> `ui/cgm/CgmFragment`
  - `Controller` -> `ui/cgm/CgmController`
  - `Widgets` -> `ui/cgm/CgmWidgets`
  - `AccountRegisterActivity` -> `ui/auth/RegisterActivity`
- Remove old fragment registration from CGM activity.
- Delete old fragments and old UI-only helper screens.
- Keep only shared views that CGM or retained screens still use.

### Stage 6: Package and resource cleanup

- Move remaining files into final package locations.
- Remove obsolete imports.
- Remove obsolete layouts and drawables.
- Run compile and unit tests.

## 12. Testing Strategy

Unit tests:

- `CallResult` remains covered.
- `DefaultAuthService` maps HTTP success, remote error, empty response, and network failure.
- `DefaultCgmJobService` covers upload success, missing job id, poll generated, poll timeout, and network failure.
- `DefaultCgmWorkflow` covers:
  - pending lines
  - incomplete replay
  - completed replay file
  - upload and poll success
  - upload/poll error propagation
- `EncryptedSessionStore` and `EncryptedSettingsStore` should be covered with test doubles where Android encryption is unavailable in JVM tests.

Integration or contract tests:

- `BioAiEndpoints` endpoint paths remain compatible with backend contract.
- Python static-lan backend smoke tests remain responsible for deterministic fixed responses.

Build verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

Manual acceptance:

1. Start Python LAN static backend.
2. Build `static-lan`.
3. Login/register through the real HTTP path.
4. Connect the real device.
5. Trigger CGM cache read.
6. Confirm txt upload, poll, generated result, and render.
7. Build `dev` and confirm the same Android path hits the development backend.

## 13. Success Criteria

- The project tree uses the final top-level structure.
- There are no production imports from `ui` to Retrofit endpoint, SharedPreferences, or raw `DeviceModule`.
- Session and settings persistence use the unified persistence path.
- Session data is encrypted.
- `static-lan` and `dev` are the only supported env configs.
- Android internal static service classes are gone.
- Old fragment pages and obsolete helper activities are gone.
- `CgmWorkflow` makes the full device line to CGM result chain understandable from one implementation file.
- The app still compiles and the CGM static-lan and dev flows remain testable.

## 14. File Mapping Summary

| Old path | Target path or action |
|---|---|
| `MixBluetoothApplication.java` | `runtime/MixBluetoothApplication.java` |
| `api/AppApi.java` | `runtime/AppContainer.java` |
| `api/EnvConfig.java` | `runtime/EnvConfig.java` |
| `impl/AppApiBootstrap.java` | `runtime/AppApiBootstrap.java` |
| `api/ApiCallback.java` | `api/core/ApiCallback.java` |
| `api/CallResult.java` | `api/core/CallResult.java` |
| `api/auth/*` | `api/auth/*` |
| `api/file/FileService.java` | `api/file/FileUploadService.java` |
| `api/cgm/CgmService.java` | `api/cgm/CgmJobService.java` and `api/cgm/CgmWorkflow.java` |
| `api/device/DeviceDataService.java` | replaced by `CgmWorkflow` and driver recorder contracts |
| `impl/auth/DefaultAuthService.java` | `application/auth/DefaultAuthService.java` |
| `impl/file/DefaultFileService.java` | `application/file/DefaultFileUploadService.java` |
| `impl/cgm/DefaultCgmService.java` | `application/cgm/DefaultCgmJobService.java` |
| `impl/device/DefaultDeviceDataService.java` | absorbed into `application/cgm/DefaultCgmWorkflow.java` |
| `impl/log/ApiTraceLogger.java` | `driver/implementation/log/AndroidAppLogger.java` |
| `impl/auth/StaticAuthService.java` | delete |
| `impl/cgm/StaticCgmService.java` | delete |
| `remote/ServerClient.java` | `driver/implementation/http/RetrofitHttpTransport.java` |
| `remote/ServerEndpoints.java` | `driver/implementation/http/endpoint/BioAiEndpoints.java` |
| `remote/ServerResponse.java` | `driver/implementation/http/dto/ServerResponse.java` |
| `remote/ServerModels.java` | `driver/implementation/http/dto/ServerDtos.java` plus domain model extraction |
| `local/SessionStore.java` | `api/persistence/SessionStore.java` and `persistence/EncryptedSessionStore.java` |
| `local/DeviceDataRecorder.java` | `driver/implementation/file/AndroidFileRecorder.java` |
| `local/DeviceReplaySample.java` | `driver/implementation/file/AssetReplaySource.java` |
| `storage/Storage.java` | `api/persistence/SettingsStore.java` and `persistence/EncryptedSettingsStore.java` |
| `staticdata/StaticBioAiFixtures.java` | delete |
| `activity/LoginActivity.java` | `ui/auth/LoginActivity.java` |
| `activity/AccountRegisterActivity.java` | `ui/auth/RegisterActivity.java` |
| `activity/IntroActivity.java` | `ui/intro/IntroActivity.java` |
| `activity/MainActivity.java` | `ui/main/MainActivity.java` |
| `activity/CommunicationActivity.java` | `ui/cgm/CgmActivity.java` |
| `activity/DebugActivity.java` | delete |
| `activity/SendFileActivity.java` | delete |
| `activity/HIDActivity.java` | delete |
| `activity/single/HoldBluetooth.java` | `driver/implementation/bluetooth/AndroidBluetoothTransport.java` |
| `activity/single/BTPackage.java` | `api/device/DeviceEvent.java` plus mapper |
| `activity/single/FragmentParameter.java` | delete, replace with `SettingsStore` |
| `activity/single/StaticConstants.java` | transition-only `ui/cgm/CgmEventKeys`, then delete after direct controller/device events replace LiveEventBus keys |
| `activity/tool/Analysis.java` | split into codec implementation and UI helpers |
| `activity/tool/BluetoothSample.java` | delete with old realtime sample parsing |
| `activity/tool/BluetoothSampleParser.java` | delete with old realtime sample parsing |
| `activity/tool/Profiles.java` | delete |
| `activity/tool/SampleRecorder.java` | delete |
| `fragment/UniFragment.java` | `ui/cgm/CgmFragment.java` |
| `fragment/BTFragment.java` | delete after event routing moves to `DeviceGateway` |
| old non-CGM fragments | delete |
| `uni/Controller.java` | `ui/cgm/CgmController.java` |
| `uni/Widgets.java` | `ui/cgm/CgmWidgets.java` |
| `uni/profile/cgm/CgmProfile.java` | `ui/cgm/CgmProfile.java` |
| `uni/profile/eis/EisProfile.java` | delete |
| `uni/Commands.java` | split into CGM command encoder and UI profile actions |
| `uni/Codec.java` | `api/codec/TextCodec.java` and `driver/implementation/codec/AnalysisTextCodec.java` |
| `uni/Output.java` | delete |
| `uni/Profiles.java` | delete after single CGM profile is direct |
| `recyclerData/MainRecyclerAdapter.java` | `ui/main/DeviceListAdapter.java` |
| `recyclerData/FragmentMessAdapter.java` | `ui/cgm/MessageAdapter.java` |
| `recyclerData/itemHolder/FragmentMessageItem.java` | `ui/cgm/MessageItem.java` |
| `recyclerData/FileRecyclerAdapter.java` | delete |
| `recyclerData/FragmentLogAdapter.java` | delete |
| `recyclerData/itemHolder/FragmentLogItem.java` | delete |
| reusable custom views | `ui/shared/view` |
| old-only custom views and dialogs | delete |
