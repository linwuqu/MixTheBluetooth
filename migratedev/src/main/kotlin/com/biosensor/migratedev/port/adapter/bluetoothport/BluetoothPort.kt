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
    /** 发送指令数据。旧库内部分包 + 信号量节流,上层只给整块数据(架构 07 §3)。 */
    data class SendData(val data: ByteArray) : BluetoothEffect
    /** 可选能力:默认业务不调用(架构 07 §1.4 考察结论,MTU 由库自适应)。 */
    data class RequestMtu(val mtu: Int) : BluetoothEffect
}

sealed interface BluetoothEvent {
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothEvent
    data class ConnectFailed(val message: String) : BluetoothEvent
    data object ConnectTimeout : BluetoothEvent
    data object Disconnected : BluetoothEvent
    /** 收:设备主动推送,库按缓冲/静默合块交付,块边界任意(架构 07 §1.4)。 */
    data class DataReceived(val data: ByteArray) : BluetoothEvent
    /** 发:旧库 readNumber 每包 GATT 写成功后回调,值为本包字节数(架构 07 §3.1)。 */
    data class DataSent(val bytesSent: Int) : BluetoothEvent
    /** 诊断用:MTU 变化通知(库自适应放大,上层不干预)。 */
    data class MtuChanged(val mtu: Int) : BluetoothEvent
}

class BluetoothScanException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)

interface BluetoothPort {
    fun execute(effect: BluetoothEffect): Flow<BluetoothEvent>

    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
