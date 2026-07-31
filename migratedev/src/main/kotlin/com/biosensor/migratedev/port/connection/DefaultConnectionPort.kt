package com.biosensor.migratedev.port.connection

import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.adapter.localport.LocalPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * 具体 ConnectionCommand & ConnectionResult 见接口文件
 * DefaultConnectionPort 对接口进行实现 使用了两个 adapter 能力: local、bluetooth
 * readBinding、saveBinding 使用 sql 查询也给出了实现
 */
class DefaultConnectionPort(
    private val local: LocalPort,
    private val bluetooth: BluetoothPort,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : ConnectionPort {

    override fun scanDevices(): Flow<BluetoothDeviceInfo> = bluetooth.scanDevices()

    override fun execute(
        command: ConnectionCommand
    ): Flow<ConnectionResult> = when (command) {
        is ConnectionCommand.ReadBinding -> readBinding(command.userId)

        is ConnectionCommand.SaveBinding -> saveBinding(command.userId, command.deviceId)

        is ConnectionCommand.Bluetooth -> bluetooth.execute(command.command).map {
            ConnectionResult.Bluetooth(it)
        }
    }

    private fun readBinding(
        userId: String
    ): Flow<ConnectionResult> = flow {
        val result = runCatching {
            local.sqlite.deviceBindingQueries.findByUserId(userId).executeAsOneOrNull()
        }.fold(onSuccess = { binding ->
            binding?.let {
                ConnectionResult.BindingLoaded(it.deviceId)
            } ?: ConnectionResult.BindingMissing
        }, onFailure = {
            ConnectionResult.BindingFailed(
                it.message ?: "读取设备绑定失败"
            )
        })
        Timber.tag(BINDING_TAG).i(
            "binding read result=%s", result::class.simpleName
        )
        emit(result)
    }.flowOn(Dispatchers.IO)

    private fun saveBinding(
        userId: String, deviceId: String
    ): Flow<ConnectionResult> = flow {
        val result = runCatching {
            local.sqlite.deviceBindingQueries.upsert(
                userId = userId, deviceId = deviceId, updatedAtMillis = nowMillis()
            )
        }.fold(onSuccess = {
            ConnectionResult.BindingSaved(deviceId)
        }, onFailure = {
            ConnectionResult.BindingSaveFailed(
                it.message ?: "保存设备绑定失败"
            )
        })
        Timber.tag(BINDING_TAG).i(
            "binding save result=%s", result::class.simpleName
        )
        emit(result)
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val BINDING_TAG = "Connection.Binding"
    }
}
