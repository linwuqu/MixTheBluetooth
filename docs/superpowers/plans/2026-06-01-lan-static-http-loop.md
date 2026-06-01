# LAN + Static HTTP CGM Loop Implementation Plan

> **Status:** Superseded for realistic LAN testing by `docs/superpowers/plans/2026-06-01-lan-static-http-backend.md`. Use the LAN static HTTP backend plan when the goal is to exercise real Android HTTP packets.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a practical static/LAN HTTP CGM loop so the app can complete register, login, device data, upload, CGM result, and chart rendering without USB or backend, while real LAN HTTP remains one `apiEnv` configuration change away.

**Architecture:** Keep environment selection simple: `API_ENV=static` means app-internal static loop; `lan`, `dev`, and `prod` use real Retrofit HTTP. Add static auth and CGM services behind the existing `AppApi` interfaces, wire them in `AppApiBootstrap`, and keep UI/controller code unaware of whether the backend is static or real. Static CGM uses the real endpoint shape conceptually and always returns `jobId=64`.

**Tech Stack:** Android Java, Gradle BuildConfig fields, Retrofit 2, OkHttp, Gson, MPAndroidChart, JUnit 4, MockWebServer, logcat.

---

## Scope Check

This plan is one focused implementation slice: environment selection plus static implementations for the existing service layer. It does not fake Bluetooth packets, does not add a new debug-only page, and does not restructure the large UI files. The goal is a working loop first; deeper package cleanup can happen after the behavior is stable.

## Design Decisions

- Do not add `ApiMode`. `API_ENV` already expresses the required behavior for this project stage.
- Keep the current `EnvConfig.fromBuildConfig()` style. Add only small helpers such as `isStatic()` and `isRealHttp()`.
- Keep `AppApiBootstrap` as the dependency assembly point. It should choose static or default services with one clear `if (env.isStatic())` branch.
- Do not add a `Services` wrapper type just for tests. Test public behavior and service outcomes instead.
- Static runtime implementations are new. Existing `StaticConstants` is only an event constant holder, not static HTTP.
- Tests must verify meaningful behavior: endpoint contracts, full service-chain outcomes, and failure tolerance. Delete old tests that only cover removed mock infrastructure or conflict with the current chain.

## Current Runtime API Implementations

```text
AuthService
  current: DefaultAuthService
  real HTTP:
    POST /api/account/v1/register
    POST /api/account/v1/login
    GET  /api/account/v1/detail
  new static: StaticAuthService

FileService
  current: DefaultFileService
  real HTTP:
    POST /api/file/v1/upload
  static loop:
    keep DefaultFileService installed, but CGM loop uses CgmService testUpload path

CgmService
  current: DefaultCgmService
  real HTTP:
    POST /api/test/v1/upload
    GET  /api/cgm/v1/jobs/{jobId}
  new static: StaticCgmService
    returns upload success with jobId=64
    returns generated CGM result for /api/cgm/v1/jobs/64 conceptually

DeviceDataService
  current: DefaultDeviceDataService
  local device/cache file handling
  unchanged in this plan
```

## File Map

- Create: `app/config/env.static.properties`
- Create: `app/config/env.lan.properties`
- Modify: `app/config/env.dev.properties`
- Modify: `app/config/env.prod.properties`
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/api/EnvConfig.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/staticdata/StaticBioAiFixtures.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/auth/StaticAuthService.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmService.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java`
- Add focused tests under:
  - `app/src/test/java/com/hc/mixthebluetooth/impl/auth/StaticAuthServiceTest.java`
  - `app/src/test/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmServiceTest.java`
  - `app/src/test/java/com/hc/mixthebluetooth/impl/StaticLoopServiceChainTest.java`
- Keep and update meaningful existing tests:
  - `ServerEndpointsContractTest`
  - `CgmJobRespParsingTest`
  - `DefaultCgmServiceTest`
  - auth/file/device service tests that still reflect current behavior
- Delete obsolete tests that reference removed runtime mock infrastructure or old debug pages.

---

### Task 1: Simplify Environment Selection

**Files:**
- Create: `app/config/env.static.properties`
- Create: `app/config/env.lan.properties`
- Modify: `app/config/env.dev.properties`
- Modify: `app/config/env.prod.properties`
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/api/EnvConfig.java`

