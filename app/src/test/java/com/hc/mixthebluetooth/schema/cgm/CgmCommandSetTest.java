package com.hc.mixthebluetooth.schema.cgm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CgmCommandSetTest {

    @Test
    public void readCacheCommand() {
        assertEquals("ALL\n\r", CgmCommandSet.readCache());
    }

    @Test
    public void deleteCacheCommand() {
        assertEquals("DELETE\n\r", CgmCommandSet.deleteCache());
    }

    @Test
    public void startCommandUsesProvidedTime() {
        assertEquals("TIME,2026,05,04,10,20,30\n\r", CgmCommandSet.startWithTime("2026,05,04,10,20,30"));
    }

    @Test
    public void parameterCommandMatchesLegacyJoin() {
        CgmParameters params = new CgmParameters("60", "10", "12", "5", "9");
        assertEquals("0600010100120115109", CgmCommandSet.buildParameters(params));
    }
}
