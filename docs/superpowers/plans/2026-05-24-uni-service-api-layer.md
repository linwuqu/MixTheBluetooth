# Uni Service API Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the fourth/fifth-stage API, auth, file upload, device replay, and debug verification paths behind a stable `api/` Service API layer.

**Architecture:** Activity, Debug, and Uni call only `api/`. `impl/` provides default service implementations, `remote/` owns Retrofit and mock server endpoints, and `local/` owns session/file/sample handling. mock/dev/prod differ only by `ServerEndpoints` selection; the default service implementations are shared.

**Tech Stack:** Android Java, ViewBinding, Retrofit 2, OkHttp, MockWebServer, JUnit 4.

---

## Scope And Guardrails

Spec:

```text
docs/superpowers/specs/2026-05-24-uni-service-api-layer-design.md
```

Guardrails:

- Do not introduce Hilt/Dagger/ARouter.
- Do not split Gradle modules.
- Do not rename Uni core classes unless required by compile errors.
- Do not change legacy Bluetooth CMD strings.
- Do not add CGM naming to `api/device`; CGM remains a current `local/` implementation detail.
- Keep mock/dev/prod difference at `ServerEndpoints`: `MockServer` vs `ServerClient.create(baseUrl)`.

Execution should happen in an isolated worktree based on the current service-api branch/checkpoint. Before editing:

```powershell
git status --short
git branch --show-current
```

If unrelated user edits exist outside files in this plan, leave them alone.

---

## File Map

Create:

```text
app/src/main/java/com/hc/mixthebluetooth/api/AppApi.java
app/src/main/java/com/hc/mixthebluetooth/api/ApiCallback.java
app/src/main/java/com/hc/mixthebluetooth/api/CallResult.java
app/src/main/java/com/hc/mixthebluetooth/api/auth/AuthService.java
app/src/main/java/com/hc/mixthebluetooth/api/auth/AuthUser.java
app/src/main/java/com/hc/mixthebluetooth/api/file/FileService.java
app/src/main/java/com/hc/mixthebluetooth/api/file/UploadedFile.java
app/src/main/java/com/hc/mixthebluetooth/api/device/DeviceDataService.java

app/src/main/java/com/hc/mixthebluetooth/impl/EnvConfig.java
app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java
app/src/main/java/com/hc/mixthebluetooth/impl/auth/DefaultAuthService.java
app/src/main/java/com/hc/mixthebluetooth/impl/file/DefaultFileService.java
app/src/main/java/com/hc/mixthebluetooth/impl/device/DefaultDeviceDataService.java

app/src/main/java/com/hc/mixthebluetooth/remote/ServerClient.java
app/src/main/java/com/hc/mixthebluetooth/remote/ServerEndpoints.java
app/src/main/java/com/hc/mixthebluetooth/remote/ServerResponse.java
app/src/main/java/com/hc/mixthebluetooth/remote/ServerModels.java
app/src/main/java/com/hc/mixthebluetooth/remote/MockServer.java

app/src/main/java/com/hc/mixthebluetooth/local/SessionStore.java
app/src/main/java/com/hc/mixthebluetooth/local/DeviceDataRecorder.java
app/src/main/java/com/hc/mixthebluetooth/local/DeviceReplaySample.java

app/src/main/java/com/hc/mixthebluetooth/debug/VerificationActivity.java
app/src/main/res/layout/activity_verification.xml

app/src/debug/assets/device/device_replay_sample.txt
app/src/test/resources/device/device_replay_sample.txt

app/src/test/java/com/hc/mixthebluetooth/api/CallResultTest.java
app/src/test/java/com/hc/mixthebluetooth/remote/MockServerTest.java
app/src/test/java/com/hc/mixthebluetooth/remote/ServerEndpointsContractTest.java
app/src/test/java/com/hc/mixthebluetooth/impl/auth/DefaultAuthServiceTest.java
app/src/test/java/com/hc/mixthebluetooth/impl/file/DefaultFileServiceTest.java
app/src/test/java/com/hc/mixthebluetooth/impl/device/DefaultDeviceDataServiceTest.java
```

Modify:

```text
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/com/hc/mixthebluetooth/activity/LoginActivity.java
app/src/main/java/com/hc/mixthebluetooth/activity/AccountRegisterActivity.java
app/src/main/java/com/hc/mixthebluetooth/activity/DebugActivity.java
app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java
app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java
```

