package com.biosensor.migratedev.ui.connection

import android.Manifest
import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BluetoothAccessGateTest {

    @Test
    fun `Android 12 and newer request nearby devices and both location levels`() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            requiredBluetoothPermissions(Build.VERSION_CODES.S)
        )
    }

    @Test
    fun `Android 11 and older request fine location`() {
        assertArrayEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            requiredBluetoothPermissions(Build.VERSION_CODES.R)
        )
    }
}
