package com.hc.mixthebluetooth.impl.cgm;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.impl.log.ApiTraceLogger;
import com.hc.mixthebluetooth.remote.ServerEndpoints;
import com.hc.mixthebluetooth.remote.ServerModels;
import com.hc.mixthebluetooth.remote.ServerResponse;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DefaultCgmService implements CgmService {
    interface PollScheduler {
        void postDelayed(@NonNull Runnable runnable, long delayMillis);
    }

    private static final String OWNER = "DefaultCgmService";
    private static final String API_UPLOAD = "POST /api/test/v1/upload";
    private static final String API_CGM = "GET /api/cgm/v1/jobs/{jobId}";
    private static final int MAX_POLL_ATTEMPTS = 8;
    private static final long POLL_DELAY_MS = 1500L;

    private final ServerEndpoints endpoints;
    private final PollScheduler scheduler;

    public DefaultCgmService(@NonNull ServerEndpoints endpoints) {
        this(endpoints, (runnable, delayMillis) ->
                new Handler(Looper.getMainLooper()).postDelayed(runnable, delayMillis));
    }

    DefaultCgmService(@NonNull ServerEndpoints endpoints, @NonNull PollScheduler scheduler) {
        this.endpoints = endpoints;
        this.scheduler = scheduler;
    }

    @Override
    public void uploadAndPoll(File cacheFile,
                              ApiCallback<CallResult<CgmResult>> callback) {
        if (cacheFile == null || !cacheFile.exists() || !cacheFile.isFile()) {
            callback.onResult(CallResult.error(CallResult.LOCAL_FILE_NOT_FOUND, "CGM 缓存 txt 不存在", null));
            return;
        }

        ApiTraceLogger.file(OWNER, API_UPLOAD, cacheFile);
        RequestBody body = RequestBody.create(cacheFile, MediaType.parse("text/plain"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", cacheFile.getName(), body);
        endpoints.testUpload(part).enqueue(new Callback<ServerResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ServerResponse<Object>> call,
                                   @NonNull Response<ServerResponse<Object>> response) {
                ServerResponse<Object> server = response.body();
                ApiTraceLogger.json(OWNER, API_UPLOAD, "response", server);
                if (!isUploadSuccess(response, server)) {
                    callback.onResult(CallResult.error(response.code(), uploadMessage(server), null));
                    return;
                }

                Long jobId = extractJobId(server);
                if (jobId == null) {
                    callback.onResult(CallResult.error(CallResult.EMPTY_DATA, "上传成功但缺少 jobId", null));
                    return;
                }

                boolean deleted = cacheFile.delete();
                ApiTraceLogger.text(OWNER, API_UPLOAD, "deleteTxt",
                        cacheFile.getAbsolutePath() + " deleted=" + deleted);
                pollAttempt(jobId, 1, callback);
            }

            @Override
            public void onFailure(@NonNull Call<ServerResponse<Object>> call, @NonNull Throwable t) {
                ApiTraceLogger.text(OWNER, API_UPLOAD, "failure", failureText(t));
                callback.onResult(CallResult.error(CallResult.NETWORK, "上传失败", t));
            }
        });
    }

    @Override
    public void poll(long jobId, ApiCallback<CallResult<CgmResult>> callback) {
        pollAttempt(jobId, 1, callback);
    }

    private void pollAttempt(long jobId,
                             int attempt,
                             ApiCallback<CallResult<CgmResult>> callback) {
        String api = API_CGM.replace("{jobId}", String.valueOf(jobId));
        endpoints.cgmJob(jobId).enqueue(new Callback<ServerModels.CgmJobResp>() {
            @Override
            public void onResponse(@NonNull Call<ServerModels.CgmJobResp> call,
                                   @NonNull Response<ServerModels.CgmJobResp> response) {
                ServerModels.CgmJobResp result = response.body();
                ApiTraceLogger.json(OWNER, api, "response attempt=" + attempt, result);
                if (isGenerated(response, result)) {
                    callback.onResult(CallResult.ok(toApiResult(result.data)));
                    return;
                }
                if (attempt >= MAX_POLL_ATTEMPTS) {
                    callback.onResult(CallResult.error(CallResult.EMPTY_DATA,
                            "CGM 数据尚未生成: attempts=" + attempt, null));
                    return;
                }
                scheduler.postDelayed(() -> pollAttempt(jobId, attempt + 1, callback), POLL_DELAY_MS);
            }

            @Override
            public void onFailure(@NonNull Call<ServerModels.CgmJobResp> call, @NonNull Throwable t) {
                ApiTraceLogger.text(OWNER, api, "failure attempt=" + attempt, failureText(t));
                if (attempt >= MAX_POLL_ATTEMPTS) {
                    callback.onResult(CallResult.error(CallResult.NETWORK, "CGM 轮询失败", t));
                    return;
                }
                scheduler.postDelayed(() -> pollAttempt(jobId, attempt + 1, callback), POLL_DELAY_MS);
            }
        });
    }

    private static boolean isUploadSuccess(@NonNull Response<ServerResponse<Object>> response,
                                           @Nullable ServerResponse<Object> server) {
        if (!response.isSuccessful() || server == null) {
            return false;
        }
        return server.success || server.code == 0 || server.code == 200;
    }

    @NonNull
    private static String uploadMessage(@Nullable ServerResponse<Object> server) {
        if (server == null) {
            return "上传响应为空";
        }
        return server.msg != null ? server.msg : "上传失败";
    }

    private static boolean isGenerated(@NonNull Response<ServerModels.CgmJobResp> response,
                                       @Nullable ServerModels.CgmJobResp result) {
        return response.isSuccessful()
                && result != null
                && result.code == 200
                && result.data != null
                && "GENERATED".equalsIgnoreCase(result.data.status);
    }

    @NonNull
    private static CgmResult toApiResult(@NonNull ServerModels.CgmJobData data) {
        CgmResult result = new CgmResult();
        result.resultId = data.resultId;
        result.jobId = data.jobId;
        result.datasetId = data.datasetId;
        result.pointCount = data.pointCount;
        result.unitCount = data.unitCount;
        result.predictionMin = data.predictionMin;
        result.predictionMax = data.predictionMax;
        result.predictionMean = data.predictionMean;
        result.predictionStd = data.predictionStd;
        result.avgMard = data.avgMard;
        result.mardStd = data.mardStd;
        result.summaryJson = toApiSummary(data.summaryJson);
        result.status = data.status;
        result.gmtCreate = data.gmtCreate;
        return result;
    }

    @Nullable
    private static CgmResult.Summary toApiSummary(@Nullable ServerModels.CgmSummary data) {
        if (data == null) return null;
        CgmResult.Summary result = new CgmResult.Summary();
        result.avgMard = data.avgMard;
        result.mardStd = data.mardStd;
        result.pointCount = data.pointCount;
        result.unitCount = data.unitCount;
        result.predictionStats = toApiPredictionStats(data.predictionStats);
        if (data.units != null) {
            result.units = new ArrayList<>();
            for (ServerModels.CgmUnit unit : data.units) {
                result.units.add(toApiUnit(unit));
            }
        }
        return result;
    }

    @Nullable
    private static CgmResult.PredictionStats toApiPredictionStats(@Nullable ServerModels.CgmPredictionStats data) {
        if (data == null) return null;
        CgmResult.PredictionStats result = new CgmResult.PredictionStats();
        result.min = data.min;
        result.max = data.max;
        result.mean = data.mean;
        result.std = data.std;
        return result;
    }

    @NonNull
    private static CgmResult.Unit toApiUnit(@NonNull ServerModels.CgmUnit data) {
        CgmResult.Unit result = new CgmResult.Unit();
        result.unit = data.unit;
        result.unitTitle = data.unitTitle;
        result.pointCount = data.pointCount;
        result.mard = data.mard;
        if (data.points != null) {
            result.points = new ArrayList<>();
            for (ServerModels.CgmPoint point : data.points) {
                result.points.add(toApiPoint(point));
            }
        }
        return result;
    }

    @NonNull
    private static CgmResult.Point toApiPoint(@NonNull ServerModels.CgmPoint data) {
        CgmResult.Point result = new CgmResult.Point();
        result.index = data.index;
        result.time = data.time;
        result.predicted = data.predicted;
        result.actual = data.actual;
        return result;
    }

    @Nullable
    static Long extractJobId(@Nullable ServerResponse<Object> server) {
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

    @NonNull
    private static String failureText(@NonNull Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }
}