Delete after migration:

```text
app/src/main/java/com/hc/mixthebluetooth/api/ApiClient.java
app/src/main/java/com/hc/mixthebluetooth/api/ApiEnvironment.java
app/src/main/java/com/hc/mixthebluetooth/api/ApiModels.java
app/src/main/java/com/hc/mixthebluetooth/api/AuthApi.java
app/src/main/java/com/hc/mixthebluetooth/api/FileApi.java
app/src/main/java/com/hc/mixthebluetooth/api/FileUploadUseCase.java
app/src/main/java/com/hc/mixthebluetooth/api/MockApi.java
app/src/main/java/com/hc/mixthebluetooth/auth/AuthRepository.java
app/src/main/java/com/hc/mixthebluetooth/auth/AuthSessionStore.java
app/src/main/java/com/hc/mixthebluetooth/cgm/CgmReplayFixture.java
app/src/main/java/com/hc/mixthebluetooth/cgm/CgmReplayUploadUseCase.java
app/src/main/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmPlaybackRecorder.java
```

Only delete old files after all references are replaced and tests pass.

---

## Task 1: Add API Contract Layer

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/CallResult.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/AppApi.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/auth/AuthService.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/auth/AuthUser.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/file/FileService.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/file/UploadedFile.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/device/DeviceDataService.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/api/CallResultTest.java`

- [ ] **Step 1: Create `CallResult`**

```java
package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class CallResult<T> {
    public static final int UNKNOWN = -1;
    public static final int NETWORK = -100;
    public static final int EMPTY_RESPONSE = -101;
    public static final int EMPTY_DATA = -102;
    public static final int LOCAL_FILE_NOT_FOUND = -200;
    public static final int DEVICE_REPLAY_INCOMPLETE = -300;

    public enum State {
        OK,
        PENDING,
        ERROR
    }

    @NonNull
    public final State state;
    public final int code;
    @NonNull
    public final String message;
    @Nullable
    public final T data;
    @Nullable
    public final Throwable cause;

    private CallResult(@NonNull State state, int code, @NonNull String message,
                       @Nullable T data, @Nullable Throwable cause) {
        this.state = state;
        this.code = code;
        this.message = message;
        this.data = data;
        this.cause = cause;
    }

    @NonNull
    public static <T> CallResult<T> ok(@Nullable T data) {
        return new CallResult<>(State.OK, 0, "", data, null);
    }

    @NonNull
    public static <T> CallResult<T> pending(@NonNull String message) {
        return new CallResult<>(State.PENDING, 0, message, null, null);
    }

    @NonNull
    public static <T> CallResult<T> error(int code, @NonNull String message,
                                          @Nullable Throwable cause) {
        return new CallResult<>(State.ERROR, code, message, null, cause);
    }

    public boolean isOk() {
        return state == State.OK;
    }

    public boolean isPending() {
        return state == State.PENDING;
    }

    public boolean isError() {
        return state == State.ERROR;
    }
}
```

- [ ] **Step 2: Add `CallResultTest`**

```java
package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallResultTest {
    @Test
    public void okCarriesData() {
        CallResult<String> result = CallResult.ok("value");

        assertTrue(result.isOk());
        assertEquals("value", result.data);
        assertEquals(0, result.code);
    }

    @Test
    public void pendingHasNoData() {
        CallResult<String> result = CallResult.pending("recording");

        assertTrue(result.isPending());
        assertEquals("recording", result.message);
        assertNull(result.data);
    }

    @Test
    public void errorCarriesCodeAndCause() {
        RuntimeException cause = new RuntimeException("boom");

        CallResult<String> result = CallResult.error(CallResult.NETWORK, "网络错误", cause);

        assertTrue(result.isError());
        assertEquals(CallResult.NETWORK, result.code);
        assertEquals(cause, result.cause);
    }
}
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.api.CallResultTest"
```

Expected: PASS.

- [ ] **Step 3: Create API data and service interfaces**

Create `AuthUser.java`:

```java
package com.hc.mixthebluetooth.api.auth;

import androidx.annotation.Nullable;

public final class AuthUser {
    public long accountId;
    @Nullable
    public String username;
    @Nullable
    public String phone;
    @Nullable
    public String token;
}
```

Create `AuthService.java`:

```java
package com.hc.mixthebluetooth.api.auth;

