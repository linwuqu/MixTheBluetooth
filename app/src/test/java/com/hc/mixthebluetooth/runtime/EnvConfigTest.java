package com.hc.mixthebluetooth.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EnvConfigTest {
    @Test
    public void staticLanIsAccepted() {
        EnvConfig config = new EnvConfig("static-lan", "http://127.0.0.1:18080/", true, "static-lan", true);

        assertEquals("static-lan", config.env());
        assertTrue(config.isStaticLan());
        assertFalse(config.isDev());
        assertTrue(config.networkEnabled());
    }

    @Test
    public void devIsAccepted() {
        EnvConfig config = new EnvConfig("dev", "http://example.test/", true, "dev", true);

        assertEquals("dev", config.env());
        assertTrue(config.isDev());
        assertFalse(config.isStaticLan());
    }

    @Test(expected = IllegalArgumentException.class)
    public void staticIsRejected() {
        EnvConfig.normalizeEnv("static");
    }

    @Test(expected = IllegalArgumentException.class)
    public void prodIsRejected() {
        EnvConfig.normalizeEnv("prod");
    }

    @Test(expected = IllegalArgumentException.class)
    public void loopIsRejected() {
        EnvConfig.normalizeEnv("loop");
    }
}
