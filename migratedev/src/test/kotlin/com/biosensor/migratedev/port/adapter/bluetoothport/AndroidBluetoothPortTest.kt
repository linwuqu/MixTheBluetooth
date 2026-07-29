package com.biosensor.migratedev.port.adapter.bluetoothport

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
    fun `natural SDK end starts another round without ending scan session`() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client, backgroundScope)
        val results = mutableListOf<BluetoothResult>()

        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(BluetoothCommand.StartScan("session-a")).toList(results)
        }
        runCurrent()
        client.scanFinished()
        runCurrent()

        assertEquals(2, client.mixedScanCount)
        assertEquals(
            listOf(
                BluetoothResult.ScanStarted("session-a", 1),
                BluetoothResult.ScanRoundEnded("session-a", 1),
                BluetoothResult.ScanStarted("session-a", 2)
            ),
            results
        )
        assertTrue(collection.isActive)
        port.execute(BluetoothCommand.StopScan("session-a")).toList()
    }

    @Test
    fun `scan filters BT24 devices and updates duplicates in place`() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client, backgroundScope)
        val results = mutableListOf<BluetoothResult>()

        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(BluetoothCommand.StartScan("session-a")).toList(results)
        }
        client.deviceFound(device("OTHER", service = "FFF0"))
        client.deviceFound(device("AA:01", name = "old", rssi = -60))
        client.deviceFound(device("AA:01", name = "new", rssi = -45))
        runCurrent()

        assertEquals(
            listOf(
                BluetoothResult.ScanStarted("session-a", 1),
                BluetoothResult.DevicesUpdated(
                    "session-a",
                    1,
                    listOf(deviceInfo("AA:01", "old", -60))
                ),
                BluetoothResult.DevicesUpdated(
                    "session-a",
                    1,
                    listOf(deviceInfo("AA:01", "new", -45))
                )
            ),
            results
        )
        port.execute(BluetoothCommand.StopScan("session-a")).toList()
        collection.join()
    }

    @Test
    fun `refresh replaces current round but keeps the same scan flow`() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client, backgroundScope)
        val scanResults = mutableListOf<BluetoothResult>()

        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(BluetoothCommand.StartScan("session-a")).toList(scanResults)
        }
        val refreshResults = port.execute(
            BluetoothCommand.RefreshScan("session-a")
        ).toList()
        runCurrent()

        assertEquals(1, client.stopScanCount)
        assertEquals(2, client.mixedScanCount)
        assertEquals(
            listOf(BluetoothResult.ScanRefreshed("session-a", 2)),
            refreshResults
        )
        assertTrue(collection.isActive)
        port.execute(BluetoothCommand.StopScan("session-a")).toList()
    }

    @Test
    fun `stop prevents another scan round`() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client, backgroundScope)
        val scanResults = mutableListOf<BluetoothResult>()

        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(BluetoothCommand.StartScan("session-a")).toList(scanResults)
        }
        val stopResults = port.execute(
            BluetoothCommand.StopScan("session-a")
        ).toList()
        client.scanFinished()
        runCurrent()

        assertEquals(
            listOf(BluetoothResult.ScanStopped("session-a")),
            stopResults
        )
        assertEquals(1, client.mixedScanCount)
        collection.join()
    }

    @Test
    fun `connect stops active scan before calling the library`() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client, backgroundScope)
        val connectionResults = mutableListOf<BluetoothResult>()

        val scanCollection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(BluetoothCommand.StartScan("session-a")).toList()
        }
        client.deviceFound(device("AA:02"))
        val connection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(
                BluetoothCommand.Connect("AA:02", timeoutMillis = 5_000)
            ).toList(connectionResults)
        }
        runCurrent()

        assertEquals(listOf("startScan", "stopScan", "connect:AA:02"), client.calls)
        scanCollection.join()

        client.connected(device("AA:02"))
        runCurrent()
        assertEquals(
            listOf(BluetoothResult.Connected(deviceInfo("AA:02"))),
            connectionResults
        )
        assertTrue(connection.isActive)
        client.connectionLost("AA:02")
        connection.join()
    }

    @Test
    fun `connect timeout closes the session and disconnects`() = runTest {
        val client = FakeBluetoothLibraryClient()
        val port = AndroidBluetoothPort(client, backgroundScope)
        val results = mutableListOf<BluetoothResult>()

        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            port.execute(
                BluetoothCommand.Connect("AA:03", timeoutMillis = 1_000)
            ).toList(results)
        }
        advanceTimeBy(1_000)
        runCurrent()
        collection.join()

        assertEquals(listOf(BluetoothResult.ConnectTimeout), results)
        assertEquals(listOf("AA:03"), client.disconnectedDeviceIds)
    }

    @Test
    fun `runtime permission failure is a scan failure for the requested session`() = runTest {
        val client = FakeBluetoothLibraryClient().apply {
            scanFailure = SecurityException("permission denied")
        }
        val port = AndroidBluetoothPort(client, backgroundScope)

        val results = port.execute(
            BluetoothCommand.StartScan("session-a")
        ).toList()

        assertEquals(
            listOf(
                BluetoothResult.ScanFailed(
                    "session-a",
                    "缺少蓝牙扫描或连接权限"
                )
            ),
            results
        )
    }

    private fun device(
        id: String,
        name: String? = "BT24-S",
        rssi: Int = -30,
        service: String = "FFE0",
        manufacturerId: Int = 0x4458
    ) = LibraryBluetoothDevice(
        id = id,
        name = name,
        isBle = true,
        rssi = rssi,
        serviceUuids = setOf(service),
        manufacturerIds = setOf(manufacturerId)
    )

    private fun deviceInfo(
        id: String,
        name: String? = "BT24-S",
        rssi: Int = -30
    ) = BluetoothDeviceInfo(
        id = id,
        name = name,
        isBle = true,
        rssi = rssi
    )

    private class FakeBluetoothLibraryClient : BluetoothLibraryClient {
        private lateinit var listener: BluetoothLibraryListener

        var mixedScanCount = 0
        var stopScanCount = 0
        var scanFailure: Throwable? = null
        val calls = mutableListOf<String>()
        val disconnectedDeviceIds = mutableListOf<String?>()

        override fun setListener(listener: BluetoothLibraryListener?) {
            if (listener != null) {
                this.listener = listener
            }
        }

        override fun startScan(): Boolean {
            mixedScanCount += 1
            calls += "startScan"
            scanFailure?.let { throw it }
            return true
        }

        override fun stopScan() {
            stopScanCount += 1
            calls += "stopScan"
        }

        override fun connect(deviceId: String): Boolean {
            calls += "connect:$deviceId"
            return true
        }

        override fun disconnect(deviceId: String?) {
            disconnectedDeviceIds += deviceId
        }

        fun deviceFound(device: LibraryBluetoothDevice) =
            listener.onDeviceFound(device)

        fun scanFinished() = listener.onScanFinished()

        fun connected(device: LibraryBluetoothDevice) =
            listener.onConnected(device)

        fun connectionLost(deviceId: String?) =
            listener.onConnectionLost(deviceId)
    }
}
