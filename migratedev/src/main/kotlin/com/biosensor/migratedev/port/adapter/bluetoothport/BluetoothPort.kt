package com.biosensor.migratedev.port.adapter.bluetoothport

import kotlinx.coroutines.flow.Flow

data class BluetoothDeviceInfo(
    val id: String, val name: String?, val isBle: Boolean, val rssi: Int? = null
)

data class BluetoothAdvertisement(
    val isBle: Boolean, val serviceUuids: Set<String>?, val manufacturerIds: Set<Int>?
)

sealed interface BluetoothEffect {
    data class Connect(val deviceId: String, val timeoutMillis: Long = 15_000) : BluetoothEffect
    data object Disconnect : BluetoothEffect

    // TODO(Workflow 03): SendData / ObserveReceivedData / RequestMtu /
    //  transfer progress / protocol errors.
}

sealed interface BluetoothEvent {
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothEvent
    data class ConnectFailed(val message: String) : BluetoothEvent
    data object ConnectTimeout : BluetoothEvent
    data object Disconnected : BluetoothEvent

    // TODO(Workflow 03): DataSent / DataReceived / MtuChanged /
    //  TransferProgress / TransferFailed.
}

class BluetoothScanException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)

interface BluetoothPort {
    fun execute(effect: BluetoothEffect): Flow<BluetoothEvent>

    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
