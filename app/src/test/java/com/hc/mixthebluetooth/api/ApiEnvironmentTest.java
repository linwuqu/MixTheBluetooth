package com.hc.mixthebluetooth.api;

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