import com.hc.mixthebluetooth.api.CallResult;

public interface AuthService {
    void register(String username, String password, String phone, ApiCallback<CallResult<AuthUser>> callback);

    void login(String phoneOrAccount, String password, ApiCallback<CallResult<AuthUser>> callback);

    void detail(ApiCallback<CallResult<AuthUser>> callback);

    AuthUser currentUser();

    void clearSession();
}
```

Create `UploadedFile.java`:

```java
package com.hc.mixthebluetooth.api.file;

import androidx.annotation.Nullable;

public final class UploadedFile {
    public long fileId;
    @Nullable
    public String fileName;
    @Nullable
    public String path;
    @Nullable
    public String url;
}
```

Create `FileService.java`:

```java
package com.hc.mixthebluetooth.api.file;

import com.hc.mixthebluetooth.api.CallResult;

import java.io.File;

public interface FileService {
    void upload(File file, ApiCallback<CallResult<UploadedFile>> callback);
}
```

Create `DeviceDataService.java`:

```java
package com.hc.mixthebluetooth.api.device;

import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.file.UploadedFile;

import java.io.File;

public interface DeviceDataService {
    void replaySample(ApiCallback<CallResult<File>> callback);

    void consumeLine(String line, ApiCallback<CallResult<File>> callback);

    File lastDataFile();

    void uploadLastDataFile(ApiCallback<CallResult<UploadedFile>> callback);
}
```

- [ ] **Step 4: Create `AppApi`**

```java
package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.impl.EnvConfig;

public final class AppApi {
    private static AuthService auth;
    private static FileService file;
    private static DeviceDataService deviceData;
    private static EnvConfig env;

    private AppApi() {
    }

    public static synchronized void install(@NonNull AuthService authService,
                                            @NonNull FileService fileService,
                                            @NonNull DeviceDataService deviceDataService,
                                            @NonNull EnvConfig envConfig) {
        auth = authService;
        file = fileService;
        deviceData = deviceDataService;
        env = envConfig;
    }

    @NonNull
    public static synchronized AuthService auth() {
        if (auth == null) throw new IllegalStateException("AppApi is not initialized");
        return auth;
    }

    @NonNull
    public static synchronized FileService file() {
        if (file == null) throw new IllegalStateException("AppApi is not initialized");
        return file;
    }

    @NonNull
    public static synchronized DeviceDataService deviceData() {
        if (deviceData == null) throw new IllegalStateException("AppApi is not initialized");
        return deviceData;
    }

    @NonNull
    public static synchronized EnvConfig env() {
        if (env == null) throw new IllegalStateException("AppApi is not initialized");
        return env;
    }

    public static synchronized void clearForTest() {
        auth = null;
        file = null;
        deviceData = null;
        env = null;
    }
}
```

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS after `EnvConfig` is added in Task 4. If run before Task 4, expected compile error is missing `EnvConfig`.

---

## Task 2: Add Remote Server Layer

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/remote/ServerResponse.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/remote/ServerModels.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/remote/ServerEndpoints.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/remote/ServerClient.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/remote/MockServer.java`
- Modify: `app/build.gradle`
- Test: `app/src/test/java/com/hc/mixthebluetooth/remote/MockServerTest.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/remote/ServerEndpointsContractTest.java`

- [ ] **Step 1: Ensure dependencies exist**

In `app/build.gradle`, keep/add:

```groovy
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'
```

- [ ] **Step 2: Create server DTOs**

Create `ServerResponse.java`:

```java
package com.hc.mixthebluetooth.remote;

import androidx.annotation.Nullable;

public final class ServerResponse<T> {
    public int code;
    public boolean success;
    @Nullable
    public String msg;
    @Nullable
    public T data;

    public static <T> ServerResponse<T> success(T data) {
        ServerResponse<T> response = new ServerResponse<>();
        response.code = 0;
        response.success = true;
        response.msg = "";
        response.data = data;
        return response;
    }
}
```

Create `ServerModels.java`:

