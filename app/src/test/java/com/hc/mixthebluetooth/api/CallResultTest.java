package com.hc.mixthebluetooth.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallResultTest {
    @Test
    public void okCarriesData() {
        CallResult<String> result = CallResult.ok("value");

        assertTrue(result.isOk());
        assertEquals(CallResult.Status.OK, result.status);
        assertEquals("value", result.data);
        assertEquals(0, result.code);
    }

    @Test
    public void pendingHasNoData() {
        CallResult<String> result = CallResult.pending("recording");

        assertTrue(result.isPending());
        assertEquals(CallResult.Status.PENDING, result.status);
        assertEquals("recording", result.message);
        assertNull(result.data);
    }

    @Test
    public void errorCarriesCodeAndCause() {
        RuntimeException cause = new RuntimeException("boom");

        CallResult<String> result = CallResult.error(CallResult.NETWORK, "网络错误", cause);

        assertTrue(result.isError());
        assertEquals(CallResult.Status.ERROR, result.status);
        assertEquals(CallResult.NETWORK, result.code);
        assertEquals(cause, result.cause);
    }

    @Test
    public void pendingAndOkShareCodeButDifferentStatus() {
        CallResult<String> ok = CallResult.ok("value");
        CallResult<String> pending = CallResult.pending("recording");

        assertEquals(0, ok.code);
        assertEquals(0, pending.code);
        assertNotEquals(ok.status, pending.status);
        assertTrue(ok.isOk());
        assertTrue(pending.isPending());
    }

    @Test
    public void negativeCodesAreOnlyForLocalErrors() {
        assertTrue(CallResult.UNKNOWN < 0);
        assertTrue(CallResult.NETWORK < 0);
        assertTrue(CallResult.EMPTY_RESPONSE < 0);
        assertTrue(CallResult.EMPTY_DATA < 0);
        assertTrue(CallResult.LOCAL_FILE_NOT_FOUND < 0);
        assertTrue(CallResult.DEVICE_REPLAY_INCOMPLETE < 0);
    }
}