- [ ] **Step 1: Add env files**

Create `app/config/env.static.properties`:

```properties
API_ENV=static
API_BASE_URL=http://127.0.0.1/
```

Create `app/config/env.lan.properties`:

```properties
API_ENV=lan
API_BASE_URL=http://124.16.68.18:8080/
```

Update `app/config/env.dev.properties`:

```properties
API_ENV=dev
API_BASE_URL=http://124.16.68.18:8080/
```

Update `app/config/env.prod.properties`:

```properties
API_ENV=prod
API_BASE_URL=https://example.com/
```

- [ ] **Step 2: Keep Gradle BuildConfig simple**

In `app/build.gradle`, keep only the existing env/baseUrl fields:

```groovy
        buildConfigField "String", "API_ENV", "\"${envProps.getProperty("API_ENV", envName)}\""
        buildConfigField "String", "API_BASE_URL", "\"${envProps.getProperty("API_BASE_URL", "http://10.0.2.2:8080/")}\""
```

Do not add `API_MODE`.

- [ ] **Step 3: Add small EnvConfig helpers**

Update `app/src/main/java/com/hc/mixthebluetooth/api/EnvConfig.java` to keep the existing constructor shape and add helper methods:

```java
package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.BuildConfig;

public final class EnvConfig {
    @NonNull
    public final String env;
    @NonNull
    public final String baseUrl;
    public final boolean debug;
    @NonNull
    public final String remoteName;
    public final boolean networkEnabled;

    public EnvConfig(@NonNull String env,
                     @NonNull String baseUrl,
                     boolean debug,
                     @NonNull String remoteName,
                     boolean networkEnabled) {
        this.env = env;
        this.baseUrl = baseUrl;
        this.debug = debug;
        this.remoteName = remoteName;
        this.networkEnabled = networkEnabled;
    }

    @NonNull
    public static EnvConfig fromBuildConfig() {
        return new EnvConfig(
                BuildConfig.API_ENV,
                BuildConfig.API_BASE_URL,
                BuildConfig.DEBUG,
                "",
                false
        );
    }

    public boolean isStatic() {
        return "static".equalsIgnoreCase(env);
    }

    public boolean isRealHttp() {
        return !isStatic();
    }

    @NonNull
    public EnvConfig withRemote(@NonNull String remoteName, boolean networkEnabled) {
        return new EnvConfig(env, baseUrl, debug, remoteName, networkEnabled);
    }
}
```

- [ ] **Step 4: Build env variants enough to catch config mistakes**

Run:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=static
.\gradlew.bat :app:assembleDebug -PapiEnv=lan
```

Expected: both compile. If Gradle cannot run because the wrapper needs a network download, install/use the already configured local Gradle distribution, then rerun these commands.

- [ ] **Step 5: Commit Task 1**

```powershell
git add app/config/env.static.properties app/config/env.lan.properties app/config/env.dev.properties app/config/env.prod.properties app/build.gradle app/src/main/java/com/hc/mixthebluetooth/api/EnvConfig.java
git commit -m "feat: add static and lan env selection"
```

---

### Task 2: Add Static Fixtures And Static Auth

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/staticdata/StaticBioAiFixtures.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/auth/StaticAuthService.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/auth/StaticAuthServiceTest.java`

- [ ] **Step 1: Create static fixture data**

Create `app/src/main/java/com/hc/mixthebluetooth/staticdata/StaticBioAiFixtures.java`:

