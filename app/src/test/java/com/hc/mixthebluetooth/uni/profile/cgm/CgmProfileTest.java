package com.hc.mixthebluetooth.uni.profile.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.hc.mixthebluetooth.uni.Controller;

import org.junit.Test;

public class CgmProfileTest {
    @Test
    public void cgmProfileDeclaresThreeCommandActions() {
        Controller.ProfileSpec spec = CgmProfile.create();

        assertEquals("cgm", spec.id);
        assertEquals(3, spec.actions.size());
        assertNotNull(spec.rawLineConsumer);
        assertTrue(spec.actions.get(0).textSupplier.get().startsWith("TIME,"));
        assertEquals("ALL\n\r", spec.actions.get(1).textSupplier.get());
        assertEquals("DELETE\n\r", spec.actions.get(2).textSupplier.get());
    }
}
