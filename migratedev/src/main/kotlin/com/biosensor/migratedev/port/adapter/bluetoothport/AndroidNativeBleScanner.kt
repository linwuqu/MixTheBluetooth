package com.biosensor.migratedev.port.adapter.bluetoothport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.util.size
import com.hc.bluetoothlibrary.DeviceModule

internal data class ScannedBleDevice(
    val info: BluetoothDeviceInfo,
    val advertisement: BluetoothAdvertisement,
    val legacyDevice: DeviceModule?
)

internal interface NativeBleScanListener {
    fun onDeviceFound(device: ScannedBleDevice)
    fun onScanFailed(failure: BluetoothScanException)
}

internal interface NativeBleScanner {
    fun start(listener: NativeBleScanListener)

    fun stop(listener: NativeBleScanListener)
}

/**
 * AndroidNativeBleScanner 是对 Android 系统 BluetoothLeScanner 的直接封装
 * 使用互斥锁管理变量 active 确保类内只存有一个连接
 * 实现了扫描器的两个功能 start & stop
 * 通过 scanner.startScan & scanner.stopScan 来控制收发的启停
 * 通过 callback: ScanCallback() 实现回调结果的接收
 * 最终将硬件结果上报给 listener: NativeBleScanListener
 */
internal class AndroidNativeBleScanner(
    context: Context
) : NativeBleScanner {
    private val applicationContext = context.applicationContext
    private val bluetoothManager = applicationContext.getSystemService(BluetoothManager::class.java)
    private val lock = Any()
    private var active: ActiveNativeScan? = null

    @SuppressLint("MissingPermission")
    override fun start(listener: NativeBleScanListener) {
        requireScanPermission()
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
            ?: throw IllegalStateException("蓝牙扫描器不可用")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.toScannedDevice(applicationContext)?.let(listener::onDeviceFound)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                listener.onScanFailed(
                    BluetoothScanException(errorCode.scanFailureMessage())
                )
            }
        }

        synchronized(lock) {
            check(active == null) { "蓝牙扫描已经在进行中" }
            active = ActiveNativeScan(listener, scanner, callback)
        }

        try {
            // 部分设备不会返回同时包含 Service UUID 与 Manufacturer ID 的原生组合过滤结果。
            // 保持系统扫描无过滤，在 AndroidBluetoothPort 收到完整广播后统一判断。
            scanner.startScan(
                null, ScanSettings.Builder().setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                ).build(), callback
            )
        } catch (error: Throwable) {
            synchronized(lock) {
                active?.takeIf {
                    it.listener === listener
                }?.also {
                    active = null
                }
            }
            throw error
        }
    }

    @SuppressLint("MissingPermission")
    override fun stop(
        listener: NativeBleScanListener
    ) {
        val scan = synchronized(lock) {
            active?.takeIf {
                it.listener === listener
            }?.also {
                active = null
            }
        } ?: return
        if (!hasScanPermission()) {
            return
        }
        scan.scanner.stopScan(scan.callback)
    }

    private fun requireScanPermission() {
        if (!hasScanPermission()) {
            throw SecurityException("缺少蓝牙扫描权限")
        }
    }

    private fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

    private data class ActiveNativeScan(
        val listener: NativeBleScanListener,
        val scanner: BluetoothLeScanner,
        val callback: ScanCallback
    )

}

private fun ScanResult.toScannedDevice(
    context: Context
): ScannedBleDevice? {
    val record = scanRecord ?: return null
    val name = record.deviceName
        ?: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            device.name
        } else {
            null
        }
    val manufacturerIds = buildSet {
        val data = record.manufacturerSpecificData
        repeat(data.size) {
            add(data.keyAt(it))
        }
    }
    val info = BluetoothDeviceInfo(
        id = device.address, name = name, isBle = true, rssi = rssi
    )
    return ScannedBleDevice(
        info = info, advertisement = BluetoothAdvertisement(
            isBle = true, serviceUuids = record.serviceUuids?.map {
                it.uuid.toString()
            }?.toSet(), manufacturerIds = manufacturerIds
        ), legacyDevice = DeviceModule(
            device, rssi, name, context, this
        )
    )
}

private fun Int.scanFailureMessage(): String = when (this) {
    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "蓝牙扫描已经在进行中"

    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "蓝牙扫描注册失败"

    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "蓝牙扫描内部错误"

    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "当前设备不支持所需蓝牙扫描功能"

    ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "蓝牙扫描硬件资源不足"

    ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "蓝牙扫描过于频繁"

    else -> "蓝牙扫描失败：$this"
}
