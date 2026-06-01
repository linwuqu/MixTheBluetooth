# LAN + Static HTTP CGM Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a switchable static/LAN HTTP CGM loop so the app can complete register, login, device, upload, CGM result, and chart rendering without USB or backend, while keeping real LAN HTTP one configuration change away.

**Architecture:** Add `API_MODE` to environment config and select service implementations in `AppApiBootstrap`. `STATIC_LOOP` installs deterministic static auth and CGM services, with CGM fixed to `jobId=64`; `LAN_HTTP`, `DEV_HTTP`, and `PROD_HTTP` keep using Retrofit. UI and controller code continue to call the same `AppApi` interfaces.

**Tech Stack:** Android Java, Gradle BuildConfig fields, Retrofit 2, OkHttp, Gson, MPAndroidChart, JUnit 4, MockWebServer.

---

## Scope Check

This plan is one cohesive subsystem: environment selection plus static service implementations for the existing HTTP/CGM loop. It does not create a fake Bluetooth packet source, does not add a new debug screen, and does not alter the real board command path.

## File Map

- Create: `app/config/env.static.properties`
  Static app-internal loop configuration.
- Create: `app/config/env.lan.properties`
  LAN backend configuration.
- Modify: `app/config/env.dev.properties`
  Add `API_MODE=DEV_HTTP`.
- Modify: `app/config/env.prod.properties`
  Add `API_MODE=PROD_HTTP`.
- Modify: `app/build.gradle`
  Read `API_MODE` and expose it through `BuildConfig`.
- Modify: `app/src/main/java/com/hc/mixthebluetooth/api/EnvConfig.java`
  Add `ApiMode`, parsing, and helper methods.
- Test: `app/src/test/java/com/hc/mixthebluetooth/api/EnvConfigTest.java`
  Verify mode parsing and fallback behavior.
- Create: `app/src/main/java/com/hc/mixthebluetooth/staticdata/StaticBioAiFixtures.java`
  Deterministic static account, upload, and CGM data.
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/auth/StaticAuthService.java`
  Static account implementation using the existing `AuthService`.
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/auth/StaticAuthServiceTest.java`
  Verify register/login/detail and token persistence.
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmService.java`
  Static CGM implementation using `jobId=64`.
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmServiceTest.java`
  Verify upload, poll, generated data, and missing file behavior.
- Modify: `app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java`
  Select static vs Retrofit services based on `EnvConfig.apiMode`.
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/AppApiBootstrapModeTest.java`
  Verify service selection without needing Android instrumentation.
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`
  Add `BioAI.Http` logs for connection, send, receive/cache, and render handoff.
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`
  Log cache-file upload entry through `ApiTraceLogger`.
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java`
  Log CGM render summary through `ApiTraceLogger`.
- Test: existing unit tests plus focused new tests.

---

### Task 1: Add API_MODE Configuration

