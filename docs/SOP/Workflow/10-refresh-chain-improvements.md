# 刷新链路的两点改进:反馈可见、重启无成本

> 范围:`translation` 层(isRefreshing)+ `port` 层(共享扫描)。
> **不动**:`decisioncore` / `ui` 组件结构 / `AndroidNativeBleScanner`。
> 行为说明:刷新语义保持"清空 + 重扫"(用户意图),**不引入保留脏数据**。
> 前置:[09-flow-landscape-and-combine.md](./09-flow-landscape-and-combine.md)(流的分类与建模)。

## 0. 一句话

两点改进:
1. **isRefreshing 变真**——从硬编码 `false` 改为真实扫描状态,让刷新有反馈、有手势锁、有按钮禁用;
2. **共享扫描**——扫描从"每订阅一个会话"改为"引用计数共享",让重启从 stop/start 往返变成集合增删。

```text
刷新(清空+重扫,语义不变)
  ├─ 前端:isRefreshing = 扫描状态 → 转圈 + 手势锁 + 按钮禁用    ← 反馈可见
  └─ 后端:共享扫描 → 重启 = 订阅切换,无 stop/start、无竞争       ← 重启无成本
```

## 1. 现状(Before)

### 1.1 相关代码与当前行为

| 文件 | 相关代码 | 当前行为 |
|---|---|---|
| [ConnectionTranslation.kt:288](migratedev/src/main/kotlin/com/biosensor/migratedev/translation/connection/ConnectionTranslation.kt#L288) | `toUiState` 里 `isRefreshing = false` | 刷新指示器永不转圈;`pullRefresh` 的手势锁(依赖 refreshing=true)永不生效;按钮无禁用态 |
| [ConnectionTranslation.kt:153-157](migratedev/src/main/kotlin/com/biosensor/migratedev/translation/connection/ConnectionTranslation.kt#L153-L157) | `submit(Refresh)`:`clearDevices()` + dispatch + `if (!scanActive) scanRestart += 1` | 刷新 = 清空列表 + 视扫描状态决定是否重启 |
| [ConnectionTranslation.kt:175-186](migratedev/src/main/kotlin/com/biosensor/migratedev/translation/connection/ConnectionTranslation.kt#L175-L186) | `scanFlow()`:`onStart { clearDevices(); scanActive = true }` / `onCompletion { scanActive = false }` / `catch { dispatch ScanFailed }` | `scanActive` 是普通 var,只被 submit 读取,不进 UI 流 |
| [AndroidBluetoothPort.kt:63-111](migratedev/src/main/kotlin/com/biosensor/migratedev/port/adapter/bluetoothport/AndroidBluetoothPort.kt#L63-L111) | `scanDevices()`:callbackFlow 单飞(`activeScan` 竞争,失败 `close("蓝牙扫描已经在进行中")`) | 每次订阅 = 完整会话:start / 取消 = stop |
| [AndroidNativeBleScanner.kt:73](migratedev/src/main/kotlin/com/biosensor/migratedev/port/adapter/bluetoothport/AndroidNativeBleScanner.kt#L73) | `check(active == null)` | native 层单会话硬约束 |

### 1.2 问题定位

| # | 问题 | 表现 | 影响 |
|---|---|---|---|
| P1 | 刷新无反馈、无锁 | `isRefreshing` 恒 false:指示器不转、下拉手势不锁、连发时每次 Refresh 都清一次列表 | 用户不知道扫描是否在跑;连发放大了"清空 + 等广播"的感知成本 |
| P2 | 真重启有成本、有竞争 | 每次真重启(Failed→Scanning、隐藏→可见、未启动时刷新)都走完整的 stop/start;flatMapLatest 取消旧流是**异步**的,新流的 `check(active == null)` 可能撞上未收尾的旧扫描 → 新流以失败关闭 → **管道死**,只能手动刷新自愈 | 30 秒 5 次启停被系统限流(SCAN_FAILED_SCANNING_TOO_FREQUENTLY);竞争导致扫描管道死亡 |

### 1.3 明确不做的事(取舍记录)

| 方案 | 结论 | 原因 |
|---|---|---|
| 刷新不清空列表(stale-while-revalidate) | **不做** | 刷新语义 = 清空 + 重扫是**用户意图**——用户主动刷新就是想看到干净的新列表;保留脏数据等替换,违背预期,效果反而不好 |
| 因此:0.4-0.5s 空屏(清空后等第一个广播)是语义成本 | **接受**,由 P1 的反馈缓解 | 转圈让等待可感知,不再"静默空屏";根治等待要靠减少重启(P2),而非保留旧数据 |

## 2. 修订后(After)

### 2.1 分层模型

```mermaid
flowchart LR
  subgraph UI["UI 层"]
    UI["PullRefreshIndicator / 按钮\nrefreshing = isRefreshing"]
  end
  subgraph TR["translation 层"]
    SCAN["scanning: MutableStateFlow&lt;Boolean&gt;\n(原 scanActive var)"]
    C["combine(state, binding, devices, scanning)"]
  end
  subgraph PORT["port 层"]
    SUB["scanSubscribers: 订阅者集合"]
    NATIVE["sharedScanListener 一份 native 回调"]
  end

  NATIVE -->|分发| SUB
  SUB -->|scanFlow| SCAN
  SCAN --> C
  C -->|isRefreshing| UI
```

### 2.2 文件与职能变化

| 文件 | 变化 |
|---|---|
| `ConnectionTranslation.kt` | `scanActive: Boolean` → `scanning: MutableStateFlow<Boolean>`;`uiState` 合流加第 4 输入;`toUiState` 签名加 `isRefreshing` |
| `DeviceList.kt` | 空态"刷新扫描"按钮加 `!isRefreshing` 禁用(骨架已传 isRefreshing,不再改) |
| `AndroidBluetoothPort.kt` | `activeScan`(单 listener)→ `scanSubscribers`(集合)+ `nativeScanning`(布尔);新增 `sharedScanListener`;`scanDevices` 改为共享语义 |
| `AndroidNativeBleScanner.kt` | **不动**(native 层仍只持有一个 sharedScanListener 会话) |

## 3. 是否真的有优化(诚实评估)

### 3.1 改善了什么

| 维度 | Before | After |
|---|---|---|
| 刷新反馈 | 静默,用户不知道扫描在跑 | 指示器转圈、按钮禁用、下拉手势锁住 |
| 连发行为 | 每次 Refresh 都清一次列表 | 刷新中连发被手势锁 + 按钮挡住(一次刷新一次清空) |
| 重启成本 | 每次真重启 stop/start 往返 | 订阅集合增删,native 不停 |
| 竞争 | 新旧交接可能撞单飞拒绝 → 管道死 | 拒绝路径消失(native 只在"从 0 到 1"和"从 1 到 0"时启停) |
| 系统限流 | 高频启停可触发 SCAN_FAILED_SCANNING_TOO_FREQUENTLY | 启停次数降到最低,几乎不可能触发 |

### 3.2 代价与边界(诚实部分)

| 项 | 说明 |
|---|---|
| `scanActive` 变 StateFlow | 语义不变,只是可观察;`submit(Refresh)` 里读 `.value` |
| 共享扫描改变"每订阅独立会话"语义 | 多个订阅者共享同一份 native 扫描与 `discovered` 缓存;现在实际只有一个订阅者(scanCollection),共享是**为未来与健壮性设计**,当前行为等价 |
| `discovered` 缓存生命周期 | 原:每次会话开始清空;后:只在 native 真正启动(0→1)时清空——复用订阅(1→N)时设备列表延续 |
| 共享扫描是行为等价重构 | 单订阅者场景下输出完全一致;需测试覆盖订阅/退订的边界 |
| **解决不了** | 0.4-0.5s 清空感知(语义成本,见 1.3);设备物理断电后的陈旧条目(清空时机在下次真重启——与现状一致) |

## 4. 代码意图与完整代码

### 4.1 改进一:isRefreshing 接入真实扫描状态

**意图**:把"扫描是否在跑"变成 UI 可观察的状态。`scanning` 替代 `scanActive`,进入 uiState 合流;`toUiState` 的 `isRefreshing` 不再硬编码。

```kotlin
// ConnectionTranslation.kt —— 改动段

// 原:private var scanActive = false
private val scanning = MutableStateFlow(false)

private fun scanFlow(): Flow<BluetoothDeviceInfo> = port.scanDevices().onStart {
    clearDevices()
    scanning.value = true          // 原 scanActive = true
}.onCompletion {
    scanning.value = false         // 原 scanActive = false
}.catch { error ->
    orchestrator.dispatch(
        ConnectionEvent.ScanFailed(
            error.message?.takeIf(String::isNotBlank) ?: "蓝牙扫描失败"
        )
    )
}

// submit(Refresh) 内:原 if (!scanActive) scanRestart.value += 1
if (!scanning.value) scanRestart.value += 1

// uiState 合流加第 4 输入
override val uiState: StateFlow<ConnectionUiState> = combine(
    orchestrator.state, binding, devices, scanning
) { state, currentBinding, currentDevices, isScanning ->
    state.toUiState(currentBinding, currentDevices, isRefreshing = isScanning)
}.stateIn(
    viewModelScope,
    SharingStarted.Eagerly,
    ConnectionState.Idle.toUiState(BindingSnapshot.Loading, emptyList(), isRefreshing = false)
)

// toUiState 签名:加 isRefreshing 参数,替换硬编码
private fun ConnectionState.toUiState(
    binding: BindingSnapshot,
    scannedDevices: List<BluetoothDeviceInfo>,
    isRefreshing: Boolean
): ConnectionUiState {
    ...
    isRefreshing = isRefreshing,   // 原 isRefreshing = false
    ...
}
```

配套(UI 层一行):

```kotlin
// DeviceList.kt —— 空态"刷新扫描"按钮加禁用条件
Button(
    enabled = canInteract && !isRefreshing,   // 扫描中不可再点
    onClick = onRefresh
) { Text("刷新扫描") }
```

> 效果闭环:扫描开始 → `scanning = true` → uiState 重算 → 指示器转圈 + 手势锁 + 按钮禁用 → 扫描结束 → 状态复位。下拉连发被 Compose 的手势锁挡住,不再有"一次手势清一次列表"的连发。

### 4.2 改进二:共享扫描(引用计数)

**意图**:把"每订阅一个扫描会话"改成"一份 native 扫描 + 订阅者集合"。第一个订阅者负责 start(并清空缓存),最后一个负责 stop,中间订阅/退订只是集合增删——重启零成本,拒绝路径消失。`sharedScanListener` 只做一次过滤与缓存,再分发给所有订阅者。

```kotlin
// AndroidBluetoothPort.kt —— scanDevices 重写,其余逻辑(连接/断开)不动

private val lock = Any()
private val discovered = linkedMapOf<String, ScannedBleDevice>()
private val scanSubscribers = mutableSetOf<NativeBleScanListener>()   // 原 activeScan 单 listener
private var nativeScanning = false                                    // native 层当前是否在跑
private var connectionSession: ConnectionSession? = null

// 共享监听:一份 native 回调 → 过滤 + 缓存一次 → 分发给所有订阅者
private val sharedScanListener = object : NativeBleScanListener {
    override fun onDeviceFound(device: ScannedBleDevice) {
        if (!filter.matches(device.advertisement)) return
        synchronized(lock) {
            discovered[device.info.id] = device
        }
        scanSubscribers.toList().forEach { it.onDeviceFound(device) }
    }

    override fun onScanFailed(failure: BluetoothScanException) {
        finishActiveScan(failure = failure)
        scanSubscribers.toList().forEach { it.onScanFailed(failure) }
    }
}

override fun scanDevices(): Flow<BluetoothDeviceInfo> = callbackFlow {
    val subscriber = object : NativeBleScanListener {
        override fun onDeviceFound(device: ScannedBleDevice) {
            trySend(device.info)
        }

        override fun onScanFailed(failure: BluetoothScanException) {
            close(failure)
        }
    }

    // 第一个订阅者:启动 native(幂等:已在跑则跳过),同时清空会话缓存
    val startFailure = synchronized(lock) {
        val firstSubscriber = scanSubscribers.isEmpty()
        scanSubscribers += subscriber
        if (firstSubscriber && !nativeScanning) {
            discovered.clear()
            val started = runCatching { scanner.start(sharedScanListener) }
            nativeScanning = started.isSuccess
            started.exceptionOrNull()
        } else null
    }
    if (startFailure != null) {
        synchronized(lock) { scanSubscribers -= subscriber }
        close(startFailure.toBluetoothScanException())
        return@callbackFlow
    }
    Timber.tag(BLUETOOTH_TAG).i("native BLE scan started")

    awaitClose {
        val shouldStop = synchronized(lock) {
            scanSubscribers -= subscriber
            if (scanSubscribers.isEmpty() && nativeScanning) {
                nativeScanning = false
                true
            } else false
        }
        if (shouldStop) {
            runCatching { scanner.stop(sharedScanListener) }
            Timber.tag(BLUETOOTH_TAG).i("native BLE scan stopped")
        }
    }
}

// finishActiveScan 改为只处理 native 停止(不再持有单 listener 的 output 关闭)
private fun finishActiveScan(failure: Throwable? = null) {
    val shouldStop = synchronized(lock) {
        if (nativeScanning) {
            nativeScanning = false
            true
        } else false
    }
    if (shouldStop) runCatching { scanner.stop(sharedScanListener) }
}
```

关键设计点:

| 点 | 说明 |
|---|---|
| native 启停条件 | 只在订阅者数量 0→1(native 未跑时)start、1→0 stop;期间任何订阅切换零 native 成本 |
| 竞争消失 | flatMapLatest 重启 = 旧订阅 awaitClose(移除)+ 新订阅(加入)。只要还有订阅者在,`nativeScanning` 恒为 true,新订阅走"已在跑则跳过"分支——**没有拒绝路径** |
| start 幂等 | `firstSubscriber && !nativeScanning` 才真正 start;重复订阅直接挂上 |
| 失败分发 | native 失败 → `finishActiveScan` 停 native → 分发失败给所有订阅者 → 各订阅者 close;订阅者集合在 awaitClose 里自清理,不会重复 stop |
| native 层 | `AndroidNativeBleScanner` 只面对 `sharedScanListener` 这一个 listener,`check(active == null)` 依旧成立(会话永远唯一) |
| 缓存清空时机 | 从"每次会话开始"改为"native 真正启动(0→1)时"——复用订阅(1→N)设备列表延续,语义更合理 |

## 5. 对之前问题的解答

**Q:现状不是已经幂等了吗(扫描中刷新不重启、单飞唯一),为什么还要改?**
现状的幂等只覆盖**一条路**:扫描中刷新(distinctUntilChanged 吞掉,不重启)。它没覆盖另外两类问题:
- P1 是**反馈缺失**:刷新有没有生效、扫描在不在跑,用户完全无感——这是 UI 层问题,与幂等无关;
- P2 是**重启路径的健壮性**:真重启(Failed→Scanning、隐藏→可见、未启动时刷新)仍然每次 stop/start,且存在时序竞争撞单飞拒绝导致管道死——现状的幂等覆盖不到这条路径。
所以两点改进分别补"反馈"和"重启路径",与已有的幂等是互补关系。

**Q:共享扫描改了 scanDevices 的语义,单订阅者场景会有行为差异吗?**
不会。现在实际只有一个订阅者(scanCollection);单订阅者下,共享扫描 = 原会话语义(订阅→start,取消→stop)。差异只在多订阅者时体现(共享一份 native + 缓存延续),这是为未来与健壮性设计的,当前行为等价。

## 6. 影响面与实施顺序

### 6.1 影响面

| 项 | 说明 |
|---|---|
| 生产代码 | `ConnectionTranslation.kt`(约 8 行)、`DeviceList.kt`(1 行)、`AndroidBluetoothPort.kt`(scanDevices 重写,约 50 行) |
| 测试 | `ConnectionPortAdapterTest` 不动(port 接口未变);新增共享扫描测试(首订阅启、末订阅停、复用不启停、start 失败、native 失败分发) |
| 上游 | `decisioncore` / `ui` 骨架 / `AndroidNativeBleScanner` **零改动** |

### 6.2 实施顺序(每步可独立验证)

| 步骤 | 事项 | 验证 |
|---|---|---|
| 1 | 改进一:scanning StateFlow + uiState 合流 + 按钮禁用 | 编译 + 73 测试;真机:下拉刷新转圈、扫描中按钮禁用 |
| 2 | 改进二:共享扫描重写 | 编译 + 新增单测;真机:反复下拉/切后台/快速刷新,无"蓝牙扫描已经在进行中"日志 |
| 3 | 收尾 | 全量回归;观察 logcat 确认 native 启停次数骤降 |

> 两步互相独立,可各自回滚。改进一是纯前端 10 分钟;改进二是 port 层健壮性,需测试兜底。