```java
package com.hc.mixthebluetooth.staticdata;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.remote.ServerModels;
import com.hc.mixthebluetooth.remote.ServerResponse;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StaticBioAiFixtures {
    public static final long ACCOUNT_ID = 10001L;
    public static final String USERNAME = "bioai-dev-user";
    public static final String PHONE = "18800000001";
    public static final String PASSWORD = "123456";
    public static final String STATIC_TOKEN = "static-token-job-64";
    public static final long STATIC_JOB_ID = 64L;

    private static final Gson GSON = new Gson();

    private StaticBioAiFixtures() {
    }

    @NonNull
    public static AuthUser authUser() {
        return new AuthUser(ACCOUNT_ID, USERNAME, PHONE, null, STATIC_TOKEN);
    }

    @NonNull
    public static ServerResponse<Object> uploadResponse() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", STATIC_JOB_ID);
        return ServerResponse.success(data);
    }

    @NonNull
    public static ServerModels.CgmJobResp cgmJob64() {
        return GSON.fromJson(CGM_JOB_64_JSON, ServerModels.CgmJobResp.class);
    }

    public static final String CGM_JOB_64_JSON = "{"
            + "\"code\":200,"
            + "\"message\":\"success\","
            + "\"data\":{"
            + "\"resultId\":64001,"
            + "\"jobId\":64,"
            + "\"datasetId\":640,"
            + "\"pointCount\":6,"
            + "\"unitCount\":1,"
            + "\"predictionMin\":4.12,"
            + "\"predictionMax\":9.87,"
            + "\"predictionMean\":6.54,"
            + "\"predictionStd\":1.23,"
            + "\"avgMard\":16.3,"
            + "\"mardStd\":null,"
            + "\"summaryJson\":{"
            + "\"avg_mard\":16.3,"
            + "\"mard_std\":null,"
            + "\"point_count\":6,"
            + "\"unit_count\":1,"
            + "\"prediction_stats\":{\"min\":4.12,\"max\":9.87,\"mean\":6.54,\"std\":1.23},"
            + "\"units\":[{"
            + "\"unit\":1,"
            + "\"unit_title\":\"static-job-64\","
            + "\"point_count\":6,"
            + "\"mard\":16.3,"
            + "\"points\":["
            + "{\"index\":0,\"time\":0,\"predicted\":5.21,\"actual\":5.0},"
            + "{\"index\":1,\"time\":60,\"predicted\":6.18,\"actual\":5.7},"
            + "{\"index\":2,\"time\":120,\"predicted\":6.85,\"actual\":6.4},"
            + "{\"index\":3,\"time\":180,\"predicted\":7.43,\"actual\":7.0},"
            + "{\"index\":4,\"time\":240,\"predicted\":8.10,\"actual\":7.8},"
            + "{\"index\":5,\"time\":300,\"predicted\":9.02,\"actual\":8.6}"
            + "]}]},"
            + "\"status\":\"GENERATED\","
            + "\"gmtCreate\":\"2026-06-01T10:00:00\""
            + "}}";
}
```

- [ ] **Step 2: Implement static auth**

Create `app/src/main/java/com/hc/mixthebluetooth/impl/auth/StaticAuthService.java`:

```java
package com.hc.mixthebluetooth.impl.auth;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.staticdata.StaticBioAiFixtures;

public final class StaticAuthService implements AuthService {
    private static final String OWNER = "StaticAuthService";
    private static final String API_REGISTER = "POST /api/account/v1/register";
    private static final String API_LOGIN = "POST /api/account/v1/login";
    private static final String API_DETAIL = "GET /api/account/v1/detail";

    private final SessionStore sessionStore;

    public StaticAuthService(@NonNull SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public void register(String username, String password, String phone, ApiCallback<CallResult<AuthUser>> callback) {
        ApiTraceLogger.json(OWNER, API_REGISTER, "request",
                ApiTraceLogger.maskedAuthBody(username, phone, password));
        AuthUser user = StaticBioAiFixtures.authUser();
        ApiTraceLogger.json(OWNER, API_REGISTER, "response", user);
        callback.onResult(CallResult.ok(user));
    }

    @Override
    public void login(String phoneOrAccount, String password, ApiCallback<CallResult<AuthUser>> callback) {
        ApiTraceLogger.json(OWNER, API_LOGIN, "request",
                ApiTraceLogger.maskedAuthBody(null, phoneOrAccount, password));
        AuthUser user = StaticBioAiFixtures.authUser();
        sessionStore.save(user);
        ApiTraceLogger.json(OWNER, API_LOGIN, "response", user);
        callback.onResult(CallResult.ok(user));
    }

    @Override
    public void detail(ApiCallback<CallResult<AuthUser>> callback) {
        ApiTraceLogger.text(OWNER, API_DETAIL, "request", "{}");
        AuthUser user = sessionStore.currentUser();
        if (user == null) {
            user = StaticBioAiFixtures.authUser();
            sessionStore.save(user);
        }
        ApiTraceLogger.json(OWNER, API_DETAIL, "response", user);
        callback.onResult(CallResult.ok(user));
    }
}
```

