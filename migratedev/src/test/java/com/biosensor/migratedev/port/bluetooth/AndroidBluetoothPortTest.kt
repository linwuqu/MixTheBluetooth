package com.biosensor.migratedev.port.bluetooth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidBluetoothPortTest {

    @Test
    fun legacyParametersKeepTheOldLibraryDefaults() {
        assertEquals(
            LegacyBluetoothParameters(
                bleSendDelayState = 1,
                regularSendIntervalLevel = 0,
                bleReadBufferBytes = 1_000,
                classicReadBufferBytes = 1_500,
                receiveQuietPeriodMillis = 100,
                checkNewline = true
            ),
            LegacyBluetoothParameters()
        )
    }

    @Test
    fun mixedScanUpdatesOneDeviceInsteadOfAppendingDuplicates() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client)
        val results = mutableListOf<BluetoothResult>()

        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(BluetoothCommand.StartScan).toList(results)
        }

        client.deviceFound(LibraryBluetoothDevice("AA:01", "旧名称", true, -60))
        client.deviceFound(LibraryBluetoothDevice("AA:01", "新名称", true, -45))
        client.scanFinished()
        collection.join()

        assertEquals(1, client.mixedScanCount)
        assertEquals(
            listOf(
                BluetoothResult.DevicesFound(
                    listOf(deviceInfo("AA:01", "旧名称", true, -60))
                ),
                BluetoothResult.DevicesFound(
                    listOf(deviceInfo("AA:01", "新名称", true, -45))
                ),
                BluetoothResult.ScanStopped
            ),
            results
        )
    }

    @Test
    fun connectionFlowRemainsOpenUntilTheConnectedDeviceDisconnects() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client, connectionTimeoutMillis = 5_000)
        val results = mutableListOf<BluetoothResult>()
        val device = LibraryBluetoothDevice("AA:02", "CGM", true, -30)

        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(BluetoothCommand.Connect(device.id)).toList(results)
        }
        client.connected(device)
        runCurrent()

        assertEquals(listOf(BluetoothResult.Connected(device.toDeviceInfo())), results)
        assertTrue(collection.isActive)

        client.connectionLost(device.id)
        collection.join()

        assertEquals(
            listOf(
                BluetoothResult.Connected(device.toDeviceInfo()),
                BluetoothResult.Disconnected
            ),
            results
        )
    }

    @Test
    fun connectionLossBeforeSuccessIsReportedAsConnectFailed() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client, connectionTimeoutMillis = 5_000)
        val results = mutableListOf<BluetoothResult>()

        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(BluetoothCommand.Connect("AA:03")).toList(results)
        }
        client.connectionLost("AA:03")
        collection.join()

        assertEquals(
            listOf(BluetoothResult.ConnectFailed("蓝牙连接失败")),
            results
        )
    }

    @Test
    fun connectionTimeoutClosesTheSessionAndDisconnectsTheLibrary() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client, connectionTimeoutMillis = 1_000)
        val results = mutableListOf<BluetoothResult>()

        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(BluetoothCommand.Connect("AA:04")).toList(results)
        }
        advanceTimeBy(1_000)
        runCurrent()
        collection.join()

        assertEquals(listOf(BluetoothResult.ConnectTimeout), results)
        assertEquals(listOf("AA:04"), client.disconnectedDeviceIds)
    }

    @Test
    fun missingRuntimePermissionIsReportedAsScanFailure() = runTest {
        val client = FakeBluetoothLibraryClient().apply {
            scanFailure = SecurityException("permission denied")
        }
        val port = AndroidBluetoothPort(client)

        val results = port.execute(BluetoothCommand.StartScan).toList()

        assertEquals(
            listOf(BluetoothResult.ScanFailed("缺少蓝牙扫描或连接权限")),
            results
        )
    }

    private fun deviceInfo(id: String, name: String?, isBle: Boolean, rssi: Int) =
        com.biosensor.migratedev.decisioncore.connection.BluetoothDeviceInfo(
            id = id,
            name = name,
            isBle = isBle,
            rssi = rssi
        )

    private class FakeBluetoothLibraryClient : BluetoothLibraryClient {
        private lateinit var listener: BluetoothLibraryListener

        var mixedScanCount = 0
        var scanFailure: Throwable? = null
        val disconnectedDeviceIds = mutableListOf<String?>()

        override fun setListener(listener: BluetoothLibraryListener) {
            this.listener = listener
        }

        override fun startMixedScan(): Boolean {
            mixedScanCount += 1
            scanFailure?.let { throw it }
            return true
        }

        override fun stopScan() = Unit

        override fun connect(deviceId: String): Boolean = true

        override fun disconnect(deviceId: String?) {
            disconnectedDeviceIds += deviceId
        }

        fun deviceFound(device: LibraryBluetoothDevice) = listener.onDeviceFound(device)

        fun scanFinished() = listener.onScanFinished()

        fun connected(device: LibraryBluetoothDevice) = listener.onConnected(device)

        fun connectionLost(deviceId: String?) = listener.onConnectionLost(deviceId)
    }
}