**Files:**
- Create: `app/config/env.static.properties`
- Create: `app/config/env.lan.properties`
- Modify: `app/config/env.dev.properties`
- Modify: `app/config/env.prod.properties`
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/api/EnvConfig.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/api/EnvConfigTest.java`

- [ ] **Step 1: Write failing EnvConfig tests**

Create `app/src/test/java/com/hc/mixthebluetooth/api/EnvConfigTest.java`:

```java
package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EnvConfigTest {
    @Test
    public void parsesStaticMode() {
        EnvConfig env = EnvConfig.fromValues(
                "static",
                "http://127.0.0.1/",
                "STATIC_LOOP",
                true
        );

        assertEquals("static", env.env);
        assertEquals("http://127.0.0.1/", env.baseUrl);
        assertEquals(EnvConfig.ApiMode.STATIC_LOOP, env.apiMode);
        assertTrue(env.isStaticLoop());
        assertFalse(env.usesRealHttp());
    }

    @Test
    public void parsesLanMode() {
        EnvConfig env = EnvConfig.fromValues(
                "lan",
                "http://192.168.10.23:8080/",
                "LAN_HTTP",
                true
        );

        assertEquals(EnvConfig.ApiMode.LAN_HTTP, env.apiMode);
        assertFalse(env.isStaticLoop());
        assertTrue(env.usesRealHttp());
    }

    @Test
    public void unknownModeFallsBackFromEnvName() {
        EnvConfig staticEnv = EnvConfig.fromValues(
                "static",
                "http://127.0.0.1/",
                "",
                true
        );
        EnvConfig prodEnv = EnvConfig.fromValues(
                "prod",
                "https://example.com/",
                "not-a-mode",
                false
        );

        assertEquals(EnvConfig.ApiMode.STATIC_LOOP, staticEnv.apiMode);
        assertEquals(EnvConfig.ApiMode.PROD_HTTP, prodEnv.apiMode);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.api.EnvConfigTest"
```

Expected: FAIL because `EnvConfig.ApiMode`, `fromValues`, `isStaticLoop`, and `usesRealHttp` do not exist.

- [ ] **Step 3: Add environment files**

Create `app/config/env.static.properties`:

```properties
API_ENV=static
API_BASE_URL=http://127.0.0.1/
API_MODE=STATIC_LOOP
```

Create `app/config/env.lan.properties`:

```properties
API_ENV=lan
API_BASE_URL=http://124.16.68.18:8080/
API_MODE=LAN_HTTP
```

Replace `app/config/env.dev.properties` with:

```properties
API_ENV=dev
API_BASE_URL=http://124.16.68.18:8080/
API_MODE=DEV_HTTP
```

Replace `app/config/env.prod.properties` with:

```properties
API_ENV=prod
API_BASE_URL=https://example.com/
API_MODE=PROD_HTTP
```

- [ ] **Step 4: Expose API_MODE in Gradle**

In `app/build.gradle`, replace the existing `buildConfigField` block inside `defaultConfig` with:

```groovy
        buildConfigField "String", "API_ENV", "\"${envProps.getProperty("API_ENV", envName)}\""
        buildConfigField "String", "API_BASE_URL", "\"${envProps.getProperty("API_BASE_URL", "http://10.0.2.2:8080/")}\""
        buildConfigField "String", "API_MODE", "\"${envProps.getProperty("API_MODE", envName == "static" ? "STATIC_LOOP" : "DEV_HTTP")}\""
```

- [ ] **Step 5: Implement EnvConfig mode parsing**

Replace `app/src/main/java/com/hc/mixthebluetooth/api/EnvConfig.java` with:

```java
package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.BuildConfig;

import java.util.Locale;

public final class EnvConfig {
    public enum ApiMode {
        STATIC_LOOP,
        LAN_HTTP,
        DEV_HTTP,
        PROD_HTTP
    }

    @NonNull
    public final String env;
    @NonNull
    public final String baseUrl;
    public final boolean debug;
    @NonNull
    public final ApiMode apiMode;
    @NonNull
    public final String remoteName;
    public final boolean networkEnabled;

    public EnvConfig(@NonNull String env,
                     @NonNull String baseUrl,
                     boolean debug,
                     @NonNull ApiMode apiMode,
                     @NonNull String remoteName,
                     boolean networkEnabled) {
        this.env = env;
        this.baseUrl = baseUrl;
        this.debug = debug;
        this.apiMode = apiMode;
        this.remoteName = remoteName;
        this.networkEnabled = networkEnabled;
    }

    @NonNull
    public static EnvConfig fromBuildConfig() {
        return fromValues(
                BuildConfig.API_ENV,
                BuildConfig.API_BASE_URL,
                BuildConfig.API_MODE,
                BuildConfig.DEBUG
        );
    }

    @NonNull
    public static EnvConfig fromValues(@NonNull String env,
                                       @NonNull String baseUrl,
                                       @NonNull String modeName,
                                       boolean debug) {
        ApiMode mode = parseMode(env, modeName);
        return new EnvConfig(env, baseUrl, debug, mode, "", mode != ApiMode.STATIC_LOOP);
    }

    @NonNull
    public EnvConfig withRemote(@NonNull String remoteName, boolean networkEnabled) {
        return new EnvConfig(env, baseUrl, debug, apiMode, remoteName, networkEnabled);
    }

    public boolean isStaticLoop() {
        return apiMode == ApiMode.STATIC_LOOP;
    }

    public boolean usesRealHttp() {
        return apiMode != ApiMode.STATIC_LOOP;
    }

    @NonNull
    private static ApiMode parseMode(@NonNull String env, @NonNull String modeName) {
        String normalized = modeName.trim().toUpperCase(Locale.US);
        for (ApiMode mode : ApiMode.values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        String envName = env.trim().toLowerCase(Locale.US);
        if ("static".equals(envName)) {
            return ApiMode.STATIC_LOOP;
        }
        if ("lan".equals(envName)) {
            return ApiMode.LAN_HTTP;
        }
        if ("prod".equals(envName)) {
            return ApiMode.PROD_HTTP;
        }
        return ApiMode.DEV_HTTP;
    }
}
```

- [ ] **Step 6: Run focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.api.EnvConfigTest"
```

Expected: PASS.

- [ ] **Step 7: Commit Task 1**

```powershell
git add app/config/env.static.properties app/config/env.lan.properties app/config/env.dev.properties app/config/env.prod.properties app/build.gradle app/src/main/java/com/hc/mixthebluetooth/api/EnvConfig.java app/src/test/java/com/hc/mixthebluetooth/api/EnvConfigTest.java
git commit -m "feat: add api mode configuration"
```

---

### Task 2: Add Static Fixtures And Static Auth Service

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/staticdata/StaticBioAiFixtures.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/auth/StaticAuthService.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/auth/StaticAuthServiceTest.java`

- [ ] **Step 1: Write failing StaticAuthService tests**

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
    public void registerReturnsDeterministicStaticUser() {
        StaticAuthService service = new StaticAuthService(new SessionStore(new MemoryStore()));
        AtomicReference<CallResult<AuthUser>> result = new AtomicReference<>();

        service.register(
                StaticBioAiFixtures.USERNAME,
                StaticBioAiFixtures.PASSWORD,
                StaticBioAiFixtures.PHONE,
                result::set
        );

        assertNotNull(result.get());
        assertTrue(result.get().isOk());
        assertEquals(StaticBioAiFixtures.ACCOUNT_ID, result.get().data.accountId);
        assertEquals(StaticBioAiFixtures.USERNAME, result.get().data.username);
        assertEquals(StaticBioAiFixtures.PHONE, result.get().data.phone);
    }

    @Test
    public void loginSavesStaticSession() {
        SessionStore store = new SessionStore(new MemoryStore());
        StaticAuthService service = new StaticAuthService(store);
        AtomicReference<CallResult<AuthUser>> result = new AtomicReference<>();

        service.login(StaticBioAiFixtures.PHONE, StaticBioAiFixtures.PASSWORD, result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isOk());
        assertEquals(StaticBioAiFixtures.STATIC_TOKEN, result.get().data.token);
        assertNotNull(store.currentUser());
        assertEquals(StaticBioAiFixtures.STATIC_TOKEN, store.currentUser().token);
    }

    @Test
    public void detailReturnsCurrentStaticSession() {
        SessionStore store = new SessionStore(new MemoryStore());
        StaticAuthService service = new StaticAuthService(store);
        AtomicReference<CallResult<AuthUser>> result = new AtomicReference<>();

        service.login(StaticBioAiFixtures.PHONE, StaticBioAiFixtures.PASSWORD, value -> { });
        service.detail(result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isOk());
        assertEquals(StaticBioAiFixtures.ACCOUNT_ID, result.get().data.accountId);
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

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.auth.StaticAuthServiceTest"
```

Expected: FAIL because `StaticAuthService` and `StaticBioAiFixtures` do not exist.

- [ ] **Step 3: Create StaticBioAiFixtures**

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

- [ ] **Step 4: Create StaticAuthService**

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

- [ ] **Step 5: Run focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.auth.StaticAuthServiceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/staticdata/StaticBioAiFixtures.java app/src/main/java/com/hc/mixthebluetooth/impl/auth/StaticAuthService.java app/src/test/java/com/hc/mixthebluetooth/impl/auth/StaticAuthServiceTest.java
git commit -m "feat: add static auth service"
```

---

### Task 3: Add Static CGM Service With jobId=64

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmService.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmServiceTest.java`

- [ ] **Step 1: Write failing StaticCgmService tests**

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
    public void uploadAndPollReturnsStaticJob64AndKeepsLocalFile() throws Exception {
        File file = temporaryFolder.newFile("cgm-cache.txt");
        Files.write(file.toPath(), "Start Playback\nstatic payload\n".getBytes(StandardCharsets.UTF_8));
        StaticCgmService service = new StaticCgmService();
        AtomicReference<CallResult<ServerModels.CgmJobData>> result = new AtomicReference<>();

        service.uploadAndPoll(file, result::set);

        assertNotNull(result.get());
        assertTrue(result.get().isOk());
        assertNotNull(result.get().data);
        assertEquals(StaticBioAiFixtures.STATIC_JOB_ID, result.get().data.jobId);
        assertEquals("GENERATED", result.get().data.status);
        assertEquals(6, result.get().data.pointCount);
        assertTrue(file.exists());
    }

    @Test
    public void pollOnlyAcceptsStaticJob64() {
        StaticCgmService service = new StaticCgmService();
        AtomicReference<CallResult<ServerModels.CgmJobData>> success = new AtomicReference<>();
        AtomicReference<CallResult<ServerModels.CgmJobData>> failure = new AtomicReference<>();

        service.poll(64L, success::set);
        service.poll(65L, failure::set);

        assertTrue(success.get().isOk());
        assertEquals(64L, success.get().data.jobId);
        assertFalse(failure.get().isOk());
        assertEquals(CallResult.EMPTY_DATA, failure.get().code);
    }

    @Test
    public void uploadMissingFileReturnsLocalError() {
        StaticCgmService service = new StaticCgmService();
        AtomicReference<CallResult<ServerModels.CgmJobData>> result = new AtomicReference<>();

        service.uploadAndPoll(new File(temporaryFolder.getRoot(), "missing.txt"), result::set);

        assertNotNull(result.get());
        assertFalse(result.get().isOk());
        assertEquals(CallResult.LOCAL_FILE_NOT_FOUND, result.get().code);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.cgm.StaticCgmServiceTest"
```

Expected: FAIL because `StaticCgmService` does not exist.

- [ ] **Step 3: Create StaticCgmService**

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

- [ ] **Step 4: Run focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.cgm.StaticCgmServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Run real CGM tests to protect existing behavior**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.cgm.DefaultCgmServiceTest" --tests "com.hc.mixthebluetooth.remote.ServerEndpointsContractTest"
```

Expected: PASS; `DefaultCgmService` still uploads to `/api/test/v1/upload` and polls `/api/cgm/v1/jobs/{jobId}`.

- [ ] **Step 6: Commit Task 3**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmService.java app/src/test/java/com/hc/mixthebluetooth/impl/cgm/StaticCgmServiceTest.java
git commit -m "feat: add static cgm service"
```

---

### Task 4: Select Static Or Retrofit Services In AppApiBootstrap

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/AppApiBootstrapModeTest.java`

- [ ] **Step 1: Write failing service selection test**

Create `app/src/test/java/com/hc/mixthebluetooth/impl/AppApiBootstrapModeTest.java`:

```java
package com.hc.mixthebluetooth.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.EnvConfig;
import com.hc.mixthebluetooth.impl.auth.StaticAuthService;
import com.hc.mixthebluetooth.impl.cgm.DefaultCgmService;
import com.hc.mixthebluetooth.impl.cgm.StaticCgmService;
import com.hc.mixthebluetooth.local.SessionStore;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AppApiBootstrapModeTest {
    @Test
    public void staticModeCreatesStaticServices() {
        EnvConfig env = EnvConfig.fromValues("static", "http://127.0.0.1/", "STATIC_LOOP", true);
        AppApiBootstrap.Services services = AppApiBootstrap.createCoreServicesForTest(
                env,
                new SessionStore(new MemoryStore())
        );

        assertTrue(services.auth instanceof StaticAuthService);
        assertTrue(services.cgm instanceof StaticCgmService);
        assertEquals("StaticBioAiTransport", services.remoteName);
        assertEquals(false, services.networkEnabled);
    }

    @Test
    public void lanModeCreatesRetrofitServices() {
        EnvConfig env = EnvConfig.fromValues("lan", "http://127.0.0.1:8080/", "LAN_HTTP", true);
        AppApiBootstrap.Services services = AppApiBootstrap.createCoreServicesForTest(
                env,
                new SessionStore(new MemoryStore())
        );

        assertTrue(services.cgm instanceof DefaultCgmService);
        assertEquals("RetrofitServer(http://127.0.0.1:8080/)", services.remoteName);
        assertEquals(true, services.networkEnabled);
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

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.AppApiBootstrapModeTest"
```

Expected: FAIL because `AppApiBootstrap.Services` and `createCoreServicesForTest` do not exist.

- [ ] **Step 3: Add service factory to AppApiBootstrap**

Replace `app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java` with:

```java
package com.hc.mixthebluetooth.impl;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.EnvConfig;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.impl.auth.DefaultAuthService;
import com.hc.mixthebluetooth.impl.auth.StaticAuthService;
import com.hc.mixthebluetooth.impl.cgm.DefaultCgmService;
import com.hc.mixthebluetooth.impl.cgm.StaticCgmService;
import com.hc.mixthebluetooth.impl.device.DefaultDeviceDataService;
import com.hc.mixthebluetooth.impl.file.DefaultFileService;
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
import com.hc.mixthebluetooth.local.DeviceDataRecorder;
import com.hc.mixthebluetooth.local.DeviceReplaySample;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.remote.ServerClient;
import com.hc.mixthebluetooth.remote.ServerEndpoints;

public final class AppApiBootstrap {
    public static final class Services {
        @NonNull
        public final AuthService auth;
        @NonNull
        public final FileService file;
        @NonNull
        public final CgmService cgm;
        @NonNull
        public final String remoteName;
        public final boolean networkEnabled;

        Services(@NonNull AuthService auth,
                 @NonNull FileService file,
                 @NonNull CgmService cgm,
                 @NonNull String remoteName,
                 boolean networkEnabled) {
            this.auth = auth;
            this.file = file;
            this.cgm = cgm;
            this.remoteName = remoteName;
            this.networkEnabled = networkEnabled;
        }
    }

    private static boolean initialized;

    private AppApiBootstrap() {
    }

    public static synchronized void init(Context context) {
        if (initialized) return;

        Context app = context.getApplicationContext();
        EnvConfig env = EnvConfig.fromBuildConfig();
        SessionStore sessionStore = new SessionStore(app);
        Services services = createCoreServices(env, sessionStore);
        env = env.withRemote(services.remoteName, services.networkEnabled);

        ApiTraceLogger.text("AppApiBootstrap", "ENV", "config",
                "env=" + env.env
                        + "\nmode=" + env.apiMode
                        + "\nbaseUrl=" + env.baseUrl
                        + "\nremote=" + env.remoteName);

        DeviceDataService deviceDataService = new DefaultDeviceDataService(
                new DeviceDataRecorder(app),
                new DeviceReplaySample(app),
                services.file
        );

        AppApi.install(services.auth, services.file, deviceDataService, services.cgm, env);
        initialized = true;
    }

    @NonNull
    static Services createCoreServicesForTest(@NonNull EnvConfig env, @NonNull SessionStore sessionStore) {
        return createCoreServices(env, sessionStore);
    }

    @NonNull
    private static Services createCoreServices(@NonNull EnvConfig env, @NonNull SessionStore sessionStore) {
        if (env.isStaticLoop()) {
            ServerEndpoints endpoints = ServerClient.create(env.baseUrl, sessionStore, false);
            FileService fileService = new DefaultFileService(endpoints);
            return new Services(
                    new StaticAuthService(sessionStore),
                    fileService,
                    new StaticCgmService(),
                    "StaticBioAiTransport",
                    false
            );
        }

        ServerEndpoints endpoints = ServerClient.create(env.baseUrl, sessionStore, env.debug);
        FileService fileService = new DefaultFileService(endpoints);
        return new Services(
                new DefaultAuthService(endpoints, sessionStore),
                fileService,
                new DefaultCgmService(endpoints),
                "RetrofitServer(" + env.baseUrl + ")",
                true
        );
    }

    public static synchronized void resetForTest() {
        initialized = false;
        AppApi.clearForTest();
    }
}
```

- [ ] **Step 4: Run focused bootstrap test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.AppApiBootstrapModeTest"
```

Expected: PASS.

- [ ] **Step 5: Run existing API tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.auth.DefaultAuthServiceTest" --tests "com.hc.mixthebluetooth.impl.file.DefaultFileServiceTest" --tests "com.hc.mixthebluetooth.impl.cgm.*"
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java app/src/test/java/com/hc/mixthebluetooth/impl/AppApiBootstrapModeTest.java
git commit -m "feat: select api services by mode"
```

---

### Task 5: Add Missing BioAI.Http Logs For Device And Render Nodes

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java`

- [ ] **Step 1: Add controller log helper**

In `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`, add this import:

```java
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
```

Add these constants near `AUTO_CLEAR_BYTES`:

```java
    private static final String OWNER = "Controller";
    private static final String API_BT_SEND = "BT_SEND";
    private static final String API_BT_RECV = "BT_RECV";
    private static final String API_DEVICE_CONNECT = "DEVICE_CONNECT";
    private static final String API_RENDER = "RENDER";
```

- [ ] **Step 2: Log connection events**

In `Controller.onEvent`, replace the connected/disconnected branches with:

```java
        } else if (event instanceof BTPackage.Connected) {
            module = ((BTPackage.Connected) event).module;
            ApiTraceLogger.text(OWNER, API_DEVICE_CONNECT, "state",
                    "connected=true\ndevice=" + (module == null ? "" : module.getName()));
        } else if (event instanceof BTPackage.Disconnected) {
            module = null;
            ApiTraceLogger.text(OWNER, API_DEVICE_CONNECT, "state", "connected=false");
```

- [ ] **Step 3: Log BT send commands**

In `Controller.handleAction`, before `gateway.postText(...)`, replace the POST branch with:

```java
        if (action.route == Route.POST && action.textSupplier != null && module != null) {
            String payload = action.textSupplier.get();
            ApiTraceLogger.text(OWNER, API_BT_SEND, "command",
                    "id=" + action.id + "\npayload=" + payload);
            gateway.postText(module, payload);
            return;
        }
```

- [ ] **Step 4: Log BT receive text and CGM result handoff**

In `Controller.onBtData`, after decoding `text` and before creating `FragmentMessageItem`, add:

```java
        ApiTraceLogger.text(OWNER, API_BT_RECV, "data",
                "bytes=" + data.bytes.length + "\ntext=" + text);
```

In `Controller.onCgmResult`, add the render handoff log before iterating widgets:

```java
        ApiTraceLogger.text(OWNER, API_RENDER, "cgmResult",
                "jobId=" + result.jobId
                        + "\nstatus=" + result.status
                        + "\npointCount=" + result.pointCount
                        + "\nunitCount=" + result.unitCount);
```

- [ ] **Step 5: Log UniFragment cache-file entry**

In `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`, add:

```java
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
```

Replace the first line of `FragmentGateway.onCacheFileReady` with:

```java
            ApiTraceLogger.file("UniFragment", "CACHE_FILE_READY", file);
```

- [ ] **Step 6: Log widget render summary**

In `app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java`, add:

```java
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
```

In `CgmResultMetricWidget.onCgmResult`, after `ArrayList<Entry> actual = new ArrayList<>();` and after the loop has populated both lists, add:

```java
            ApiTraceLogger.text("CgmWidgetBinder", "RENDER", "result",
                    "jobId=" + result.jobId
                            + "\nstatus=" + result.status
                            + "\npoints=" + predicted.size()
                            + "\npredicted=" + predicted.size()
                            + "\nactual=" + actual.size()
                            + "\nunitCount=" + result.unitCount);
```

- [ ] **Step 7: Run compile and unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.*" --tests "com.hc.mixthebluetooth.impl.cgm.*"
```

Expected: PASS.

- [ ] **Step 8: Commit Task 5**

```powershell
git add app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java app/src/main/java/com/hc/mixthebluetooth/uni/Widgets.java
git commit -m "feat: log cgm loop milestones"
```

---

### Task 6: Final Verification

**Files:**
- No planned source edits.

- [ ] **Step 1: Run full unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build static debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=static
```

Expected: BUILD SUCCESSFUL and generated debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Build LAN debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=lan
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Confirm endpoint contract tests still protect real paths**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.remote.ServerEndpointsContractTest"
```

Expected: PASS, including:

```text
testUploadEndpointUsesMultipartFileOnly
cgmEndpointUsesFixedJobPathWithoutQuery
```

- [ ] **Step 5: Manual USB logcat check**

Run:

```powershell
adb logcat -v time -s BioAI.Http
```

Expected static flow log contains:

```text
AppApiBootstrap API ENV config
mode=STATIC_LOOP
StaticAuthService API POST /api/account/v1/register request
StaticAuthService API POST /api/account/v1/login response
StaticCgmService API POST /api/test/v1/upload response
StaticCgmService API GET /api/cgm/v1/jobs/64 response attempt=1
CgmWidgetBinder API RENDER result
jobId=64
```

- [ ] **Step 6: Commit verification note only if files changed**

If verification required changing `env.lan.properties` to the current AP-assigned host IP, commit that config change:

```powershell
git add app/config/env.lan.properties
git commit -m "chore: update lan api url"
```

If no files changed during verification, do not create an empty commit.

---

## Risks And Guardrails

- Do not use `0.0.0.0` as an Android target URL.
- Do not add adb reverse or USB-only networking.
- Do not add a separate debug page for the static loop.
- Do not remove `DefaultCgmService` or Retrofit endpoints.
- Do not change the confirmed real CGM path: `/api/cgm/v1/jobs/{jobId}`.
- Static loop must use `jobId=64`.
- Static CGM upload should not delete the local txt file; real upload deletion remains controlled by `DefaultCgmService`.
- UI code should continue to call `AppApi.auth()` and `AppApi.cgm()` without checking API mode.

## Self-Review

- Spec coverage: Tasks cover API mode config, static loop, LAN real HTTP config, `jobId=64`, logging, tests, and build verification.
- Placeholder scan: No banned placeholder markers or unresolved file names remain.
- Type consistency: `EnvConfig.ApiMode`, `StaticBioAiFixtures.STATIC_JOB_ID`, `StaticAuthService`, `StaticCgmService`, and `AppApiBootstrap.Services` are defined before later tasks use them.
- Endpoint consistency: All CGM result paths use `/api/cgm/v1/jobs/{jobId}`; static examples use `/api/cgm/v1/jobs/64`.