```java
package com.hc.mixthebluetooth.remote;

import androidx.annotation.Nullable;

public final class ServerModels {
    private ServerModels() {
    }

    public static final class LoginReq {
        public String phone;
        public String password;

        public LoginReq(String phone, String password) {
            this.phone = phone;
            this.password = password;
        }
    }

    public static final class RegisterReq {
        public String username;
        public String password;
        public String phone;
        @Nullable
        public String avatarUrl;

        public RegisterReq(String username, String password, String phone, @Nullable String avatarUrl) {
            this.username = username;
            this.password = password;
            this.phone = phone;
            this.avatarUrl = avatarUrl;
        }
    }

    public static final class AccountResp {
        public long accountId;
        @Nullable
        public String username;
        @Nullable
        public String phone;
        @Nullable
        public String avatarUrl;
        @Nullable
        public String token;
    }

    public static final class FileResp {
        public long fileId;
        @Nullable
        public String fileName;
        @Nullable
        public String path;
        @Nullable
        public String url;
    }
}
```

- [ ] **Step 3: Create `ServerEndpoints`**

```java
package com.hc.mixthebluetooth.remote;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ServerEndpoints {
    @POST("/api/account/v1/login")
    Call<ServerResponse<ServerModels.AccountResp>> login(@Body ServerModels.LoginReq req);

    @POST("/api/account/v1/register")
    Call<ServerResponse<ServerModels.AccountResp>> register(@Body ServerModels.RegisterReq req);

    @GET("/api/account/v1/detail")
    Call<ServerResponse<ServerModels.AccountResp>> detail();

    @Multipart
    @POST("/api/file/v1/upload")
    Call<ServerResponse<ServerModels.FileResp>> upload(
            @Part("fileName") RequestBody fileName,
            @Part("identify") RequestBody identify,
            @Part("parentId") RequestBody parentId,
            @Part("fileSize") RequestBody fileSize,
            @Part MultipartBody.Part file
    );
}
```

- [ ] **Step 4: Create `ServerClient`**

```java
package com.hc.mixthebluetooth.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ServerClient {
    public interface TokenProvider {
        @Nullable
        String token();
    }

    private ServerClient() {
    }

    @NonNull
    public static ServerEndpoints create(@NonNull String baseUrl, @NonNull TokenProvider tokenProvider) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request.Builder builder = chain.request().newBuilder();
                    String token = tokenProvider.token();
                    if (token != null && !token.trim().isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .build();

        return create(baseUrl, client);
    }

    @NonNull
    public static ServerEndpoints create(@NonNull String baseUrl, @NonNull OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ServerEndpoints.class);
    }
}
```

- [ ] **Step 5: Create `MockServer`**

`MockServer` implements `ServerEndpoints` and returns fake `Call<ServerResponse<T>>`. Reuse the existing `MockApi.FakeCall` code by moving it into `MockServer` as a nested `FakeCall`.

Required behavior:

```java
login(req)    -> accountId=1001, phone=req.phone, username="mock-user", token="mock-token"
register(req) -> accountId=1001, phone=req.phone, username=req.username, token="mock-token"
detail()      -> accountId=1001, phone="13800138000", username="mock-user", token="mock-token"
upload(...)   -> fileId=1, fileName="CGM_Cache_data.txt", path="/mock/CGM_Cache_data.txt"
```

- [ ] **Step 6: Add `MockServerTest`**

Test that `login(...).execute()` and `upload(...).execute()` return successful `ServerResponse`.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.remote.MockServerTest"
```

Expected: PASS.

- [ ] **Step 7: Add `ServerEndpointsContractTest`**

Use `MockWebServer` and `ServerClient.create(server.url("/").toString(), okHttpClient)` to verify:

```text
POST /api/account/v1/login
POST /api/account/v1/register
GET /api/account/v1/detail
POST /api/file/v1/upload
multipart body contains fileName, fileSize, CGM_Cache_data.txt content
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.remote.ServerEndpointsContractTest"
```

Expected: PASS.

---

## Task 3: Add Local Session And Device Data Layer

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/local/SessionStore.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/local/DeviceDataRecorder.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/local/DeviceReplaySample.java`
- Create: `app/src/debug/assets/device/device_replay_sample.txt`
- Create: `app/src/test/resources/device/device_replay_sample.txt`

- [ ] **Step 1: Create replay sample assets**

Both files must contain exactly:

```text
Start Playback
EIS:1,1000,0.12
CA:1,0.08
CA:2,0.09
Playback all done
```

- [ ] **Step 2: Create `SessionStore`**

