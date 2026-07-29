package com.biosensor.migratedev.port.adapter.bluetoothport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Bt24AdvertisementFilterTest {

    @Test
    fun `accepts only BLE advertisements with FFE0 and manufacturer 4458`() {
        assertTrue(
            Bt24AdvertisementFilter.matches(
                BluetoothAdvertisement(
                    isBle = true,
                    serviceUuids = setOf("FFE0"),
                    manufacturerIds = setOf(0x4458)
                )
            )
        )

        assertFalse(
            Bt24AdvertisementFilter.matches(
                BluetoothAdvertisement(
                    isBle = false,
                    serviceUuids = setOf("FFE0"),
                    manufacturerIds = setOf(0x4458)
                )
            )
        )
        assertFalse(
            Bt24AdvertisementFilter.matches(
                BluetoothAdvertisement(
                    isBle = true,
                    serviceUuids = setOf("FFF0"),
                    manufacturerIds = setOf(0x4458)
                )
            )
        )
        assertFalse(
            Bt24AdvertisementFilter.matches(
                BluetoothAdvertisement(
                    isBle = true,
                    serviceUuids = setOf("FFE0"),
                    manufacturerIds = setOf(0x004C)
                )
            )
        )
    }

    @Test
    fun `accepts the standard 128 bit representation of FFE0`() {
        assertTrue(
            Bt24AdvertisementFilter.matches(
                BluetoothAdvertisement(
                    isBle = true,
                    serviceUuids = setOf(
                        "0000ffe0-0000-1000-8000-00805f9b34fb"
                    ),
                    manufacturerIds = setOf(0x4458)
                )
            )
        )
    }

    @Test
    fun `rejects an advertisement when a required field is absent`() {
        assertFalse(
            Bt24AdvertisementFilter.matches(
                BluetoothAdvertisement(
                    isBle = true,
                    serviceUuids = null,
                    manufacturerIds = setOf(0x4458)
                )
            )
        )
        assertFalse(
            Bt24AdvertisementFilter.matches(
                BluetoothAdvertisement(
                    isBle = true,
                    serviceUuids = setOf("FFE0"),
                    manufacturerIds = null
                )
            )
        )
    }
}
