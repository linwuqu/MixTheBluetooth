package com.hc.mixthebluetooth.application.cgm;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.api.cgm.CgmResult;
import com.hc.mixthebluetooth.api.cgm.CgmService;
import com.hc.mixthebluetooth.driver.implementation.log.ApiTraceLogger;
import com.hc.mixthebluetooth.driver.implementation.http.endpoint.BioAiEndpoints;
import com.hc.mixthebluetooth.driver.implementation.http.dto.ServerDtos;
import com.hc.mixthebluetooth.driver.implementation.http.dto.ServerResponse;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DefaultCgmJobService implements CgmService {
    interface PollScheduler {
        void postDelayed(@NonNull Runnable runnable, long delayMillis);
    }

    private static final String OWNER = "DefaultCgmJobService";
    private static final String API_UPLOAD = "POST /api/cgm/v1/dataset/upload";
    private static final String API_CGM_JOB = "GET /api/cgm/v1/jobs/{jobId}";
    private static final String API_CGM_INFO = "GET /api/cgm/v1/predictions/{resultId}/info";
    private static final String API_CGM_CURVE = "GET /api/cgm/v1/predictions/{resultId}/curve";

    private static final int MAX_POLL_ATTEMPTS = 3;
    private static final long POLL_DELAY_MS = 1500L;
    private static final String DEFAULT_PARENT_ID = "0";

    private final BioAiEndpoints endpoints;
    private final PollScheduler scheduler;

    public DefaultCgmJobService(@NonNull BioAiEndpoints endpoints) {
        this(endpoints, (runnable, delayMillis) ->
                new Handler(Looper.getMainLooper()).postDelayed(runnable, delayMillis));
    }

    DefaultCgmJobService(@NonNull BioAiEndpoints endpoints, @NonNull PollScheduler scheduler) {
        this.endpoints = endpoints;
        this.scheduler = scheduler;
    }

    @Override
    public void uploadAndPoll(File cacheFile,
                              ApiCallback<CallResult<CgmResult>> callback) {
        if (cacheFile == null || !cacheFile.exists() || !cacheFile.isFile()) {
            callback.onResult(CallResult.error(CallResult.LOCAL_FILE_NOT_FOUND, "CGM cache txt file not found", null));
            return;
        }

        ApiTraceLogger.file(OWNER, API_UPLOAD, cacheFile);

        String identify = cacheFile.getName() + "_" + cacheFile.length() + "_" + System.currentTimeMillis();
        RequestBody identifyBody = RequestBody.create(MediaType.parse("text/plain"), identify);
        RequestBody parentIdBody = RequestBody.create(MediaType.parse("text/plain"), DEFAULT_PARENT_ID);
        RequestBody fileSizeBody = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(cacheFile.length()));
        RequestBody fileContent = RequestBody.create(cacheFile, MediaType.parse("text/plain"));
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", cacheFile.getName(), fileContent);



        endpoints.cgmUpload(identifyBody, parentIdBody, fileSizeBody, filePart)
                .enqueue(new Callback<ServerResponse<ServerDtos.CgmUploadResp>>() {
                    @Override
                    public void onResponse(@NonNull Call<ServerResponse<ServerDtos.CgmUploadResp>> call,
                                           @NonNull Response<ServerResponse<ServerDtos.CgmUploadResp>> response) {
                        ServerResponse<ServerDtos.CgmUploadResp> server = response.body();
                        ApiTraceLogger.json(OWNER, API_UPLOAD, "response", server);

                        if (!response.isSuccessful() || server == null) {
                            callback.onResult(CallResult.error(response.code(),
                                    uploadMessage(server), null));
                            return;
                        }
                        if (!server.isOk()) {
                            callback.onResult(CallResult.error(server.code,
                                    server.msg != null ? server.msg : "Upload failed", null));
                            return;
                        }
                        if (server.data == null) {
                            callback.onResult(CallResult.error(CallResult.EMPTY_DATA,
                                    "Empty upload response", null));
                            return;
                        }
                        if (!"VALID".equalsIgnoreCase(server.data.datasetStatus)
                                || server.data.jobId == null) {
                            String err = server.data.errorMsg != null
                                    ? server.data.errorMsg
                                    : ("datasetStatus=" + server.data.datasetStatus);
                            callback.onResult(CallResult.error(CallResult.EMPTY_DATA, err, null));
                            return;
                        }

                        pollAttempt(server.data.jobId, 1, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ServerResponse<ServerDtos.CgmUploadResp>> call,
                                          @NonNull Throwable t) {
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
        String api = API_CGM_JOB.replace("{jobId}", String.valueOf(jobId));
        endpoints.cgmJob(jobId).enqueue(new Callback<ServerResponse<ServerDtos.CgmJobData>>() {
            @Override
            public void onResponse(@NonNull Call<ServerResponse<ServerDtos.CgmJobData>> call,
                                   @NonNull Response<ServerResponse<ServerDtos.CgmJobData>> response) {
                ServerResponse<ServerDtos.CgmJobData> server = response.body();
                ServerDtos.CgmJobData data = server == null ? null : server.data;
                ApiTraceLogger.json(OWNER, api, "response attempt=" + attempt, server);

                if (!response.isSuccessful() || server == null || !server.isOk()) {
                    if (attempt >= MAX_POLL_ATTEMPTS) {
                        callback.onResult(CallResult.error(response.code(),
                                server != null && server.msg != null ? server.msg : "CGM 轮询失败", null));
                        return;
                    }
                    scheduler.postDelayed(() -> pollAttempt(jobId, attempt + 1, callback), POLL_DELAY_MS);
                    return;
                }
                if (data == null) {
                    if (attempt >= MAX_POLL_ATTEMPTS) {
                        callback.onResult(CallResult.error(CallResult.EMPTY_DATA, "CGM 数据尚未生成: attempts=" + attempt, null));
                        return;
                    }
                    scheduler.postDelayed(() -> pollAttempt(jobId, attempt + 1, callback), POLL_DELAY_MS);
                    return;
                }

                String status = data.status == null ? "" : data.status.toUpperCase();
                if ("FAILED".equals(status)) {
                    String err = data.errorMsg != null ? data.errorMsg : "CGM job failed";
                    callback.onResult(CallResult.error(CallResult.EMPTY_DATA, err, null));
                    return;
                }
                if ("SUCCESS".equals(status) && data.resultId != null) {
                    fetchResultDetail(data.resultId, jobId, callback);
                    return;
                }

                if (attempt >= MAX_POLL_ATTEMPTS) {
                    callback.onResult(CallResult.error(CallResult.EMPTY_DATA,
                            "CGM 数据尚未生成: attempts=" + attempt + ", status=" + status, null));
                    return;
                }
                scheduler.postDelayed(() -> pollAttempt(jobId, attempt + 1, callback), POLL_DELAY_MS);
            }

            @Override
            public void onFailure(@NonNull Call<ServerResponse<ServerDtos.CgmJobData>> call,
                                  @NonNull Throwable t) {
                ApiTraceLogger.text(OWNER, api, "failure attempt=" + attempt, failureText(t));
                if (attempt >= MAX_POLL_ATTEMPTS) {
                    callback.onResult(CallResult.error(CallResult.NETWORK, "CGM 轮询失败", t));
                    return;
                }
                scheduler.postDelayed(() -> pollAttempt(jobId, attempt + 1, callback), POLL_DELAY_MS);
            }
        });
    }

    private void fetchResultDetail(long resultId, long jobId, ApiCallback<CallResult<CgmResult>> callback) {
        AtomicReference<ServerDtos.CgmResultInfo> infoRef = new AtomicReference<>(null);
        AtomicReference<ServerDtos.CgmCurveResp> curveRef = new AtomicReference<>(null);
        AtomicReference<Throwable> errRef = new AtomicReference<>(null);

        endpoints.cgmResultInfo(resultId, null)
                .enqueue(new Callback<ServerResponse<ServerDtos.CgmResultInfo>>() {
                    @Override
                    public void onResponse(@NonNull Call<ServerResponse<ServerDtos.CgmResultInfo>> call,
                                           @NonNull Response<ServerResponse<ServerDtos.CgmResultInfo>> response) {
                        ServerResponse<ServerDtos.CgmResultInfo> body = response.body();
                        ApiTraceLogger.json(OWNER, API_CGM_INFO.replace("{resultId}", String.valueOf(resultId)),
                                "response", body);
                        if (body != null && body.isOk() && body.data != null) {
                            infoRef.set(body.data);
                        }
                        tryStartCurve();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ServerResponse<ServerDtos.CgmResultInfo>> call,
                                          @NonNull Throwable t) {
                        errRef.set(t);
                        ApiTraceLogger.text(OWNER, API_CGM_INFO, "failure", failureText(t));
                        tryStartCurve();
                    }

                    private void tryStartCurve() {
                        endpoints.cgmCurve(resultId, null)
                                .enqueue(new Callback<ServerResponse<ServerDtos.CgmCurveResp>>() {
                                    @Override
                                    public void onResponse(@NonNull Call<ServerResponse<ServerDtos.CgmCurveResp>> call,
                                                           @NonNull Response<ServerResponse<ServerDtos.CgmCurveResp>> response) {
                                        ServerResponse<ServerDtos.CgmCurveResp> body = response.body();
                                        ApiTraceLogger.json(OWNER, API_CGM_CURVE.replace("{resultId}", String.valueOf(resultId)),
                                                "response", body);
                                        if (body != null && body.isOk() && body.data != null) {
                                            curveRef.set(body.data);
                                        }
                                        deliver();
                                    }

                                    @Override
                                    public void onFailure(@NonNull Call<ServerResponse<ServerDtos.CgmCurveResp>> call,
                                                          @NonNull Throwable t) {
                                        errRef.set(t);
                                        ApiTraceLogger.text(OWNER, API_CGM_CURVE, "failure", failureText(t));
                                        deliver();
                                    }

                                    private void deliver() {
                                        if (infoRef.get() == null && curveRef.get() == null) {
                                            Throwable cause = errRef.get();
                                            callback.onResult(CallResult.error(CallResult.EMPTY_DATA,
                                                    "CGM 结果拉取失败", cause));
                                            return;
                                        }
                                        callback.onResult(CallResult.ok(
                                                buildCgmResult(resultId, jobId, infoRef.get(), curveRef.get())));
                                    }
                                });
                    }
                });
    }

    @NonNull
    private static CgmResult buildCgmResult(long resultId, long jobId,
                                            @Nullable ServerDtos.CgmResultInfo info,
                                            @Nullable ServerDtos.CgmCurveResp curve) {
        CgmResult result = new CgmResult();
        result.resultId = resultId;
        result.jobId = jobId;
        if (info != null) {
            if (info.min != null) result.predictionMin = info.min;
            if (info.max != null) result.predictionMax = info.max;
            if (info.mean != null) result.predictionMean = info.mean;
            if (info.std != null) result.predictionStd = info.std;
        }
        result.status = "SUCCESS";
        if (curve != null) {
            if (curve.unitCount != null) {
                result.unitCount = curve.unitCount;
            }
            if (curve.units != null) {
                int totalPoints = 0;
                double mardSum = 0d;
                int mardCount = 0;
                ArrayList<CgmResult.Unit> apiUnits = new ArrayList<>();
                for (ServerDtos.CgmUnit u : curve.units) {
                    CgmResult.Unit apiUnit = new CgmResult.Unit();
                    apiUnit.unit = u.unit;
                    apiUnit.unitTitle = u.unitTitle;
                    apiUnit.pointCount = u.pointCount;
                    apiUnit.mard = u.mard;
                    totalPoints += u.pointCount;
                    if (!Double.isNaN(u.mard)) {
                        mardSum += u.mard;
                        mardCount++;
                    }
                    if (u.points != null) {
                        ArrayList<CgmResult.Point> apiPoints = new ArrayList<>(u.points.size());
                        for (ServerDtos.CgmPoint p : u.points) {
                            CgmResult.Point ap = new CgmResult.Point();
                            ap.index = p.index;
                            ap.time = p.time;
                            ap.predicted = p.predicted;
                            ap.actual = p.actual;
                            ap.rawTime = p.rawTime;
                            apiPoints.add(ap);
                        }
                        apiUnit.points = apiPoints;
                    }
                    apiUnits.add(apiUnit);
                }
                result.pointCount = totalPoints;
                if (mardCount > 0) {
                    result.avgMard = mardSum / mardCount;
                }

                CgmResult.Summary summary = new CgmResult.Summary();
                summary.avgMard = result.avgMard;
                summary.pointCount = totalPoints;
                summary.unitCount = curve.unitCount == null ? apiUnits.size() : curve.unitCount;
                if (info != null) {
                    CgmResult.PredictionStats stats = new CgmResult.PredictionStats();
                    if (info.min != null) stats.min = info.min;
                    if (info.max != null) stats.max = info.max;
                    if (info.mean != null) stats.mean = info.mean;
                    if (info.std != null) stats.std = info.std;
                    summary.predictionStats = stats;
                }
                summary.units = apiUnits;
                result.summaryJson = summary;
            }
        }
        return result;
    }

    @NonNull
    private static String uploadMessage(@Nullable ServerResponse<ServerDtos.CgmUploadResp> server) {
        if (server == null) {
            return "上传响应为空";
        }
        return server.msg != null ? server.msg : "上传失败";
    }

    @NonNull
    private static String failureText(@NonNull Throwable t) {
        return t.getClass().getName() + ": " + t.getMessage();
    }
}
