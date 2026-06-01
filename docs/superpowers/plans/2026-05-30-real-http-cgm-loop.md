# UniFragment 真实 HTTP CGM 闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to execute this plan when multiple independent edit areas can be split safely. Use `superpowers:executing-plans` for inline execution. Check off each task as it is completed.

**Goal:** 在 `dev-1.5` 上建立一个可在 logcat 里观察真实 HTTP 数据包的 CGM 联调闭环：静态账号注册 -> 登录 -> 进入 `UniFragment` -> 连接真实板子 -> 同步/拉取缓存生成真实 txt -> 上传 `/api/test/v1/upload` -> 上传成功删除 txt -> 轮询 `GET /api/cgm/v1/jobs/{jobId}` -> 解析 `summaryJson.units[].points[]` 以及汇总字段 -> 喂给 `UniFragment` 的 CGM widget 画 mmol/L 点图并展示统计信息。

**Architecture:** 保留现有蓝牙读取与文件生成链路，不模拟设备包，不假写文件。删除运行时 mock/debug 页面，新增真实 HTTP CGM 服务层，新增文档契约 DTO，复用 Retrofit/OkHttp/Gson/MPAndroidChart。`CommunicationActivity` 只负责蓝牙连接与页面宿主；`UniFragment` 的 gateway 收到真实缓存文件后触发上传、轮询和 widget 更新。

**Tech Stack:** Android Java, Retrofit 2, OkHttp, Gson, MPAndroidChart, ViewBinding, JUnit 4, MockWebServer.

---

## Current Baseline

- 已验证 `.\gradlew.bat :app:testDebugUnitTest` 可以通过。
- 已验证 `.\gradlew.bat :app:assembleDebug -PapiEnv=dev` 可以通过。
- 之前的 `com.hc.mixthebluetooth.R` 找不到更像本地增量编译状态异常，不作为当前基线失败处理。
- `FragmentIonAnalysis.java` 以及非 `UniFragment` 的碎片页面不进入本次范围。

---

## Contract Decisions

- `/api/cgm/v1/jobs/{jobId}` 使用固定 GET path 参数，不传 query：

```java
@GET("/api/cgm/v1/jobs/{jobId}")
Call<ServerModels.CgmJobResp> cgmJob(@Path("jobId") long jobId);
```

- `/api/test/v1/upload` 使用接口文档里的测试上传端点，CGM 闭环不调用旧的 `/api/file/v1/upload`：

```java
@Multipart
@POST("/api/test/v1/upload")
Call<ServerResponse<Object>> testUpload(@Part MultipartBody.Part file);
```

- `ServerModels.CgmJobResp` 直接匹配接口返回：`code/message/data`。不把 `/api/cgm/v1/jobs/{jobId}` 套进现有 `ServerResponse`，不新增另一个泛型响应壳，也不新增单独的 `api/cgm/CgmResult.java`。
- `jobId` 是轮询键，优先由上传响应或上游链路提供；如果后端字段名变动，只在 `DefaultCgmService` 的适配层改一次。
- 上传成功后删除 txt 文件；上传失败保留 txt 文件，便于复查原始数据。
- 注册账号已存在时记录业务结果并继续登录；登录失败时终止闭环。
- 静态账号：

```text
username = bioai-dev-user
phone    = 18800000001
password = 123456
```

---

## Logcat Requirements

所有真实 HTTP 场景日志统一带类名、API、pretty JSON body、关键响应字段；过长内容截断。

建议 tag：

```text
BioAI.Http
```

日志形态：

```text
DefaultAuthService API POST /api/account/v1/register request
{
  "username": "bioai-dev-user",
  "phone": "18800000001",
  "password": "***"
}

DefaultCgmService API POST /api/test/v1/upload file
{
  "name": "cgm_cache_20260530_101500.txt",
  "length": 18422,
  "preview": "..."
}

DefaultCgmService API GET /api/cgm/v1/jobs/456 response
{
  "code": 200,
  "message": "success",
  "status": "GENERATED",
  "pointCount": 28,
  "unitCount": 1
}
```

---

## Task 1: Add Failing Contract Tests First

**Files:**

- Modify `app/src/test/java/com/hc/mixthebluetooth/remote/ServerEndpointsContractTest.java`
- Add `app/src/test/java/com/hc/mixthebluetooth/remote/CgmJobRespParsingTest.java`

**Steps:**

- [ ] Add endpoint contract tests that assert `/api/test/v1/upload` and `/api/cgm/v1/jobs/{jobId}` are the exact paths used by Retrofit.
- [ ] Add JSON parsing test for the confirmed `/api/cgm/v1/jobs/{jobId}` response shape.
- [ ] Run the focused tests and confirm they fail for missing `ServerModels.CgmJobResp`, `testUpload`, and `cgmJob`.

