package com.hc.mixthebluetooth.impl.cgm;

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