- [ ] **Step 3: Add realistic static auth test**

Create `app/src/test/java/com/hc/mixthebluetooth/impl/auth/StaticAuthServiceTest.java`:

```java
package com.hc.mixthebluetooth.impl.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.staticdata.StaticBioAiFixtures;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class StaticAuthServiceTest {
    @Test
    public void registerThenLoginThenDetailPreservesSession() {
        SessionStore store = new SessionStore(new MemoryStore());
        StaticAuthService service = new StaticAuthService(store);
        AtomicReference<CallResult<AuthUser>> register = new AtomicReference<>();
        AtomicReference<CallResult<AuthUser>> login = new AtomicReference<>();
        AtomicReference<CallResult<AuthUser>> detail = new AtomicReference<>();

        service.register(
                StaticBioAiFixtures.USERNAME,
                StaticBioAiFixtures.PASSWORD,
                StaticBioAiFixtures.PHONE,
                register::set
        );
        service.login(StaticBioAiFixtures.PHONE, StaticBioAiFixtures.PASSWORD, login::set);
        service.detail(detail::set);

        assertTrue(register.get().isOk());
        assertTrue(login.get().isOk());
        assertTrue(detail.get().isOk());
        assertEquals(StaticBioAiFixtures.ACCOUNT_ID, detail.get().data.accountId);
        assertEquals(StaticBioAiFixtures.STATIC_TOKEN, detail.get().data.token);
        assertNotNull(store.currentUser());
        assertEquals(StaticBioAiFixtures.STATIC_TOKEN, store.currentUser().token);
    }

    private static final class MemoryStore implements SessionStore.Store {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public void putString(@NonNull String key, @NonNull String value) {
            values.put(key, value);
        }

        @Override
        public void putLong(@NonNull String key, long value) {
            values.put(key, value);
        }

        @Nullable
        @Override
        public String getString(@NonNull String key) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : null;
        }

        @Override
        public long getLong(@NonNull String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defaultValue;
        }

        @Override
        public void remove(@NonNull String key) {
            values.remove(key);
        }

        @Override
        public void clear() {
            values.clear();
        }
    }
}
```

- [ ] **Step 4: Run focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.auth.StaticAuthServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit Task 2**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/staticdata/StaticBioAiFixtures.java app/src/main/java/com/hc/mixthebluetooth/impl/auth/StaticAuthService.java app/src/test/java/com/hc/mixthebluetooth/impl/auth/StaticAuthServiceTest.java
git commit -m "feat: add static auth loop"
```

---

### Task 3: Add Static CGM With Meaningful Chain And Failure Tests

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmService.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmServiceTest.java`

- [ ] **Step 1: Implement StaticCgmService**

Create `app/src/main/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmService.java`:

```java
package com.hc.mixthebluetooth.impl.cgm;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
import com.hc.mixthebluetooth.remote.ServerModels;
import com.hc.mixthebluetooth.remote.ServerResponse;
import com.hc.mixthebluetooth.staticdata.StaticBioAiFixtures;

import java.io.File;

public final class StaticCgmService implements CgmService {
    private static final String OWNER = "StaticCgmService";
    private static final String API_UPLOAD = "POST /api/test/v1/upload";
    private static final String API_CGM_64 = "GET /api/cgm/v1/jobs/64";

    @Override
    public void uploadAndPoll(File cacheFile,
                              ApiCallback<CallResult<ServerModels.CgmJobData>> callback) {
        if (cacheFile == null || !cacheFile.exists() || !cacheFile.isFile()) {
            ApiTraceLogger.text(OWNER, API_UPLOAD, "failure", "local file not found");
            callback.onResult(CallResult.error(CallResult.LOCAL_FILE_NOT_FOUND, "CGM cache txt not found", null));
            return;
        }

        ApiTraceLogger.file(OWNER, API_UPLOAD, cacheFile);
        ServerResponse<Object> upload = StaticBioAiFixtures.uploadResponse();
        ApiTraceLogger.json(OWNER, API_UPLOAD, "response", upload);
        poll(StaticBioAiFixtures.STATIC_JOB_ID, callback);
    }

    @Override
    public void poll(long jobId, ApiCallback<CallResult<ServerModels.CgmJobData>> callback) {
        if (jobId != StaticBioAiFixtures.STATIC_JOB_ID) {
            ApiTraceLogger.text(OWNER, "GET /api/cgm/v1/jobs/" + jobId, "failure",
                    "static loop only supports jobId=" + StaticBioAiFixtures.STATIC_JOB_ID);
            callback.onResult(CallResult.error(CallResult.EMPTY_DATA,
                    "static loop only supports jobId=" + StaticBioAiFixtures.STATIC_JOB_ID, null));
            return;
        }

        ServerModels.CgmJobResp response = StaticBioAiFixtures.cgmJob64();
        ApiTraceLogger.json(OWNER, API_CGM_64, "response attempt=1", response);
        if (response.data == null) {
            callback.onResult(CallResult.error(CallResult.EMPTY_DATA, "static CGM data is empty", null));
            return;
        }
        callback.onResult(CallResult.ok(response.data));
    }
}
```

