package com.biosensor.migratedev.port.connection

import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * 业务适配器:连接业务协议(设备绑定表读写 + 扫描/连接)。
 */
class ConnectionPortAdapter(
    private val sqlite: LocalDatabase,
    private val bluetooth: BluetoothPort,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ConnectionPort {

    override fun scanDevices(): Flow<BluetoothDeviceInfo> = bluetooth.scanDevices()

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

    override fun execute(command: ConnectionCommand): Flow<ConnectionResult> = when (command) {
        is ConnectionCommand.SaveBinding -> saveBinding(command.userId, command.deviceId)
        is ConnectionCommand.Bluetooth -> bluetooth.execute(command.command).map {
            ConnectionResult.Bluetooth(it)
        }
    }

    private fun saveBinding(userId: String, deviceId: String): Flow<ConnectionResult> = flow {
        val result = runCatching {
            sqlite.deviceBindingQueries.upsert(
                userId = userId, deviceId = deviceId, updatedAtMillis = nowMillis()
            )
        }.fold(onSuccess = {
            ConnectionResult.BindingSaved(deviceId)
        }, onFailure = {
            ConnectionResult.BindingSaveFailed(it.message ?: "保存设备绑定失败")
        })
        Timber.tag(BINDING_TAG).i("绑定保存 结果=%s", result::class.simpleName)
        emit(result)
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val BINDING_TAG = "Connection.Binding"
    }
}
