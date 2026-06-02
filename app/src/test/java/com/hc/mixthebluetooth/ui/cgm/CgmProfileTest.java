package com.hc.mixthebluetooth.ui.cgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CgmProfileTest {
    @Test
    public void cgmProfileDeclaresThreeCommandActions() {
        CgmController.ProfileSpec spec = CgmProfile.create();

        assertEquals("cgm", spec.id);
        assertEquals(3, spec.actions.size());
        assertEquals(1, spec.widgets.size());
        assertNotNull(spec.rawLineConsumer);
        assertTrue(spec.actions.get(0).textSupplier.get().startsWith("TIME,"));
        assertEquals("ALL\n\r", spec.actions.get(1).textSupplier.get());
        assertEquals("DELETE\n\r", spec.actions.get(2).textSupplier.get());
        assertEquals(CgmWidgets.WidgetKind.CGM_RESULT, spec.widgets.get(0).kind);
    }
}