- [ ] **Step 2: Add static CGM service-chain test**

Create `app/src/test/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmServiceTest.java`:

```java
package com.hc.mixthebluetooth.impl.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.remote.ServerModels;
import com.hc.mixthebluetooth.staticdata.StaticBioAiFixtures;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

public class StaticCgmServiceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void uploadAndPollReturnsRenderableGeneratedCgmData() throws Exception {
        File file = temporaryFolder.newFile("cgm-cache.txt");
        Files.write(file.toPath(), "Start Playback\nstatic payload\nPlayback all done\n".getBytes(StandardCharsets.UTF_8));
        StaticCgmService service = new StaticCgmService();
        AtomicReference<CallResult<ServerModels.CgmJobData>> result = new AtomicReference<>();

        service.uploadAndPoll(file, result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isOk());
        ServerModels.CgmJobData data = result.get().data;
        assertNotNull(data);
        assertEquals(StaticBioAiFixtures.STATIC_JOB_ID, data.jobId);
        assertEquals("GENERATED", data.status);
        assertEquals(6, data.pointCount);
        assertNotNull(data.summaryJson);
        assertNotNull(data.summaryJson.units);
        assertFalse(data.summaryJson.units.isEmpty());
        assertNotNull(data.summaryJson.units.get(0).points);
        assertEquals(6, data.summaryJson.units.get(0).points.size());
        assertEquals(0, data.summaryJson.units.get(0).points.get(0).index);
        assertTrue(file.exists());
    }

    @Test
    public void missingFileReturnsLocalErrorWithoutThrowing() {
        StaticCgmService service = new StaticCgmService();
        AtomicReference<CallResult<ServerModels.CgmJobData>> result = new AtomicReference<>();

        service.uploadAndPoll(new File(temporaryFolder.getRoot(), "missing.txt"), result::set);

        assertNotNull(result.get());
        assertFalse(result.get().isOk());
        assertEquals(CallResult.LOCAL_FILE_NOT_FOUND, result.get().code);
    }

    @Test
    public void unknownStaticJobReturnsControlledError() {
        StaticCgmService service = new StaticCgmService();
        AtomicReference<CallResult<ServerModels.CgmJobData>> result = new AtomicReference<>();

        service.poll(65L, result::set);

        assertNotNull(result.get());
        assertFalse(result.get().isOk());
        assertEquals(CallResult.EMPTY_DATA, result.get().code);
    }
}
```

- [ ] **Step 3: Run focused tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.cgm.StaticCgmServiceTest"
```

Expected: PASS.

- [ ] **Step 4: Keep real HTTP endpoint tests passing**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.cgm.DefaultCgmServiceTest" --tests "com.hc.mixthebluetooth.remote.ServerEndpointsContractTest"
```

Expected: PASS. These tests prove real HTTP still uses:

```text
POST /api/test/v1/upload
GET  /api/cgm/v1/jobs/{jobId}
```

- [ ] **Step 5: Commit Task 3**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmService.java app/src/test/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmServiceTest.java
git commit -m "feat: add static cgm job 64 loop"
```

---

### Task 4: Wire Static Services In AppApiBootstrap

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/StaticLoopServiceChainTest.java`

