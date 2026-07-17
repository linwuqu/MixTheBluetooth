package com.biosensor.migratedev.orchestrator.connection

import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.orchestrator.EffectExecutor
import com.biosensor.migratedev.port.bluetooth.BluetoothCommand
import com.biosensor.migratedev.port.bluetooth.BluetoothPort
import com.biosensor.migratedev.port.bluetooth.BluetoothResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConnectionEffectExecutor(
    private val port: BluetoothPort
) : EffectExecutor<ConnectionEffect, ConnectionEvent> {

    override fun execute(effect: ConnectionEffect): Flow<ConnectionEvent> {
        return port.execute(effect.toCommand()).map { it.toEvent() }
    }

    private fun ConnectionEffect.toCommand(): BluetoothCommand {
        return when (this) {
            ConnectionEffect.StartScan -> BluetoothCommand.StartScan
            ConnectionEffect.StopScan -> BluetoothCommand.StopScan
            is ConnectionEffect.ConnectDevice -> BluetoothCommand.Connect(deviceId)
            ConnectionEffect.DisconnectDevice -> BluetoothCommand.Disconnect
        }
    }

    private fun BluetoothResult.toEvent(): ConnectionEvent {
        return when (this) {
            is BluetoothResult.DevicesFound -> ConnectionEvent.DevicesFound(devices)
            BluetoothResult.ScanStopped -> ConnectionEvent.ScanStopped
            is BluetoothResult.ScanFailed -> ConnectionEvent.ScanFailed(message)
            is BluetoothResult.Connected -> ConnectionEvent.ConnectAccepted(device)
            is BluetoothResult.ConnectFailed -> ConnectionEvent.ConnectFailed(message)
            BluetoothResult.ConnectTimeout -> ConnectionEvent.ConnectTimeout
            BluetoothResult.Disconnected -> ConnectionEvent.Disconnected
        }
    }
}
