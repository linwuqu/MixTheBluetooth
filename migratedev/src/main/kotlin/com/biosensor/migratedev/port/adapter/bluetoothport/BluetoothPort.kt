package com.biosensor.migratedev.port.adapter.bluetoothport

import com.biosensor.migratedev.port.CommandPort

data class BluetoothDeviceInfo(
    val id: String, val name: String?, val isBle: Boolean, val rssi: Int? = null
)

data class BluetoothAdvertisement(
    val isBle: Boolean, val serviceUuids: Set<String>?, val manufacturerIds: Set<Int>?
)

sealed interface BluetoothCommand {
    data class StartScan(
        val scanSessionId: String
    ) : BluetoothCommand

    data class RefreshScan(
        val scanSessionId: String
    ) : BluetoothCommand

    data class StopScan(
        val scanSessionId: String
    ) : BluetoothCommand

    data class Connect(
        val deviceId: String, val timeoutMillis: Long = 15_000
    ) : BluetoothCommand

    data object Disconnect : BluetoothCommand

    // TODO(Workflow 03): SendData / ObserveReceivedData / RequestMtu /
    // transfer progress / protocol errors.
}

sealed interface BluetoothResult {
    data class ScanStarted(
        val scanSessionId: String, val roundId: Long
    ) : BluetoothResult

    data class DevicesUpdated(
        val scanSessionId: String, val roundId: Long, val devices: List<BluetoothDeviceInfo>
    ) : BluetoothResult

    data class ScanRoundEnded(
        val scanSessionId: String, val roundId: Long
    ) : BluetoothResult

    data class ScanRefreshed(
        val scanSessionId: String, val roundId: Long
    ) : BluetoothResult

    data class ScanFailed(
        val scanSessionId: String, val message: String
    ) : BluetoothResult

    data class ScanStopped(
        val scanSessionId: String
    ) : BluetoothResult

    data class Connected(
        val device: BluetoothDeviceInfo
    ) : BluetoothResult

    data class ConnectFailed(
        val message: String
    ) : BluetoothResult

    data object ConnectTimeout : BluetoothResult

    data object Disconnected : BluetoothResult

    // TODO(Workflow 03): DataSent / DataReceived / MtuChanged /
    // TransferProgress / TransferFailed.
}

interface BluetoothPort : CommandPort<BluetoothCommand, BluetoothResult>
