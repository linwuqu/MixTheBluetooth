package com.hc.bluetoothlibrary;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class AllBluetoothManageTest {

    @Test
    public void remembersExternalScanDeviceByReplacingSameMac() {
        DeviceModule oldTarget = device("AA:01");
        DeviceModule other = device("AA:02");
        DeviceModule replacement = device("AA:01");
        List<DeviceModule> devices = new ArrayList<>();
        devices.add(oldTarget);
        devices.add(other);

        AllBluetoothManage.rememberForConnection(
                devices,
                replacement
        );

        assertEquals(2, devices.size());
        assertSame(other, devices.get(0));
        assertSame(replacement, devices.get(1));
    }

    private static DeviceModule device(final String mac) {
        return new DeviceModule("BT24-S", null) {
            @Override
            public String getMac() {
                return mac;
            }
        };
    }
}
