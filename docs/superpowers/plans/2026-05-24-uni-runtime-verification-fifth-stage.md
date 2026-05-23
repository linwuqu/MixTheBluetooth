# Uni Runtime Verification Fifth Stage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a verifiable runtime loop for auth, API routing, CGM fixture replay, txt generation, and upload without expanding product-facing scope.

**Architecture:** Keep API differences at the client/factory boundary, route login/register/debug through one `AuthRepository`, and make Diagnostics call the same Repository/UseCase paths as real UI. CGM mock behavior is represented as a fixture replay that feeds `CgmPlaybackRecorder`, not as a parallel file writer.

**Tech Stack:** Android Java, ViewBinding, Retrofit 2, OkHttp MockWebServer, JUnit 4.

---

## Scope And Guardrails

Spec:

```text
docs/superpowers/specs/2026-05-24-uni-runtime-verification-fifth-stage-design.md
```

Guardrails:

- Do not beautify UI in this stage.
- Do not introduce Hilt.
- Do not implement local CGM numeric calculation.
- Do not make `uni.Controller` import API classes.
- Keep diagnostics as a debug/dev tool and make it call real Repository/UseCase paths.

---

## File Map

Create:

```text
app/src/main/java/com/hc/mixthebluetooth/auth/AuthRepository.java
app/src/main/java/com/hc/mixthebluetooth/cgm/CgmReplayFixture.java
app/src/main/java/com/hc/mixthebluetooth/cgm/CgmReplayUploadUseCase.java
app/src/test/resources/cgm/cgm_playback_sample.txt
app/src/debug/assets/cgm/cgm_playback_sample.txt
app/src/test/java/com/hc/mixthebluetooth/auth/AuthRepositoryTest.java
app/src/test/java/com/hc/mixthebluetooth/cgm/CgmReplayFixtureTest.java
app/src/test/java/com/hc/mixthebluetooth/api/RetrofitHttpContractTest.java
```

Move/modify:

```text
app/src/main/java/com/hc/mixthebluetooth/api/AuthSessionStore.java
  -> app/src/main/java/com/hc/mixthebluetooth/auth/AuthSessionStore.java

app/src/main/java/com/hc/mixthebluetooth/activity/ApiDebugActivity.java
  -> app/src/main/java/com/hc/mixthebluetooth/debug/DiagnosticsActivity.java
```

Modify:

```text
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/com/hc/mixthebluetooth/api/ApiClient.java
app/src/main/java/com/hc/mixthebluetooth/activity/LoginActivity.java
app/src/main/java/com/hc/mixthebluetooth/activity/AccountRegisterActivity.java
app/src/main/java/com/hc/mixthebluetooth/activity/DebugActivity.java
app/src/main/res/layout/activity_api_debug.xml
app/src/test/java/com/hc/mixthebluetooth/api/AuthSessionStoreTest.java
```

---

## Task 1: Add AuthRepository And Move Session Store

**Files:**
- Move: `app/src/main/java/com/hc/mixthebluetooth/api/AuthSessionStore.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/auth/AuthRepository.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/auth/AuthRepositoryTest.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/api/ApiClient.java`

- [ ] **Step 1: Move `AuthSessionStore` to `auth` package**

Use `git mv`, then change the package line:

```java
package com.hc.mixthebluetooth.auth;
```

Update imports from:

```java
import com.hc.mixthebluetooth.api.AuthSessionStore;
```

to:

```java
import com.hc.mixthebluetooth.auth.AuthSessionStore;
```

- [ ] **Step 2: Create `AuthRepository`**

Create `app/src/main/java/com/hc/mixthebluetooth/auth/AuthRepository.java`:

```java
public final class AuthRepository {
    public interface ResultCallback {
        void onSuccess(@NonNull AccountInfo info);
        void onError(@NonNull String message);
    }

    public AuthRepository(@NonNull AuthApi authApi, @NonNull AuthSessionStore sessionStore) { ... }

    public void register(@NonNull String username, @NonNull String password,
                         @NonNull String phone, @NonNull ResultCallback callback) { ... }

    public void login(@NonNull String phone, @NonNull String password,
                      @NonNull ResultCallback callback) { ... }

    public void detail(@NonNull ResultCallback callback) { ... }

    public long accountId() { return sessionStore.accountId(); }
    @Nullable public String phone() { return sessionStore.phone(); }
    @Nullable public String token() { return sessionStore.token(); }
    public void clearSession() { sessionStore.clear(); }
}
```