**ServerEndpointsContractTest additions:**

```java
@Test
public void testUploadEndpointUsesMultipartFileOnly() throws Exception {
    server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"code\":200,\"success\":true,\"msg\":\"success\",\"data\":null}"));

    File file = temporaryFolder.newFile("cgm-cache.txt");
    Files.write(file.toPath(), "raw txt payload".getBytes(StandardCharsets.UTF_8));

    RequestBody body = RequestBody.create(file, MediaType.parse("text/plain"));
    MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), body);

    ServerEndpoints endpoints = ServerClient.create(server.url("/").toString(), () -> null, true);
    Response<ServerResponse<Object>> response = endpoints.testUpload(part).execute();

    assertThat(response.isSuccessful(), is(true));
    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod(), is("POST"));
    assertThat(request.getPath(), is("/api/test/v1/upload"));
    assertThat(request.getHeader("Content-Type"), containsString("multipart/form-data"));
    assertThat(request.getBody().readUtf8(), containsString("raw txt payload"));
}

@Test
public void cgmEndpointUsesFixedJobPathWithoutQuery() throws Exception {
    server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(CgmJobRespParsingTest.SAMPLE_JSON));

    ServerEndpoints endpoints = ServerClient.create(server.url("/").toString(), () -> null, true);
    Response<ServerModels.CgmJobResp> response = endpoints.cgmJob(456L).execute();

    assertThat(response.isSuccessful(), is(true));
    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod(), is("GET"));
    assertThat(request.getPath(), is("/api/cgm/v1/jobs/456"));
}
```

**CgmJobRespParsingTest content:**

```java
package com.hc.mixthebluetooth.remote;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.google.gson.Gson;
import org.junit.Test;

public class CgmJobRespParsingTest {
    public static final String SAMPLE_JSON = "{"
            + "\"code\":200,"
            + "\"message\":\"success\","
            + "\"data\":{"
            + "\"resultId\":789,"
            + "\"jobId\":456,"
            + "\"datasetId\":123,"
            + "\"pointCount\":10,"
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
            + "\"point_count\":10,"
            + "\"unit_count\":1,"
            + "\"prediction_stats\":{\"min\":4.12,\"max\":9.87,\"mean\":6.54,\"std\":1.23},"
            + "\"units\":[{"
            + "\"unit\":1,"
            + "\"unit_title\":\"lyc-5V-1h\","
            + "\"point_count\":10,"
            + "\"mard\":16.3,"
            + "\"points\":["
            + "{\"index\":0,\"time\":33,\"predicted\":6.21,\"actual\":5.4},"
            + "{\"index\":1,\"time\":96,\"predicted\":6.85,\"actual\":4.4},"
            + "{\"index\":2,\"time\":130,\"predicted\":7.43,\"actual\":7.0}"
            + "]}]},"
            + "\"status\":\"GENERATED\","
            + "\"gmtCreate\":\"2026-05-14T10:01:07\""
            + "}}";

    @Test
    public void parsesConfirmedCgmResultShape() {
        ServerModels.CgmJobResp result = new Gson().fromJson(SAMPLE_JSON, ServerModels.CgmJobResp.class);

        assertThat(result.code, is(200));
        assertThat(result.message, is("success"));
        assertThat(result.data.status, is("GENERATED"));
        assertThat(result.data.pointCount, is(10));
        assertThat(result.data.unitCount, is(1));
        assertThat(result.data.predictionMin, closeTo(4.12, 0.001));
        assertThat(result.data.predictionMax, closeTo(9.87, 0.001));
        assertThat(result.data.predictionMean, closeTo(6.54, 0.001));
        assertThat(result.data.predictionStd, closeTo(1.23, 0.001));
        assertThat(result.data.avgMard, closeTo(16.3, 0.001));
        assertThat(result.data.mardStd, nullValue());
        assertThat(result.data.summaryJson.predictionStats.mean, closeTo(6.54, 0.001));
        assertThat(result.data.summaryJson.units.get(0).unitTitle, is("lyc-5V-1h"));
        assertThat(result.data.summaryJson.units.get(0).points.size(), is(3));
        assertThat(result.data.summaryJson.units.get(0).points.get(0).time, is(33));
        assertThat(result.data.summaryJson.units.get(0).points.get(0).predicted, closeTo(6.21, 0.001));
        assertThat(result.data.summaryJson.units.get(0).points.get(0).actual, closeTo(5.4, 0.001));
    }
}
```

