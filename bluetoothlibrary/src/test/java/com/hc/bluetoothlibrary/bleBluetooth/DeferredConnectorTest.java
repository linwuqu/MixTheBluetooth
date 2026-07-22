package com.hc.bluetoothlibrary.bleBluetooth;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DeferredConnectorTest {

    @Test
    public void waitsForConnectionBeforeStartingPendingTarget() {
        DeferredConnector<String> connector = new DeferredConnector<>();
        List<String> connected = new ArrayList<>();

        connector.submit("BT24-S");
        assertTrue(connected.isEmpty());

        connector.attach(connected::add);

        assertEquals(1, connected.size());
        assertEquals("BT24-S", connected.get(0));
    }

    @Test
    public void startsImmediatelyWhenConnectionIsAlreadyAvailable() {
        DeferredConnector<String> connector = new DeferredConnector<>();
        List<String> connected = new ArrayList<>();
        connector.attach(connected::add);

        connector.submit("BT24-S");

        assertEquals(1, connected.size());
    }

    @Test
    public void clearDropsPendingTarget() {
        DeferredConnector<String> connector = new DeferredConnector<>();
        List<String> connected = new ArrayList<>();

        connector.submit("BT24-S");
        connector.clear();
        connector.attach(connected::add);

        assertTrue(connected.isEmpty());
    }
}
