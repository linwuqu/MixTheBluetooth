package com.biosensor.migratedev.port.bluetooth

import com.biosensor.migratedev.decisioncore.connection.BluetoothDeviceInfo
import com.biosensor.migratedev.port.CommandPort

sealed interface BluetoothCommand {
    data object StartScan : BluetoothCommand
    data object StopScan : BluetoothCommand
    data class Connect(val deviceId: String) : BluetoothCommand
    data object Disconnect : BluetoothCommand
}

sealed interface BluetoothResult {
    data class DevicesFound(val devices: List<BluetoothDeviceInfo>) : BluetoothResult
    data object ScanStopped : BluetoothResult
    data class ScanFailed(val message: String) : BluetoothResult
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothResult
    data class ConnectFailed(val message: String) : BluetoothResult
    data object ConnectTimeout : BluetoothResult
    data object Disconnected : BluetoothResult
}

interface BluetoothPort : CommandPort<BluetoothCommand, BluetoothResult>
