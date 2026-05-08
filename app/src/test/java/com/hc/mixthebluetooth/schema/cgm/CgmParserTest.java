package com.hc.mixthebluetooth.schema.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CgmParserTest {

    @Test
    public void parseCacheStart() {
        CgmSample sample = CgmParser.parseLine("Start Playback");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_CACHE_START, sample.event);
    }

    @Test
    public void parseCacheDone() {
        CgmSample sample = CgmParser.parseLine("Playback all done");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_CACHE_DONE, sample.event);
    }

    @Test
    public void parseCaPrimary() {
        CgmSample sample = CgmParser.parseLine("CA:1,12.5");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_CA, sample.event);
        assertEquals(12.5f, sample.metrics().get(CgmSample.METRIC_PRIMARY), 0.001f);
    }

    @Test
    public void parseCurrentValue() {
        CgmSample sample = CgmParser.parseLine("CA:266,8.8");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_CURRENT, sample.event);
        assertEquals(8.8f, sample.metrics().get(CgmSample.METRIC_CURRENT), 0.001f);
    }

    @Test
    public void parseEisPrimary() {
        CgmSample sample = CgmParser.parseLine("EIS:10,20,30.5");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_EIS, sample.event);
        assertEquals(30.5f, sample.metrics().get(CgmSample.METRIC_PRIMARY), 0.001f);
    }

    @Test
    public void parseRiStatus() {
        CgmSample sample = CgmParser.parseLine("RI:done");
        assertNotNull(sample);
        assertEquals(CgmSample.EVENT_RI, sample.event);
        assertEquals("RI:done", sample.status);
    }

    @Test
    public void parseUnknownReturnsNull() {
        assertNull(CgmParser.parseLine("hello"));
    }

    @Test
    public void parseMalformedNumberReturnsNull() {
        assertNull(CgmParser.parseLine("CA:1,abc"));
        assertNull(CgmParser.parseLine("EIS:10,20,abc"));
    }
}
