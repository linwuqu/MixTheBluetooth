package com.biosensor.migratedev.port.connection

import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.port.CommandPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import kotlinx.coroutines.flow.Flow

sealed interface BindingSnapshot {
    data object Loading : BindingSnapshot
    data object Missing : BindingSnapshot
    data class Found(val deviceId: String) : BindingSnapshot
    data class Failed(val message: String) : BindingSnapshot
}

/**
 * 连接业务端口:直接执行领域副作用([ConnectionEffect]),翻译成领域事件([ConnectionEvent])上报。
 * [readBinding] / [scanDevices] 是直读流(非 effect→event 形态),保持不变。
 */
interface ConnectionPort : CommandPort<ConnectionEffect, ConnectionEvent> {
    fun readBinding(userId: String): Flow<BindingSnapshot>

    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