**Run:**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.remote.ServerEndpointsContractTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.remote.CgmJobRespParsingTest"
```

**Expected failure before implementation:** missing `ServerModels.CgmJobResp`, `ServerClient.create(..., boolean)`, `ServerEndpoints.testUpload`, and `ServerEndpoints.cgmJob(long)`.

---

## Task 2: Add CGM API Contract And Raw HTTP Logging Helper

**Files:**

- Add `app/src/main/java/com/hc/mixthebluetooth/api/cgm/CgmService.java`
- Add `app/src/main/java/com/hc/mixthebluetooth/impl/log/ApiTraceLogger.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/remote/ServerModels.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/remote/ServerEndpoints.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/remote/ServerClient.java`

**Steps:**

- [ ] Add CGM response DTOs to `ServerModels` with fields matching `code/message/data`.
- [ ] Add `CgmService` as app-facing service for upload plus polling.
- [ ] Add `ApiTraceLogger` for pretty JSON, truncation, password masking, and file previews.
- [ ] Add Retrofit endpoints for `/api/test/v1/upload` and `/api/cgm/v1/jobs/{jobId}`.
- [ ] Change `ServerClient.create` to accept `bodyLogging` so dev builds can log OkHttp BODY.

**ServerModels CGM additions:**

```java
import com.google.gson.annotations.SerializedName;
import java.util.List;

public static final class CgmJobResp {
    public int code;
    @Nullable
    public String message;
    @Nullable
    public CgmJobData data;
}

public static final class CgmJobData {
    public long resultId;
    public long jobId;
    public long datasetId;
    public int pointCount;
    public int unitCount;
    public double predictionMin;
    public double predictionMax;
    public double predictionMean;
    public double predictionStd;
    public double avgMard;
    @Nullable
    public Double mardStd;
    @Nullable
    public CgmSummary summaryJson;
    @Nullable
    public String status;
    @Nullable
    public String gmtCreate;
}

public static final class CgmSummary {
    @SerializedName("avg_mard")
    public double avgMard;
    @SerializedName("mard_std")
    @Nullable
    public Double mardStd;
    @SerializedName("point_count")
    public int pointCount;
    @SerializedName("unit_count")
    public int unitCount;
    @SerializedName("prediction_stats")
    @Nullable
    public CgmPredictionStats predictionStats;
    @Nullable
    public List<CgmUnit> units;
}

public static final class CgmPredictionStats {
    public double min;
    public double max;
    public double mean;
    public double std;
}

public static final class CgmUnit {
    public int unit;
    @SerializedName("unit_title")
    @Nullable
    public String unitTitle;
    @SerializedName("point_count")
    public int pointCount;
    public double mard;
    @Nullable
    public List<CgmPoint> points;
}

public static final class CgmPoint {
    public int index;
    public int time;
    public double predicted;
    @Nullable
    public Double actual;
}
```

**CgmService.java:**

```java
package com.hc.mixthebluetooth.api.cgm;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import java.io.File;

public interface CgmService {
    void uploadAndPoll(File cacheFile, ApiCallback<CallResult<ServerModels.CgmJobData>> callback);
    void poll(long jobId, ApiCallback<CallResult<ServerModels.CgmJobData>> callback);
}
```

**ApiTraceLogger.java:**

```java
package com.hc.mixthebluetooth.impl.log;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiTraceLogger {
    private static final String TAG = "BioAI.Http";
    private static final int MAX_CHARS = 4096;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ApiTraceLogger() {
    }

    public static void json(String owner, String api, String label, Object body) {
        Log.d(TAG, owner + " API " + api + " " + label + "\n" + truncate(toPrettyJson(body)));
    }

    public static void text(String owner, String api, String label, String value) {
        Log.d(TAG, owner + " API " + api + " " + label + "\n" + truncate(value));
    }

    public static void file(String owner, String api, File file) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", file.getName());
        body.put("absolutePath", file.getAbsolutePath());
        body.put("length", file.length());
        body.put("preview", preview(file));
        json(owner, api, "file", body);
    }

    public static Map<String, Object> maskedAuthBody(String username, String phone, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("phone", phone);
        body.put("password", password == null ? null : "***");
        return body;
    }

    private static String toPrettyJson(Object body) {
        if (body == null) {
            return "null";
        }
        if (body instanceof String) {
            try {
                JsonElement element = JsonParser.parseString((String) body);
                return GSON.toJson(element);
            } catch (RuntimeException ignored) {
                return (String) body;
            }
        }
        return GSON.toJson(body);
    }

    private static String preview(File file) {
        if (!file.exists() || !file.isFile()) {
            return "";
        }
        int length = (int) Math.min(file.length(), 2048);
        byte[] buffer = new byte[length];
        try (FileInputStream input = new FileInputStream(file)) {
            int read = input.read(buffer);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "preview failed: " + e.getMessage();
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_CHARS) {
            return value;
        }
        return value.substring(0, MAX_CHARS) + "\n... truncated, totalChars=" + value.length();
    }
}
```

**ServerEndpoints additions:**

```java
@Multipart
@POST("/api/test/v1/upload")
Call<ServerResponse<Object>> testUpload(@Part MultipartBody.Part file);

