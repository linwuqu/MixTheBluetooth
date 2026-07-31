package com.biosensor.migratedev.port.adapter.bluetoothport

import android.app.Application
import android.content.Context
import com.hc.bluetoothlibrary.AllBluetoothManage
import com.hc.bluetoothlibrary.DeviceModule
import com.hc.bluetoothlibrary.IBluetooth
import com.hc.bluetoothlibrary.tootl.DataMemory
import com.hc.bluetoothlibrary.tootl.ModuleParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber

data class LegacyBluetoothParameters(
    val bleSendDelayState: Int = 1,
    val regularSendIntervalLevel: Int = 0,
    val bleReadBufferBytes: Int = 1_000,
    val classicReadBufferBytes: Int = 1_500,
    val receiveQuietPeriodMillis: Int = 100,
    val checkNewline: Boolean = true
)

class AndroidBluetoothPort internal constructor(
    private val client: BluetoothLibraryClient,
    private val scanScope: CoroutineScope,
    private val filter: BluetoothAdvertisementFilter = Bt24AdvertisementFilter
) : BluetoothPort {

    constructor(
        application: Application,
        scope: CoroutineScope,
        parameters: LegacyBluetoothParameters = LegacyBluetoothParameters(),
        filter: BluetoothAdvertisementFilter = Bt24AdvertisementFilter
    ) : this(
        client = HcBluetoothLibraryClient(application, parameters),
        scanScope = scope,
        filter = filter
    )

    private val lock = Any()
    private var nextRoundId = 0L
    private var activeScan: ScanSession? = null
    private var connectionSession: ConnectionSession? = null

    private val libraryListener = object : BluetoothLibraryListener {
        override fun onDeviceFound(device: LibraryBluetoothDevice) {
            handleDeviceFound(device)
        }

        override fun onScanFinished() {
            handleScanFinished()
        }

        override fun onConnected(device: LibraryBluetoothDevice) {
            handleConnected(device)
        }

        override fun onConnectionLost(deviceId: String?) {
            handleConnectionLost(deviceId)
        }
    }

    init {
        client.setListener(libraryListener)
    }

    override fun execute(
        command: BluetoothCommand
    ): Flow<BluetoothResult> = when (command) {
        is BluetoothCommand.StartScan -> startScan(command.scanSessionId)

        is BluetoothCommand.RefreshScan -> refreshScan(command.scanSessionId)

        is BluetoothCommand.StopScan -> stopScan(command.scanSessionId)

        is BluetoothCommand.Connect -> connect(command.deviceId, command.timeoutMillis)

        BluetoothCommand.Disconnect -> disconnect()
    }

    private fun startScan(
        scanSessionId: String
    ): Flow<BluetoothResult> = callbackFlow {
        val session = ScanSession(
            id = scanSessionId, output = channel
        )
        val accepted = synchronized(lock) {
            if (activeScan != null) {
                false
            } else {
                activeScan = session
                true
            }
        }
        if (!accepted) {
            trySend(
                BluetoothResult.ScanFailed(
                    scanSessionId, "蓝牙扫描已经在进行中"
                )
            )
            close()
            return@callbackFlow
        }

        if (!beginRound(session)) {
            close()
            return@callbackFlow
        }

        awaitClose {
            finishScanSession(
                expectedSessionId = scanSessionId, stopLibrary = true
            )
        }
    }

    private fun refreshScan(
        scanSessionId: String
    ): Flow<BluetoothResult> = flow {
        val session = synchronized(lock) {
            activeScan?.takeIf { it.id == scanSessionId }?.also {
                it.suppressNextRoundEnd = true
            }
        }
        if (session == null) {
            emit(
                BluetoothResult.ScanFailed(
                    scanSessionId, "当前扫描会话不存在"
                )
            )
            return@flow
        }

        runCatching { client.stopScan() }
        synchronized(lock) {
            if (activeScan === session) {
                session.suppressNextRoundEnd = false
            }
        }
        val started = beginRound(session)
        if (started) {
            emit(
                BluetoothResult.ScanRefreshed(
                    scanSessionId, session.roundId
                )
            )
        } else {
            emit(
                BluetoothResult.ScanFailed(
                    scanSessionId, session.lastFailure ?: "刷新蓝牙扫描失败"
                )
            )
        }
    }

    private fun stopScan(
        scanSessionId: String
    ): Flow<BluetoothResult> = flow {
        finishScanSession(
            expectedSessionId = scanSessionId, stopLibrary = true
        )
        emit(BluetoothResult.ScanStopped(scanSessionId))
    }

    private fun beginRound(session: ScanSession): Boolean {
        val roundId = synchronized(lock) {
            if (activeScan !== session) {
                return false
            }
            nextRoundId += 1
            session.roundId = nextRoundId
            nextRoundId
        }
        val started = runCatching { client.startScan() }
        if (started.isFailure || !started.getOrDefault(false)) {
            val message = started.exceptionOrNull()?.bluetoothFailureMessage("启动蓝牙扫描失败")
                ?: "启动蓝牙扫描失败"
            synchronized(lock) {
                if (activeScan === session) {
                    activeScan = null
                }
                session.lastFailure = message
            }
            session.output.trySend(
                BluetoothResult.ScanFailed(session.id, message)
            )
            session.output.close()
            return false
        }
        session.output.trySend(
            BluetoothResult.ScanStarted(session.id, roundId)
        )
        Timber.tag(BLUETOOTH_TAG).i(
            "scanSession=%s round=%d started", session.id.redactedId(), roundId
        )
        return true
    }

    private fun finishScanSession(
        expectedSessionId: String?, stopLibrary: Boolean
    ): ScanSession? {
        val session = synchronized(lock) {
            activeScan?.takeIf {
                expectedSessionId == null || it.id == expectedSessionId
            }?.also {
                it.suppressNextRoundEnd = true
                activeScan = null
            }
        } ?: return null

        if (stopLibrary) {
            runCatching { client.stopScan() }
        }
        session.output.close()
        return session
    }

    private fun connect(
        deviceId: String, timeoutMillis: Long
    ): Flow<BluetoothResult> = callbackFlow {
        require(timeoutMillis > 0) {
            "连接超时时间必须大于 0"
        }
        finishScanSession(
            expectedSessionId = null, stopLibrary = true
        )

        val session = ConnectionSession(
            deviceId = deviceId, output = channel
        )
        val accepted = synchronized(lock) {
            if (connectionSession != null) {
                false
            } else {
                connectionSession = session
                true
            }
        }
        if (!accepted) {
            trySend(
                BluetoothResult.ConnectFailed(
                    "已有蓝牙连接会话正在进行"
                )
            )
            close()
            return@callbackFlow
        }

        val started = runCatching { client.connect(deviceId) }
        if (started.isFailure || !started.getOrDefault(false)) {
            synchronized(lock) {
                if (connectionSession === session) {
                    connectionSession = null
                }
            }
            trySend(
                BluetoothResult.ConnectFailed(
                    started.exceptionOrNull()?.bluetoothFailureMessage(
                        "找不到待连接的蓝牙设备"
                    ) ?: "找不到待连接的蓝牙设备"
                )
            )
            close()
            return@callbackFlow
        }

        val timeout = launch {
            delay(timeoutMillis)
            val timedOut = synchronized(lock) {
                if (connectionSession === session && !session.connected) {
                    connectionSession = null
                    true
                } else {
                    false
                }
            }
            if (timedOut) {
                trySend(BluetoothResult.ConnectTimeout)
                runCatching { client.disconnect(deviceId) }
                close()
            }
        }

        awaitClose {
            timeout.cancel()
            val ownsConnection = synchronized(lock) {
                if (connectionSession === session) {
                    connectionSession = null
                    true
                } else {
                    false
                }
            }
            if (ownsConnection) {
                runCatching { client.disconnect(deviceId) }
            }
        }
    }

    private fun disconnect(): Flow<BluetoothResult> = flow {
        val session = synchronized(lock) {
            connectionSession.also {
                connectionSession = null
            }
        }
        runCatching { client.disconnect(session?.deviceId) }
        session?.output?.close()
        emit(BluetoothResult.Disconnected)
    }

    private fun handleDeviceFound(
        device: LibraryBluetoothDevice
    ) {
//        if (!filter.matches(device.advertisement)) {
//            return
//        }
        val snapshot = synchronized(lock) {
            val session = activeScan ?: return
            session.devices[device.id] = device.toDeviceInfo()
            Triple(
                session.output, session.id, session.roundId
            ) to session.devices.values.toList()
        }
        val (scan, devices) = snapshot
        scan.first.trySend(
            BluetoothResult.DevicesUpdated(
                scanSessionId = scan.second, roundId = scan.third, devices = devices
            )
        )
    }

    private fun handleScanFinished() {
        val ended = synchronized(lock) {
            val session = activeScan ?: return
            if (session.suppressNextRoundEnd) {
                return
            }
            Triple(
                session, session.id, session.roundId
            )
        }
        val (session, sessionId, roundId) = ended
        session.output.trySend(
            BluetoothResult.ScanRoundEnded(
                sessionId, roundId
            )
        )
        scanScope.launch {
            beginRound(session)
        }
    }

    private fun handleConnected(
        device: LibraryBluetoothDevice
    ) {
        val session = synchronized(lock) {
            connectionSession?.takeIf { it.deviceId == device.id }?.also { it.connected = true }
        }
        session?.output?.trySend(
            BluetoothResult.Connected(device.toDeviceInfo())
        )
    }

    private fun handleConnectionLost(deviceId: String?) {
        val session = synchronized(lock) {
            connectionSession?.takeIf {
                deviceId == null || it.deviceId == deviceId
            }?.also { connectionSession = null }
        } ?: return

        session.output.trySend(
            if (session.connected) {
                BluetoothResult.Disconnected
            } else {
                BluetoothResult.ConnectFailed(
                    "蓝牙连接失败"
                )
            }
        )
        session.output.close()
    }

    private class ScanSession(
        val id: String,
        val output: SendChannel<BluetoothResult>,
        val devices: LinkedHashMap<String, BluetoothDeviceInfo> = linkedMapOf(),
        var roundId: Long = 0,
        var suppressNextRoundEnd: Boolean = false,
        var lastFailure: String? = null
    )

    private class ConnectionSession(
        val deviceId: String,
        val output: SendChannel<BluetoothResult>,
        var connected: Boolean = false
    )

    private companion object {
        const val BLUETOOTH_TAG = "Connection.Bluetooth"
    }
}

