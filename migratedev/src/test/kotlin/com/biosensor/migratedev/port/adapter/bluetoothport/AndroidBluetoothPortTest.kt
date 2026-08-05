package com.biosensor.migratedev.port.adapter.bluetoothport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
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
    fun `scan starts once and cancellation stops once`() = runTest {
        val scanner = FakeNativeBleScanner()
        val port = AndroidBluetoothPort(
            client = FakeBluetoothLibraryClient(),
            scanner = scanner
        )
        val results = mutableListOf<BluetoothDeviceInfo>()

        val collection = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.scanDevices().toList(results)
        }
        scanner.deviceFound(device("AA:01"))
        runCurrent()

        assertEquals(1, scanner.startCount)
        assertEquals(listOf(deviceInfo("AA:01")), results)

        collection.cancelAndJoin()
        assertEquals(1, scanner.stopCount)
    }

    @Test
    fun `scan filters BT24 and publishes updated advertisements`() =
        runTest {
            val scanner = FakeNativeBleScanner()
            val port = AndroidBluetoothPort(
                client = FakeBluetoothLibraryClient(),
                scanner = scanner
            )
            val results = mutableListOf<BluetoothDeviceInfo>()

            val collection = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                port.scanDevices().toList(results)
            }
            scanner.deviceFound(
                device("OTHER", service = "FFF0")
            )
            scanner.deviceFound(
                device("OTHER-MANUFACTURER", manufacturerId = 0x4459)
            )
            scanner.deviceFound(
                device("AA:01", name = "old", rssi = -60)
            )
            scanner.deviceFound(
                device("AA:01", name = "new", rssi = -45)
            )
            runCurrent()

            assertEquals(
                listOf(
                    deviceInfo("AA:01", "old", -60),
                    deviceInfo("AA:01", "new", -45)
                ),
                results
            )
            collection.cancelAndJoin()
        }

    @Test
    fun `late callback from stopped scan is ignored`() = runTest {
        val scanner = FakeNativeBleScanner()
        val port = AndroidBluetoothPort(
            client = FakeBluetoothLibraryClient(),
            scanner = scanner
        )
        val results = mutableListOf<BluetoothDeviceInfo>()

        val collection = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler)
        ) {
            port.scanDevices().toList(results)
        }
        val oldListener = scanner.currentListener
        collection.cancelAndJoin()

        oldListener.onDeviceFound(device("AA:LATE"))
        runCurrent()

        assertEquals(emptyList<BluetoothDeviceInfo>(), results)
    }

    @Test
    fun `shared scan keeps native running until last subscriber leaves`() =
        runTest {
            val scanner = FakeNativeBleScanner()
            val port = AndroidBluetoothPort(
                client = FakeBluetoothLibraryClient(),
                scanner = scanner
            )
            val resultsA = mutableListOf<BluetoothDeviceInfo>()
            val resultsB = mutableListOf<BluetoothDeviceInfo>()

            val a = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                port.scanDevices().toList(resultsA)
            }
            val b = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                port.scanDevices().toList(resultsB)
            }
            scanner.deviceFound(device("AA:01"))
            runCurrent()

            assertEquals(1, scanner.startCount)
            assertEquals(listOf(deviceInfo("AA:01")), resultsA)
            assertEquals(listOf(deviceInfo("AA:01")), resultsB)

            a.cancelAndJoin()
            runCurrent()
            assertEquals(0, scanner.stopCount)   // 还有订阅者,不停止

            b.cancelAndJoin()
            assertEquals(1, scanner.stopCount)   // 最后一个离开才停止
        }

    @Test
    fun `rejoin after full departure restarts native scan`() =
        runTest {
            val scanner = FakeNativeBleScanner()
            val port = AndroidBluetoothPort(
                client = FakeBluetoothLibraryClient(),
                scanner = scanner
            )

            val first = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                port.scanDevices().toList()
            }
            first.cancelAndJoin()
            runCurrent()
            assertEquals(1, scanner.stopCount)

            val second = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                port.scanDevices().toList()
            }
            scanner.deviceFound(device("AA:01"))
            runCurrent()

            assertEquals(2, scanner.startCount)
            second.cancelAndJoin()
        }

    @Test
    fun `native scan failure is delivered to every subscriber`() =
        runTest {
            val scanner = FakeNativeBleScanner()
            val port = AndroidBluetoothPort(
                client = FakeBluetoothLibraryClient(),
                scanner = scanner
            )
            var failureA: Throwable? = null
            var failureB: Throwable? = null

            val a = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                failureA = runCatching {
                    port.scanDevices().toList()
                }.exceptionOrNull()
            }
            val b = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                failureB = runCatching {
                    port.scanDevices().toList()
                }.exceptionOrNull()
            }
            scanner.scanFailed("蓝牙扫描过于频繁")
            a.join()
            b.join()

            assertTrue(failureA is BluetoothScanException)
            assertTrue(failureB is BluetoothScanException)
            assertEquals("蓝牙扫描过于频繁", failureA?.message)
            assertEquals("蓝牙扫描过于频繁", failureB?.message)
            assertEquals(1, scanner.stopCount)
        }

    @Test
    fun `native scan failure closes flow with business message`() =
        runTest {
            val scanner = FakeNativeBleScanner()
            val port = AndroidBluetoothPort(
                client = FakeBluetoothLibraryClient(),
                scanner = scanner
            )
            var failure: Throwable? = null

            val collection = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                failure = runCatching {
                    port.scanDevices().toList()
                }.exceptionOrNull()
            }
            scanner.scanFailed("蓝牙扫描过于频繁")
            collection.join()

            assertTrue(failure is BluetoothScanException)
            assertEquals(
                "蓝牙扫描过于频繁",
                failure?.message
            )
            assertEquals(1, scanner.stopCount)
        }

    @Test
    fun `connect stops native scan before calling legacy client`() =
        runTest {
            val calls = mutableListOf<String>()
            val scanner = FakeNativeBleScanner(calls)
            val client = FakeBluetoothLibraryClient(calls)
            val port = AndroidBluetoothPort(client, scanner)
            val connectionResults = mutableListOf<BluetoothResult>()

            val scanCollection = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                port.scanDevices().toList()
            }
            scanner.deviceFound(device("AA:02"))

            val connection = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                port.execute(
                    BluetoothCommand.Connect(
                        "AA:02",
                        timeoutMillis = 5_000
                    )
                ).toList(connectionResults)
            }
            runCurrent()

            assertEquals(
                listOf(
                    "startScan",
                    "stopScan",
                    "connect:AA:02"
                ),
                calls
            )
            scanCollection.join()

            client.connected(device("AA:02"))
            runCurrent()
            assertEquals(
                listOf(
                    BluetoothResult.Connected(
                        deviceInfo("AA:02")
                    )
                ),
                connectionResults
            )
            assertTrue(connection.isActive)

            client.connectionLost("AA:02")
            connection.join()
        }

    @Test
    fun `connect timeout closes session and disconnects target`() =
        runTest {
            val scanner = FakeNativeBleScanner()
            val client = FakeBluetoothLibraryClient()
            val port = AndroidBluetoothPort(client, scanner)
            val results = mutableListOf<BluetoothResult>()

            val scanCollection = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                port.scanDevices().toList()
            }
            scanner.deviceFound(device("AA:03"))

            val connection = backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler)
            ) {
                port.execute(
                    BluetoothCommand.Connect(
                        "AA:03",
                        timeoutMillis = 1_000
                    )
                ).toList(results)
            }
            advanceTimeBy(1_000)
            runCurrent()
            connection.join()
            scanCollection.join()

            assertEquals(
                listOf(BluetoothResult.ConnectTimeout),
                results
            )
            assertEquals(
                listOf("AA:03"),
                client.disconnectedDeviceIds
            )
        }

    private fun device(
        id: String,
        name: String? = "BT24-S",
        rssi: Int = -30,
        service: String = "FFE0",
        manufacturerId: Int = 0x4458
    ) = ScannedBleDevice(
        info = deviceInfo(id, name, rssi),
        advertisement = BluetoothAdvertisement(
            isBle = true,
            serviceUuids = setOf(service),
            manufacturerIds = setOf(manufacturerId)
        ),
        legacyDevice = null
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

    private class FakeNativeBleScanner(
        private val calls: MutableList<String> =
            mutableListOf()
    ) : NativeBleScanner {
        var startCount = 0
        var stopCount = 0
        lateinit var currentListener: NativeBleScanListener

        override fun start(listener: NativeBleScanListener) {
            startCount += 1
            calls += "startScan"
            currentListener = listener
        }

        override fun stop(listener: NativeBleScanListener) {
            stopCount += 1
            calls += "stopScan"
        }

        fun deviceFound(device: ScannedBleDevice) {
            currentListener.onDeviceFound(device)
        }

        fun scanFailed(message: String) {
            currentListener.onScanFailed(
                BluetoothScanException(message)
            )
        }
    }

    private class FakeBluetoothLibraryClient(
        private val calls: MutableList<String> =
            mutableListOf()
    ) : BluetoothLibraryClient {
        private lateinit var listener: BluetoothLibraryListener
        val disconnectedDeviceIds = mutableListOf<String?>()

        override fun setListener(
            listener: BluetoothLibraryListener?
        ) {
            if (listener != null) {
                this.listener = listener
            }
        }

        override fun connect(
            device: ScannedBleDevice
        ): Boolean {
            calls += "connect:${device.info.id}"
            return true
        }

        override fun disconnect(deviceId: String?) {
            disconnectedDeviceIds += deviceId
        }

        fun connected(device: ScannedBleDevice) {
            listener.onConnected(device.info)
        }

        fun connectionLost(deviceId: String?) {
            listener.onConnectionLost(deviceId)
        }
    }
}
