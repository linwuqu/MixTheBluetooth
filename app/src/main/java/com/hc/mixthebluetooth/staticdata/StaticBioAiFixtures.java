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
        AuthUser user = new AuthUser();
        user.accountId = ACCOUNT_ID;
        user.username = USERNAME;
        user.phone = PHONE;
        user.token = STATIC_TOKEN;
        return user;
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
