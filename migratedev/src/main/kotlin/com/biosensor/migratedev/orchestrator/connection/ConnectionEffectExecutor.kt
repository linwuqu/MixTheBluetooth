package com.biosensor.migratedev.orchestrator.connection

import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.orchestrator.EffectExecutor
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothCommand
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothResult
import com.biosensor.migratedev.port.connection.ConnectionCommand
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.port.connection.ConnectionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConnectionEffectExecutor(
    private val port: ConnectionPort
) : EffectExecutor<ConnectionEffect, ConnectionEvent> {

    override fun execute(
        effect: ConnectionEffect
    ): Flow<ConnectionEvent> = port.execute(effect.toCommand()).map {
        it.toEvent()
    }

    private fun ConnectionEffect.toCommand(): ConnectionCommand = when (this) {
        is ConnectionEffect.SaveBinding -> ConnectionCommand.SaveBinding(userId, deviceId)

        is ConnectionEffect.ConnectDevice -> ConnectionCommand.Bluetooth(
            BluetoothCommand.Connect(deviceId)
        )

        ConnectionEffect.DisconnectDevice -> ConnectionCommand.Bluetooth(
            BluetoothCommand.Disconnect
        )
    }

    private fun ConnectionResult.toEvent(): ConnectionEvent = when (this) {
        is ConnectionResult.BindingSaved -> ConnectionEvent.BindingSaved(deviceId)

        is ConnectionResult.BindingSaveFailed -> ConnectionEvent.BindingSaveFailed(message)

        is ConnectionResult.Bluetooth -> result.toEvent()
    }

    private fun BluetoothResult.toEvent(): ConnectionEvent = when (this) {
        is BluetoothResult.Connected -> ConnectionEvent.DeviceConnected(device)

        is BluetoothResult.ConnectFailed -> ConnectionEvent.DeviceConnectFailed(message)

        BluetoothResult.ConnectTimeout -> ConnectionEvent.DeviceConnectTimeout

        BluetoothResult.Disconnected -> ConnectionEvent.DeviceDisconnected
    }
}
