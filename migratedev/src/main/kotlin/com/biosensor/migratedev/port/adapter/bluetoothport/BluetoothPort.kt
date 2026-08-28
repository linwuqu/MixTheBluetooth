package com.biosensor.migratedev.port.adapter.bluetoothport

import kotlinx.coroutines.flow.Flow

data class BluetoothDeviceInfo(
    val id: String, val name: String?, val isBle: Boolean, val rssi: Int? = null
)

sealed interface BluetoothEffect {
    data class Connect(val deviceId: String, val timeoutMillis: Long = 15_000) : BluetoothEffect
    data object Disconnect : BluetoothEffect
    data class SendData(val data: ByteArray) : BluetoothEffect
    data class RequestMtu(val mtu: Int) : BluetoothEffect
}

sealed interface BluetoothEvent {
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothEvent
    data class ConnectFailed(val msg: String) : BluetoothEvent
    data object ConnectTimeout : BluetoothEvent
    data object Disconnected : BluetoothEvent
    data class DataReceived(val data: ByteArray) : BluetoothEvent
    data class DataSentAck(val bytesSentAck: Int) : BluetoothEvent
    data class MtuChanged(val mtu: Int) : BluetoothEvent
}

class BluetoothScanException(
    msg: String, cause: Throwable? = null
) : Exception(msg, cause)

interface BluetoothPort {
    fun execute(effect: BluetoothEffect): Flow<BluetoothEvent>
    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
