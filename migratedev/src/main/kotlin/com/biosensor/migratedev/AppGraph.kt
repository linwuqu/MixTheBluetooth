package com.biosensor.migratedev

import android.app.Application
import com.biosensor.migratedev.orchestrator.root.RootWorkflow
import com.biosensor.migratedev.port.adapter.bluetoothport.AndroidBluetoothPort
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothPort
import com.biosensor.migratedev.port.adapter.bluetoothport.LibraryParam
import com.biosensor.migratedev.port.adapter.localport.AndroidLocalPort
import com.biosensor.migratedev.port.adapter.localport.FileStore
import com.biosensor.migratedev.port.adapter.localport.KvStore
import com.biosensor.migratedev.port.adapter.localport.LocalPort
import com.biosensor.migratedev.port.adapter.localport.SqlStore
import com.biosensor.migratedev.port.adapter.remoteport.HttpRemote
import com.biosensor.migratedev.port.adapter.remoteport.OkHttpRemote
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.auth.AuthPortAdapter
import com.biosensor.migratedev.port.cgm.CgmPort
import com.biosensor.migratedev.port.cgm.CgmPortAdapter
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.port.connection.ConnectionPortAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

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
    private val local: LocalPort = AndroidLocalPort.create(application, applicationScope)
    private val kv: KvStore = local.kv                       // 键值存储(Tink 加密)
    private val sql: SqlStore = local.sql                    // 结构化存储(SQLDelight)
    internal val files: FileStore = local.files              // 文件(日志初始化使用)
    private val http: HttpRemote = OkHttpRemote(BuildConfig.API_BASE_URL) // https
    // 蓝牙参数配置点(与 baseurl 同层):默认值与 SDK 静态默认一致,要调参只改这里
    private val bluetoothParameters = LibraryParam()
    private val ble: BluetoothPort = AndroidBluetoothPort(application, bluetoothParameters)

    // ── 业务组装区:薄适配器,只表达业务协议 ──────────────────────
    private val authPort: AuthPort = AuthPortAdapter(kv, http, clock)
    private val connectionPort: ConnectionPort = ConnectionPortAdapter(sql, ble)
    private val cgmPort: CgmPort = CgmPortAdapter(ble, files)

    val rootWorkflow = RootWorkflow(authPort, connectionPort, cgmPort, rootScope)
}
