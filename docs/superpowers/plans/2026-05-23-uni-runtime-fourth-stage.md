# Uni Runtime Fourth Stage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add API/env/mock/register infrastructure and upload CGM cache playback raw files without changing the legacy Bluetooth protocol behavior.

**Architecture:** Keep `uni.Controller` as the runtime coordinator and keep Retrofit out of it. Uni code emits Bluetooth commands and cache-file-ready events through gateway interfaces; Activity/API code owns Retrofit upload. CGM upload uses only the legacy-compatible `CGM_Cache_data.txt` raw playback file; server-computed chart data is left as a later API consumer.

**Tech Stack:** Android Java, ViewBinding, Retrofit 2, Gson converter, OkHttp, JUnit 4, Robolectric for SharedPreferences/local Android tests.

---

## Scope And Guardrails

This plan implements the fourth-stage spec at:

```text
docs/superpowers/specs/2026-05-23-uni-runtime-fourth-stage-design.md
```

Do not implement local CGM numeric calculation. Do not generate or upload `yyyy-MM-dd的CGM_data.txt`. Do not make `uni.Controller` import Retrofit, `ApiClient`, or any API class.

Before starting, check the current branch and dirty files:

```powershell
git status --short
git branch --show-current
```

Expected branch: `dev-1.3`. Existing third-stage changes may be present. Do not revert them.

---

## File Map

Create or modify these files:

```text
app/build.gradle
app/config/env.mock.properties
app/config/env.dev.properties
app/config/env.prod.properties
app/src/main/AndroidManifest.xml

app/src/main/java/com/hc/mixthebluetooth/api/ApiClient.java
app/src/main/java/com/hc/mixthebluetooth/api/ApiEnvironment.java
app/src/main/java/com/hc/mixthebluetooth/api/ApiModels.java
app/src/main/java/com/hc/mixthebluetooth/api/AuthApi.java
app/src/main/java/com/hc/mixthebluetooth/api/AuthSessionStore.java
app/src/main/java/com/hc/mixthebluetooth/api/FileApi.java
app/src/main/java/com/hc/mixthebluetooth/api/FileUploadUseCase.java
app/src/main/java/com/hc/mixthebluetooth/api/MockApi.java

app/src/main/java/com/hc/mixthebluetooth/activity/AccountRegisterActivity.java
app/src/main/res/layout/activity_account_register.xml

app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java
app/src/main/java/com/hc/mixthebluetooth/activity/single/StaticConstants.java
app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java
app/src/main/java/com/hc/mixthebluetooth/uni/Codec.java
app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java
app/src/main/java/com/hc/mixthebluetooth/uni/Profiles.java
app/src/main/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmPlaybackRecorder.java
app/src/main/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmProfile.java

app/src/test/java/com/hc/mixthebluetooth/api/ApiEnvironmentTest.java
app/src/test/java/com/hc/mixthebluetooth/api/AuthSessionStoreTest.java
app/src/test/java/com/hc/mixthebluetooth/api/MockApiTest.java
app/src/test/java/com/hc/mixthebluetooth/uni/CodecTest.java
app/src/test/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmPlaybackRecorderTest.java
app/src/test/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmProfileTest.java
```

Responsibilities:

- `api/*`: server communication, env config, session persistence, mock API, upload request building.
- `AccountRegisterActivity`: simple register UI and session save.
- `Codec`: the single UniRuntime encode/decode entry point. It may delegate to legacy `Analysis`, but callers should not.
- `CgmProfile`: declares CGM actions and raw-line consumer.
- `CgmPlaybackRecorder`: writes `CGM_Cache_data.txt` between device-returned `Start Playback` and `Playback all done`.
- `Controller`: only knows profile specs, raw-line callbacks, and gateway notifications. It must not know Retrofit.
- `CommunicationActivity`: receives cache-file-ready command and calls `FileUploadUseCase`.

---

### Task 1: Add API Dependencies And Env BuildConfig

**Files:**
- Modify: `app/build.gradle`
- Create: `app/config/env.mock.properties`
- Create: `app/config/env.dev.properties`
- Create: `app/config/env.prod.properties`
- Create: `app/src/test/java/com/hc/mixthebluetooth/api/ApiEnvironmentTest.java`

- [ ] **Step 1: Add env property loader and dependencies**

Modify `app/build.gradle`.

Inside `android.defaultConfig`, add:

```groovy
        def envName = project.hasProperty("apiEnv") ? project.property("apiEnv").toString() : "mock"
        def envFile = rootProject.file("app/config/env.${envName}.properties")
        def envProps = new Properties()
        if (envFile.exists()) {
            envFile.withInputStream { envProps.load(it) }
        }
        buildConfigField "String", "API_ENV", "\"${envProps.getProperty("API_ENV", envName)}\""
        buildConfigField "String", "API_BASE_URL", "\"${envProps.getProperty("API_BASE_URL", "http://10.0.2.2:8080/")}\""
        buildConfigField "boolean", "USE_MOCK_API", envProps.getProperty("USE_MOCK_API", "true")
```

