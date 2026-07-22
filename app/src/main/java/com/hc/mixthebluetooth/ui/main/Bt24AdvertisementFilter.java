package com.hc.mixthebluetooth.ui.main;

final class Bt24AdvertisementFilter {
//    static final String DEVICE_NAME = "BT24-S";
    static final String SERVICE_UUID = "FFE0";
    static final int MANUFACTURER_ID = 0x4458;

    enum Result {
        MATCH,
        NOT_BLE,
        NAME_MISMATCH,
        SERVICE_UUID_MISSING,
        MANUFACTURER_ID_MISSING
    }

    private Bt24AdvertisementFilter() {
    }

    static Result evaluate(boolean isBle,
                           String deviceName,
                           boolean advertisesService,
                           boolean hasManufacturerId) {
        if (!isBle) return Result.NOT_BLE;
        if (!advertisesService) return Result.SERVICE_UUID_MISSING;
        if (!hasManufacturerId) return Result.MANUFACTURER_ID_MISSING;
        return Result.MATCH;
    }
}