- [ ] **Step 1: Wire services with a simple env branch**

Modify `AppApiBootstrap.init` so the service selection block reads:

```java
        ServerEndpoints endpoints = ServerClient.create(env.baseUrl, sessionStore, env.debug);
        FileService fileService = new DefaultFileService(endpoints);
        AuthService authService;
        CgmService cgmService;

        if (env.isStatic()) {
            authService = new StaticAuthService(sessionStore);
            cgmService = new StaticCgmService();
            env = env.withRemote("StaticBioAiTransport", false);
        } else {
            authService = new DefaultAuthService(endpoints, sessionStore);
            cgmService = new DefaultCgmService(endpoints);
            env = env.withRemote("RetrofitServer(" + env.baseUrl + ")", true);
        }

        ApiTraceLogger.text("AppApiBootstrap", "ENV", "config",
                "env=" + env.env
                        + "\nbaseUrl=" + env.baseUrl
                        + "\nremote=" + env.remoteName
                        + "\nnetworkEnabled=" + env.networkEnabled);
```

Keep the existing `DefaultDeviceDataService` construction, but pass the selected `fileService`.

Required imports:

```java
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.impl.auth.StaticAuthService;
import com.hc.mixthebluetooth.impl.cgm.StaticCgmService;
```

- [ ] **Step 2: Add a realistic static service-chain test without changing production code for testability**

Create `app/src/test/java/com/hc/mixthebluetooth/impl/StaticLoopServiceChainTest.java`:

```java
package com.hc.mixthebluetooth.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.auth.AuthUser;
import com.hc.mixthebluetooth.impl.auth.StaticAuthService;
import com.hc.mixthebluetooth.impl.cgm.StaticCgmService;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.remote.ServerModels;
import com.hc.mixthebluetooth.staticdata.StaticBioAiFixtures;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class StaticLoopServiceChainTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void staticAuthThenCgmChainProducesRenderableJob64() throws Exception {
        SessionStore store = new SessionStore(new MemoryStore());
        StaticAuthService auth = new StaticAuthService(store);
        StaticCgmService cgm = new StaticCgmService();
        File file = temporaryFolder.newFile("cgm-cache.txt");
        Files.write(file.toPath(), "Start Playback\npayload\nPlayback all done\n".getBytes(StandardCharsets.UTF_8));

        AtomicReference<CallResult<AuthUser>> register = new AtomicReference<>();
        AtomicReference<CallResult<AuthUser>> login = new AtomicReference<>();
        AtomicReference<CallResult<ServerModels.CgmJobData>> cgmResult = new AtomicReference<>();

        auth.register(StaticBioAiFixtures.USERNAME, StaticBioAiFixtures.PASSWORD, StaticBioAiFixtures.PHONE, register::set);
        auth.login(StaticBioAiFixtures.PHONE, StaticBioAiFixtures.PASSWORD, login::set);
        cgm.uploadAndPoll(file, cgmResult::set);

        assertTrue(register.get().isOk());
        assertTrue(login.get().isOk());
        assertNotNull(store.currentUser());
        assertEquals(StaticBioAiFixtures.STATIC_TOKEN, store.currentUser().token);
        assertTrue(cgmResult.get().isOk());
        assertEquals(64L, cgmResult.get().data.jobId);
        assertEquals("GENERATED", cgmResult.get().data.status);
        assertNotNull(cgmResult.get().data.summaryJson.units.get(0).points);
    }

    private static final class MemoryStore implements SessionStore.Store {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public void putString(@NonNull String key, @NonNull String value) {
            values.put(key, value);
        }

        @Override
        public void putLong(@NonNull String key, long value) {
            values.put(key, value);
        }

        @Nullable
        @Override
        public String getString(@NonNull String key) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : null;
        }

        @Override
        public long getLong(@NonNull String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defaultValue;
        }

        @Override
        public void remove(@NonNull String key) {
            values.remove(key);
        }

        @Override
        public void clear() {
            values.clear();
        }
    }
}
```

This test intentionally checks the chain outcome rather than checking `AppApiBootstrap` internals.

- [ ] **Step 3: Run service-chain test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.StaticLoopServiceChainTest"
```

Expected: PASS.

- [ ] **Step 4: Build static env**

Run:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=static
```