Move and rename the behavior from `auth/AuthSessionStore`:

```java
package com.hc.mixthebluetooth.local;
```

Keep methods:

```java
save(AuthUser user)
AuthUser currentUser()
String token()
void clear()
```

For JVM tests, keep an injectable `Store` interface as in the existing `AuthSessionStoreTest`.

- [ ] **Step 3: Create `DeviceDataRecorder`**

Move and rename the behavior from `uni/profile/cgm/CgmPlaybackRecorder`:

```java
package com.hc.mixthebluetooth.local;
```

Required methods:

```java
CallResult<File> consumeLine(String line)
File currentFile()
```

Behavior:

```text
line contains "Start Playback" -> create yyyy-MM-ddCGM_Cache_data.txt and return pending("recording")
recording data line -> append line + "\n" and return pending("recording")
line contains "Playback all done" -> stop recording and return ok(file)
null/empty line -> pending("idle") if no file completed
```

The class name is generic, but file naming may remain legacy-compatible in this stage.

- [ ] **Step 4: Create `DeviceReplaySample`**

Responsibilities:

```text
read debug asset device/device_replay_sample.txt in app runtime
read InputStream in JVM tests
return List<String>
```

Run existing recorder tests after migration:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*Device*"
```

Expected: PASS after tests are renamed from old CGM names.

---

## Task 4: Add Impl Services And Bootstrap

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/EnvConfig.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/auth/DefaultAuthService.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/file/DefaultFileService.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/impl/device/DefaultDeviceDataService.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/auth/DefaultAuthServiceTest.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/file/DefaultFileServiceTest.java`
- Test: `app/src/test/java/com/hc/mixthebluetooth/impl/device/DefaultDeviceDataServiceTest.java`

- [ ] **Step 1: Create `EnvConfig`**

```java
package com.hc.mixthebluetooth.impl;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.BuildConfig;

public final class EnvConfig {
    public final String env;
    public final String baseUrl;
    public final boolean useMock;
    public final boolean debug;
    public final String remoteName;
    public final boolean networkEnabled;

    public EnvConfig(String env, String baseUrl, boolean useMock, boolean debug,
                     String remoteName, boolean networkEnabled) {
        this.env = env;
        this.baseUrl = baseUrl;
        this.useMock = useMock;
        this.debug = debug;
        this.remoteName = remoteName;
        this.networkEnabled = networkEnabled;
    }

    @NonNull
    public static EnvConfig fromBuildConfig() {
        return new EnvConfig(
                BuildConfig.API_ENV,
                BuildConfig.API_BASE_URL,
                BuildConfig.USE_MOCK_API,
                BuildConfig.DEBUG,
                "",
                false
        );
    }

    @NonNull
    public EnvConfig withRemote(@NonNull String remoteName, boolean networkEnabled) {
        return new EnvConfig(env, baseUrl, useMock, debug, remoteName, networkEnabled);
    }
}
```

- [ ] **Step 2: Create `DefaultAuthService`**

Implement `AuthService` using `ServerEndpoints` and `SessionStore`.

Mapping requirements:

```text
ServerModels.AccountResp -> AuthUser
ServerResponse.success=false -> CallResult.error(server code, server msg, null)
null response -> CallResult.EMPTY_RESPONSE
null data -> CallResult.EMPTY_DATA
Retrofit failure -> CallResult.NETWORK
success -> SessionStore.save(user) -> CallResult.ok(user)
```

- [ ] **Step 3: Create `DefaultFileService`**

Implement `FileService` using `ServerEndpoints`.

Requirements:

```text
missing file -> CallResult.LOCAL_FILE_NOT_FOUND
multipart fields: fileName, identify="", parentId="0", fileSize
ServerModels.FileResp -> UploadedFile
```

- [ ] **Step 4: Create `DefaultDeviceDataService`**

Implement `DeviceDataService` using `DeviceDataRecorder`, `DeviceReplaySample`, and `FileService`.

Requirements:

```text
replaySample reads sample lines and feeds recorder
consumeLine returns recorder result directly
lastDataFile returns recorder.currentFile()
uploadLastDataFile calls FileService.upload(lastDataFile)
no completed file -> CallResult.LOCAL_FILE_NOT_FOUND
```

- [ ] **Step 5: Create `AppApiBootstrap`**