@GET("/api/cgm/v1/jobs/{jobId}")
Call<ServerModels.CgmJobResp> cgmJob(@Path("jobId") long jobId);
```

**ServerClient change:**

```java
public static ServerEndpoints create(String baseUrl, TokenProvider tokenProvider, boolean bodyLogging) {
    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
    logging.setLevel(bodyLogging ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.BASIC);
    ...
}
```

Keep a two-argument overload for existing tests during the transition:

```java
public static ServerEndpoints create(String baseUrl, TokenProvider tokenProvider) {
    return create(baseUrl, tokenProvider, false);
}
```

**Run:**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.remote.CgmJobRespParsingTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.remote.ServerEndpointsContractTest"
```

---

## Task 3: Implement DefaultCgmService Upload/Delete/Poll

**Files:**

- Add `app/src/main/java/com/hc/mixthebluetooth/impl/cgm/DefaultCgmService.java`
- Add `app/src/test/java/com/hc/mixthebluetooth/impl/cgm/DefaultCgmServiceTest.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/api/AppApi.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java`

**Steps:**

- [ ] Implement `uploadAndPoll(File, callback)` using real Retrofit calls.
- [ ] Delete txt only after `/api/test/v1/upload` returns successful business result.
- [ ] Extract `jobId` from the upload response or upstream handoff, then poll `/api/cgm/v1/jobs/{jobId}` up to 8 times with 1500 ms delay until `status=GENERATED`.
- [ ] Expose service through `AppApi.cgm()`.
- [ ] Add a MockWebServer test proving upload path, file deletion, and CGM parsing.

**DefaultCgmService.java key implementation:**

