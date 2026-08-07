package com.biosensor.migratedev.port.adapter.bluetoothport

import android.content.Context
import com.hc.bluetoothlibrary.AllBluetoothManage
import com.hc.bluetoothlibrary.DeviceModule
import com.hc.bluetoothlibrary.IBluetooth
import com.hc.bluetoothlibrary.tootl.DataMemory
import com.hc.bluetoothlibrary.tootl.ModuleParameters

data class LegacyBluetoothParameters(
    val bleSendDelayState: Int = 1,
    val regularSendIntervalLevel: Int = 0,
    val bleReadBufferBytes: Int = 1_000,
    val classicReadBufferBytes: Int = 1_500,
    val receiveQuietPeriodMillis: Int = 100,
    val checkNewline: Boolean = true
)

internal interface BluetoothLibraryListener {
    fun onConnected(device: BluetoothDeviceInfo)
    fun onConnectionLost(deviceId: String?)
    fun onDataReceived(data: ByteArray)        // 旧库 readData 桥接:设备推送的字节块
    fun onDataSent(bytesSent: Int)             // 旧库 readNumber 桥接:每包 GATT 写成功回调
    fun onMtuChanged(mtu: Int)                 // 旧库 callbackMTU 桥接
}

internal interface BluetoothLibraryClient {
    fun setListener(listener: BluetoothLibraryListener?)
    fun connect(device: ScannedBleDevice): Boolean
    fun disconnect(deviceId: String?)
    fun sendData(deviceId: String, data: ByteArray): Boolean
    fun requestMtu(deviceId: String, mtu: Int): Boolean
}

/**
 *  HcBluetoothLibraryClient 封装旧的蓝牙库 AllBluetoothManage
 *  向上暴露的接口是 BluetoothLibraryClient 主要负责连接部分
 *  在 manager 中实现好对应的全部接口
 */
internal class HcBluetoothLibraryClient(
    context: Context, parameters: LegacyBluetoothParameters
) : BluetoothLibraryClient {
    private val applicationContext = context.applicationContext
    private var listener: BluetoothLibraryListener? = null
    private val nativeDevices = linkedMapOf<String, DeviceModule>()
    private val manager = AllBluetoothManage(
        applicationContext, object : IBluetooth {
            override fun updateList(deviceModule: DeviceModule?) = Unit

            override fun connectSucceed(deviceModule: DeviceModule?) {
                deviceModule?.let {
                    listener?.onConnected(
                        it.toDeviceInfo()
                    )
                }
            }

            override fun updateEnd() = Unit

            override fun updateMessyCode(deviceModule: DeviceModule?) = Unit

            override fun readData(mac: String?, data: ByteArray?) {
                data?.takeIf { it.isNotEmpty() }?.let { listener?.onDataReceived(it) }
            }

            override fun reading(isStart: Boolean) = Unit

            override fun errorDisconnect(deviceModule: DeviceModule?) {
                listener?.onConnectionLost(
                    deviceModule?.mac
                )
            }

            override fun readNumber(number: Int) {
                // 旧库:每包 GATT 写成功后回调,值为该包字节数(架构 07 §3.1)
                listener?.onDataSent(number)
            }

            override fun readLog(className: String?, data: String?, lv: String?) = Unit

            override fun readVelocity(velocity: Int) = Unit

            override fun callbackMTU(mtu: Int) {
                listener?.onMtuChanged(mtu)
            }
        })

    init {
        if (DataMemory(applicationContext).parameters == null) {
            ModuleParameters.setParameters(
                parameters.bleSendDelayState,
                parameters.bleReadBufferBytes,
                parameters.classicReadBufferBytes,
                parameters.receiveQuietPeriodMillis,
                applicationContext
            )
        }
        // TODO: 这里改一下 以后应该取消对于SP的使用 同时考虑一下还有那些地方需要读取默认配置
        //  是不是都需要配置到 local 接口中 这个最好应该不要分离式注册吧
        //  最好是类似于 env 有一个统一的位置来撰写
        if (!applicationContext.getSharedPreferences("data", Context.MODE_PRIVATE)
                .contains("ModuleLevel")
        ) {
            ModuleParameters.saveLevel(
                parameters.regularSendIntervalLevel, applicationContext
            )
        }
        ModuleParameters.setNewline(
            parameters.checkNewline
        )
    }

    override fun setListener(listener: BluetoothLibraryListener?) {
        this.listener = listener
    }

    override fun connect(device: ScannedBleDevice): Boolean {
        val module = device.legacyDevice ?: return false
        nativeDevices[device.info.id] = module
        manager.connect(module)
        return true
    }

    override fun disconnect(deviceId: String?) {
        manager.disconnect(deviceId?.let(nativeDevices::get))
    }

    override fun sendData(deviceId: String, data: ByteArray): Boolean {
        val module = nativeDevices[deviceId] ?: return false
        // 旧库内部分包(sendMultiple)+ 信号量逐包发送;每包成功后 readNumber 上报
        manager.sendData(module, data)
        return true
    }

    override fun requestMtu(deviceId: String, mtu: Int): Boolean {
        val module = nativeDevices[deviceId] ?: return false
        // 默认业务不调用(架构 07 §1.4):MTU 由库自适应,这里保留能力
        manager.setMTU(module, mtu)
        return true
    }

    private fun DeviceModule.toDeviceInfo() =
        BluetoothDeviceInfo(id = mac, name = name, isBle = isBLE, rssi = rssi)
}