Repository must save session only when API response is successful and `data != null`.

- [ ] **Step 3: Write repository tests**

Create tests for:

```text
register saves accountId/phone/token
login saves accountId/phone without requiring token
detail saves returned account
clearSession clears saved data
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.auth.AuthRepositoryTest"
```

Expected: PASS.

---

## Task 2: Route Login/Register/Diagnostics Through AuthRepository

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/LoginActivity.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/AccountRegisterActivity.java`
- Move/modify: `app/src/main/java/com/hc/mixthebluetooth/debug/DiagnosticsActivity.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/DebugActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Replace login button logic**

`LoginActivity` should call:

```java
authRepository.login(username, password, new AuthRepository.ResultCallback() { ... });
```

On success, keep existing `HomeApplication` state assignment and navigate to `MainActivity`.

- [ ] **Step 2: Replace register submit logic**

`AccountRegisterActivity` should call:

```java
authRepository.register(username, password, phone, callback);
```

It should not directly call `ApiClient.authApi()`.

- [ ] **Step 3: Rename API debug Activity to Diagnostics**

Move:

```text
com.hc.mixthebluetooth.activity.ApiDebugActivity
```

to:

```text
com.hc.mixthebluetooth.debug.DiagnosticsActivity
```

Update Manifest and callers.

- [ ] **Step 4: Make Diagnostics use AuthRepository**

Diagnostics register/login/detail buttons should call `AuthRepository`, not direct `AuthApi`.

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS.

---

## Task 3: Add CGM Fixture Replay And Upload UseCase

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/cgm/CgmReplayFixture.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/cgm/CgmReplayUploadUseCase.java`
- Create: `app/src/test/resources/cgm/cgm_playback_sample.txt`
- Create: `app/src/debug/assets/cgm/cgm_playback_sample.txt`
- Test: `app/src/test/java/com/hc/mixthebluetooth/cgm/CgmReplayFixtureTest.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/debug/DiagnosticsActivity.java`

- [ ] **Step 1: Add sample fixture**

Both sample files must contain:

```text
Start Playback
EIS:1,1000,0.12
CA:1,0.08
CA:2,0.09
Playback all done
```

- [ ] **Step 2: Create fixture reader**

`CgmReplayFixture.readLines(InputStream)` returns non-null lines in order.

- [ ] **Step 3: Create replay upload use case**

`CgmReplayUploadUseCase.replayAssetAndUpload("cgm/cgm_playback_sample.txt", callback)`:

```text
open asset
read lines
feed each line to CgmPlaybackRecorder
require completed file
upload file with FileUploadUseCase
```

- [ ] **Step 4: Update Diagnostics upload button**

Diagnostics upload must use `CgmReplayUploadUseCase`; it must not hand-write a temporary txt file.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.cgm.CgmReplayFixtureTest"
```

Expected: PASS.

---

## Task 4: Add HTTP Contract Tests

**Files:**
- Modify: `app/build.gradle`
- Test: `app/src/test/java/com/hc/mixthebluetooth/api/RetrofitHttpContractTest.java`

- [ ] **Step 1: Add MockWebServer dependency**

Add:

```groovy
testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'
```

- [ ] **Step 2: Add ApiClient reset/override seam for tests**

Add package-visible or public-for-test creation path that can build Retrofit APIs from a provided base URL without Android `Context`.

Keep production `ApiClient.get(context)` unchanged.

- [ ] **Step 3: Test auth paths**

Use `MockWebServer` to verify:

```text
POST /api/account/v1/login
POST /api/account/v1/register
GET /api/account/v1/detail
```

- [ ] **Step 4: Test upload multipart**

Verify:

```text
POST /api/file/v1/upload
Content-Type contains multipart/form-data
body contains fileName
body contains fileSize
body contains CGM_Cache_data.txt content
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.api.RetrofitHttpContractTest"
```

Expected: PASS.

---

## Task 5: Final Verification And Commit

**Files:**
- Modify: all changed fifth-stage files.

- [ ] **Step 1: Run full unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Compile debug Java**

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS.

- [ ] **Step 3: Assemble mock debug**

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=mock
```

Expected: PASS.

- [ ] **Step 4: Check diff whitespace**

```powershell
git diff --check
```

Expected: no errors. Line-ending warnings are acceptable on this Windows worktree.

- [ ] **Step 5: Commit**

```powershell
git add -A
git commit -m "feat: add fifth stage verification loop"
```
