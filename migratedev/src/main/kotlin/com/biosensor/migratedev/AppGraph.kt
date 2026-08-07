package com.biosensor.migratedev

import android.app.Application
import com.biosensor.migratedev.database.LocalDatabase
import com.biosensor.migratedev.orchestrator.root.RootWorkflow
import com.biosensor.migratedev.port.adapter.bluetoothport.AndroidBluetoothPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.localport.AndroidLocalPort
import com.biosensor.migratedev.port.adapter.localport.LocalFileClient
import com.biosensor.migratedev.port.adapter.localport.StringEntropy
import com.biosensor.migratedev.port.adapter.remoteport.HttpRemote
import com.biosensor.migratedev.port.adapter.remoteport.OkHttpRemote
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.auth.AuthPortAdapter
import com.biosensor.migratedev.port.cgm.CgmPort
import com.biosensor.migratedev.port.cgm.CgmPortAdapter
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.port.connection.ConnectionPortAdapter
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 这里进行模块装载,分为两段:
 * 1. 能力挂载区:每类能力(键值存储 / sqlite / 文件 / HTTP / 蓝牙)只挂一次;
 * 2. 业务组装区:薄适配器,自持业务协议(端点、键名、序列化),按需拿能力。
 */
class AppGraph(application: Application) {
    internal val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rootScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val clock = Clock.systemUTC()

    // ── 能力挂载区:每类能力只挂一次 ────────────────────────────
    private val androidLocal = AndroidLocalPort.create(application, applicationScope)
    private val kv: StringEntropy = androidLocal.entropy      // 键值存储(Tink 加密)
    private val sqlite: LocalDatabase = androidLocal.sqlite   // 结构化存储(SQLDelight)
    internal val files: LocalFileClient = androidLocal.files  // 文件(日志初始化使用)
    private val http: HttpRemote = OkHttpRemote(BuildConfig.API_BASE_URL)
    private val ble: BluetoothPort = AndroidBluetoothPort(application)

    // ── 业务组装区:薄适配器,只表达业务协议 ──────────────────────
    private val authPort: AuthPort = AuthPortAdapter(kv, http, clock)
    private val connectionPort: ConnectionPort = ConnectionPortAdapter(sqlite, ble)
    private val cgmPort: CgmPort = CgmPortAdapter(bluetooth = ble, fileClient = files)

    val rootWorkflow = RootWorkflow(authPort, connectionPort, cgmPort, scope = rootScope)
}