```java
package com.hc.mixthebluetooth.impl.cgm;

import android.os.Handler;
import android.os.Looper;
import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.remote.ServerModels;
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
import com.hc.mixthebluetooth.remote.ServerEndpoints;
import com.hc.mixthebluetooth.remote.ServerResponse;
import java.io.File;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DefaultCgmService implements CgmService {
    private static final String OWNER = "DefaultCgmService";
    private static final String API_UPLOAD = "POST /api/test/v1/upload";
    private static final String API_CGM = "GET /api/cgm/v1/jobs/{jobId}";
    private static final int MAX_POLL_ATTEMPTS = 8;
    private static final long POLL_DELAY_MS = 1500L;

    private final ServerEndpoints endpoints;
    private final Handler handler;

    public DefaultCgmService(ServerEndpoints endpoints) {
        this(endpoints, new Handler(Looper.getMainLooper()));
    }

    DefaultCgmService(ServerEndpoints endpoints, Handler handler) {
        this.endpoints = endpoints;
        this.handler = handler;
    }

    @Override
    public void uploadAndPoll(File cacheFile, ApiCallback<CallResult<ServerModels.CgmJobData>> callback) {
        if (cacheFile == null || !cacheFile.exists() || !cacheFile.isFile()) {
            callback.onComplete(CallResult.error("CGM cache txt does not exist"));
            return;
        }
        ApiTraceLogger.file(OWNER, API_UPLOAD, cacheFile);
        RequestBody body = RequestBody.create(cacheFile, MediaType.parse("text/plain"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", cacheFile.getName(), body);
        endpoints.testUpload(part).enqueue(new Callback<ServerResponse<Object>>() {
            @Override
            public void onResponse(Call<ServerResponse<Object>> call, Response<ServerResponse<Object>> response) {
                ServerResponse<Object> server = response.body();
                ApiTraceLogger.json(OWNER, API_UPLOAD, "response", server);
                if (!response.isSuccessful() || server == null || server.code != 200) {
                    callback.onComplete(CallResult.error("Upload failed: HTTP " + response.code()));
                    return;
                }
                Long jobId = extractJobId(server);
                if (jobId == null) {
                    callback.onComplete(CallResult.error("Upload succeeded but jobId is missing"));
                    return;
                }
                boolean deleted = cacheFile.delete();
                ApiTraceLogger.text(OWNER, API_UPLOAD, "deleteTxt", cacheFile.getName() + " deleted=" + deleted);
                pollAttempt(jobId, 1, callback);
            }

            @Override
            public void onFailure(Call<ServerResponse<Object>> call, Throwable t) {
                ApiTraceLogger.text(OWNER, API_UPLOAD, "failure", t.getMessage());
                callback.onComplete(CallResult.error("Upload failed: " + t.getMessage()));
            }
        });
    }

    @Override
    public void poll(long jobId, ApiCallback<CallResult<ServerModels.CgmJobData>> callback) {
        pollAttempt(jobId, 1, callback);
    }

    private void pollAttempt(long jobId, int attempt, ApiCallback<CallResult<ServerModels.CgmJobData>> callback) {
        endpoints.cgmJob(jobId).enqueue(new Callback<ServerModels.CgmJobResp>() {
            @Override
            public void onResponse(Call<ServerModels.CgmJobResp> call, Response<ServerModels.CgmJobResp> response) {
                ServerModels.CgmJobResp result = response.body();
                ApiTraceLogger.json(OWNER, API_CGM.replace("{jobId}", String.valueOf(jobId)), "response attempt=" + attempt, result);
                if (response.isSuccessful() && result != null && result.code == 200
                        && result.data != null && "GENERATED".equalsIgnoreCase(result.data.status)) {
                    callback.onComplete(CallResult.success(result.data));
                    return;
                }
                if (attempt >= MAX_POLL_ATTEMPTS) {
                    callback.onComplete(CallResult.error("CGM result not generated after " + attempt + " attempts"));
                    return;
                }
                handler.postDelayed(() -> pollAttempt(jobId, attempt + 1, callback), POLL_DELAY_MS);
            }

            @Override
            public void onFailure(Call<ServerModels.CgmJobResp> call, Throwable t) {
                ApiTraceLogger.text(OWNER, API_CGM.replace("{jobId}", String.valueOf(jobId)), "failure attempt=" + attempt, t.getMessage());
                if (attempt >= MAX_POLL_ATTEMPTS) {
                    callback.onComplete(CallResult.error("CGM poll failed: " + t.getMessage()));
                    return;
                }
                handler.postDelayed(() -> pollAttempt(jobId, attempt + 1, callback), POLL_DELAY_MS);
            }
        });
    }

    private Long extractJobId(ServerResponse<Object> server) {
        Object data = server == null ? null : server.data;
        if (data instanceof Number) {
            return ((Number) data).longValue();
        }
        if (data instanceof Map) {
            Object value = ((Map<?, ?>) data).get("jobId");
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
```

**AppApi additions:**

```java
import com.hc.mixthebluetooth.api.cgm.CgmService;

private static CgmService cgmService;

public static CgmService cgm() {
    return cgmService;
}

public static void install(AuthService auth, FileService file, DeviceDataService deviceData,
        CgmService cgm, EnvConfig env) {
    authService = auth;
    fileService = file;
    deviceDataService = deviceData;
    cgmService = cgm;
    envConfig = env;
}
```

**AppApiBootstrap install:**

```java
ServerEndpoints endpoints = ServerClient.create(env.apiBaseUrl, store::getToken, env.debugLogging);
AppApi.install(
        new DefaultAuthService(endpoints, store),
        new DefaultFileService(endpoints),
        new DefaultDeviceDataService(context),
        new DefaultCgmService(endpoints),
        env
);
```

If current `AppApi.install` signature differs, update every compile target to the new five-argument signature in the same task.

**DefaultCgmServiceTest coverage:**

- Upload request path is `/api/test/v1/upload`.
- Upload request body contains the txt content.
- Successful upload deletes the temporary txt.
- CGM poll consumes `CgmJobRespParsingTest.SAMPLE_JSON`.
- Callback receives `CallResult.success` with 3 parsed points from sample.

