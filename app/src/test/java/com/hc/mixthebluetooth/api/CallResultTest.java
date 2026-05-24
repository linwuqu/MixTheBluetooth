package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallResultTest {
    @Test
    public void okCarriesData() {
        CallResult<String> result = CallResult.ok("value");

        assertTrue(result.isOk());
        assertEquals("value", result.data);
        assertEquals(0, result.code);
    }

    @Test
    public void pendingHasNoData() {
        CallResult<String> result = CallResult.pending("recording");

        assertTrue(result.isPending());
        assertEquals("recording", result.message);
        assertNull(result.data);
    }

    @Test
    public void errorCarriesCodeAndCause() {
        RuntimeException cause = new RuntimeException("boom");

        CallResult<String> result = CallResult.error(CallResult.NETWORK, "网络错误", cause);

        assertTrue(result.isError());
        assertEquals(CallResult.NETWORK, result.code);
        assertEquals(cause, result.cause);
    }
}