```java
package com.hc.mixthebluetooth.impl;

import android.content.Context;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.auth.AuthService;
import com.hc.mixthebluetooth.api.device.DeviceDataService;
import com.hc.mixthebluetooth.api.file.FileService;
import com.hc.mixthebluetooth.impl.auth.DefaultAuthService;
import com.hc.mixthebluetooth.impl.device.DefaultDeviceDataService;
import com.hc.mixthebluetooth.impl.file.DefaultFileService;
import com.hc.mixthebluetooth.local.DeviceDataRecorder;
import com.hc.mixthebluetooth.local.DeviceReplaySample;
import com.hc.mixthebluetooth.local.SessionStore;
import com.hc.mixthebluetooth.remote.MockServer;
import com.hc.mixthebluetooth.remote.ServerClient;
import com.hc.mixthebluetooth.remote.ServerEndpoints;

public final class AppApiBootstrap {
    private static boolean initialized;

    private AppApiBootstrap() {
    }

    public static synchronized void init(Context context) {
        if (initialized) return;

        Context app = context.getApplicationContext();
        EnvConfig env = EnvConfig.fromBuildConfig();
        SessionStore sessionStore = new SessionStore(app);

        ServerEndpoints endpoints;
        if (env.useMock) {
            endpoints = new MockServer();
            env = env.withRemote("MockServer(static Call responses)", false);
        } else {
            endpoints = ServerClient.create(env.baseUrl, sessionStore::token);
            env = env.withRemote("RetrofitServer(" + env.baseUrl + ")", true);
        }

        FileService fileService = new DefaultFileService(endpoints);
        DeviceDataService deviceDataService = new DefaultDeviceDataService(
                new DeviceDataRecorder(app),
                new DeviceReplaySample(app),
                fileService
        );
        AuthService authService = new DefaultAuthService(endpoints, sessionStore);

        AppApi.install(authService, fileService, deviceDataService, env);
        initialized = true;
    }

    public static synchronized void resetForTest() {
        initialized = false;
        AppApi.clearForTest();
    }
}
```

- [ ] **Step 6: Add service tests**

Add tests for:

```text
DefaultAuthService login with MockServer saves SessionStore and returns ok(AuthUser)
DefaultFileService upload with MockServer returns ok(UploadedFile)
DefaultDeviceDataService replaySample returns ok(File)
DefaultDeviceDataService consumeLine returns pending until completion
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.*"
```

Expected: PASS.

---

## Task 5: Replace Activity, Debug, And Uni Callers

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/LoginActivity.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/AccountRegisterActivity.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/DebugActivity.java`
- Create/Modify: `app/src/main/java/com/hc/mixthebluetooth/debug/VerificationActivity.java`
- Create/Modify: `app/src/main/res/layout/activity_verification.xml`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`

- [ ] **Step 1: Initialize AppApi**

Preferred: create app-level subclass:

```text
app/src/main/java/com/hc/mixthebluetooth/MixBluetoothApplication.java
```

```java
package com.hc.mixthebluetooth;

import com.hc.basiclibrary.viewBasic.HomeApplication;
import com.hc.mixthebluetooth.impl.AppApiBootstrap;

public class MixBluetoothApplication extends HomeApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        AppApiBootstrap.init(this);
    }
}
```

Then update manifest application name:

```xml
android:name="com.hc.mixthebluetooth.MixBluetoothApplication"
```

If this breaks `HomeApplication` behavior, revert the manifest change and call `AppApiBootstrap.init(getApplicationContext())` in `IntroActivity.onCreate()` as the short-term fallback.

- [ ] **Step 2: Replace login logic**

`LoginActivity` must call:

```java
AppApi.auth().login(username, password, result -> {
    runOnUiThread(() -> {
        loginBtn.setEnabled(true);
        if (result.isOk()) {
            homeApplication.setLimits("ordinary");
            homeApplication.setIsLogin("true");
            navigateToMain();
        } else {
            Toast.makeText(LoginActivity.this, result.message, Toast.LENGTH_SHORT).show();
        }
    });
});
```

Keep local `admin/1` fallback only if explicitly marked as mock/debug fallback and only when `AppApi.env().useMock`.

- [ ] **Step 3: Replace register logic**

`AccountRegisterActivity` must call:

```java
AppApi.auth().register(username, password, phone, result -> {
    runOnUiThread(() -> {
        viewBinding.registerSubmit.setEnabled(true);
        viewBinding.registerStatus.setText(result.isOk() ? "注册成功" : result.message);
    });
});
```

No direct imports of `ServerClient`, `MockServer`, `SessionStore`, or `DefaultAuthService`.

- [ ] **Step 4: Replace debug Activity entry**

`DebugActivity` should open `VerificationActivity`. In release build, hide the entry:

```java
if (!BuildConfig.DEBUG) {
    viewBinding.debugApi.setVisibility(View.GONE);
}
```

- [ ] **Step 5: Build `VerificationActivity`**

`VerificationActivity` should display:

```text
Environment:
  env/baseUrl/useMock/debug/remoteName/networkEnabled

Auth:
  login/register/detail/current user buttons and latest CallResult<AuthUser>

Device Data:
  replay sample
  last file path/size/first lines
  upload last file

Network:
  if networkEnabled=false -> "MockServer returns static Call responses; no network request is emitted."
  if networkEnabled=true -> "Use Android Studio Network Inspector to observe Retrofit requests."
```

All actions must call `AppApi.*`, not impl/remote/local classes.

- [ ] **Step 6: Replace upload event handling**

Where fourth-stage code currently invokes `FileUploadUseCase` directly, replace with:

```java
AppApi.file().upload(file, result -> { ... });
```

or if the source is device data:

```java
AppApi.deviceData().uploadLastDataFile(result -> { ... });
```

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS.

---

## Task 6: Remove Old API/Repository/UseCase Layer

**Files:**
- Delete old files listed in File Map after references are gone.
- Modify imports across `app/src/main/java` and tests.

- [ ] **Step 1: Search old imports**

Run:

```powershell
rg "ApiClient|ApiEnvironment|ApiModels|AuthApi|FileApi|FileUploadUseCase|MockApi|AuthRepository|AuthSessionStore|CgmReplayUploadUseCase|CgmPlaybackRecorder" app/src/main/java app/src/test/java
```

Expected after migration: no references except in deleted-file diff or deliberate history docs.

- [ ] **Step 2: Delete old files**

Use `git rm` for old Java files after compile passes.

- [ ] **Step 3: Run compile**

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS.

---

## Task 7: Verification And Commit

**Files:**
- All changed files.

- [ ] **Step 1: Run focused tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.api.*"
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.remote.*"
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.*"
```

Expected: PASS.

- [ ] **Step 2: Run full unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 3: Compile debug Java**

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: PASS.

- [ ] **Step 4: Assemble mock debug**

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=mock
```

Expected: PASS.

- [ ] **Step 5: Check diff whitespace**

```powershell
git diff --check
```

Expected: no errors. Windows line-ending warnings are acceptable.

- [ ] **Step 6: Verify dependency direction**

Run:

```powershell
rg "com\\.hc\\.mixthebluetooth\\.(impl|remote|local)" app/src/main/java/com/hc/mixthebluetooth/activity app/src/main/java/com/hc/mixthebluetooth/debug app/src/main/java/com/hc/mixthebluetooth/fragment app/src/main/java/com/hc/mixthebluetooth/uni
```

Expected:

```text
No activity/debug/uni imports of impl/remote/local, except one bootstrap call from Application or IntroActivity.
```

- [ ] **Step 7: Commit**

```powershell
git add -A
git commit -m "refactor: route app features through service api layer"
```

Expected: commit succeeds.

---

## Manual Verification Guide

After installing `mockDebug`:

```text
1. Open VerificationActivity.
2. Confirm env=mock, remoteName=MockServer(static Call responses), networkEnabled=false.
3. Tap login/register/detail.
4. Confirm CallResult state=OK and user fields are shown.
5. Tap replay sample.
6. Confirm a device txt file path, size, and first lines are shown.
7. Tap upload last file.
8. Confirm UploadedFile is shown and network hint says no network request is emitted.
```

After installing `devDebug`:

```text
1. Open Android Studio > App Inspection > Network Inspector.
2. Open VerificationActivity.
3. Confirm env=dev, remoteName=RetrofitServer(...), networkEnabled=true.
4. Tap login.
5. Confirm Network Inspector shows POST /api/account/v1/login.
6. Tap upload last file after replay.
7. Confirm Network Inspector shows POST /api/file/v1/upload multipart request.
```

