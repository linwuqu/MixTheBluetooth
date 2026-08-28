package com.biosensor.migratedev.port.adapter.bluetoothport

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hc.bluetoothlibrary.AllBluetoothManage
import com.hc.bluetoothlibrary.DeviceModule
import com.hc.bluetoothlibrary.IBluetooth
import com.hc.bluetoothlibrary.tootl.ModuleParameters

data class LibraryParam(
    val bleSendDelayState: Int = 1,
    val regularSendIntervalLevel: Int = 0,
    val bleReadBufferBytes: Int = 1_000,
    val classicReadBufferBytes: Int = 1_500,
    val receiveQuietPeriodMillis: Int = 100,
    val checkNewline: Boolean = true
)

internal interface BluetoothEngineListener {
    fun onDeviceFound(device: BluetoothDeviceInfo)
    fun onConnected(device: BluetoothDeviceInfo)
    fun onConnectionFailed(deviceId: String?)
    fun onDisconnected(deviceId: String?)
    fun onDataReceived(data: ByteArray)
    fun onDataSent(bytesSent: Int)
    fun onMtuChanged(mtu: Int)
}

internal interface BluetoothEngine {
    fun setListener(listener: BluetoothEngineListener)
    fun startScan()
    fun stopScan()
    fun connect(deviceId: String)
    fun disconnect()
    fun sendData(data: ByteArray)
    fun requestMtu(mtu: Int)
}

internal class BluetoothClient(
    context: Context, parameters: LibraryParam
) : BluetoothEngine {

    private val applicationContext = context.applicationContext
    private var listener: BluetoothEngineListener? = null
    private val nativeDevices = linkedMapOf<String, DeviceModule>()
    private var currentModule: DeviceModule? = null   // 已连接模块(单会话)
    private var connected = false

    private val manager = AllBluetoothManage(
        applicationContext, object : IBluetooth {
            override fun updateList(deviceModule: DeviceModule?) {
                deviceModule?.takeIf { it.isBt24() }?.let { module ->
                    nativeDevices[module.mac] = module
                    listener?.onDeviceFound(module.toDeviceInfo())
                }
            }

            override fun updateMessyCode(deviceModule: DeviceModule?) =
                updateList(deviceModule)   // 乱码设备同路径:过滤 + 入表 + 上报

            override fun connectSucceed(deviceModule: DeviceModule?) {
                deviceModule?.let { module ->
                    currentModule = module
                    connected = true
                    listener?.onConnected(module.toDeviceInfo())
                }
            }

            override fun errorDisconnect(deviceModule: DeviceModule?) {
                if (connected) {
                    connected = false
                    listener?.onDisconnected(deviceModule?.mac)   // 已连接后断线 → 会话终止
                } else {
                    listener?.onConnectionFailed(deviceModule?.mac)   // 连接期失败 → 连接终态
                }
            }

            override fun readData(mac: String?, data: ByteArray?) {
                data?.takeIf { it.isNotEmpty() }?.let { listener?.onDataReceived(it) }
            }

            override fun reading(isStart: Boolean) = Unit

            override fun readNumber(number: Int) {
                listener?.onDataSent(number)
            }

            override fun readLog(className: String?, data: String?, lv: String?) = Unit

            override fun readVelocity(velocity: Int) = Unit

            override fun callbackMTU(mtu: Int) {
                listener?.onMtuChanged(mtu)
            }

            override fun updateEnd() = Unit   // 20 秒自动停扫:引擎事务,不桥接
        })

    init {
        // 参数应用点唯一:SP 读写完全收进 SDK
        ModuleParameters.setParameters(
            parameters.bleSendDelayState,
            parameters.bleReadBufferBytes,
            parameters.classicReadBufferBytes,
            parameters.receiveQuietPeriodMillis,
            applicationContext
        )
        ModuleParameters.saveLevel(parameters.regularSendIntervalLevel, applicationContext)
        ModuleParameters.setNewline(parameters.checkNewline)
    }

    override fun setListener(listener: BluetoothEngineListener) {
        this.listener = listener
    }

    override fun startScan() {
        if (!hasScanPermission()) throw SecurityException("缺少蓝牙扫描权限")
        if (!manager.bleScan()) return
    }

    override fun stopScan() {
        if (hasScanPermission()) manager.stopScan()
    }

    override fun connect(deviceId: String) {
        val module = nativeDevices[deviceId]
            ?: throw IllegalArgumentException("找不到待连接的蓝牙设备")
        connected = false
        currentModule = null
        manager.connect(module)   // SDK 内部自动停扫,扫描与连接互斥是引擎事务
    }

    override fun disconnect() {
        manager.disconnect(currentModule)
        connected = false
        currentModule = null
    }

    override fun sendData(data: ByteArray) {
        val module = currentModule ?: throw IllegalStateException("未连接,无法发送")
        if (!connected) throw IllegalStateException("未连接,无法发送")
        manager.sendData(module, data)
    }

    override fun requestMtu(mtu: Int) {
        val module = currentModule ?: throw IllegalStateException("未连接")
        manager.setMTU(module, mtu)
    }

    private fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

    private fun DeviceModule.isBt24(): Boolean =
        isBLE && isHcModule(false, null) && hasManufacturerData(BT24_MANUFACTURER_ID)

    private fun DeviceModule.toDeviceInfo() =
        BluetoothDeviceInfo(id = mac, name = name, isBle = isBLE, rssi = rssi)

    private companion object {
        const val BT24_MANUFACTURER_ID = 0x4458
    }
}
