package com.hc.mixthebluetooth.ui.main;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Bt24AdvertisementFilterTest {

    @Test
    public void acceptsMatchingBt24Advertisement() {
        assertEquals(
                Bt24AdvertisementFilter.Result.MATCH,
                Bt24AdvertisementFilter.evaluate(true, "BT24-S", true, true)
        );
    }

    @Test
    public void rejectsNonBleDevice() {
        assertEquals(
                Bt24AdvertisementFilter.Result.NOT_BLE,
                Bt24AdvertisementFilter.evaluate(false, "BT24-S", true, true)
        );
    }

    @Test
    public void rejectsDifferentDeviceName() {
        assertEquals(
                Bt24AdvertisementFilter.Result.NAME_MISMATCH,
                Bt24AdvertisementFilter.evaluate(true, "Other", true, true)
        );
    }

    @Test
    public void rejectsAdvertisementWithoutFfe0() {
        assertEquals(
                Bt24AdvertisementFilter.Result.SERVICE_UUID_MISSING,
                Bt24AdvertisementFilter.evaluate(true, "BT24-S", false, true)
        );
    }

    @Test
    public void rejectsAdvertisementWithoutManufacturerId() {
        assertEquals(
                Bt24AdvertisementFilter.Result.MANUFACTURER_ID_MISSING,
                Bt24AdvertisementFilter.evaluate(true, "BT24-S", true, false)
        );
    }
}
