package com.biosensor.migratedev.port.adapter.bluetoothport

import com.biosensor.migratedev.port.CommandPort
import kotlinx.coroutines.flow.Flow

data class BluetoothDeviceInfo(
    val id: String, val name: String?, val isBle: Boolean, val rssi: Int? = null
)

data class BluetoothAdvertisement(
    val isBle: Boolean, val serviceUuids: Set<String>?, val manufacturerIds: Set<Int>?
)

sealed interface BluetoothCommand {
    data class Connect(val deviceId: String, val timeoutMillis: Long = 15_000) : BluetoothCommand
    data object Disconnect : BluetoothCommand

    // TODO(Workflow 03): SendData / ObserveReceivedData / RequestMtu /
    // transfer progress / protocol errors.
}

sealed interface BluetoothResult {
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothResult
    data class ConnectFailed(val message: String) : BluetoothResult
    data object ConnectTimeout : BluetoothResult
    data object Disconnected : BluetoothResult

    // TODO(Workflow 03): DataSent / DataReceived / MtuChanged /
    // TransferProgress / TransferFailed.
}

class BluetoothScanException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)

interface BluetoothPort : CommandPort<BluetoothCommand, BluetoothResult> {
    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