**Run:**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.impl.cgm.DefaultCgmServiceTest"
```

---

## Task 4: Wire UniFragment Cache File To CGM Service

**Files:**

- Modify `app/src/main/java/com/hc/mixthebluetooth/Controller.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/UniFragment.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/CommunicationActivity.java`

**Steps:**

- [ ] Add `Controller.onCgmResult(ServerModels.CgmJobData)` and route it to widgets.
- [ ] In `UniFragment.FragmentGateway.onCacheFileReady`, call `AppApi.cgm().uploadAndPoll(file, callback)`.
- [ ] On success, update controller/widgets on UI thread.
- [ ] On error, log and toast.
- [ ] Remove `CMD_CGM_CACHE_READY` handoff and `CommunicationActivity.onCgmCacheReady`, because CGM result belongs to `UniFragment`.

**Controller addition:**

```java
public void onCgmResult(@NonNull ServerModels.CgmJobData result) {
    for (Widgets.MetricWidget widget : widgets) {
        widget.onCgmResult(result);
    }
}
```

**UniFragment gateway change:**

```java
@Override
public void onCacheFileReady(@NonNull File file) {
    Log.d("UniFragment", "CGM cache file ready: " + file.getAbsolutePath());
    AppApi.cgm().uploadAndPoll(file, result -> {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (result.ok) {
                controller.onCgmResult(result.data);
                Toast.makeText(requireContext(), "CGM 数据已生成", Toast.LENGTH_SHORT).show();
            } else {
                Log.w("UniFragment", "CGM flow failed: " + result.message);
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
            }
        });
    });
}
```

**CommunicationActivity cleanup:**

- Delete handling of `CMD_CGM_CACHE_READY`.
- Delete `onCgmCacheReady(File file)`.
- Delete import of `AppApi.file()` that was only used by this upload path.
- Keep Bluetooth connection, fragment navigation, and device command plumbing unchanged.

**Run:**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

---

## Task 5: Add CGM Result Widget And Use All Result Fields

**Files:**

- Modify `app/src/main/java/com/hc/mixthebluetooth/Widgets.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/CgmProfile.java`
- Add or modify widget-focused tests under `app/src/test/java/com/hc/mixthebluetooth/`

**Steps:**

- [ ] Add a `MetricWidget.onCgmResult(ServerModels.CgmJobData)` default method.
- [ ] Add a `CGM_RESULT` widget kind and `WidgetSpec.cgmResult(...)`.
- [ ] Implement `CgmResultMetricWidget`.
- [ ] Plot all `summaryJson.units[].points[]` points:
  - x = `time`
  - predicted y = `predicted`
  - actual y = `actual` when present
- [ ] Display top-level stats:
  - `status`
  - `pointCount`
  - `unitCount`
  - `predictionMin`
  - `predictionMax`
  - `predictionMean`
  - `predictionStd`
  - `avgMard`
  - `mardStd`
  - `resultId`
  - `jobId`
  - `datasetId`
  - `gmtCreate`
- [ ] Display unit-level stats:
  - `unit`
  - `unitTitle`
  - `pointCount`
  - `mard`
- [ ] Add this widget to `Profiles.cgm()` so only `UniFragment` CGM page receives it.

**MetricWidget default method:**

```java
default void onCgmResult(@NonNull ServerModels.CgmJobData result) {
}
```

**Widget kind and spec:**

```java
public enum WidgetKind {
    LINE,
    GAUGE,
    VALUE,
    STATS,
    CGM_RESULT
}

public static WidgetSpec cgmResult(String id, String title) {
    return new WidgetSpec(id, title, WidgetKind.CGM_RESULT, null, 0f, 0f, null);
}
```

**CgmResultMetricWidget behavior:**

```java
private static final class CgmResultMetricWidget implements MetricWidget {
    private final View root;
    private final LineChart chart;
    private final TextView summary;
    private final TextView units;