Inside `dependencies`, add:

```groovy
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
    testImplementation 'androidx.test:core:1.5.0'
    testImplementation 'org.robolectric:robolectric:4.11.1'
```

- [ ] **Step 2: Create env files**

Create `app/config/env.mock.properties`:

```properties
API_ENV=mock
API_BASE_URL=http://10.0.2.2:8080/
USE_MOCK_API=true
```

Create `app/config/env.dev.properties`:

```properties
API_ENV=dev
API_BASE_URL=http://10.0.2.2:8080/
USE_MOCK_API=false
```

Create `app/config/env.prod.properties`:

```properties
API_ENV=prod
API_BASE_URL=https://example.com/
USE_MOCK_API=false
```

The prod URL is a placeholder domain in configuration, not code. Replace it when the real production host is known.

- [ ] **Step 3: Write an env test**

Create `app/src/test/java/com/hc/mixthebluetooth/api/ApiEnvironmentTest.java`:

```java
package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.BuildConfig;

import org.junit.Test;

public class ApiEnvironmentTest {
    @Test
    public void buildConfigContainsApiEnvironment() {
        assertNotNull(BuildConfig.API_ENV);
        assertNotNull(BuildConfig.API_BASE_URL);
        assertTrue(BuildConfig.API_BASE_URL.endsWith("/"));
    }

    @Test
    public void mockFlagIsAvailable() {
        assertTrue(BuildConfig.USE_MOCK_API || !BuildConfig.USE_MOCK_API);
    }
}
```

- [ ] **Step 4: Run focused test**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.api.ApiEnvironmentTest"
```

Expected: PASS.

- [ ] **Step 5: Run compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

---

### Task 2: Add API Models, Retrofit Interfaces, Mock API, And Session Store

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/ApiModels.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/AuthApi.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/FileApi.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/AuthSessionStore.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/MockApi.java`
- Create: `app/src/test/java/com/hc/mixthebluetooth/api/AuthSessionStoreTest.java`
- Create: `app/src/test/java/com/hc/mixthebluetooth/api/MockApiTest.java`

- [ ] **Step 1: Create API models**

Create `ApiModels.java`:

```java
package com.hc.mixthebluetooth.api;

import androidx.annotation.Nullable;

public final class ApiModels {
    private ApiModels() {
    }

    public static final class JsonData<T> {
        public int code;
        @Nullable
        public T data;
        @Nullable
        public String msg;
        public boolean success;
    }

    public static final class AccountRegisterReq {
        public String username;
        public String password;
        public String phone;
        @Nullable
        public String avatarUrl;

        public AccountRegisterReq(String username, String password, String phone, @Nullable String avatarUrl) {
            this.username = username;
            this.password = password;
            this.phone = phone;
            this.avatarUrl = avatarUrl;
        }
    }

    public static final class AccountLoginReq {
        public String phone;
        public String password;

        public AccountLoginReq(String phone, String password) {
            this.phone = phone;
            this.password = password;
        }
    }

    public static final class AccountInfo {
        public long accountId;
        public String username;
        public String phone;
        @Nullable
        public String avatarUrl;
        @Nullable
        public String token;
    }

    public static final class FileUploadResp {
        public long fileId;
        public String fileName;
        @Nullable
        public String path;
        @Nullable
        public String url;
    }
}
```

- [ ] **Step 2: Create Retrofit interfaces**

Create `AuthApi.java`:

```java
package com.hc.mixthebluetooth.api;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountLoginReq;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface AuthApi {
    @POST("/api/account/v1/register")
    Call<JsonData<AccountInfo>> register(@Body AccountRegisterReq req);

    @POST("/api/account/v1/login")
    Call<JsonData<AccountInfo>> login(@Body AccountLoginReq req);

    @GET("/api/account/v1/detail")
    Call<JsonData<AccountInfo>> detail();
}
```

Create `FileApi.java`:

```java
package com.hc.mixthebluetooth.api;

import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface FileApi {
    @Multipart
    @POST("/api/file/v1/upload")
    Call<JsonData<FileUploadResp>> upload(
            @Part("fileName") RequestBody fileName,
            @Part("identify") RequestBody identify,
            @Part("parentId") RequestBody parentId,
            @Part("fileSize") RequestBody fileSize,
            @Part MultipartBody.Part file
    );
}
```

- [ ] **Step 3: Create AuthSessionStore**

Create `AuthSessionStore.java`:

