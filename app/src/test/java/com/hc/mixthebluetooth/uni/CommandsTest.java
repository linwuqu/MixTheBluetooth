package com.hc.mixthebluetooth.uni;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

public class CommandsTest {

    @Test
    public void legacyCgmReadCacheCommandIsAll() {
        assertEquals("ALL\n\r", Commands.LegacyCgm.readCache());
    }

    @Test
    public void legacyCgmDeleteCacheCommandIsDelete() {
        assertEquals("DELETE\n\r", Commands.LegacyCgm.deleteCache());
    }

    @Test
    public void legacyCgmSyncTimeUsesExpectedFormat() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.MAY, 23, 10, 20, 30);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date = calendar.getTime();

        assertEquals("TIME,2026,05,23,10,20,30\n\r", Commands.LegacyCgm.syncTime(date));
    }
}
