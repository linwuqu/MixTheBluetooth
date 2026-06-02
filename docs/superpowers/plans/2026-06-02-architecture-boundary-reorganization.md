# Architecture Boundary Reorganization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize MixTheBluetooth into stable architecture boundaries (`runtime/`, `api/`, `application/`, `driver/`, `persistence/`, `ui/`), reduce historical UI/transport/static-data mixing, keep only `static-lan` and `dev` environments, encrypt session/settings persistence, and make CGM replay upload flow testable and understandable.

**Architecture:** Runtime composition creates a single `AppContainer`, reads build env, wires API contracts to application services, and injects driver implementations. UI depends on API contracts and CGM feature classes. Application services orchestrate business flows. Driver capability interfaces describe primitive IO, while driver implementations contain Android, Retrofit, filesystem, codec, logging, and clock details. Persistence is a visible implementation area backed by encrypted preferences.

**Tech Stack:** Android Java, Gradle, Retrofit/OkHttp, Gson, AndroidX Security Crypto, JUnit 4, MockWebServer, Python LAN static backend tests, compile-time architecture boundary script.

---

## Design Guardrails

- [ ] Keep each migration step independently buildable; do not move all packages first and fix imports later.
- [ ] Prefer adapter/shim classes for one step when needed, then delete shims in the following step.
- [ ] After every phase, run the listed unit tests and at least one assemble command before committing.
- [ ] Preserve user-visible CGM behavior while changing package boundaries.
- [ ] Do not keep app-internal fake static services; `static-lan` must use real HTTP requests to the LAN backend.
- [ ] Treat UI shell deletion as final cleanup after capabilities have a home in API/application/driver.
- [ ] Keep `SessionStore.Store` style testability by preserving a small storage abstraction.

---

## Target Package Map

Final Java package root:

```text
app/src/main/java/com/hc/mixthebluetooth/
  MixBluetoothApplication.java

  runtime/
    AppApiBootstrap.java
    AppContainer.java
    EnvConfig.java

  api/
    AppApi.java
    ApiCallback.java
    CallResult.java
    auth/AuthService.java
    cgm/CgmWorkflow.java
    cgm/CgmResult.java
    device/DeviceGateway.java
    file/FileUploadService.java
    persistence/SessionStore.java
    persistence/SettingsStore.java
    codec/TextCodec.java
    log/AppLogger.java

  application/
    auth/DefaultAuthService.java
    cgm/DefaultCgmWorkflow.java
    cgm/DefaultCgmJobService.java
    cgm/model/CgmJobResult.java
    cgm/model/CgmUploadPayload.java
    device/DefaultDeviceGateway.java
    file/DefaultFileUploadService.java

  driver/
    capability/
      BluetoothTransport.java
      FileRecorder.java
      HttpTransport.java
      ReplaySource.java
      Clock.java
    implementation/
      bluetooth/AndroidBluetoothTransport.java
      codec/AnalysisTextCodec.java
      file/AndroidFileRecorder.java
      file/AssetReplaySource.java
      http/RetrofitHttpTransport.java
      http/RetrofitServerClient.java
      http/endpoint/BioAiEndpoints.java
      http/dto/ServerDtos.java
      http/dto/ServerResponse.java
      log/AndroidAppLogger.java
      time/SystemClock.java

  persistence/
    EncryptedPreferencesStore.java
    EncryptedSessionStore.java
    EncryptedSettingsStore.java

  ui/
    auth/LoginActivity.java
    auth/RegisterActivity.java
    intro/IntroActivity.java
    main/MainActivity.java
    main/DeviceListAdapter.java
    main/CollectBluetoothDialog.java
    main/DeviceFilterDialog.java
    cgm/CgmActivity.java
    cgm/CgmFragment.java
    cgm/CgmController.java
    cgm/CgmProfile.java
    cgm/CgmCommands.java
    cgm/CgmWidgets.java
    cgm/MessageAdapter.java
    cgm/MessageItem.java
    shared/view/*
    shared/dialog/*
```

Removed final packages:

```text
activity/
fragment/
impl/
local/
remote/
staticdata/
storage/
uni/
customView/
recyclerData/
```

Deletion is staged. A package is deleted only after all imports and manifest references are moved.

---

## Phase 0: Baseline Safety Net

### Task 0.1: Confirm Branch And Dirty State

- [ ] Run:

```powershell
git branch --show-current
git status --short
```

- [ ] Expected branch:

```text
dev-1.7
```

- [ ] Record unrelated dirty files before changes. Current known unrelated files:

```text
M .codegraph/daemon.pid
M app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java
```

- [ ] Do not revert either file unless the user explicitly requests it.

### Task 0.2: Run Baseline Verification

- [ ] Run:

```powershell
python tools/test_lan_static_backend.py
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

- [ ] Expected:
  - Python LAN static backend tests pass.
  - JVM tests pass or current failures are documented with exact failing test names.
  - `dev` debug APK compiles.

- [ ] If baseline has failures, create `docs/superpowers/plans/2026-06-02-architecture-boundary-reorganization-baseline.md` with:
  - command
  - failure message
  - whether the failure is pre-existing
  - whether this plan can continue safely

- [ ] Commit only if a baseline note file was created:

```powershell
git add docs/superpowers/plans/2026-06-02-architecture-boundary-reorganization-baseline.md
git commit -m "docs: record architecture refactor baseline"
```

---

## Phase 1: Environment Contract, No App-Internal Static Mode

### Goal

Keep exactly two app environments:

```text
static-lan = real HTTP send/receive, backend returns fixed data
dev        = real HTTP send/receive, backend computes actual results
```

`loop`, `prod`, app-side `static`, and app-side static fixtures are removed from runtime decisions.

### Task 1.1: Replace Env Files

- [ ] Create:

```text
app/config/env.static-lan.properties
```

Required content:

```properties
api.env=static-lan
api.baseUrl=http://192.168.6.7:18080/
api.debug=true
api.remoteName=static-lan
api.networkEnabled=true
```

- [ ] Keep:

```text
app/config/env.dev.properties
```

- [ ] Remove old env config files that represent obsolete modes after confirming no Gradle task imports them:

```text
app/config/env.static.properties
app/config/env.loop.properties
app/config/env.prod.properties
```

- [ ] Run:

```powershell
rg -n "apiEnv|env\.static|env\.loop|env\.prod|static-lan|BuildConfig\.API_ENV|BuildConfig\.API_BASE_URL" app build.gradle settings.gradle
```

- [ ] Expected:
  - `static-lan` appears in config and docs.
  - No active build path references `env.static`, `env.loop`, or `env.prod`.

### Task 1.2: Simplify EnvConfig

- [ ] Move old env model into:

```text
app/src/main/java/com/hc/mixthebluetooth/runtime/EnvConfig.java
```

- [ ] Implement with exactly these public semantics:

```java
package com.hc.mixthebluetooth.runtime;

