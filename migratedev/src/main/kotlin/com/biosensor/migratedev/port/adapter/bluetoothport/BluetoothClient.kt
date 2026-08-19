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

/** 蓝牙参数配置:默认值与 SDK 静态默认一致(1/1000/1500/100/0/true),创建点在 AppGraph。 */
data class LegacyBluetoothParameters(
    val bleSendDelayState: Int = 1,
    val regularSendIntervalLevel: Int = 0,
    val bleReadBufferBytes: Int = 1_000,
    val classicReadBufferBytes: Int = 1_500,
    val receiveQuietPeriodMillis: Int = 100,
    val checkNewline: Boolean = true
)

/** 引擎回调:桥接层(AndroidBluetoothPort)按事件类型分发到各订阅者。 */
internal interface BluetoothEngineListener {
    fun onDeviceFound(device: BluetoothDeviceInfo)
    fun onConnected(device: BluetoothDeviceInfo)
    /** 连接期失败(SDK 连接未成功即断开)→ 连接 flow 收终态 ConnectFailed。 */
    fun onConnectionFailed(deviceId: String?)
    /** 已连接后断线(会话终止)→ 会话期订阅者收 Disconnected。 */
    fun onDisconnected(deviceId: String?)
    fun onDataReceived(data: ByteArray)
    fun onDataSent(bytesSent: Int)
    fun onMtuChanged(mtu: Int)
}

/**
 * 引擎接口:SDK 的全部能力面。会话(DeviceModule 管理)是引擎内部事务,接口只认 deviceId。
 * 同步方法只表达"指令是否发出"——发不出就抛异常(前置条件错误,同步可知);
 * 业务成功与否一律走回调(异步),不设 Boolean 返回值。
 */
internal interface BluetoothEngine {
    fun setListener(listener: BluetoothEngineListener)
    fun startScan()      // 权限缺失/蓝牙不可用 → 抛异常;SDK 已在扫描(false)静默接受
    fun stopScan()
    fun connect(deviceId: String)   // 设备不在扫描表 → 抛 IllegalArgumentException("找不到待连接的蓝牙设备")
    fun disconnect()
    fun sendData(data: ByteArray)   // 未连接 → 抛 IllegalStateException("未连接,无法发送")
    fun requestMtu(mtu: Int)        // 未连接 → 抛 IllegalStateException("未连接")
}

/**
 * BluetoothClient 是唯一引擎实现:直接包装 SDK AllBluetoothManage,SDK 接触面全部收口于此。
 * 扫描/连接/数据/线程/互斥全部由 SDK 自管(连接前自动停扫、20 秒自动停扫、分包节流),
 * 本类只做三件事:
 * 1. IBluetooth 回调 → BluetoothEngineListener 桥接(回调线程 = SDK 主线程 handler);
 * 2. BT24 过滤(DeviceModule.isHcModule/hasManufacturerData,等价于原 Bt24AdvertisementFilter);
 * 3. 会话表 nativeDevices:扫描回调入表,connect/sendData 取用。
 * 它不含任何业务状态机——连接/断开的决策在 decisioncore,这里只执行。
 */
internal class BluetoothClient(
    context: Context, parameters: LegacyBluetoothParameters
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
                // 引擎持有会话:自己区分阶段,精确路由
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
                // 旧库:每包 GATT 写成功后回调,值为该包字节数(架构 07 §3.1)
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
        // 参数应用点唯一:SP 读写完全收进 SDK(ModuleParameters 内部守卫,值没变不写 SP)
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
        // SDK false = 已在扫描中(单飞保护),静默接受:收集即扫描,扫描已就绪
        if (!manager.bleScan()) return
        // bleScan 内部异常(蓝牙不可用等)自然透传,桥接层统一转 BluetoothScanException
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
        manager.sendData(module, data)   // SDK 内部分包 + 信号量逐包发送;发送结果走 readNumber 回调
    }

    override fun requestMtu(mtu: Int) {
        val module = currentModule ?: throw IllegalStateException("未连接")
        manager.setMTU(module, mtu)   // 默认业务不调用(架构 07 §1.4),这里保留能力
    }

    private fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

    /** BT24 过滤:service UUID 0xFFE0/0xFFF0 && manufacturer 0x4458(等价于原 Bt24AdvertisementFilter)。 */
    private fun DeviceModule.isBt24(): Boolean =
        isBLE && isHcModule(false, null) && hasManufacturerData(BT24_MANUFACTURER_ID)

    private fun DeviceModule.toDeviceInfo() =
        BluetoothDeviceInfo(id = mac, name = name, isBle = isBLE, rssi = rssi)

    private companion object {
        const val BT24_MANUFACTURER_ID = 0x4458
    }
}