Expected: PASS.

- [ ] **Step 5: Commit Task 4**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java app/src/test/java/com/hc/mixthebluetooth/impl/StaticLoopServiceChainTest.java
git commit -m "feat: wire static app api services"
```

---

### Task 5: Add BioAI.Http Logs For Full Flow Visibility

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java`

- [ ] **Step 1: Log device connection and BT send/receive in Controller**

In `Controller.java`, add:

```java
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
```

Add constants:

```java
    private static final String OWNER = "Controller";
    private static final String API_BT_SEND = "BT_SEND";
    private static final String API_BT_RECV = "BT_RECV";
    private static final String API_DEVICE_CONNECT = "DEVICE_CONNECT";
    private static final String API_RENDER = "RENDER";
```

In the `BTPackage.Connected` branch:

```java
            ApiTraceLogger.text(OWNER, API_DEVICE_CONNECT, "state",
                    "connected=true\ndevice=" + (module == null ? "" : module.getName()));
```

In the `BTPackage.Disconnected` branch:

```java
            ApiTraceLogger.text(OWNER, API_DEVICE_CONNECT, "state", "connected=false");
```

In the POST action branch:

```java
            String payload = action.textSupplier.get();
            ApiTraceLogger.text(OWNER, API_BT_SEND, "command",
                    "id=" + action.id + "\npayload=" + payload);
            gateway.postText(module, payload);
```

After BT text decode succeeds:

```java
        ApiTraceLogger.text(OWNER, API_BT_RECV, "data",
                "bytes=" + data.bytes.length + "\ntext=" + text);
```

In `onCgmResult` before widget dispatch:

```java
        ApiTraceLogger.text(OWNER, API_RENDER, "cgmResult",
                "jobId=" + result.jobId
                        + "\nstatus=" + result.status
                        + "\npointCount=" + result.pointCount
                        + "\nunitCount=" + result.unitCount);
```

- [ ] **Step 2: Log cache-file handoff in UniFragment**

In `UniFragment.java`, add:

```java
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
```

At the start of `onCacheFileReady`:

```java
            ApiTraceLogger.file("UniFragment", "CACHE_FILE_READY", file);
```

- [ ] **Step 3: Log widget render result**

In `Widgets.java`, add:

```java
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
```

In `CgmResultMetricWidget.onCgmResult`, after the predicted/actual lists are populated:

```java
            ApiTraceLogger.text("CgmWidgetBinder", "RENDER", "result",
                    "jobId=" + result.jobId
                            + "\nstatus=" + result.status
                            + "\npoints=" + predicted.size()
                            + "\npredicted=" + predicted.size()
                            + "\nactual=" + actual.size()
                            + "\nunitCount=" + result.unitCount);
```

- [ ] **Step 4: Run compile/tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.StaticLoopServiceChainTest" --tests "com.hc.mixthebluetooth.impl.cgm.StaticCgmServiceTest"
.\gradlew.bat :app:assembleDebug -PapiEnv=static
```

Expected: PASS.

- [ ] **Step 5: Commit Task 5**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java
git commit -m "feat: log static cgm loop milestones"
```

---

### Task 6: Clean Up Obsolete Tests And Verify Realistic Test Set

**Files:**
- Delete stale tests only if they reference removed runtime mock/debug code.
- Keep tests that protect real current behavior.

- [ ] **Step 1: Identify obsolete tests**

Run:

```powershell
rg -n "MockServer|VerificationActivity|env.mock|useMock|activity_verification" app/src/test app/src/androidTest
```

Expected: Any matches refer to removed or obsolete runtime mock/debug paths.

- [ ] **Step 2: Delete obsolete tests**

Delete tests that are only about removed runtime mock infrastructure, for example:

```text
app/src/test/java/com/hc/mixthebluetooth/remote/MockServerTest.java
```

Do not delete:

```text
ServerEndpointsContractTest
CgmJobRespParsingTest
DefaultCgmServiceTest
DefaultAuthServiceTest
DefaultFileServiceTest
DefaultDeviceDataServiceTest
StaticAuthServiceTest
StaticCgmServiceTest
StaticLoopServiceChainTest
```

