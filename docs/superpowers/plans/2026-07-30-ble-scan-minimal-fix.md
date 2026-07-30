# BLE Scan Minimal Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `migratedev` receive BLE advertisements on Android 14 and restart each 20-second scan round without a false `ScanFailed`.

**Architecture:** Keep the existing `AndroidBluetoothPort` and legacy `bluetoothlibrary`. Complete the Android 12+ permission declaration, make legacy scan callbacks work with an application context, and defer round restart until the legacy completion callback has returned.

**Tech Stack:** Android 14 / API 34, Kotlin coroutines and Flow, Java Android Bluetooth APIs, JUnit 4, Gradle.

---

### Task 1: Reproduce the scan-round restart race

**Files:**
- Modify: `migratedev/src/test/kotlin/com/biosensor/migratedev/port/adapter/bluetoothport/AndroidBluetoothPortTest.kt`

- [ ] **Step 1: Extend the fake client to model the legacy busy callback**

Add a `finishingScan` flag. Return `false` when `startScan()` is invoked before `scanFinished()` has returned:

```kotlin
private var finishingScan = false

override fun startScan(): Boolean {
    mixedScanCount += 1
    calls += "startScan"
    scanFailure?.let { throw it }
    return !finishingScan
}

fun scanFinished() {
    finishingScan = true
    listener.onScanFinished()
    finishingScan = false
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :migratedev:testDebugUnitTest --tests "com.biosensor.migratedev.port.adapter.bluetoothport.AndroidBluetoothPortTest.natural SDK end starts another round without ending scan session"
```

Expected: FAIL because the second `startScan()` happens while `finishingScan` is true and produces `ScanFailed`.

### Task 2: Defer the next scan round

**Files:**
- Modify: `migratedev/src/main/kotlin/com/biosensor/migratedev/port/adapter/bluetoothport/AndroidBluetoothPort.kt:338-356`
- Test: `migratedev/src/test/kotlin/com/biosensor/migratedev/port/adapter/bluetoothport/AndroidBluetoothPortTest.kt`

- [ ] **Step 1: Yield before starting the next round**

Change the restart coroutine to:

```kotlin
scanScope.launch {
    yield()
    beginRound(session)
}
```

Add:

```kotlin
import kotlinx.coroutines.yield
```

- [ ] **Step 2: Run the focused test and verify GREEN**

Run the focused Gradle command from Task 1.

Expected: PASS; results contain `ScanRoundEnded` followed by the second `ScanStarted`.

- [ ] **Step 3: Run all Bluetooth port unit tests**

Run:

```powershell
.\gradlew.bat :migratedev:testDebugUnitTest --tests "com.biosensor.migratedev.port.adapter.bluetoothport.*"
```

Expected: PASS with no test failures.

### Task 3: Complete Android 12+ scan permission semantics

**Files:**
- Modify: `migratedev/src/main/AndroidManifest.xml`

- [ ] **Step 1: Assert that scans are not used for location**

Change the scan permission to:

```xml
<uses-permission
    android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
```

Keep `ACCESS_FINE_LOCATION` limited to API 30.

- [ ] **Step 2: Verify the merged debug manifest**

Run:

```powershell
.\gradlew.bat :migratedev:processDebugMainManifest
```

Expected: BUILD SUCCESSFUL. The merged manifest contains `BLUETOOTH_SCAN` with `neverForLocation`.

### Task 4: Make legacy scan callbacks application-context safe

**Files:**
- Modify: `bluetoothlibrary/src/main/java/com/hc/bluetoothlibrary/bleBluetooth/BleBluetoothManage.java`

- [ ] **Step 1: Add a main-thread handler**

Import `android.os.Looper` and define:

```java
private final Handler mMainHandler = new Handler(Looper.getMainLooper());
```

- [ ] **Step 2: Replace Activity-only callback dispatch**

In both scan callbacks, replace:

```java
((Activity) mContext).runOnUiThread(new Runnable() {
```

with:

```java
mMainHandler.post(new Runnable() {
```

Do not change the callback bodies or scan filters.

- [ ] **Step 3: Compile both affected modules**

Run:

```powershell
.\gradlew.bat :bluetoothlibrary:compileDebugJavaWithJavac :migratedev:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

### Task 5: Full build and device verification

**Files:**
- Verify only; no additional source changes unless evidence identifies a remaining root cause.

- [ ] **Step 1: Run the complete migratedev unit suite**

Run:

```powershell
.\gradlew.bat :migratedev:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 2: Build and install the debug APK**

Run:

```powershell
.\gradlew.bat :migratedev:installDebug
```

Expected: APK installs successfully on PJH110.

- [ ] **Step 3: Clear Logcat and launch the app**

Run:

```powershell
adb logcat -c
adb shell am start -n com.biosensor.migratedev/.MainActivity
```

Use the existing authenticated session to enter the connection screen. Grant Nearby devices permission if prompted.

- [ ] **Step 4: Verify scan events**

Capture at least 25 seconds of logs and check for:

```text
event=DevicesUpdated
event=ScanRoundEnded
event=ScanStarted
```

There must not be a `ScanFailed` immediately after `ScanRoundEnded`.

- [ ] **Step 5: Verify platform scan statistics**

Run:

```powershell
adb shell dumpsys bluetooth_manager
```

Expected under `com.biosensor.migratedev`: `Total number of results` is greater than 0.

- [ ] **Step 6: Review the final diff**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only the approved fix files, test update, plan file, and the user's pre-existing filter comment are present.
