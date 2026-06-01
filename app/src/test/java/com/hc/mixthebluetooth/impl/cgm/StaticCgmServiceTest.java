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
