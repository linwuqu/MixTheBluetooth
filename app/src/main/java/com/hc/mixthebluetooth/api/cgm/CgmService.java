package com.hc.mixthebluetooth.api.cgm;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;
import com.hc.mixthebluetooth.remote.ServerModels;

import java.io.File;

public interface CgmService {
    void uploadAndPoll(File cacheFile, ApiCallback<CallResult<ServerModels.CgmJobData>> callback);

    void poll(long jobId, ApiCallback<CallResult<ServerModels.CgmJobData>> callback);
}
