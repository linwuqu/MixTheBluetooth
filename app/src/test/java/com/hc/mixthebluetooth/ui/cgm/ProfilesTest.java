package com.hc.mixthebluetooth.ui.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class ProfilesTest {

    @Test
    public void cgmReturnsCgmProfileSpec() {
        CgmController.ProfileSpec spec = Profiles.cgm();

        assertEquals("cgm", spec.id);
        assertEquals(3, spec.actions.size());
        assertEquals(1, spec.widgets.size());
        assertNotNull(spec.rawLineConsumer);
    }
}
