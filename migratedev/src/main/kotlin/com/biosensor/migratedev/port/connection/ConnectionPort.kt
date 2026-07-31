package com.biosensor.migratedev.port.connection

import com.biosensor.migratedev.port.CommandPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothCommand
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothResult
import kotlinx.coroutines.flow.Flow

sealed interface ConnectionCommand {
    data class ReadBinding(val userId: String) : ConnectionCommand

    data class SaveBinding(val userId: String, val deviceId: String) : ConnectionCommand

    data class Bluetooth(val command: BluetoothCommand) : ConnectionCommand
}

sealed interface ConnectionResult {
    data class BindingLoaded(val deviceId: String) : ConnectionResult

    data object BindingMissing : ConnectionResult

    data class BindingFailed(val message: String) : ConnectionResult

    data class BindingSaved(val deviceId: String) : ConnectionResult

    data class BindingSaveFailed(val message: String) : ConnectionResult

    data class Bluetooth(val result: BluetoothResult) : ConnectionResult
}

interface ConnectionPort : CommandPort<ConnectionCommand, ConnectionResult> {
    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
