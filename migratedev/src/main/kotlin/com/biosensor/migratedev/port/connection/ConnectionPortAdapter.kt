package com.biosensor.migratedev.port.connection

import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothCommand
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * 业务适配器:连接业务协议(设备绑定表读写 + 扫描/连接)。
 * 下行直接执行 [ConnectionEffect](ConnectDevice/DisconnectDevice 的蓝牙命令包装在内部完成);
 * 上行把蓝牙结果翻译成 [ConnectionEvent] 上报。
 */
class ConnectionPortAdapter(
    private val sqlite: LocalDatabase,
    private val bluetooth: BluetoothPort,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ConnectionPort {

    override fun scanDevices(): Flow<BluetoothDeviceInfo> = bluetooth.scanDevices()

    // 调用 sqlite 能力从 deviceBinding 表搜索绑定信息
    override fun readBinding(userId: String): Flow<BindingSnapshot> = flow {
        val result = runCatching {
            sqlite.deviceBindingQueries.findByUserId(userId).executeAsOneOrNull()
        }.fold(onSuccess = { binding ->
            binding?.let { BindingSnapshot.Found(it.deviceId) } ?: BindingSnapshot.Missing
        }, onFailure = {
            BindingSnapshot.Failed(it.message ?: "读取设备绑定失败")
        })
        Timber.tag(BINDING_TAG).i("绑定读取 结果=%s", result::class.simpleName)
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun execute(effect: ConnectionEffect): Flow<ConnectionEvent> = when (effect) {
        is ConnectionEffect.SaveBinding -> saveBinding(effect.userId, effect.deviceId)

        is ConnectionEffect.ConnectDevice -> bluetooth.execute(
            BluetoothCommand.Connect(effect.deviceId)
        ).map { it.toEvent() }

        ConnectionEffect.DisconnectDevice -> bluetooth.execute(
            BluetoothCommand.Disconnect
        ).map { it.toEvent() }
    }

    private fun saveBinding(userId: String, deviceId: String): Flow<ConnectionEvent> = flow {
        val result = runCatching {
            sqlite.deviceBindingQueries.upsert(
                userId = userId, deviceId = deviceId, updatedAtMillis = nowMillis()
            )
        }.fold(onSuccess = {
            ConnectionEvent.BindingSaved(deviceId)
        }, onFailure = {
            ConnectionEvent.BindingSaveFailed(it.message ?: "保存设备绑定失败")
        })
        Timber.tag(BINDING_TAG).i("绑定保存 结果=%s", result::class.simpleName)
        emit(result)
    }.flowOn(Dispatchers.IO)

    private fun BluetoothResult.toEvent(): ConnectionEvent = when (this) {
        is BluetoothResult.Connected -> ConnectionEvent.DeviceConnected(device)
        is BluetoothResult.ConnectFailed -> ConnectionEvent.DeviceConnectFailed(message)
        BluetoothResult.ConnectTimeout -> ConnectionEvent.DeviceConnectTimeout
        BluetoothResult.Disconnected -> ConnectionEvent.DeviceDisconnected
    }

    private companion object {
        const val BINDING_TAG = "Connection.Binding"
    }
}