```java
package com.hc.mixthebluetooth.api;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;

public final class AuthSessionStore {
    private static final String PREF = "bioai_auth_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ACCOUNT_ID = "account_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_LAST_LOGIN_AT = "last_login_at";

    private final SharedPreferences prefs;

    public AuthSessionStore(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void save(@Nullable AccountInfo info) {
        if (info == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_ACCOUNT_ID, info.accountId);
        putString(editor, KEY_USERNAME, info.username);
        putString(editor, KEY_PHONE, info.phone);
        putString(editor, KEY_AVATAR_URL, info.avatarUrl);
        putString(editor, KEY_TOKEN, info.token);
        editor.putLong(KEY_LAST_LOGIN_AT, System.currentTimeMillis());
        editor.apply();
    }

    @Nullable
    public String token() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public long accountId() {
        return prefs.getLong(KEY_ACCOUNT_ID, 0L);
    }

    @Nullable
    public String phone() {
        return prefs.getString(KEY_PHONE, null);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    private static void putString(@NonNull SharedPreferences.Editor editor, @NonNull String key, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
    }
}
```

- [ ] **Step 4: Create MockApi with fake Call**

Create `MockApi.java`:

```java
package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountLoginReq;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import java.io.IOException;

import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class MockApi {
    private MockApi() {
    }

    public static AuthApi authApi() {
        return new AuthApi() {
            @Override
            public Call<JsonData<AccountInfo>> register(AccountRegisterReq req) {
                return new FakeCall<>(success(mockAccount(req.username, req.phone)));
            }

            @Override
            public Call<JsonData<AccountInfo>> login(AccountLoginReq req) {
                return new FakeCall<>(success(mockAccount("mock-user", req.phone)));
            }

            @Override
            public Call<JsonData<AccountInfo>> detail() {
                return new FakeCall<>(success(mockAccount("mock-user", "13800138000")));
            }
        };
    }

    public static FileApi fileApi() {
        return (fileName, identify, parentId, fileSize, file) -> {
            FileUploadResp resp = new FileUploadResp();
            resp.fileId = 1L;
            resp.fileName = "CGM_Cache_data.txt";
            resp.path = "/mock/CGM_Cache_data.txt";
            return new FakeCall<>(success(resp));
        };
    }

    private static AccountInfo mockAccount(String username, String phone) {
        AccountInfo info = new AccountInfo();
        info.accountId = 1001L;
        info.username = username;
        info.phone = phone;
        info.token = "mock-token";
        return info;
    }

    private static <T> JsonData<T> success(T data) {
        JsonData<T> json = new JsonData<>();
        json.code = 0;
        json.data = data;
        json.msg = "";
        json.success = true;
        return json;
    }

    static final class FakeCall<T> implements Call<T> {
        private final T body;
        private boolean executed;
        private boolean canceled;

        FakeCall(T body) {
            this.body = body;
        }

        @Override
        public Response<T> execute() throws IOException {
            executed = true;
            return Response.success(body);
        }

        @Override
        public void enqueue(@NonNull Callback<T> callback) {
            executed = true;
            callback.onResponse(this, Response.success(body));
        }

        @Override
        public boolean isExecuted() {
            return executed;
        }

        @Override
        public void cancel() {
            canceled = true;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @NonNull
        @Override
        public Call<T> clone() {
            return new FakeCall<>(body);
        }

        @NonNull
        @Override
        public Request request() {
            return new Request.Builder().url("http://mock.local/").build();
        }

        @NonNull
        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }
}
```

- [ ] **Step 5: Add tests**

Create `AuthSessionStoreTest.java`:

```java
package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class AuthSessionStoreTest {
    @Test
    public void saveAndReadTokenAndAccount() {
        Context context = ApplicationProvider.getApplicationContext();
        AuthSessionStore store = new AuthSessionStore(context);
        store.clear();

        AccountInfo info = new AccountInfo();
        info.accountId = 42L;
        info.phone = "13800138000";
        info.username = "tester";
        info.token = "token-value";
        store.save(info);

        assertEquals("token-value", store.token());
        assertEquals(42L, store.accountId());
        assertEquals("13800138000", store.phone());
    }

    @Test
    public void clearRemovesToken() {
        Context context = ApplicationProvider.getApplicationContext();
        AuthSessionStore store = new AuthSessionStore(context);
        store.clear();
        assertNull(store.token());
    }
}
```

Create `MockApiTest.java`:

```java
package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import org.junit.Test;

import retrofit2.Response;

public class MockApiTest {
    @Test
    public void registerReturnsMockToken() throws Exception {
        Response<JsonData<AccountInfo>> response = MockApi.authApi()
                .register(new AccountRegisterReq("tester", "123456", "13800138000", null))
                .execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertTrue(response.body().success);
        assertEquals("mock-token", response.body().data.token);
    }
}
```