    CgmResultMetricWidget(Context context, WidgetSpec spec) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));

        TextView title = new TextView(context);
        title.setText(spec.title);
        title.setTextSize(14f);
        title.setTextColor(Color.rgb(30, 41, 59));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        container.addView(title);

        chart = new LineChart(context);
        chart.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 220)
        ));
        chart.getDescription().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(15f);
        chart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.US, "%.0f", value);
            }
        });
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setGranularity(1f);
        chart.getLegend().setEnabled(true);
        container.addView(chart);

        summary = new TextView(context);
        summary.setTextSize(12f);
        summary.setTextColor(Color.rgb(71, 85, 105));
        container.addView(summary);

        units = new TextView(context);
        units.setTextSize(12f);
        units.setTextColor(Color.rgb(71, 85, 105));
        container.addView(units);

        root = container;
    }

    @NonNull
    @Override
    public View view() {
        return root;
    }

    @Override
    public void onSample(@NonNull BluetoothSample sample) {
    }

    @Override
    public void onCgmResult(@NonNull ServerModels.CgmJobData result) {
        List<Entry> predicted = new ArrayList<>();
        List<Entry> actual = new ArrayList<>();
        for (ServerModels.CgmPoint point : allPoints(result)) {
            predicted.add(new Entry(point.time, (float) point.predicted));
            if (point.actual != null) {
                actual.add(new Entry(point.time, point.actual.floatValue()));
            }
        }

        LineDataSet predictedSet = new LineDataSet(predicted, "Predicted mmol/L");
        predictedSet.setColor(Color.rgb(45, 99, 155));
        predictedSet.setCircleColor(Color.rgb(45, 99, 155));
        predictedSet.setDrawCircles(true);
        predictedSet.setCircleRadius(3f);
        predictedSet.setDrawValues(false);

        LineDataSet actualSet = new LineDataSet(actual, "Actual mmol/L");
        actualSet.setColor(Color.rgb(245, 158, 11));
        actualSet.setCircleColor(Color.rgb(245, 158, 11));
        actualSet.setDrawCircles(true);
        actualSet.setCircleRadius(3f);
        actualSet.setDrawValues(false);

        chart.setData(new LineData(predictedSet, actualSet));
        chart.invalidate();

        ServerModels.CgmJobData data = result;
        summary.setText(String.format(Locale.US,
                "status=%s points=%d units=%d prediction[min=%.2f max=%.2f mean=%.2f std=%.2f] avgMard=%.2f mardStd=%s ids[result=%d job=%d dataset=%d] created=%s",
                data.status,
                data.pointCount,
                data.unitCount,
                data.predictionMin,
                data.predictionMax,
                data.predictionMean,
                data.predictionStd,
                data.avgMard,
                String.valueOf(data.mardStd),
                data.resultId,
                data.jobId,
                data.datasetId,
                data.gmtCreate));

        units.setText(formatUnits(data.summaryJson == null ? null : data.summaryJson.units));
    }

    @Override
    public void reset() {
        chart.clear();
        summary.setText("");
        units.setText("");
    }

    private String formatUnits(List<ServerModels.CgmUnit> unitList) {
        if (unitList == null || unitList.isEmpty()) {
            return "units=[]";
        }
        StringBuilder builder = new StringBuilder();
        for (ServerModels.CgmUnit unit : unitList) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(String.format(Locale.US,
                    "unit=%d title=%s points=%d mard=%.2f",
                    unit.unit,
                    unit.unitTitle,
                    unit.pointCount,
                    unit.mard));
        }
        return builder.toString();
    }
}
```

**Widget factory switch:**

```java
case CGM_RESULT:
    return new CgmResultMetricWidget(context, spec);
```

**CgmProfile addition:**

```java
.widget(Widgets.WidgetSpec.cgmResult("cgm_result", "CGM mmol/L"))
```

**Run:**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.hc.mixthebluetooth.*Widgets*"
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

---

## Task 6: Replace Runtime Mock And Debug Page With Real Dev Entry

**Files:**

- Modify `app/build.gradle`
- Modify `app/src/main/java/com/hc/mixthebluetooth/impl/EnvConfig.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/impl/AppApiBootstrap.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/LoginActivity.java`
- Modify `app/src/main/java/com/hc/mixthebluetooth/RegisterActivity.java`
- Modify `app/src/main/AndroidManifest.xml`
- Delete `app/src/main/java/com/hc/mixthebluetooth/remote/MockServer.java`
- Delete `app/src/main/java/com/hc/mixthebluetooth/debug/VerificationActivity.java`
- Delete `app/src/main/res/layout/activity_verification.xml`
- Delete `app/config/env.mock.properties`
- Delete or rewrite tests that directly depend on `MockServer`

**Steps:**

- [ ] Make `dev` the default API env in Gradle.
- [ ] Remove runtime `useMock` branching from `EnvConfig` and `AppApiBootstrap`.
- [ ] Remove `MockServer`.
- [ ] Remove `VerificationActivity` from manifest and source tree.
- [ ] Replace the old login-page debug jump with a dev-only “真实 HTTP 联调” action.
- [ ] The dev action calls register first, then login with the static account.
- [ ] Register “account already exists” logs the business response and continues to login.
- [ ] Remove local login fallback such as `admin/1` and `normal/1`.

**Gradle default:**

```groovy
def apiEnv = (project.findProperty("apiEnv") ?: "dev").toString()
```

**EnvConfig target shape:**

```java
public final class EnvConfig {
    public final String name;
    public final String apiBaseUrl;
    public final boolean networkEnabled;
    public final boolean debugLogging;

    public EnvConfig(String name, String apiBaseUrl, boolean networkEnabled, boolean debugLogging) {
        this.name = name;
        this.apiBaseUrl = apiBaseUrl;
        this.networkEnabled = networkEnabled;
        this.debugLogging = debugLogging;
    }
}
```

**AppApiBootstrap target behavior:**

```java
ServerEndpoints endpoints = ServerClient.create(env.apiBaseUrl, store::getToken, env.debugLogging);
AppApi.install(
        new DefaultAuthService(endpoints, store),
        new DefaultFileService(endpoints),
        new DefaultDeviceDataService(context),
        new DefaultCgmService(endpoints),
        env
);
```

No `if (env.useMock)` branch remains.

**LoginActivity dev flow shape:**

```java
private static final String DEV_USERNAME = "bioai-dev-user";
private static final String DEV_PHONE = "18800000001";
private static final String DEV_PASSWORD = "123456";