internal data class LibraryBluetoothDevice(
    val id: String,
    val name: String?,
    val isBle: Boolean,
    val rssi: Int?,
    val serviceUuids: Set<String>?,
    val manufacturerIds: Set<Int>?
) {
    val advertisement = BluetoothAdvertisement(
        isBle = isBle, serviceUuids = serviceUuids, manufacturerIds = manufacturerIds
    )
}

internal fun LibraryBluetoothDevice.toDeviceInfo() = BluetoothDeviceInfo(
    id = id, name = name, isBle = isBle, rssi = rssi
)

internal interface BluetoothLibraryListener {
    fun onDeviceFound(device: LibraryBluetoothDevice)
    fun onScanFinished()
    fun onConnected(device: LibraryBluetoothDevice)
    fun onConnectionLost(deviceId: String?)
}

internal interface BluetoothLibraryClient {
    fun setListener(listener: BluetoothLibraryListener?)
    fun startScan(): Boolean
    fun stopScan()
    fun connect(deviceId: String): Boolean
    fun disconnect(deviceId: String?)
}

internal class HcBluetoothLibraryClient(
    context: Context, parameters: LegacyBluetoothParameters
) : BluetoothLibraryClient {

    private val applicationContext = context.applicationContext
    private var listener: BluetoothLibraryListener? = null
    private val nativeDevices = linkedMapOf<String, DeviceModule>()
    private val manager = AllBluetoothManage(
        applicationContext, object : IBluetooth {
            override fun updateList(
                deviceModule: DeviceModule?
            ) = publishDevice(deviceModule)

            override fun connectSucceed(
                deviceModule: DeviceModule?
            ) {
                deviceModule?.let {
                    listener?.onConnected(
                        it.toLibraryDevice()
                    )
                }
            }

            override fun updateEnd() {
                listener?.onScanFinished()
            }

            override fun updateMessyCode(
                deviceModule: DeviceModule?
            ) = publishDevice(deviceModule)

            override fun readData(
                mac: String?, data: ByteArray?
            ) = Unit

            override fun reading(isStart: Boolean) = Unit

            override fun errorDisconnect(
                deviceModule: DeviceModule?
            ) {
                listener?.onConnectionLost(
                    deviceModule?.mac
                )
            }

            override fun readNumber(number: Int) = Unit
            override fun readLog(
                className: String?, data: String?, lv: String?
            ) = Unit

            override fun readVelocity(velocity: Int) = Unit
            override fun callbackMTU(mtu: Int) = Unit
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
        if (!applicationContext.getSharedPreferences(
                "data", Context.MODE_PRIVATE
            ).contains("ModuleLevel")
        ) {
            ModuleParameters.saveLevel(
                parameters.regularSendIntervalLevel, applicationContext
            )
        }
        ModuleParameters.setNewline(parameters.checkNewline)
    }

    override fun setListener(
        listener: BluetoothLibraryListener?
    ) {
        this.listener = listener
    }

    override fun startScan(): Boolean = manager.bleScan()

    override fun stopScan() = manager.stopScan()

    override fun connect(deviceId: String): Boolean {
        val device = nativeDevices[deviceId] ?: return false
        manager.connect(device)
        return true
    }

    override fun disconnect(deviceId: String?) {
        manager.disconnect(
            deviceId?.let(nativeDevices::get)
        )
    }

    private fun publishDevice(
        deviceModule: DeviceModule?
    ) {
        deviceModule ?: return
        nativeDevices[deviceModule.mac] = deviceModule
        listener?.onDeviceFound(
            deviceModule.toLibraryDevice()
        )
    }

    private fun DeviceModule.toLibraryDevice() = LibraryBluetoothDevice(
        id = mac,
        name = name,
        isBle = isBLE,
        rssi = rssi,
        serviceUuids = if (isHcModule(true, "FFE0")) {
            setOf("FFE0")
        } else {
            emptySet()
        },
        manufacturerIds = if (hasManufacturerData(0x4458)) {
            setOf(0x4458)
        } else {
            emptySet()
        }
    )
}

private fun Throwable.bluetoothFailureMessage(
    fallback: String
): String = if (this is SecurityException) {
    "缺少蓝牙扫描或连接权限"
} else {
    message?.takeIf { it.isNotBlank() } ?: fallback
}

private fun String.redactedId(): String = takeLast(4).padStart(length.coerceAtMost(4), '*')