import androidx.annotation.NonNull;

public final class EnvConfig {
    public static final String STATIC_LAN = "static-lan";
    public static final String DEV = "dev";

    private final String env;
    private final String baseUrl;
    private final boolean debug;
    private final String remoteName;
    private final boolean networkEnabled;

    public EnvConfig(
            @NonNull String env,
            @NonNull String baseUrl,
            boolean debug,
            @NonNull String remoteName,
            boolean networkEnabled
    ) {
        this.env = normalizeEnv(env);
        this.baseUrl = baseUrl;
        this.debug = debug;
        this.remoteName = remoteName;
        this.networkEnabled = networkEnabled;
    }

    @NonNull
    public String env() {
        return env;
    }

    @NonNull
    public String baseUrl() {
        return baseUrl;
    }

    public boolean debug() {
        return debug;
    }

    @NonNull
    public String remoteName() {
        return remoteName;
    }

    public boolean networkEnabled() {
        return networkEnabled;
    }

    public boolean isStaticLan() {
        return STATIC_LAN.equals(env);
    }

    public boolean isDev() {
        return DEV.equals(env);
    }

    @NonNull
    public static String normalizeEnv(@NonNull String rawEnv) {
        String value = rawEnv.trim().toLowerCase();
        if (STATIC_LAN.equals(value) || DEV.equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported api.env: " + rawEnv);
    }
}
```

- [ ] Remove `isStatic()`, `isRealHttp()`, and `withRemote()` from active code.

### Task 1.3: Delete Static Service Wiring

- [ ] In the future runtime bootstrap file, both envs must wire:
  - Retrofit HTTP driver
  - default application services
  - same upload/poll code path

- [ ] Delete after imports are gone:

```text
app/src/main/java/com/hc/mixthebluetooth/impl/auth/StaticAuthService.java
app/src/main/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmService.java
app/src/main/java/com/hc/mixthebluetooth/staticdata/
```

- [ ] Replace old static service tests with HTTP-backed tests. Delete:

```text
app/src/test/java/com/hc/mixthebluetooth/impl/auth/StaticAuthServiceTest.java
app/src/test/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmServiceTest.java
app/src/test/java/com/hc/mixthebluetooth/impl/StaticLoopServiceChainTest.java
```

- [ ] Add one env test:

```text
app/src/test/java/com/hc/mixthebluetooth/runtime/EnvConfigTest.java
```

Test cases:

```java
@Test public void staticLanIsAccepted() { ... }
@Test public void devIsAccepted() { ... }
@Test public void staticIsRejected() { ... }
@Test public void prodIsRejected() { ... }
@Test public void loopIsRejected() { ... }
```

### Task 1.4: Verify Phase 1

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.runtime.EnvConfigTest"
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
python tools/test_lan_static_backend.py
```

- [ ] Expected:
  - Both env APK builds compile.
  - No app-side static service class remains.
  - LAN backend test remains green.

- [ ] Commit:

```powershell
git add app docs tools
git commit -m "refactor: limit api environments to static-lan and dev"
```

---

## Phase 2: Persistence Contracts And Encrypted Stores

### Goal

Move all SharedPreferences ownership into a visible persistence area:

```text
api/persistence/SessionStore.java
api/persistence/SettingsStore.java
persistence/EncryptedPreferencesStore.java
persistence/EncryptedSessionStore.java
persistence/EncryptedSettingsStore.java
```

Session and settings are kept together because both are preference-backed persistence. Session data must be encrypted. Settings should also use the encrypted store for consistency and future simplicity.

### Task 2.1: Add AndroidX Security Crypto

- [ ] Modify:

```text
app/build.gradle
```

- [ ] Add dependency:

```groovy
implementation 'androidx.security:security-crypto:1.0.0'
```

- [ ] Run dependency resolution:

```powershell
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
```

- [ ] Expected:
  - `androidx.security:security-crypto:1.0.0` resolves.
  - If dependency resolution fails because the repository cannot resolve this version, replace it with the nearest stable AndroidX Security Crypto version available in Google Maven and record the version in this plan file before continuing.

### Task 2.2: Create Persistence API Contracts

- [ ] Create:

```text
app/src/main/java/com/hc/mixthebluetooth/api/persistence/SessionStore.java
```

Required contract:

```java
package com.hc.mixthebluetooth.api.persistence;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface SessionStore {
    void save(@NonNull Session session);
    @Nullable Session load();
    void clear();

    final class Session {
        @NonNull public final String token;
        @NonNull public final String account;
        @Nullable public final String userId;
        @Nullable public final String userName;

        public Session(
                @NonNull String token,
                @NonNull String account,
                @Nullable String userId,
                @Nullable String userName
        ) {
            this.token = token;
            this.account = account;
            this.userId = userId;
            this.userName = userName;
        }
    }
}
```

- [ ] Create:

```text
app/src/main/java/com/hc/mixthebluetooth/api/persistence/SettingsStore.java
```

Required contract:

```java
package com.hc.mixthebluetooth.api.persistence;

import androidx.annotation.NonNull;

public interface SettingsStore {
    @NonNull String textEncoding();
    void setTextEncoding(@NonNull String encoding);
    boolean firstLaunch();
    void setFirstLaunch(boolean firstLaunch);
    boolean deviceFilterEnabled();
    void setDeviceFilterEnabled(boolean enabled);
}
```

### Task 2.3: Add Preferences Capability

- [ ] Create:

```text
app/src/main/java/com/hc/mixthebluetooth/driver/capability/PreferencesStore.java
```

Required contract:

```java
package com.hc.mixthebluetooth.driver.capability;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface PreferencesStore {
    void putString(@NonNull String key, @Nullable String value);
    @Nullable String getString(@NonNull String key, @Nullable String defaultValue);
    void putBoolean(@NonNull String key, boolean value);
    boolean getBoolean(@NonNull String key, boolean defaultValue);
    void remove(@NonNull String key);
    void clear();
}
```

### Task 2.4: Implement Encrypted Preferences

- [ ] Create:

```text
app/src/main/java/com/hc/mixthebluetooth/persistence/EncryptedPreferencesStore.java
```

Implementation requirements:

- Use `MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()`.
- Use `EncryptedSharedPreferences.create(...)`.
- Use `PrefKeyEncryptionScheme.AES256_SIV`.
- Use `PrefValueEncryptionScheme.AES256_GCM`.
- Use `applicationContext`, not an Activity context.
- Implement `PreferencesStore`.
- Keep all writes synchronous with `apply()` unless a caller needs immediate disk flush; current session/settings writes do not.

- [ ] Create:

```text
app/src/main/java/com/hc/mixthebluetooth/persistence/EncryptedSessionStore.java
app/src/main/java/com/hc/mixthebluetooth/persistence/EncryptedSettingsStore.java
```

- [ ] `EncryptedSessionStore` stores keys:

```text
session.token
session.account
session.user_id
session.user_name
```

- [ ] `load()` returns `null` unless both token and account are present and non-empty.

- [ ] `EncryptedSettingsStore` stores keys:

```text
settings.text_encoding
settings.first_launch
settings.device_filter_enabled
```

- [ ] Defaults:

```text
textEncoding = "GBK"
firstLaunch = true
deviceFilterEnabled = true
```

### Task 2.5: Migrate Old SessionStore And Storage Callers

- [ ] Replace imports from:

```text
com.hc.mixthebluetooth.local.SessionStore
com.hc.mixthebluetooth.storage.Storage
```

to:

```text
com.hc.mixthebluetooth.api.persistence.SessionStore
com.hc.mixthebluetooth.api.persistence.SettingsStore
```

- [ ] Update likely callers:

```text
app/src/main/java/com/hc/mixthebluetooth/activity/LoginActivity.java
app/src/main/java/com/hc/mixthebluetooth/activity/AccountRegisterActivity.java
app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java
app/src/main/java/com/hc/mixthebluetooth/fragment/FragmentSetting.java
app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java
```

- [ ] After migration, delete:

```text
app/src/main/java/com/hc/mixthebluetooth/local/SessionStore.java
```

- [ ] Do not delete `storage/Storage.java` until Phase 8 removes old fragment shells or all imports are gone.

### Task 2.6: Add Persistence Tests

- [ ] Create:

```text
app/src/test/java/com/hc/mixthebluetooth/persistence/MemoryPreferencesStore.java
app/src/test/java/com/hc/mixthebluetooth/persistence/EncryptedSessionStoreTest.java
app/src/test/java/com/hc/mixthebluetooth/persistence/EncryptedSettingsStoreTest.java
```

`MemoryPreferencesStore` implements `PreferencesStore` with a `HashMap`.

Session tests:

```java
@Test public void loadReturnsNullWhenEmpty() { ... }
@Test public void saveAndLoadRoundTripsTokenAndAccount() { ... }
@Test public void clearRemovesSession() { ... }
@Test public void incompleteSessionIsIgnored() { ... }
```

Settings tests:

```java
@Test public void defaultsAreStable() { ... }
@Test public void textEncodingRoundTrips() { ... }
@Test public void firstLaunchRoundTrips() { ... }
@Test public void deviceFilterRoundTrips() { ... }
```

### Task 2.7: Verify Phase 2

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.persistence.*"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

- [ ] Expected:
  - Session/settings contract tests pass.
  - App compiles with encrypted persistence dependency.
  - No import of `local.SessionStore` remains:

```powershell
rg -n "local\.SessionStore|new SessionStore|storage\.Storage" app/src/main/java
```

`storage.Storage` may still appear only in old UI fragments pending Phase 8.

- [ ] Commit:

```powershell
git add app
git commit -m "refactor: move preferences into encrypted persistence stores"
```

---

## Phase 3: API Package As Public Contract Layer

### Goal

`api/` contains only contracts and small result/callback types. Retrofit DTOs, Android implementations, local files, and static fixtures must not live here.

### Task 3.1: Normalize Result Types

- [ ] Keep:

```text
app/src/main/java/com/hc/mixthebluetooth/api/CallResult.java
app/src/main/java/com/hc/mixthebluetooth/api/ApiCallback.java
```

- [ ] Make `CallResult` status explicit:

```java
public enum Status {
    OK,
    PENDING,
    ERROR
}
```

- [ ] Keep negative error codes to avoid collision with backend codes:

```java
public static final int UNKNOWN = -1;
public static final int NETWORK = -100;
public static final int EMPTY_RESPONSE = -101;
public static final int EMPTY_DATA = -102;
public static final int LOCAL_FILE_NOT_FOUND = -200;
public static final int DEVICE_REPLAY_INCOMPLETE = -300;
```

- [ ] Required behavior:
  - `ok(data)` has `Status.OK`, code `0`.
  - `pending(message)` has `Status.PENDING`, code `0`.
  - `error(code, message)` has `Status.ERROR`, code is caller supplied.
  - Helper methods `isOk()`, `isPending()`, `isError()` check `Status`, not code.

- [ ] Update `CallResultTest`:

```java
@Test public void pendingAndOkShareCodeButDifferentStatus() { ... }
@Test public void negativeCodesAreOnlyForLocalErrors() { ... }
```

### Task 3.2: Define API Contracts

- [ ] Ensure these files exist under `api/`:

```text
api/auth/AuthService.java
api/file/FileUploadService.java
api/cgm/CgmWorkflow.java
api/cgm/CgmResult.java
api/device/DeviceGateway.java
api/codec/TextCodec.java
api/log/AppLogger.java
api/persistence/SessionStore.java
api/persistence/SettingsStore.java
```

- [ ] Move old service contracts from `api/device`, `api/file`, `api/cgm`, and `api/auth` into this shape without changing method behavior yet.

- [ ] `CgmWorkflow` public contract:

```java
package com.hc.mixthebluetooth.api.cgm;

import androidx.annotation.NonNull;
import com.hc.mixthebluetooth.api.ApiCallback;

public interface CgmWorkflow {
    void onDeviceLine(@NonNull String line, @NonNull ApiCallback<CgmResult> callback);
    void reset();
}
```

- [ ] Create `api/cgm/CgmResult.java` as the UI-facing CGM result contract. Application services may use internal models, but `DefaultCgmWorkflow` must map final backend job data into `CgmResult` before invoking UI callbacks.

### Task 3.3: Keep AppApi As Compatibility Facade

- [ ] Keep:

```text
api/AppApi.java
```

- [ ] Its only responsibility in this phase:
  - hold installed API contracts
  - throw `IllegalStateException("AppApi is not initialized")` when accessed too early
  - expose `auth()`, `fileUpload()`, `cgmWorkflow()`, `deviceGateway()`, `sessionStore()`, `settingsStore()`, `logger()`

- [ ] Remove obsolete accessors that point at deleted static/loop services.

- [ ] Add:

```text
app/src/test/java/com/hc/mixthebluetooth/api/AppApiTest.java
```

Test cases:

```java
@Test public void accessBeforeInstallFails() { ... }
@Test public void installMakesContractsAvailable() { ... }
@Test public void reinstallReplacesContractsForTests() { ... }
```

### Task 3.4: Verify Phase 3

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.api.*"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
```

- [ ] Expected:
  - `api/` has no imports from `driver.implementation`, `persistence.Encrypted*`, `remote`, `impl`, `activity`, `fragment`, or Android Activity classes.

- [ ] Verify:

```powershell
rg -n "com\.hc\.mixthebluetooth\.(driver\.implementation|persistence\.Encrypted|remote|impl|activity|fragment|uni|storage)" app/src/main/java/com/hc/mixthebluetooth/api
```

Expected: no matches.

- [ ] Commit:

```powershell
git add app
git commit -m "refactor: keep api package as contract layer"
```

---

## Phase 4: Driver Capabilities And Primitive Implementations

### Goal

Create the lower-level primitive capabilities separately from application business logic:

```text
driver/capability/
driver/implementation/
```

This phase moves primitive IO without changing the CGM workflow yet.

### Task 4.1: Add Capability Interfaces

- [ ] Create:

```text
app/src/main/java/com/hc/mixthebluetooth/driver/capability/BluetoothTransport.java
app/src/main/java/com/hc/mixthebluetooth/driver/capability/FileRecorder.java
app/src/main/java/com/hc/mixthebluetooth/driver/capability/ReplaySource.java
app/src/main/java/com/hc/mixthebluetooth/driver/capability/Clock.java
app/src/main/java/com/hc/mixthebluetooth/driver/capability/HttpTransport.java
```

Required signatures:

```java
public interface BluetoothTransport {
    boolean isConnected();
    void sendBytes(@NonNull byte[] bytes);
    void sendText(@NonNull String text, @NonNull String charsetName);
    void disconnect();
}
```

```java
public interface FileRecorder {
    void appendLine(@NonNull String line);
    @NonNull java.io.File finish();
    void reset();
}
```

```java
public interface ReplaySource {
    @NonNull java.util.List<String> readLines() throws java.io.IOException;
}
```

```java
public interface Clock {
    long nowMillis();
}
```

```java
public interface HttpTransport {
    @NonNull okhttp3.OkHttpClient okHttpClient();
    @NonNull retrofit2.Retrofit retrofit();
}
```

### Task 4.2: Move File Replay/Recording

- [ ] Move old local implementations:

```text
old: local/DeviceDataRecorder.java
new: driver/implementation/file/AndroidFileRecorder.java

old: local/DeviceReplaySample.java
new: driver/implementation/file/AssetReplaySource.java
```

- [ ] Preserve behavior:
  - line append order
  - newline format
  - completed file path behavior used by upload code
  - asset replay sample parsing used in tests

- [ ] Update tests:

```text
old: app/src/test/java/com/hc/mixthebluetooth/local/DeviceReplaySampleTest.java
new: app/src/test/java/com/hc/mixthebluetooth/driver/implementation/file/AssetReplaySourceTest.java
new: app/src/test/java/com/hc/mixthebluetooth/driver/implementation/file/AndroidFileRecorderTest.java
```

`AndroidFileRecorderTest` may use a JVM-safe constructor that accepts a base `File` directory rather than Android `Context`.

### Task 4.3: Move Text Codec

- [ ] Create:

```text
app/src/main/java/com/hc/mixthebluetooth/api/codec/TextCodec.java
app/src/main/java/com/hc/mixthebluetooth/driver/implementation/codec/AnalysisTextCodec.java
```

- [ ] `AnalysisTextCodec` wraps current `uni/Codec` and `activity/tool/Analysis` behavior.

- [ ] Move tests:

```text
old: app/src/test/java/com/hc/mixthebluetooth/uni/CodecTest.java
new: app/src/test/java/com/hc/mixthebluetooth/driver/implementation/codec/AnalysisTextCodecTest.java
```

### Task 4.4: Move Logging And Clock

- [ ] Create:

```text
api/log/AppLogger.java
driver/implementation/log/AndroidAppLogger.java
driver/implementation/time/SystemClock.java
```

- [ ] Replace old trace logger calls with `AppLogger`.

- [ ] `SystemClock.nowMillis()` returns `System.currentTimeMillis()`.

### Task 4.5: Add Bluetooth Transport Adapter

- [ ] Create:

```text
driver/implementation/bluetooth/AndroidBluetoothTransport.java
```

- [ ] It adapts old Bluetooth holder code. If current code lives under `activity/single/HoldBluetooth`, keep that class temporarily but mark all usage outside the adapter for removal in Phase 8.

- [ ] Verify only this file imports old Bluetooth holder:

```powershell
rg -n "HoldBluetooth|BTPackage" app/src/main/java/com/hc/mixthebluetooth
```

Expected during this phase:
  - `AndroidBluetoothTransport`
  - old UI classes not yet moved

### Task 4.6: Verify Phase 4

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.driver.implementation.file.*"
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.driver.implementation.codec.*"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

- [ ] Expected:
  - Primitive driver tests pass.
  - App still compiles before UI migration.

- [ ] Commit:

```powershell
git add app
git commit -m "refactor: introduce driver capabilities and primitive implementations"
```

---

## Phase 5: HTTP Driver And Application Services

### Goal

Split backend endpoint details from business service orchestration:

```text
driver/implementation/http/* = Retrofit client, endpoint, DTO, transport
application/* = auth/file/cgm service behavior
```

### Task 5.1: Move HTTP Endpoint And DTO Files

- [ ] Move:

```text
old: remote/ServerClient.java
new: driver/implementation/http/RetrofitServerClient.java

old: remote/ServerEndpoints.java
new: driver/implementation/http/endpoint/BioAiEndpoints.java

old: remote/ServerResponse.java
new: driver/implementation/http/dto/ServerResponse.java

old: remote/ServerModels.java
new: driver/implementation/http/dto/ServerDtos.java
```

- [ ] Preserve JSON field names exactly.

- [ ] Preserve Retrofit paths exactly.

- [ ] Preserve `MockWebServer` test behavior from:

```text
app/src/test/java/com/hc/mixthebluetooth/remote/ServerEndpointsContractTest.java
app/src/test/java/com/hc/mixthebluetooth/remote/CgmJobRespParsingTest.java
```

- [ ] Move tests to:

```text
app/src/test/java/com/hc/mixthebluetooth/driver/implementation/http/ServerEndpointsContractTest.java
app/src/test/java/com/hc/mixthebluetooth/driver/implementation/http/CgmJobRespParsingTest.java
```

### Task 5.2: Add RetrofitHttpTransport

- [ ] Create:

```text
driver/implementation/http/RetrofitHttpTransport.java
```

- [ ] Implementation:
  - owns `OkHttpClient`
  - owns `Retrofit`
  - accepts `EnvConfig` and `AppLogger`
  - uses `env.baseUrl()`
  - enables verbose HTTP logging only when `env.debug()` is true

- [ ] `RetrofitServerClient` should become a small factory/helper, not a service locator.

### Task 5.3: Move Auth And File Services Into Application

- [ ] Move:

```text
old: impl/auth/DefaultAuthService.java
new: application/auth/DefaultAuthService.java

old: impl/file/DefaultFileService.java
new: application/file/DefaultFileUploadService.java
```

- [ ] Update contracts:

```text
api/auth/AuthService.java
api/file/FileUploadService.java
```

- [ ] Update tests:

```text
old: app/src/test/java/com/hc/mixthebluetooth/impl/auth/DefaultAuthServiceTest.java
new: app/src/test/java/com/hc/mixthebluetooth/application/auth/DefaultAuthServiceTest.java

old: app/src/test/java/com/hc/mixthebluetooth/impl/file/DefaultFileServiceTest.java
new: app/src/test/java/com/hc/mixthebluetooth/application/file/DefaultFileUploadServiceTest.java
```

- [ ] MockWebServer tests must assert:
  - correct HTTP method
  - correct path
  - token/account fields are included where required
  - network failure maps to `CallResult.NETWORK`
  - empty body maps to `CallResult.EMPTY_RESPONSE`

### Task 5.4: Move CGM Job Service Into Application

- [ ] Move:

```text
old: impl/cgm/DefaultCgmService.java
new: application/cgm/DefaultCgmJobService.java
```

- [ ] This class is responsible only for:
  - accepting completed replay file or upload payload
  - uploading to backend
  - polling backend job status
  - mapping DTO to domain result

- [ ] It must not:
  - consume Bluetooth lines
  - know UI widgets
  - know Activity/Fragment
  - own a recorder

- [ ] Create:

```text
application/cgm/model/CgmJobResult.java
application/cgm/model/CgmUploadPayload.java
```

- [ ] Update tests:

```text
old: app/src/test/java/com/hc/mixthebluetooth/impl/cgm/DefaultCgmServiceTest.java
new: app/src/test/java/com/hc/mixthebluetooth/application/cgm/DefaultCgmJobServiceTest.java
```

Required test cases:

```java
@Test public void uploadAndPollReturnsCompletedResult() { ... }
@Test public void uploadFailureReturnsError() { ... }
@Test public void uploadEmptyResponseReturnsEmptyResponseError() { ... }
@Test public void pollPendingThenCompletedReturnsResult() { ... }
@Test public void pollTimeoutReturnsDeviceReplayIncomplete() { ... }
@Test public void pollNetworkFailureReturnsNetworkError() { ... }
```

### Task 5.5: Verify Phase 5

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.driver.implementation.http.*"
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.application.*"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

- [ ] Verify no active source imports old `remote`:

```powershell
rg -n "com\.hc\.mixthebluetooth\.remote" app/src/main/java app/src/test/java
```

Expected: no matches.

- [ ] Delete old `remote/` after no matches remain.

- [ ] Commit:

```powershell
git add app
git commit -m "refactor: separate http driver from application services"
```

---

## Phase 6: CGM Workflow As The Bridge Between Device Lines And Backend Jobs

### Goal

Make the previously confusing path explicit:

```text
Bluetooth line
  -> CgmWorkflow.onDeviceLine()
  -> FileRecorder.appendLine()
  -> completion detector
  -> FileRecorder.finish()
  -> CgmJobService.uploadAndPoll()
  -> callback result
  -> CgmController renders
```

`DefaultCgmJobService.uploadAndPoll` uploads completed replay data and polls server job state. It no longer decides when a replay is complete.

### Task 6.1: Create Workflow Contract And Implementation

- [ ] Create:

```text
api/cgm/CgmWorkflow.java
application/cgm/DefaultCgmWorkflow.java
```

- [ ] `DefaultCgmWorkflow` dependencies:

```java
private final FileRecorder recorder;
private final CgmJobService cgmJobService;
private final AppLogger logger;
```

- [ ] Completion rule:
  - Reuse current `consumeLine` completion logic exactly at first.
  - Move it into a small private method or package-private `CgmReplayCompletionDetector`.
  - Add tests before simplifying the rule.

- [ ] `onDeviceLine(line, callback)` behavior:
  - append line to recorder
  - if not complete: callback receives `CallResult.pending("device replay pending")`
  - if complete: finish recorder, call `cgmJobService.uploadAndPoll(file, jobCallback)`, map `CgmJobResult` into `api.cgm.CgmResult`, then invoke the original callback
  - if local recorder file is missing: callback receives `LOCAL_FILE_NOT_FOUND`
  - if replay is incomplete at forced finish: callback receives `DEVICE_REPLAY_INCOMPLETE`

### Task 6.2: Add Workflow Tests With Fakes

- [ ] Create:

```text
app/src/test/java/com/hc/mixthebluetooth/application/cgm/DefaultCgmWorkflowTest.java
app/src/test/java/com/hc/mixthebluetooth/application/cgm/CgmReplayCompletionDetectorTest.java
```

- [ ] Test with fake `FileRecorder`, fake `CgmJobService`, and capturing callback.

Required workflow tests:

```java
@Test public void incompleteLineAppendsAndReturnsPending() { ... }
@Test public void completionLineFinishesFileAndStartsUploadPoll() { ... }
@Test public void uploadResultIsReturnedToOriginalCallback() { ... }
@Test public void resetClearsRecorderAndCompletionState() { ... }
@Test public void recorderFailureReturnsLocalFileError() { ... }
```

- [ ] Required detector tests:

```java
@Test public void sampleReplayDoesNotCompleteBeforeLastLine() { ... }
@Test public void sampleReplayCompletesOnCurrentKnownTerminator() { ... }
@Test public void emptyLineDoesNotCompleteReplay() { ... }
```

Use `app/src/test/resources/device/device_replay_sample.txt`.

### Task 6.3: Replace UI Direct Upload Calls

- [ ] Replace any UI/controller direct call to old `DefaultCgmService.uploadAndPoll`.

- [ ] `CgmController` should call only:

```java
AppApi.cgmWorkflow().onDeviceLine(line, callback);
```

- [ ] Verify:

```powershell
rg -n "uploadAndPoll|DefaultCgmService|DefaultCgmJobService" app/src/main/java/com/hc/mixthebluetooth
```

Expected:
  - `uploadAndPoll` only in application CGM job service and tests.
  - UI/controller imports only `api.cgm.CgmWorkflow` or `AppApi`.

### Task 6.4: Verify Phase 6

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.application.cgm.*"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
```

- [ ] Manual static-lan smoke:
  - Start backend:

```powershell
python tools/lan_static_backend.py --host 0.0.0.0 --port 18080
```

  - Build/install `static-lan`.
  - Connect device or replay sample.
  - Confirm app logs show upload request then poll request.
  - Confirm UI receives completed CGM result.

- [ ] Commit:

```powershell
git add app
git commit -m "refactor: isolate cgm workflow from backend job polling"
```

---

## Phase 7: Runtime Composition And App Container

### Goal

Move startup wiring into runtime:

```text
runtime/AppApiBootstrap.java
runtime/AppContainer.java
runtime/EnvConfig.java
```

The runtime reads BuildConfig, creates concrete drivers, creates application services, and installs contracts into `AppApi`.

### Task 7.1: Create AppContainer

- [ ] Create:

```text
app/src/main/java/com/hc/mixthebluetooth/runtime/AppContainer.java
```

Required fields:

```java
public final class AppContainer {
    public final EnvConfig envConfig;
    public final AuthService authService;
    public final FileUploadService fileUploadService;
    public final CgmWorkflow cgmWorkflow;
    public final DeviceGateway deviceGateway;
    public final SessionStore sessionStore;
    public final SettingsStore settingsStore;
    public final TextCodec textCodec;
    public final AppLogger logger;
}
```

- [ ] Constructor must receive every field explicitly and assign it.

### Task 7.2: Move Bootstrap

- [ ] Move:

```text
old: impl/AppApiBootstrap.java
new: runtime/AppApiBootstrap.java
```

- [ ] `init(Application application)` behavior:
  - builds `EnvConfig` from `BuildConfig.API_ENV`, `BuildConfig.API_BASE_URL`, `BuildConfig.API_DEBUG`, `BuildConfig.API_REMOTE_NAME`, `BuildConfig.API_NETWORK_ENABLED`
  - creates `AndroidAppLogger`
  - creates encrypted stores with application context
  - creates `RetrofitHttpTransport`
  - creates endpoint
  - creates application services
  - creates `DefaultCgmWorkflow`
  - installs all contracts into `AppApi`

- [ ] No branch may instantiate static services.

- [ ] Add package-private factory method for JVM tests:

```java
static AppContainer createForTest(
        EnvConfig envConfig,
        PreferencesStore preferencesStore,
        HttpTransport httpTransport,
        BluetoothTransport bluetoothTransport,
        FileRecorder fileRecorder,
        AppLogger logger
) { ... }
```

If Java visibility makes this awkward, create `AppContainerFactory` in runtime with the same method. The point is to test wiring without Android encrypted preferences.

### Task 7.3: Update Application Startup

- [ ] Modify:

```text
MixBluetoothApplication.java
```

- [ ] Import:

```java
import com.hc.mixthebluetooth.runtime.AppApiBootstrap;
```

- [ ] Keep startup:

```java
AppApiBootstrap.init(this);
```

### Task 7.4: Add Runtime Wiring Tests

- [ ] Create:

```text
app/src/test/java/com/hc/mixthebluetooth/runtime/AppApiBootstrapTest.java
```

Required tests:

```java
@Test public void staticLanUsesHttpBackedServices() { ... }
@Test public void devUsesHttpBackedServices() { ... }
@Test public void unsupportedEnvThrows() { ... }
@Test public void containerInstallsAllAppApiContracts() { ... }
```

- [ ] Tests must assert service class names do not contain:

```text
Static
Loop
```

### Task 7.5: Verify Phase 7

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.runtime.*"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

- [ ] Verify old bootstrap package is gone:

```powershell
rg -n "impl\.AppApiBootstrap|com\.hc\.mixthebluetooth\.impl" app/src/main/java
```

Expected after this phase:
  - no `impl.AppApiBootstrap`
  - `impl` imports may remain only if Phase 5 did not fully migrate a legacy class; if present, resolve before committing

- [ ] Commit:

```powershell
git add app
git commit -m "refactor: move dependency wiring into runtime container"
```

---

## Phase 8: UI Package Migration And Legacy Shell Removal

### Goal

Move Android pages/components into `ui/`, keep only active screens, and delete old fragment shells after their capabilities have moved into API/application/driver/persistence.

### Task 8.1: Move Auth UI

- [ ] Move:

```text
old: activity/LoginActivity.java
new: ui/auth/LoginActivity.java

old: activity/AccountRegisterActivity.java
new: ui/auth/RegisterActivity.java
```

- [ ] Update AndroidManifest activity names.

- [ ] Update imports and layout references.

- [ ] Verify no references to `AccountRegisterActivity` remain:

```powershell
rg -n "AccountRegisterActivity" app/src/main
```

Expected: no matches.

### Task 8.2: Move Intro And Main UI

- [ ] Move:

```text
old: activity/IntroActivity.java
new: ui/intro/IntroActivity.java

old: activity/MainActivity.java
new: ui/main/MainActivity.java

old: recyclerData/MainRecyclerAdapter.java
new: ui/main/DeviceListAdapter.java

old: customView/dialog/CollectBluetooth.java
new: ui/main/CollectBluetoothDialog.java

old: customView/PopWindowMain.java
new: ui/main/DeviceFilterDialog.java
```

- [ ] Update AndroidManifest and XML references.

- [ ] Main UI may use:
  - `api.device.DeviceGateway`
  - `api.persistence.SettingsStore`
  - `api.log.AppLogger`

- [ ] Main UI must not import:

```text
remote
driver.implementation.http
persistence.Encrypted*
impl
staticdata
```

### Task 8.3: Move And Simplify CGM UI

- [ ] Move:

```text
old: activity/CommunicationActivity.java
new: ui/cgm/CgmActivity.java

old: fragment/UniFragment.java
new: ui/cgm/CgmFragment.java

old: uni/Controller.java
new: ui/cgm/CgmController.java

old: uni/profile/cgm/CgmProfile.java
new: ui/cgm/CgmProfile.java

old: uni/Commands.java or LegacyCgm command holder
new: ui/cgm/CgmCommands.java

old: uni/Widgets.java
new: ui/cgm/CgmWidgets.java

old: recyclerData/FragmentMessAdapter.java
new: ui/cgm/MessageAdapter.java

old: recyclerData/itemHolder/FragmentMessageItem.java
new: ui/cgm/MessageItem.java
```

- [ ] Remove tab/page registration for:

```text
FragmentCustom
FragmentIonAnalysis
FragmentSetting
FragmentLog
```

- [ ] Preserve capabilities from deleted fragments:
  - encoding format moves to `SettingsStore`
  - custom send moves to `DeviceGateway.sendText/sendBytes`
  - log moves to `AppLogger`; no separate debug page

- [ ] `CgmController` may call:

```text
AppApi.cgmWorkflow()
AppApi.deviceGateway()
AppApi.settingsStore()
AppApi.textCodec()
AppApi.logger()
```

- [ ] `CgmController` must not call:

```text
DefaultCgmJobService
Retrofit*
ServerEndpoints
Encrypted*
HoldBluetooth directly
```

### Task 8.4: Move Shared Views And Dialogs

- [ ] Move still-used view/dialog classes:

```text
customView/CircleProgressView.java        -> ui/shared/view/CircleProgressView.java
customView/CheckBoxSample.java            -> ui/shared/view/CheckBoxSample.java
customView/ChartMarkerView.java           -> ui/shared/view/ChartMarkerView.java
customView/UnderlineTextView.java         -> ui/shared/view/UnderlineTextView.java
customView/NumPickView.java               -> ui/shared/view/NumPickView.java
customView/CustomButtonView.java          -> ui/shared/view/CustomButtonView.java
customView/dialog/SetMtu.java             -> ui/shared/dialog/SetMtu.java
customView/dialog/SetButton.java          -> ui/shared/dialog/SetButton.java
customView/dialog/PermissionHint.java     -> ui/shared/dialog/PermissionHint.java
customView/dialog/InvalidHint.java        -> ui/shared/dialog/InvalidHint.java
customView/dialog/HintHID.java            -> ui/shared/dialog/HintHID.java
```

- [ ] Before moving each class, verify it is still referenced:

```powershell
rg -n "<ClassName>" app/src/main
```

- [ ] If a class has no references and no XML references, delete it instead of moving.

### Task 8.5: Delete Dead UI Shells

- [ ] Delete old screens:

```text
activity/DebugActivity.java
activity/SendFileActivity.java
activity/HIDActivity.java
fragment/FragmentCustom.java
fragment/FragmentIonAnalysis.java
fragment/FragmentSetting.java
fragment/FragmentLog.java
```

- [ ] Remove manifest entries for deleted activities.

- [ ] Delete adapters/items used only by deleted screens:

```text
recyclerData/FragmentLogAdapter.java
recyclerData/itemHolder/FragmentLogItem.java
recyclerData/FileRecyclerAdapter.java
```

- [ ] Delete layouts/resources used only by deleted screens after a compile-driven check:

```powershell
rg -n "activity_debug|activity_send_file|activity_hid|fragment_custom|fragment_ion|fragment_setting|fragment_log" app/src/main
```

- [ ] Remove those XML files only after no active Java/Kotlin/XML references remain.

### Task 8.6: Update UI Tests

- [ ] Move tests:

```text
old: app/src/test/java/com/hc/mixthebluetooth/uni/CommandsTest.java
new: app/src/test/java/com/hc/mixthebluetooth/ui/cgm/CgmCommandsTest.java

old: app/src/test/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmProfileTest.java
new: app/src/test/java/com/hc/mixthebluetooth/ui/cgm/CgmProfileTest.java

old: app/src/test/java/com/hc/mixthebluetooth/uni/ProfilesTest.java
new: app/src/test/java/com/hc/mixthebluetooth/ui/cgm/ProfilesTest.java

old: app/src/test/java/com/hc/mixthebluetooth/uni/WidgetsTest.java
new: app/src/test/java/com/hc/mixthebluetooth/ui/cgm/CgmWidgetsTest.java
```

- [ ] Add controller test:

```text
app/src/test/java/com/hc/mixthebluetooth/ui/cgm/CgmControllerTest.java
```

Required tests:

```java
@Test public void incomingDeviceLineIsSentToWorkflow() { ... }
@Test public void workflowResultUpdatesMessageModel() { ... }
@Test public void sendTextUsesDeviceGatewayAndSettingsEncoding() { ... }
```

### Task 8.7: Verify Phase 8

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.ui.cgm.*"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

- [ ] Verify old UI package imports are gone:

```powershell
rg -n "com\.hc\.mixthebluetooth\.(activity|fragment|customView|recyclerData|uni)" app/src/main/java app/src/test/java
```

Expected: no matches.

- [ ] Commit:

```powershell
git add app
git commit -m "refactor: consolidate android ui into feature packages"
```

---

## Phase 9: Device Gateway And Bluetooth Boundary Cleanup

### Goal

Make UI and application use a device-facing API instead of direct Bluetooth implementation classes.

### Task 9.1: Define DeviceGateway

- [ ] Ensure:

```text
api/device/DeviceGateway.java
application/device/DefaultDeviceGateway.java
```

- [ ] Required contract:

```java
package com.hc.mixthebluetooth.api.device;

import androidx.annotation.NonNull;

public interface DeviceGateway {
    boolean isConnected();
    void sendText(@NonNull String text);
    void sendBytes(@NonNull byte[] bytes);
    void disconnect();
}
```

- [ ] `DefaultDeviceGateway` dependencies:

```java
BluetoothTransport bluetoothTransport;
TextCodec textCodec;
SettingsStore settingsStore;
AppLogger logger;
```

- [ ] Behavior:
  - `sendText` reads encoding from `SettingsStore`.
  - `sendText` delegates to `BluetoothTransport.sendText`.
  - `sendBytes` delegates to `BluetoothTransport.sendBytes`.
  - failed sends are logged via `AppLogger`.

### Task 9.2: Migrate Direct Bluetooth Usage

- [ ] Replace direct imports:

```text
HoldBluetooth
BTPackage
activity/single
```

with:

```text
api.device.DeviceGateway
driver.implementation.bluetooth.AndroidBluetoothTransport
```

- [ ] The only package allowed to import old Bluetooth implementation details is:

```text
driver/implementation/bluetooth/
```

- [ ] Delete old Bluetooth holder package only after the adapter no longer needs it. If the adapter still needs it because it wraps vendor/global behavior, move the holder classes under:

```text
driver/implementation/bluetooth/internal/
```

### Task 9.3: Add Device Gateway Tests

- [ ] Move/update:

```text
old: app/src/test/java/com/hc/mixthebluetooth/impl/device/DefaultDeviceDataServiceTest.java
new: app/src/test/java/com/hc/mixthebluetooth/application/device/DefaultDeviceGatewayTest.java
```

Required tests:

```java
@Test public void sendTextUsesSettingsEncoding() { ... }
@Test public void sendBytesDelegatesToBluetoothTransport() { ... }
@Test public void disconnectDelegatesToBluetoothTransport() { ... }
@Test public void isConnectedDelegatesToBluetoothTransport() { ... }
```

### Task 9.4: Verify Phase 9

- [ ] Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.application.device.*"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
```

- [ ] Verify:

```powershell
rg -n "HoldBluetooth|BTPackage|activity\.single" app/src/main/java app/src/test/java
```

Expected:
  - no UI/application matches
  - if matches remain, they are only in `driver/implementation/bluetooth/internal` or `AndroidBluetoothTransport`

- [ ] Commit:

```powershell
git add app
git commit -m "refactor: route device access through gateway contract"
```

---

## Phase 10: Architecture Boundary Test Script

### Goal

Add a repeatable, fast boundary check so future changes cannot silently reintroduce mixed layers.

### Task 10.1: Add Boundary Checker

- [ ] Create:

```text
tools/check_architecture_boundaries.py
```

Required behavior:

- Scan Java files under:

```text
app/src/main/java/com/hc/mixthebluetooth
```

- Fail if:
  - `api/` imports `activity`, `fragment`, `ui`, `application`, `driver.implementation`, `persistence.Encrypted`, `remote`, `impl`, `local`, `storage`, `staticdata`, or Android Activity classes.
  - `application/` imports `ui`, `activity`, `fragment`, `remote`, `impl`, `local`, `storage`, or `staticdata`.
  - `ui/` imports `remote`, `impl`, `local`, `storage`, `staticdata`, `driver.implementation.http`, or `persistence.Encrypted`.
  - any active source imports `staticdata`.
  - any active source imports obsolete packages after Phase 8.

Suggested script structure:

```python
from pathlib import Path
import re
import sys

ROOT = Path("app/src/main/java/com/hc/mixthebluetooth")

RULES = [
    ("api", re.compile(r"com\.hc\.mixthebluetooth\.(activity|fragment|ui|application|remote|impl|local|storage|staticdata|driver\.implementation|persistence\.Encrypted)")),
    ("application", re.compile(r"com\.hc\.mixthebluetooth\.(activity|fragment|ui|remote|impl|local|storage|staticdata)")),
    ("ui", re.compile(r"com\.hc\.mixthebluetooth\.(remote|impl|local|storage|staticdata|driver\.implementation\.http|persistence\.Encrypted)")),
]

def package_part(path: Path) -> str:
    return path.relative_to(ROOT).parts[0]

def main() -> int:
    failures = []
    for path in ROOT.rglob("*.java"):
        rel = path.relative_to(ROOT)
        top = package_part(path)
        text = path.read_text(encoding="utf-8")
        for scope, pattern in RULES:
            if top == scope and pattern.search(text):
                failures.append(f"{rel}: {scope} boundary imports forbidden package")
        if "com.hc.mixthebluetooth.staticdata" in text:
            failures.append(f"{rel}: staticdata import is forbidden")
    if failures:
        print("\n".join(failures))
        return 1
    print("architecture boundaries ok")
    return 0

if __name__ == "__main__":
    sys.exit(main())
```

- [ ] Adjust regex only to account for actual final package names, not to hide violations.

### Task 10.2: Add Boundary Check Documentation

- [ ] Add to this plan's final verification section and optionally project README if present:

```powershell
python tools/check_architecture_boundaries.py
```

### Task 10.3: Verify Phase 10

- [ ] Run:

```powershell
python tools/check_architecture_boundaries.py
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

- [ ] Expected:

```text
architecture boundaries ok
```

- [ ] Commit:

```powershell
git add tools app
git commit -m "test: enforce architecture package boundaries"
```

---

## Phase 11: Final Package Deletion And Import Sweep

### Goal

Remove historical package shells after all live code has moved.

### Task 11.1: Delete Empty Or Obsolete Packages

- [ ] Run:

```powershell
Get-ChildItem app/src/main/java/com/hc/mixthebluetooth -Directory | Select-Object Name
```

- [ ] Final allowed package directories:

```text
api
application
driver
persistence
runtime
ui
```

Plus root file:

```text
MixBluetoothApplication.java
```

- [ ] Delete only if empty or confirmed obsolete:

```text
activity
fragment
impl
local
remote
staticdata
storage
uni
customView
recyclerData
```

- [ ] Use PowerShell native deletion only after confirming resolved paths are under:

```text
E:\AndroidStudioProject\MixTheBluetooth\app\src\main\java\com\hc\mixthebluetooth
```

### Task 11.2: Resource Sweep

- [ ] Run:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
```

- [ ] If compile fails due to stale XML class references, update XML package names.

- [ ] Search for deleted screen resources:

```powershell
rg -n "DebugActivity|SendFileActivity|HIDActivity|FragmentCustom|FragmentIonAnalysis|FragmentSetting|FragmentLog|AccountRegisterActivity|CommunicationActivity" app/src/main
```

Expected: no matches.

- [ ] Delete resource files used only by removed screens.

### Task 11.3: Final Full Verification

- [ ] Run all automated checks:

```powershell
python tools/test_lan_static_backend.py
python tools/check_architecture_boundaries.py
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

- [ ] Expected:
  - Python LAN static backend tests pass.
  - Architecture boundary checker prints `architecture boundaries ok`.
  - JVM unit tests pass.
  - Both debug APK variants compile.

### Task 11.4: Manual Usability Smoke

- [ ] Run static LAN backend:

```powershell
python tools/lan_static_backend.py --host 0.0.0.0 --port 18080
```

- [ ] Build/install static-lan:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
```

- [ ] Manual checks:
  - App launches without crash.
  - Intro/main navigation reaches auth and CGM screens.
  - Login screen can submit to LAN backend.
  - Register screen can submit to LAN backend.
  - Device list screen still shows scanned devices when Bluetooth permission is granted.
  - CGM screen can send a command through `DeviceGateway`.
  - CGM replay lines produce pending UI updates before completion.
  - Completed replay triggers one upload and one or more poll requests.
  - Completed backend response renders on CGM UI.
  - App restart preserves encrypted session/settings.
  - Clearing session logs user out or returns to login as existing behavior expects.

- [ ] Capture logcat evidence:

```powershell
adb logcat -c
adb logcat | Select-String -Pattern "AppApiBootstrap|CgmWorkflow|DefaultCgmJobService|Retrofit"
```

- [ ] Stop logcat after observing:
  - env loaded as `static-lan`
  - upload request
  - poll request
  - completed CGM result

### Task 11.5: Final Commit

- [ ] Commit:

```powershell
git add app tools docs
git commit -m "refactor: complete architecture boundary reorganization"
```

---

## Risk Register And Mitigations

- [ ] **Risk: package moves break XML/manifest references.** Mitigation: after each UI move, run `assembleDebug`; do not wait until the end.
- [ ] **Risk: encrypted preferences cannot be JVM-tested directly.** Mitigation: test session/settings behavior through `PreferencesStore` memory implementation; compile Android encrypted implementation with both env builds.
- [ ] **Risk: static-lan accidentally becomes fake app-side static again.** Mitigation: delete static services and run MockWebServer/backend checks; runtime tests assert service class names do not contain `Static`.
- [ ] **Risk: CGM replay completion behavior changes during refactor.** Mitigation: detector tests use the real replay sample before workflow code is simplified.
- [ ] **Risk: UI old fragments contain hidden needed capabilities.** Mitigation: migrate encoding, custom send, and logging capabilities first; delete fragment shells only after API access exists.
- [ ] **Risk: driver implementation leaks into UI.** Mitigation: boundary script fails on forbidden imports.
- [ ] **Risk: large rename creates hard-to-review diff.** Mitigation: commit at every phase with behavior-focused tests.

---

## Final Acceptance Criteria

- [ ] Only these top-level source packages remain under `com.hc.mixthebluetooth`:

```text
api
application
driver
persistence
runtime
ui
```

- [ ] `static-lan` and `dev` are the only accepted env names.
- [ ] `static-lan` uses real HTTP requests to LAN backend.
- [ ] Session and settings are persisted through encrypted stores.
- [ ] UI no longer imports Retrofit, remote DTOs, static fixtures, old local stores, or encrypted implementation classes.
- [ ] CGM replay flow is readable through `DefaultCgmWorkflow` and `DefaultCgmJobService`.
- [ ] Legacy screens/fragments are removed from manifest and active package tree.
- [ ] Boundary checker passes.
- [ ] Full automated verification passes:

```powershell
python tools/test_lan_static_backend.py
python tools/check_architecture_boundaries.py
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=static-lan
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

- [ ] Manual static-lan smoke confirms app launch, auth, device list, CGM send, replay upload, poll, result render, and restart persistence.