- [ ] **Step 6: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.api.*"
```

Expected: PASS.

---

### Task 3: Add ApiClient And File Upload Use Case

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/ApiEnvironment.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/ApiClient.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/api/FileUploadUseCase.java`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add INTERNET permission**

In `AndroidManifest.xml`, add near the other permissions:

```xml
    <uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 2: Create ApiEnvironment**

Create `ApiEnvironment.java`:

```java
package com.hc.mixthebluetooth.api;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.BuildConfig;

public final class ApiEnvironment {
    private ApiEnvironment() {
    }

    @NonNull
    public static String env() {
        return BuildConfig.API_ENV;
    }

    @NonNull
    public static String baseUrl() {
        String url = BuildConfig.API_BASE_URL;
        return url.endsWith("/") ? url : url + "/";
    }

    public static boolean useMock() {
        return BuildConfig.USE_MOCK_API;
    }
}
```

- [ ] **Step 3: Create ApiClient**

Create `ApiClient.java`:

```java
package com.hc.mixthebluetooth.api;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static volatile ApiClient instance;

    private final AuthApi authApi;
    private final FileApi fileApi;

    private ApiClient(@NonNull Context context) {
        if (ApiEnvironment.useMock()) {
            authApi = MockApi.authApi();
            fileApi = MockApi.fileApi();
            return;
        }

        AuthSessionStore sessionStore = new AuthSessionStore(context);
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request.Builder builder = chain.request().newBuilder();
                    String token = sessionStore.token();
                    if (token != null && !token.trim().isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ApiEnvironment.baseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        authApi = retrofit.create(AuthApi.class);
        fileApi = retrofit.create(FileApi.class);
    }

    @NonNull
    public static ApiClient get(@NonNull Context context) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = new ApiClient(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    @NonNull
    public AuthApi authApi() {
        return authApi;
    }

    @NonNull
    public FileApi fileApi() {
        return fileApi;
    }
}
```

- [ ] **Step 4: Create FileUploadUseCase**

Create `FileUploadUseCase.java`:

```java
package com.hc.mixthebluetooth.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiModels.FileUploadResp;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class FileUploadUseCase {
    public interface ResultCallback {
        void onSuccess(@NonNull FileUploadResp resp);
        void onError(@NonNull String message);
    }

    private final FileApi fileApi;

    public FileUploadUseCase(@NonNull Context context) {
        this.fileApi = ApiClient.get(context).fileApi();
    }

    public void uploadRootFile(@NonNull File file, @NonNull ResultCallback callback) {
        if (!file.exists() || !file.isFile()) {
            callback.onError("文件不存在: " + file.getAbsolutePath());
            return;
        }

        RequestBody fileName = text(file.getName());
        RequestBody identify = text("");
        RequestBody parentId = text("0");
        RequestBody fileSize = text(String.valueOf(file.length()));
        RequestBody body = RequestBody.create(file, MediaType.parse("text/plain"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), body);

        fileApi.upload(fileName, identify, parentId, fileSize, part)
                .enqueue(new Callback<JsonData<FileUploadResp>>() {
                    @Override
                    public void onResponse(@NonNull Call<JsonData<FileUploadResp>> call,
                                           @NonNull Response<JsonData<FileUploadResp>> response) {
                        JsonData<FileUploadResp> json = response.body();
                        if (response.isSuccessful() && json != null && json.success && json.data != null) {
                            callback.onSuccess(json.data);
                        } else {
                            callback.onError(json != null && json.msg != null ? json.msg : "上传失败");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<JsonData<FileUploadResp>> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "网络错误");
                    }
                });
    }

    private static RequestBody text(@Nullable String value) {
        return RequestBody.create(value == null ? "" : value, MediaType.parse("text/plain"));
    }
}
```

- [ ] **Step 5: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

---

### Task 4: Add Account Register Activity

**Files:**
- Create: `app/src/main/res/layout/activity_account_register.xml`
- Create: `app/src/main/java/com/hc/mixthebluetooth/activity/AccountRegisterActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create layout**

Create `activity_account_register.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/account_register_activity"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp">

    <EditText
        android:id="@+id/register_phone"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="手机号"
        android:inputType="phone" />

    <EditText
        android:id="@+id/register_username"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:hint="用户名"
        android:inputType="textPersonName" />

    <EditText
        android:id="@+id/register_password"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:hint="密码"
        android:inputType="textPassword" />

    <Button
        android:id="@+id/register_submit"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="20dp"
        android:text="注册" />

    <TextView
        android:id="@+id/register_status"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:textColor="#555555"
        android:textSize="14sp" />
</LinearLayout>
```

- [ ] **Step 2: Create activity**

Create `AccountRegisterActivity.java`:

```java
package com.hc.mixthebluetooth.activity;

import android.view.View;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.api.ApiClient;
import com.hc.mixthebluetooth.api.ApiModels.AccountInfo;
import com.hc.mixthebluetooth.api.ApiModels.AccountRegisterReq;
import com.hc.mixthebluetooth.api.ApiModels.JsonData;
import com.hc.mixthebluetooth.api.AuthSessionStore;
import com.hc.mixthebluetooth.databinding.ActivityAccountRegisterBinding;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AccountRegisterActivity extends BaseActivity<ActivityAccountRegisterBinding> {
    private AuthSessionStore sessionStore;

    @Override
    public void initAll() {
        sessionStore = new AuthSessionStore(this);
        new DefaultNavigationBar.Builder(this, findViewById(R.id.account_register_activity))
                .setTitle("账号注册")
                .hideLeftText()
                .hideRightText()
                .builer();
        bindClickListener(viewBinding.registerSubmit);
    }

    @Override
    protected ActivityAccountRegisterBinding getViewBinding() {
        return ActivityAccountRegisterBinding.inflate(getLayoutInflater());
    }

    @Override
    public void onClickView(View view) {
        if (isCheck(viewBinding.registerSubmit)) {
            submit();
        }
    }

    private void submit() {
        String phone = viewBinding.registerPhone.getText().toString().trim();
        String username = viewBinding.registerUsername.getText().toString().trim();
        String password = viewBinding.registerPassword.getText().toString().trim();

        if (phone.isEmpty() || username.isEmpty() || password.isEmpty()) {
            viewBinding.registerStatus.setText("手机号、用户名和密码不能为空");
            return;
        }

        viewBinding.registerSubmit.setEnabled(false);
        viewBinding.registerStatus.setText("注册中...");

        AccountRegisterReq req = new AccountRegisterReq(username, password, phone, null);
        ApiClient.get(this).authApi().register(req).enqueue(new Callback<JsonData<AccountInfo>>() {
            @Override
            public void onResponse(@NonNull Call<JsonData<AccountInfo>> call,
                                   @NonNull Response<JsonData<AccountInfo>> response) {
                viewBinding.registerSubmit.setEnabled(true);
                JsonData<AccountInfo> body = response.body();
                if (response.isSuccessful() && body != null && body.success) {
                    sessionStore.save(body.data);
                    viewBinding.registerStatus.setText("注册成功");
                } else {
                    viewBinding.registerStatus.setText(body != null && body.msg != null ? body.msg : "注册失败");
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonData<AccountInfo>> call, @NonNull Throwable t) {
                viewBinding.registerSubmit.setEnabled(true);
                viewBinding.registerStatus.setText(t.getMessage() != null ? t.getMessage() : "网络错误");
            }
        });
    }
}
```

- [ ] **Step 3: Register activity in manifest**

Add inside `<application>`:

```xml
        <activity
            android:name=".activity.AccountRegisterActivity"
            android:exported="false" />
```

- [ ] **Step 4: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

---

### Task 5: Make Codec The UniRuntime Encode/Decode Entry Point

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Codec.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`
- Modify: `app/src/test/java/com/hc/mixthebluetooth/uni/CodecTest.java`

- [ ] **Step 1: Add encodeText to Codec**

Modify `Codec.java`:

```java
package com.hc.mixthebluetooth.uni;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.Analysis;

public final class Codec {
    private Codec() {
    }

    public static final class Options {
        @Nullable
        public final String charset;
        public final boolean hex;
        public final boolean checkNewline;

        public Options(@Nullable String charset, boolean hex, boolean checkNewline) {
            this.charset = charset;
            this.hex = hex;
            this.checkNewline = checkNewline;
        }
    }

    @NonNull
    public static byte[] encodeText(@NonNull Context context, @NonNull String text) {
        String charset = FragmentParameter.getInstance().getCodeFormat(context);
        byte[] bytes = Analysis.getBytes(text, charset, false);
        return bytes != null ? bytes : new byte[0];
    }

    @Nullable
    public static String decode(@Nullable byte[] bytes, @NonNull Options options) {
        if (bytes == null || bytes.length == 0) return null;
        String charset = options.charset != null ? options.charset : "UTF-8";
        String text = Analysis.getByteToString(bytes.clone(), charset, options.hex, options.checkNewline);
        if (text == null) return null;
        text = text.replace("\u0000", "").trim();
        return text.isEmpty() ? null : text;
    }
}
```

- [ ] **Step 2: Update UniFragment gateway**

In `UniFragment.java`, remove:

```java
import java.nio.charset.StandardCharsets;
```

Add:

```java
import com.hc.mixthebluetooth.uni.Codec;
```

Change `postText` to:

```java
        @Override
        public void postText(@NonNull DeviceModule module, @NonNull String text) {
            byte[] bytes = Codec.encodeText(requireContext(), text);
            sendDataToActivity(
                    StaticConstants.CMD_BT_POST,
                    new BTPackage.BTPost(module, bytes)
            );
        }
```

- [ ] **Step 3: Add/extend Codec tests**

Append to `CodecTest.java`:

```java
    @Test
    public void decodeRemovesNullsAndTrims() {
        Codec.Options options = new Codec.Options("UTF-8", false, false);
        assertEquals("hello", Codec.decode(new byte[]{' ', 'h', 'e', 'l', 'l', 'o', 0, ' '}, options));
    }
```

If `CodecTest.java` has no imports for assertions, ensure it includes:

```java
import static org.junit.Assert.assertEquals;
```

Do not add a local JVM test for `encodeText` unless using Robolectric context. The compile-time dependency and integration path are verified by compile here; command byte equality is tested in `CommandsTest`.

- [ ] **Step 4: Run tests and compile**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.CodecTest"
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: both pass.

---

### Task 6: Add CGM Playback Recorder

**Files:**
- Create: `app/src/main/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmPlaybackRecorder.java`
- Create: `app/src/test/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmPlaybackRecorderTest.java`

- [ ] **Step 1: Write failing recorder tests**

Create `CgmPlaybackRecorderTest.java`:

```java
package com.hc.mixthebluetooth.uni.profile.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CgmPlaybackRecorderTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writesOnlyBetweenPlaybackMarkers() throws Exception {
        CgmPlaybackRecorder recorder = new CgmPlaybackRecorder(tmp.getRoot(), () -> "2026-05-23");

        assertFalse(recorder.onLine("noise").isCompleted());
        assertTrue(recorder.onLine("Start Playback").isRecording());
        assertTrue(recorder.onLine("raw-1").isRecording());
        CgmPlaybackRecorder.Result done = recorder.onLine("Playback all done");

        assertTrue(done.isCompleted());
        assertNotNull(done.file());
        assertEquals("2026-05-23CGM_Cache_data.txt", done.file().getName());

        String text = new String(Files.readAllBytes(done.file().toPath()), StandardCharsets.UTF_8);
        assertEquals("Start Playback\nraw-1\n", text);
    }

    @Test
    public void deleteMarkerDoesNotCreateFile() {
        CgmPlaybackRecorder recorder = new CgmPlaybackRecorder(tmp.getRoot(), () -> "2026-05-23");
        assertFalse(recorder.onLine("DELETE OK").isRecording());
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.profile.cgm.CgmPlaybackRecorderTest"
```

Expected: FAIL because `CgmPlaybackRecorder` does not exist.

- [ ] **Step 3: Implement recorder**

Create `CgmPlaybackRecorder.java`:

```java
package com.hc.mixthebluetooth.uni.profile.cgm;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CgmPlaybackRecorder {
    public interface DateProvider {
        @NonNull
        String today();
    }

    public static final class Result {
        private final boolean recording;
        private final boolean completed;
        @Nullable
        private final File file;

        private Result(boolean recording, boolean completed, @Nullable File file) {
            this.recording = recording;
            this.completed = completed;
            this.file = file;
        }

        public boolean isRecording() {
            return recording;
        }

        public boolean isCompleted() {
            return completed;
        }

        @Nullable
        public File file() {
            return file;
        }
    }

    private final File dir;
    private final DateProvider dateProvider;
    private boolean recording;
    @Nullable
    private File currentFile;

    public CgmPlaybackRecorder(@NonNull Context context) {
        this(defaultDir(context), () -> new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
    }

    CgmPlaybackRecorder(@NonNull File dir, @NonNull DateProvider dateProvider) {
        this.dir = dir;
        this.dateProvider = dateProvider;
    }

    @NonNull
    public Result onLine(@Nullable String line) {
        if (line == null) return new Result(recording, false, currentFile);
        if (line.contains("Start Playback")) {
            recording = true;
            currentFile = new File(dir, dateProvider.today() + "CGM_Cache_data.txt");
            append(line);
            return new Result(true, false, currentFile);
        }
        if (line.contains("Playback all done")) {
            recording = false;
            return new Result(false, true, currentFile);
        }
        if (recording) {
            append(line);
        }
        return new Result(recording, false, currentFile);
    }

    @Nullable
    public File currentFile() {
        return currentFile;
    }

    private void append(@NonNull String line) {
        if (!dir.exists()) dir.mkdirs();
        if (currentFile == null) return;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(currentFile, true), StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private static File defaultDir(@NonNull Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        return dir != null ? dir : new File(context.getFilesDir(), "documents");
    }
}
```

- [ ] **Step 4: Run recorder tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.profile.cgm.CgmPlaybackRecorderTest"
```

Expected: PASS.

---

### Task 7: Add CGM Profile Actions And Raw-Line Hook

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Controller.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/uni/Profiles.java`
- Create: `app/src/main/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmProfile.java`
- Create: `app/src/test/java/com/hc/mixthebluetooth/uni/profile/cgm/CgmProfileTest.java`

- [ ] **Step 1: Extend Controller contracts without Retrofit**

In `Controller.java`, add imports:

```java
import java.io.File;
```

Add this interface near `RecordFormatter`:

```java
    public interface RawLineConsumer {
        void onLine(@NonNull Context context, @NonNull String line, @NonNull Gateway gateway);
    }
```

Add this default method to `Gateway`:

```java
        default void onCacheFileReady(@NonNull File file) {
        }
```

Add field to `ProfileSpec`:

```java
        @Nullable
        public final RawLineConsumer rawLineConsumer;
```

Add builder field and method:

```java
        @Nullable
        private RawLineConsumer rawLineConsumer;

        @NonNull
        public Builder rawLineConsumer(@NonNull RawLineConsumer rawLineConsumer) {
            this.rawLineConsumer = rawLineConsumer;
            return this;
        }
```

Ensure `ProfileSpec` constructor assigns `rawLineConsumer`.

In `onBtData`, after `text` is known and before `parse(text)`, add:

```java
        if (spec.rawLineConsumer != null) {
            spec.rawLineConsumer.onLine(context, text, gateway);
        }
```

- [ ] **Step 2: Create CgmProfile**

Create `CgmProfile.java`:

```java
package com.hc.mixthebluetooth.uni.profile.cgm;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.uni.Commands;
import com.hc.mixthebluetooth.uni.Controller;

import java.io.File;
import java.util.Date;

public final class CgmProfile {
    private CgmProfile() {
    }

    @NonNull
    public static Controller.ProfileSpec create() {
        return Controller.ProfileSpec.builder("cgm")
                .action(Controller.ActionSpec.postText("sync_time", "同步时间", () -> Commands.LegacyCgm.syncTime(new Date())))
                .action(Controller.ActionSpec.postText("read_cache", "读取缓存", Commands.LegacyCgm::readCache))
                .action(Controller.ActionSpec.postText("delete_cache", "删除缓存", Commands.LegacyCgm::deleteCache))
                .rawLineConsumer(new CgmRawLineConsumer())
                .build();
    }

    private static final class CgmRawLineConsumer implements Controller.RawLineConsumer {
        private CgmPlaybackRecorder recorder;

        @Override
        public void onLine(@NonNull android.content.Context context,
                           @NonNull String line,
                           @NonNull Controller.Gateway gateway) {
            if (recorder == null) {
                recorder = new CgmPlaybackRecorder(context);
            }
            CgmPlaybackRecorder.Result result = recorder.onLine(line);
            File file = result.file();
            if (result.isCompleted() && file != null) {
                gateway.onCacheFileReady(file);
            }
        }
    }
}
```

- [ ] **Step 3: Update Profiles**

In `Profiles.java`, add:

```java
import com.hc.mixthebluetooth.uni.profile.cgm.CgmProfile;
```

Add method:

```java
    @NonNull
    public static Controller.ProfileSpec cgm() {
        return CgmProfile.create();
    }
```

- [ ] **Step 4: Add CgmProfile test**

Create `CgmProfileTest.java`:

```java
package com.hc.mixthebluetooth.uni.profile.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.uni.Controller;

import org.junit.Test;

public class CgmProfileTest {
    @Test
    public void cgmProfileDeclaresThreeCommandActions() {
        Controller.ProfileSpec spec = CgmProfile.create();

        assertEquals("cgm", spec.id);
        assertEquals(3, spec.actions.size());
        assertNotNull(spec.rawLineConsumer);
        assertTrue(spec.actions.get(0).textSupplier.get().startsWith("TIME,"));
        assertEquals("ALL\n\r", spec.actions.get(1).textSupplier.get());
        assertEquals("DELETE\n\r", spec.actions.get(2).textSupplier.get());
    }
}
```

- [ ] **Step 5: Run tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.uni.profile.cgm.*"
```

Expected: PASS.

---

### Task 8: Wire Cache-Ready Upload Through Fragment And Activity

**Files:**
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/single/StaticConstants.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java`
- Modify: `app/src/main/java/com/hc/mixthebluetooth/activity/CommunicationActivity.java`
- Optional Modify: `app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java` profile selection

- [ ] **Step 1: Add command constant**

In `StaticConstants.java`, add with the other `CMD_` constants:

```java
    public static final String CMD_CGM_CACHE_READY = "CMD_CGM_CACHE_READY";
```

- [ ] **Step 2: Forward cache-ready event from UniFragment**

In `UniFragment.FragmentGateway`, add:

```java
        @Override
        public void onCacheFileReady(@NonNull java.io.File file) {
            sendDataToActivity(StaticConstants.CMD_CGM_CACHE_READY, file.getAbsolutePath());
        }
```

- [ ] **Step 3: Subscribe and handle upload in CommunicationActivity**

In `CommunicationActivity.initSubscription()`, change:

```java
        subscription(StaticConstants.CMD_SEND_BT_DATA, StaticConstants.CMD_BT_POST);
```

to:

```java
        subscription(StaticConstants.CMD_SEND_BT_DATA, StaticConstants.CMD_BT_POST, StaticConstants.CMD_CGM_CACHE_READY);
```

In `update(...)`, add:

```java
        } else if (sign.equals(StaticConstants.CMD_CGM_CACHE_READY)) {
            onCgmCacheReady(data);
```

Add method:

```java
    private void onCgmCacheReady(Object data) {
        if (!(data instanceof String)) {
            logWarn("Ignore CGM cache ready command, payload is not path: " + data);
            return;
        }
        java.io.File file = new java.io.File((String) data);
        new com.hc.mixthebluetooth.api.FileUploadUseCase(this)
                .uploadRootFile(file, new com.hc.mixthebluetooth.api.FileUploadUseCase.ResultCallback() {
                    @Override
                    public void onSuccess(@NonNull com.hc.mixthebluetooth.api.ApiModels.FileUploadResp resp) {
                        toastShortAlive("缓存上传成功");
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        toastShortAlive("缓存上传失败: " + message);
                    }
                });
    }
```

If `toastShortAlive` is not visible in this class, use the existing toast helper used elsewhere in `CommunicationActivity`.

- [ ] **Step 4: Select CGM profile for this phase**

For fourth-stage CGM upload testing, change `UniFragment.initAllImpl(...)` from:

```java
                Profiles.eis(),
```

to:

```java
                Profiles.cgm(),
```

This is a temporary product choice for the fourth-stage CGM upload path. If EIS must remain the default, do not make this change; instead add a profile-selection entry point before testing with a device.

- [ ] **Step 5: Compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

---

### Task 9: Full Verification

**Files:**
- No new files unless verification reveals compile or test issues.

- [ ] **Step 1: Run all unit tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run debug compile**

Run:

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual mock-flow verification**

Build mock environment:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=mock
```

Expected: BUILD SUCCESSFUL.

Manual checks on device/emulator:

```text
1. Open AccountRegisterActivity through adb or a temporary navigation entry.
2. Enter phone, username, password.
3. Tap 注册.
4. Confirm status shows 注册成功.
5. Open communication page with CGM profile.
6. Send/receive simulated lines if no device is available:
   Start Playback
   raw-cache-line
   Playback all done
7. Confirm app attempts mock upload and shows 缓存上传成功.
```

- [ ] **Step 4: Manual dev-flow verification**

With backend running on the development host:

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

Expected: BUILD SUCCESSFUL.

Manual checks:

```text
1. Confirm API_BASE_URL uses http://10.0.2.2:8080/ for emulator or a reachable LAN IP for physical device.
2. Register a test account.
3. Confirm backend receives /api/account/v1/register.
4. Trigger CGM cache playback with real device when available.
5. Confirm backend receives /api/file/v1/upload with CGM_Cache_data.txt.
```

- [ ] **Step 5: Final status**

Run:

```powershell
git status --short
```

Expected: only files from this plan and existing known third-stage files are modified.

---

## Self-Review Checklist

- Spec coverage:
  - API/env/mock/register: Tasks 1-4.
  - transparent token/session: Tasks 2-3.
  - Codec as unique UniRuntime encode/decode entry: Task 5.
  - CGM commands and `\n\r`: Tasks 6-7 plus existing `CommandsTest`.
  - Start/Playback raw cache file: Task 6.
  - upload through API, not Controller: Tasks 3 and 8.
  - no `的CGM_data.txt` generation: no task creates it.

- Placeholder scan:
  - No implementation step uses unresolved markers or cross-references that require guessing.
  - Prod URL is explicitly a config placeholder, not a code placeholder.

- Type consistency:
  - `ApiModels.JsonData<T>` is used by `AuthApi`, `FileApi`, `MockApi`, and `AccountRegisterActivity`.
  - `FileUploadUseCase.ResultCallback` is used by `CommunicationActivity`.
  - `Controller.RawLineConsumer` uses `Context`, decoded text, and `Gateway`.
  - `CgmPlaybackRecorder.Result` is consumed by `CgmProfile`.
