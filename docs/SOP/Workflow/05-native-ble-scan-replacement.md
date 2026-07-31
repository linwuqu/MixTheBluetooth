# 原生 `BluetoothLeScanner` 替换方案

## 目标

`migratedev` 使用 Android 原生 BLE 持续扫描；HC 旧库只保留连接和通信。

```text
BluetoothLeScanner
→ AndroidBluetoothPort.scanDevices()
→ ConnectionTranslation
→ DecisionCore / UI

AndroidBluetoothPort.connect(deviceId)
→ AllBluetoothManage.connect(DeviceModule)
```

文件分工：

- `AndroidBluetoothPort`：扫描与连接会话编排、监听回调。
- `AndroidNativeBleScanner`：原生扫描、权限检查、结果映射。
- `HcBluetoothLibraryClient`：HC 旧库初始化、连接、断开和回调。

## Port

扫描不再使用 Command/Result，而是一个冷 Flow：

```kotlin
interface BluetoothPort :
    CommandPort<BluetoothCommand, BluetoothResult> {
    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
```

`ConnectionPort` 原样向上转发这个 Flow。

删除旧扫描协议：

```text
StartScan / RefreshScan / StopScan
ScanStarted / ScanRoundEnded / ScanRefreshed / ScanStopped
scanSessionId / roundId
```

## 原生扫描适配器与 Port

每次收集 `scanDevices()` 时：

1. 调用一次 `BluetoothLeScanner.startScan()`。
2. 用现有 `Bt24AdvertisementFilter` 筛选结果。
3. 按 MAC 保存最新的 `BluetoothDeviceInfo` 和连接所需的 `DeviceModule`。
4. Flow 取消、扫描失败或开始连接时，统一调用一次 `stopScan()`。
5. 停止后的旧 callback 不再向 Flow 发送数据。

核心形态：

```kotlin
override fun scanDevices(): Flow<BluetoothDeviceInfo> =
    callbackFlow {
        val listener = createListener(channel)
        registerAsActive(listener)
        scanner.start(listener)
        awaitClose { finishActiveScan(listener) }
    }
```

扫描使用 `SCAN_MODE_LOW_LATENCY`，不设置原生 `ScanFilter`，避免改变已经验证过的 BT24 发现条件。

连接前先停止原生扫描，再把已保存的 `DeviceModule` 交给 HC 旧库：

```kotlin
finishActiveScan()
client.connect(discovered.getValue(deviceId))
```

`AllBluetoothManage.connect()` 需要把外部扫描得到的 `DeviceModule` 补入旧库缓存，否则旧库连接成功后找不到设备，无法回传成功事件。

## Translation

Route 仍提交前后台 Intent，但可见性只控制这一条扫描 Flow，不再进入 DecisionCore：

```kotlin
combine(
    bluetoothAccessGranted,
    visible,
    orchestrator.state,
    scanRestart
) { access, visible, state, restart ->
    ScanRequest(
        access && visible &&
            state is ConnectionState.Scanning,
        restart
    )
}
    .distinctUntilChanged()
    .flatMapLatest { request ->
        if (request.enabled) port.scanDevices()
        else emptyFlow()
    }
    .collect { device ->
        devices[device.id] = device
        dispatch(DevicesUpdated(devices.values.toList()))
    }
```

- 页面隐藏：取消收集，Port 自动停止扫描。
- 页面恢复：重新收集，开始一次新扫描。
- 扫描运行中刷新：只清空当前列表，不重启扫描。
- 扫描失败后刷新：递增 `scanRestart`，重新收集。
- 退出：先 `cancelAndJoin()` 扫描，再进入连接资源退出流程。

## DecisionCore

`Scanning` 只保留业务数据：

```kotlin
data class Scanning(
    val userId: String,
    val devices: List<BluetoothDeviceInfo>,
    val binding: BindingLookup,
    val autoConnectAttempted: Boolean,
    val message: String?
) : ConnectionState
```

移除扫描资源状态、轮次、会话 ID、页面可见性和扫描启停 Effect。DecisionCore 只负责：

- 绑定结果与设备列表汇合；
- 自动或手动连接；
- 连接成功、失败、断开；
- 已连接资源的退出释放。

## 验证

- 一次收集只启动一次原生扫描。
- 取消 Flow 只停止一次扫描，迟到 callback 被忽略。
- 刷新不产生第二次 `startScan()`。
- 连接前扫描已经停止，HC 连接事件正常回传。
- 自动连接、手动连接、隐藏恢复和退出行为不变。
