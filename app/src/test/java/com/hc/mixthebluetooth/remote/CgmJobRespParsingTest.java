package com.hc.mixthebluetooth.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
    public void parsesConfirmedCgmJobResponseShape() {
        ServerModels.CgmJobResp result = new Gson().fromJson(
                SAMPLE_JSON,
                ServerModels.CgmJobResp.class
        );

        assertEquals(200, result.code);
        assertEquals("success", result.message);
        assertNotNull(result.data);
        assertEquals(789L, result.data.resultId);
        assertEquals(456L, result.data.jobId);
        assertEquals(123L, result.data.datasetId);
        assertEquals(10, result.data.pointCount);
        assertEquals(1, result.data.unitCount);
        assertEquals(4.12, result.data.predictionMin, 0.001);
        assertEquals(9.87, result.data.predictionMax, 0.001);
        assertEquals(6.54, result.data.predictionMean, 0.001);
        assertEquals(1.23, result.data.predictionStd, 0.001);
        assertEquals(16.3, result.data.avgMard, 0.001);
        assertNull(result.data.mardStd);
        assertEquals("GENERATED", result.data.status);
        assertEquals("2026-05-14T10:01:07", result.data.gmtCreate);

        assertNotNull(result.data.summaryJson);
        assertEquals(16.3, result.data.summaryJson.avgMard, 0.001);
        assertNull(result.data.summaryJson.mardStd);
        assertEquals(10, result.data.summaryJson.pointCount);
        assertEquals(1, result.data.summaryJson.unitCount);
        assertNotNull(result.data.summaryJson.predictionStats);
        assertEquals(6.54, result.data.summaryJson.predictionStats.mean, 0.001);
        assertNotNull(result.data.summaryJson.units);
        assertEquals(1, result.data.summaryJson.units.size());
        assertEquals("lyc-5V-1h", result.data.summaryJson.units.get(0).unitTitle);
        assertNotNull(result.data.summaryJson.units.get(0).points);
        assertEquals(3, result.data.summaryJson.units.get(0).points.size());
        assertEquals(33, result.data.summaryJson.units.get(0).points.get(0).time);
        assertEquals(6.21, result.data.summaryJson.units.get(0).points.get(0).predicted, 0.001);
        assertEquals(5.4, result.data.summaryJson.units.get(0).points.get(0).actual, 0.001);
    }
}
