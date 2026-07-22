package com.hc.bluetoothlibrary.bleBluetooth;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BleNotificationTraceTest {

    @Test
    public void recordsSequenceGapElapsedAndTotalBytes() {
        BleNotificationTrace trace = new BleNotificationTrace();

        BleNotificationTrace.Sample first = trace.record(1_000, 20);
        BleNotificationTrace.Sample second = trace.record(1_012, 15);

        assertEquals(1, first.sequence);
        assertEquals(0, first.gapMillis);
        assertEquals(0, first.elapsedMillis);
        assertEquals(20, first.totalBytes);
        assertEquals(2, second.sequence);
        assertEquals(12, second.gapMillis);
        assertEquals(12, second.elapsedMillis);
        assertEquals(35, second.totalBytes);
    }

    @Test
    public void formatsBoundedHexPreview() {
        byte[] data = new byte[]{0x41, 0x4C, 0x4C, 0x0A, 0x0D};

        assertEquals("41 4C 4C ...", BleNotificationTrace.hexPreview(data, 3));
        assertEquals("41 4C 4C 0A 0D", BleNotificationTrace.hexPreview(data, 8));
    }
}
