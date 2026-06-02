package com.hc.mixthebluetooth.ui.cgm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

public class CgmCommandsTest {

    @Test
    public void legacyCgmReadCacheCommandIsAll() {
        assertEquals("ALL\n\r", CgmCommands.LegacyCgm.readCache());
    }

    @Test
    public void legacyCgmDeleteCacheCommandIsDelete() {
        assertEquals("DELETE\n\r", CgmCommands.LegacyCgm.deleteCache());
    }

    @Test
    public void legacyCgmSyncTimeUsesExpectedFormat() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.MAY, 23, 10, 20, 30);
        calendar.set(Calendar.MILLISECOND, 0);
        Date date = calendar.getTime();

        assertEquals("TIME,2026,05,23,10,20,30\n\r", CgmCommands.LegacyCgm.syncTime(date));
    }
}
