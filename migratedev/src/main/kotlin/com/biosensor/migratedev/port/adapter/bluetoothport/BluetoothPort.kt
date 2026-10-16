package com.biosensor.migratedev.port.adapter.bluetoothport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

/** 连接生命周期状态。进入 CGM 页面后 CONNECTING 即表示正在重连。 */
enum class BluetoothConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

class BluetoothScanException(
    msg: String, cause: Throwable? = null
) : Exception(msg, cause)

interface BluetoothPort {
    fun execute(effect: BluetoothEffect): Flow<BluetoothEvent>
    fun scanDevices(): Flow<BluetoothDeviceInfo>

    /** 长生命周期状态流；默认实现只用于不关心连接状态的测试替身。 */
    fun observeConnectionState(): Flow<BluetoothConnectionState> =
        flowOf(BluetoothConnectionState.DISCONNECTED)
}