private void startDevRealHttpFlow() {
    setLoading(true);
    ApiTraceLogger.json("LoginActivity", "POST /api/account/v1/register", "request",
            ApiTraceLogger.maskedAuthBody(DEV_USERNAME, DEV_PHONE, DEV_PASSWORD));
    AppApi.auth().register(DEV_USERNAME, DEV_PHONE, DEV_PASSWORD, registerResult -> runOnUiThread(() -> {
        ApiTraceLogger.json("LoginActivity", "POST /api/account/v1/register", "result", registerResult);
        loginDevAccount();
    }));
}

private void loginDevAccount() {
    ApiTraceLogger.json("LoginActivity", "POST /api/account/v1/login", "request",
            ApiTraceLogger.maskedAuthBody(null, DEV_PHONE, DEV_PASSWORD));
    AppApi.auth().login(DEV_PHONE, DEV_PASSWORD, loginResult -> runOnUiThread(() -> {
        setLoading(false);
        ApiTraceLogger.json("LoginActivity", "POST /api/account/v1/login", "result", loginResult);
        if (loginResult.ok) {
            openMainPage();
        } else {
            showError(loginResult.message);
        }
    }));
}
```

Use the existing navigation method name if it differs from `openMainPage()`.

**Auth service logging:**

Add logging inside `DefaultAuthService.register`, `DefaultAuthService.login`, and `DefaultAuthService.detail` so manual flows also show class name, API, request body, and response.

**Tests to update:**

- Remove `MockServerTest`.
- Convert auth/file tests that used `MockServer` to `MockWebServer`.
- Keep test-only fake responses inside test code; do not keep app runtime mock infrastructure.

**Run:**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
```

---

## Task 7: Final Verification And Manual Logcat Checklist

**Files:**

- No planned code files. This task verifies the full loop after implementation.

**Steps:**

- [ ] Run all unit tests.
- [ ] Build dev APK.
- [ ] Install/run on a device with the real board available.
- [ ] Start logcat filtered by `BioAI.Http`, `DefaultCgmService`, `UniFragment`, and Bluetooth/controller tags.
- [ ] Tap the real HTTP dev entry.
- [ ] Confirm register request and response appear.
- [ ] Confirm login request and response appear.
- [ ] Enter `UniFragment` CGM page.
- [ ] Connect the real board.
- [ ] Tap command/sync/cache action.
- [ ] Confirm the existing board chain generates a real txt file.
- [ ] Confirm `/api/test/v1/upload` multipart request appears.
- [ ] Confirm txt file deletion log appears only after upload success.
- [ ] Confirm `/api/cgm/v1/jobs/{jobId}` polling appears.
- [ ] Confirm the CGM widget renders predicted/actual mmol/L points.
- [ ] Confirm summary fields and unit fields are visible in the widget.
- [ ] Confirm failed upload keeps the txt file when testing with an unreachable server.

**Commands:**

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
adb logcat -v time | findstr /i "BioAI.Http DefaultCgmService UniFragment"
```

---

## Risks And Guardrails

- Do not introduce `DevCgmPacketSource`.
- Do not write fake CGM txt files.
- Do not add `@QueryMap` or `fileId/datasetId` query params to `/api/cgm/v1/jobs/{jobId}`; `jobId` belongs only in the path.
- Do not route CGM result through `FragmentIonAnalysis`.
- Do not wrap `/api/cgm/v1/jobs/{jobId}` in `ServerResponse`.
- Delete runtime mock/debug code only after replacing affected tests with `MockWebServer`.
- Keep upload deletion order strict: upload success first, delete txt second, poll third.
- If backend returns `code=200` with `status` other than `GENERATED`, continue polling until max attempts.
- If backend returns `code/message/data` but `summaryJson.units` is empty, widget clears chart and still shows top-level status and stats.

---

## Self-Review

- [x] Every task names exact files.
- [x] Tests are written before implementation for new contracts.
- [x] Plan keeps real board txt generation intact.
- [x] Plan removes runtime mock/debug surfaces.
- [x] Plan uses fixed `/api/cgm/v1/jobs/{jobId}` with no query.
- [x] Plan uses `/api/test/v1/upload` for this loop.
- [x] Plan routes result to `UniFragment`, not obsolete fragments.
- [x] Plan logs class name, API, pretty body, response, and file lifecycle.
- [x] Plan has concrete verification commands.