- [ ] **Step 3: Run the realistic backend/service contract test set**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.remote.ServerEndpointsContractTest" --tests "com.hc.mixthebluetooth.remote.CgmJobRespParsingTest" --tests "com.hc.mixthebluetooth.impl.cgm.*" --tests "com.hc.mixthebluetooth.impl.auth.*" --tests "com.hc.mixthebluetooth.impl.StaticLoopServiceChainTest"
```

Expected: PASS.

- [ ] **Step 4: Run all unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS. If failures are from obsolete tests found in Step 1, delete or rewrite those tests so they match the current service architecture.

- [ ] **Step 5: Commit Task 6**

```powershell
git add app/src/test
git commit -m "test: align tests with static and real http loop"
```

If no test files changed, skip this commit.

---

### Task 7: Full Chain Verification

**Files:**
- No planned source edits unless verification exposes a defect.

- [ ] **Step 1: Build static and LAN APKs**

Run:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=static
.\gradlew.bat :app:assembleDebug -PapiEnv=lan
```

Expected: both builds pass.

- [ ] **Step 2: USB logcat static acceptance**

Install/run the static APK and start:

```powershell
adb logcat -v time -s BioAI.Http
```

Complete the app flow through login and CGM cache/upload/result.

Expected log milestones:

```text
AppApiBootstrap API ENV config
env=static
remote=StaticBioAiTransport
StaticAuthService API POST /api/account/v1/register request
StaticAuthService API POST /api/account/v1/login response
Controller API DEVICE_CONNECT state
Controller API BT_SEND command
Controller API BT_RECV data
UniFragment API CACHE_FILE_READY file
StaticCgmService API POST /api/test/v1/upload response
StaticCgmService API GET /api/cgm/v1/jobs/64 response attempt=1
Controller API RENDER cgmResult
CgmWidgetBinder API RENDER result
jobId=64
status=GENERATED
```

- [ ] **Step 3: No-USB static acceptance**

Run the static APK without USB attached. Complete the same app flow.

Expected: the app does not require adb reverse, a backend server, or a USB cable to complete the static service chain. If log visibility is needed without USB, use the existing in-app log page if available; otherwise record this as a follow-up rather than blocking the service implementation.

- [ ] **Step 4: LAN smoke test**

With backend/proxy listening on its host and Android using a concrete LAN URL, build:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=lan
```

Expected log milestones use `DefaultAuthService` and `DefaultCgmService` instead of static services. The CGM result request must be:

```text
GET /api/cgm/v1/jobs/{jobId}
```

- [ ] **Step 5: Real HTTP fault tolerance smoke checks**

Using MockWebServer tests and/or a temporarily unreachable LAN URL, confirm:

```text
register/login network failure -> logged failure, app callback gets error
upload non-200 -> txt file is not deleted
upload success without jobId -> controlled EMPTY_DATA error
CGM non-GENERATED -> polling continues until limit
CGM network failure -> retries until limit, then controlled error
CGM missing optional fields -> no crash in service parsing
```

Add or adjust tests if any of these fail in a way not already covered.

- [ ] **Step 6: Final commit for verification fixes**

If verification required fixes:

```powershell
git add app
git commit -m "fix: harden static and lan cgm loop"
```

If no files changed, do not create an empty commit.

---

## Risk Notes

- `0.0.0.0` is only for backend listening. Android target URLs must be concrete IPs or hostnames.
- Static CGM uses `jobId=64`; real CGM uses backend-provided `jobId`.
- Static mode should not delete local txt files. Real upload deletion behavior remains in `DefaultCgmService`.
- Avoid tests that only assert constants or constructors. Prefer chain outcomes, endpoint paths, retry behavior, file lifecycle, and parsed renderable data.
- Keep old tests only when they protect current behavior. Delete tests that preserve removed mock/debug behavior.

## Self-Review

- Endpoint consistency: all real CGM result paths use `/api/cgm/v1/jobs/{jobId}`.
- Static consistency: all static CGM result paths use `jobId=64`.
- Simplicity: no `ApiMode`, no test-only `Services` wrapper in `AppApiBootstrap`.
- Testing quality: tests check chain outcomes, endpoint contracts, and fault tolerance rather than trivial static assertions.
- Scope: no fake Bluetooth source, no new debug page, no large package restructure.
