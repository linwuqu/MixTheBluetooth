# 指令直达,分层上报:词汇统一与契约收尾(修订)

> 范围:`port` 契约层 + `orchestrator` 执行器层 + `bluetoothport` 能力端口。
> 本文件是 07 首版的**落地复查修订**——首版落地后检查发现两处不彻底,本文补完。
> **不动** `decisioncore` 的状态机逻辑、`translation` 的业务语义、`ui`。
> 本重构是**行为等价重构**:只删词汇与接口形态,不改变任何运行逻辑。
> 前置:[06-port-capability-separation.md](./06-port-capability-separation.md)(已落地)、07 首版(已落地)。

## 0. 一句话

**07 首版落地后复查,发现两处不彻底:**

1. **`CommandPort` 还留着**。首版当时为省 import 的妥协("建议保留名字,语义已更新")——复查结论:名字带 Command,与"指令直达"精神相悖,且它的存在让"全链路只流通 Effect/Event"的说法不成立。**修订:删除 `CommandPort`,orchestrator 的执行器参数改用函数类型 `(Effect) -> Flow<Event>`。**
2. **蓝牙能力端口还是旧词汇**。`BluetoothPort : CommandPort<BluetoothCommand, BluetoothResult>` 是全库唯一残留的 Command/Result 词汇表。**修订:`BluetoothCommand → BluetoothEffect`、`BluetoothResult → BluetoothEvent`,全库 Command/Result 词汇清零。**

```text
首版落地后:  decisioncore ──Effect──▶ AuthPort/ConnectionPort(新)      ✓
                                    └─ BluetoothPort(旧:Command/Result) ✗ 词汇残留
                                    └─ CommandPort(接口名带 Command)    ✗ 语义残留
修订后:      decisioncore ──Effect──▶ 各 port 自带 execute(Effect→Flow<Event>)
             执行器参数 = 函数类型(Effect) -> Flow<Event>,无接口名可言
```

## 1. 现状(Before):首版落地后的残留

### 1.1 残留清单

| 位置 | 残留 | 问题 |
|---|---|---|
| [PortContracts.kt](migratedev/src/main/kotlin/com/biosensor/migratedev/port/PortContracts.kt) | `fun interface CommandPort<Effect, Event>` | 名字带 Command,语义已错位(它执行的是 Effect);它存在的唯一理由是 RootWorkflow 的 SAM 用法,函数类型可替代 |
| [WorkflowOrchestrator.kt:50](migratedev/src/main/kotlin/com/biosensor/migratedev/orchestrator/WorkflowOrchestrator.kt#L50) | `effectExecutor: CommandPort<Effect, Event>` | 同上 |
| [RootWorkflow.kt:49](migratedev/src/main/kotlin/com/biosensor/migratedev/orchestrator/root/RootWorkflow.kt#L49) | `CommandPort<RootEffect, RootEvent>(::execute)` | SAM 写法,函数类型下变成 `::execute` 直传 |
| [BluetoothPort.kt](migratedev/src/main/kotlin/com/biosensor/migratedev/port/adapter/bluetoothport/BluetoothPort.kt) | `BluetoothCommand` / `BluetoothResult` 词汇表 + `CommandPort<BluetoothCommand, BluetoothResult>` | 全库唯一 Command/Result 残留;新词汇(Effect/Event)在全库无法闭环 |
| [AndroidBluetoothPort.kt](migratedev/src/main/kotlin/com/biosensor/migratedev/port/adapter/bluetoothport/AndroidBluetoothPort.kt) | 实现上述旧词汇 | 同上 |
| [ConnectionPortAdapter.kt](migratedev/src/main/kotlin/com/biosensor/migratedev/port/connection/ConnectionPortAdapter.kt) | 包装 `BluetoothCommand` + 翻译 `BluetoothResult→ConnectionEvent` | 词汇形态不一致(左侧新右侧旧) |
| [AuthPort.kt](migratedev/src/main/kotlin/com/biosensor/migratedev/port/auth/AuthPort.kt) / [ConnectionPort.kt](migratedev/src/main/kotlin/com/biosensor/migratedev/port/connection/ConnectionPort.kt) | `interface XxxPort : CommandPort<Effect, Event>` | 首版已改为新词汇,但**继承了 CommandPort**——本修订中一并剥离继承 |

### 1.2 首版为什么留了这两处(诚实复盘)

| 首版的理由 | 复查结论 |
|---|---|
| "`CommandPort` 改名要全改 import,建议保留" | 改名只是把**泛型接口换成函数类型**,改动集中在 orchestrator 一处参数 + 两个 port 接口的继承,比想象的小;名字带 Command 的接口是"指令直达"精神上的自相矛盾,值得收掉 |
| "范围不动能力层,蓝牙属于能力" | 能力层**语义**(说蓝牙自己的话)与**词汇形态**(Effect/Event)是两回事——`BluetoothEffect/BluetoothEvent` 仍然是蓝牙自己的话,只是形态一致;能力层词汇形态统一后,全库只有两种词汇,边界清晰 |

## 2. 修订后(After)

### 2.1 CommandPort 删除,函数类型接管

```text
删除 PortContracts.kt(CommandPort 无替代文件,直接消失)

orchestrator:  effectExecutor: (Effect) -> Flow<Event>          // 原 CommandPort<Effect, Event>
RootWorkflow:  effectExecutor = ::execute                        // 原 CommandPort<RootEffect, RootEvent>(::execute)
translation:   effectExecutor = port::execute                    // 原 effectExecutor = port
```

port 接口自带 `execute`,不再继承任何接口:

```kotlin
interface AuthPort {
    fun execute(effect: AuthEffect): Flow<AuthEvent>
}

interface ConnectionPort {
    fun execute(effect: ConnectionEffect): Flow<ConnectionEvent>
    fun readBinding(userId: String): Flow<BindingSnapshot>
    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
```

### 2.2 蓝牙词汇统一

```kotlin
sealed interface BluetoothEffect {
    data class Connect(val deviceId: String, val timeoutMillis: Long = 15_000) : BluetoothEffect
    data object Disconnect : BluetoothEffect
    // TODO(Workflow 03): SendData / ObserveReceivedData / RequestMtu /
    // transfer progress / protocol errors.
}

sealed interface BluetoothEvent {
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothEvent
    data class ConnectFailed(val message: String) : BluetoothEvent
    data object ConnectTimeout : BluetoothEvent
    data object Disconnected : BluetoothEvent
    // TODO(Workflow 03): DataSent / DataReceived / MtuChanged /
    // TransferProgress / TransferFailed.
}

interface BluetoothPort {
    fun execute(effect: BluetoothEffect): Flow<BluetoothEvent>
    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
```

### 2.3 修订后全链路词汇图

```text
decisioncore(领域词汇)
   │ Effect
   ▼
port 接口(自带 execute)
   ├─ AuthPort          : AuthEffect      → AuthEvent
   ├─ ConnectionPort    : ConnectionEffect→ ConnectionEvent
   └─ BluetoothPort     : BluetoothEffect → BluetoothEvent     ← 能力端口,说蓝牙自己的话,形态一致
   │ 内部翻译(底层结果 → 领域事件,止步于端口边界)
   ▼
decisioncore(Event)
```

全库只流通一种接口形态:`execute(effect) -> Flow<event>`;全库无 Command/Result 词汇。

## 3. 是否真的有优化(诚实评估)

### 3.1 改善了什么

| 维度 | 首版后 | 本修订后 |
|---|---|---|
| 接口形态 | `CommandPort`(泛型)+ 3 个 port 接口 | 3 个 port 接口自带 execute,无公共泛型接口 |
| 执行器参数 | `CommandPort<Effect, Event>` | 函数类型 `(Effect) -> Flow<Event>` |
| 词汇 | Command/Result 残留于蓝牙域 | 全库只有 Effect/Event |
| orchestrator 依赖 | 依赖 `port.CommandPort` | 零 port 依赖(函数类型是 Kotlin 内置) |

### 3.2 代价与边界(诚实部分)

| 项 | 说明 |
|---|---|
| RootWorkflow 的 execute 必须与方法引用签名匹配 | `::execute` 依赖 `fun execute(effect: RootEffect): Flow<RootEvent>` 签名,类型不匹配时编译器立刻报错,无隐性问题 |
| 测试改动 | 3 个文件(WorkflowOrchestratorTest 的 SAM 改 lambda、ConnectionPortAdapterTest 的 FakeBluetoothPort、AndroidBluetoothPortTest 词汇替换),纯签名替换 |
| 蓝牙域翻译环节保留 | `BluetoothEvent → ConnectionEvent` 的翻译仍在 ConnectionPortAdapter 内部,翻译是**业务语义**(蓝牙连接结果归约为连接领域事件),不能省——07 首版 3.3 的论证不变 |
| 能力层词汇形态统一是否越界 | 只改名字不改语义:`BluetoothEffect.Connect(deviceId)` 与 `BluetoothCommand.Connect(deviceId)` 逐字同构,能力层"说自己的话"没有丢失 |

### 3.3 为什么这次敢收掉 CommandPort(首版不敢)

首版保留 CommandPort 的唯一实质理由是 **RootWorkflow 的 SAM 需要接口**:`CommandPort<RootEffect, RootEvent>(::execute)` 把方法引用包装成接口实例。复查发现 Kotlin 函数类型本身支持方法引用直传——`effectExecutor: (Effect) -> Flow<Event>` 直接接收 `::execute`,`fun interface` 的全部价值消失。**首版为保留一个名字付出的代价是:全库存在一个语义错位的公共接口。** 修订后接口数量不增不减(CommandPort 消失,port 接口自带 execute),代码量反而更少。

## 4. 完整代码

### 4.1 删除

| 文件 | 去向 |
|---|---|
| `port/PortContracts.kt`(CommandPort) | 删除,无替代文件 |
| `port/auth/AuthPort.kt` 的 `: CommandPort<AuthEffect, AuthEvent>` 继承 | 剥离 |
| `port/connection/ConnectionPort.kt` 的 `: CommandPort<ConnectionEffect, ConnectionEvent>` 继承 | 剥离 |

### 4.2 `WorkflowOrchestrator.kt`(改动行)

```kotlin
private val effectExecutor: (Effect) -> Flow<Event>,
// 原:private val effectExecutor: CommandPort<Effect, Event>,

// 执行副作用
effectExecutor(effect).collect(events::send)
// 原:effectExecutor.execute(effect).collect(events::send)
```

### 4.3 `RootWorkflow.kt`(改动行)

```kotlin
effectExecutor = ::execute,
// 原:private val effectExecutor = CommandPort<RootEffect, RootEvent>(::execute)
//     effectExecutor = effectExecutor,
```

### 4.4 端口接口(修改后全量)

```kotlin
// port/auth/AuthPort.kt
package com.biosensor.migratedev.port.auth

import com.biosensor.migratedev.decisioncore.auth.AuthEffect
import com.biosensor.migratedev.decisioncore.auth.AuthEvent
import kotlinx.coroutines.flow.Flow

interface AuthPort {
    fun execute(effect: AuthEffect): Flow<AuthEvent>
}
```

```kotlin
// port/connection/ConnectionPort.kt
package com.biosensor.migratedev.port.connection

import com.biosensor.migratedev.decisioncore.connection.ConnectionEffect
import com.biosensor.migratedev.decisioncore.connection.ConnectionEvent
import com.biosensor.migratedev.port.adapter.bluetoothport.BluetoothDeviceInfo
import kotlinx.coroutines.flow.Flow

interface ConnectionPort {
    fun execute(effect: ConnectionEffect): Flow<ConnectionEvent>

    fun readBinding(userId: String): Flow<BindingSnapshot>

    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
```

### 4.5 `BluetoothPort.kt`(修改后全量)

```kotlin
package com.biosensor.migratedev.port.adapter.bluetoothport

import kotlinx.coroutines.flow.Flow

data class BluetoothDeviceInfo(
    val id: String, val name: String?, val isBle: Boolean, val rssi: Int? = null
)

data class BluetoothAdvertisement(
    val isBle: Boolean, val serviceUuids: Set<String>?, val manufacturerIds: Set<Int>?
)

sealed interface BluetoothEffect {
    data class Connect(val deviceId: String, val timeoutMillis: Long = 15_000) : BluetoothEffect
    data object Disconnect : BluetoothEffect

    // TODO(Workflow 03): SendData / ObserveReceivedData / RequestMtu /
    // transfer progress / protocol errors.
}

sealed interface BluetoothEvent {
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothEvent
    data class ConnectFailed(val message: String) : BluetoothEvent
    data object ConnectTimeout : BluetoothEvent
    data object Disconnected : BluetoothEvent

    // TODO(Workflow 03): DataSent / DataReceived / MtuChanged /
    // TransferProgress / TransferFailed.
}

class BluetoothScanException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)

interface BluetoothPort {
    fun execute(effect: BluetoothEffect): Flow<BluetoothEvent>

    fun scanDevices(): Flow<BluetoothDeviceInfo>
}
```

### 4.6 `AndroidBluetoothPort.kt`(词汇替换,结构不变)

```kotlin
override fun execute(
    effect: BluetoothEffect
): Flow<BluetoothEvent> = when (effect) {
    is BluetoothEffect.Connect -> connect(effect.deviceId, effect.timeoutMillis)
    BluetoothEffect.Disconnect -> disconnect()
}
```

文件内其余为机械替换:`BluetoothResult` → `BluetoothEvent`(含 `ConnectionSession.output: SendChannel<BluetoothEvent>`、各 `trySend(BluetoothEvent.Connected/ConnectFailed/ConnectTimeout)`、`emit(BluetoothEvent.Disconnected)`),注释中 `BluetoothCommand` → `BluetoothEffect`。

### 4.7 `ConnectionPortAdapter.kt`(改动段)

```kotlin
is ConnectionEffect.ConnectDevice -> bluetooth.execute(
    BluetoothEffect.Connect(effect.deviceId)
).map { it.toEvent() }

ConnectionEffect.DisconnectDevice -> bluetooth.execute(
    BluetoothEffect.Disconnect
).map { it.toEvent() }

private fun BluetoothEvent.toEvent(): ConnectionEvent = when (this) {
    is BluetoothEvent.Connected -> ConnectionEvent.DeviceConnected(device)
    is BluetoothEvent.ConnectFailed -> ConnectionEvent.DeviceConnectFailed(message)
    BluetoothEvent.ConnectTimeout -> ConnectionEvent.DeviceConnectTimeout
    BluetoothEvent.Disconnected -> ConnectionEvent.DeviceDisconnected
}
```

> 翻译环节原样保留:`BluetoothEvent → ConnectionEvent` 的归约(连接成功/失败/超时/断开)是业务语义,位置不变。

### 4.8 Translation(改动行)

```kotlin
// AuthTranslation.kt / ConnectionTranslation.kt
effectExecutor = port::execute,    // 原 effectExecutor = port
```

### 4.9 测试改动

```kotlin
// WorkflowOrchestratorTest.kt
effectExecutor = { flowOf<TestEvent>() },              // 原 CommandPort { flowOf<TestEvent>() }
val effectExecutor: (TestEffect) -> Flow<TestEvent> = { effect -> ... }
// 原 val effectExecutor = CommandPort<TestEffect, TestEvent> { effect -> ... }

// ConnectionPortAdapterTest.kt:FakeBluetoothPort
override fun execute(effect: BluetoothEffect): Flow<BluetoothEvent> { ... }
// 原 override fun execute(command: BluetoothCommand): Flow<BluetoothResult> { ... }

// AndroidBluetoothPortTest.kt:词汇替换 BluetoothCommand→BluetoothEffect、BluetoothResult→BluetoothEvent
```

## 5. 对之前问题的解答

**Q:为什么首版说"建议保留 CommandPort 名字",现在又删了?**
首版保留的唯一理由是 SAM(方法引用 → 接口实例)。复查发现函数类型参数同样接收方法引用(`::execute`),且语法更短。保留一个名字没有换回任何东西,却让"全库无 Command 词汇"的说法不成立——删。

**Q:BluetoothPort 是能力端口,为什么也统一词汇?**
能力层**语义**(说蓝牙自己的话)与**词汇形态**(Effect/Event)分离:统一形态后 `BluetoothEffect.Connect` 仍然是蓝牙的 Connect,不是业务的 Connect。全库只有一种词汇形态,新读者无需在"Command 是哪个域、Result 是哪个域"上花时间。

**Q:函数类型会不会丢失"端口执行器"这个概念的显式性?**
不会。`port::execute` 的调用点一目了然;orchestrator 对执行器的全部需求就是"执行 effect 返回事件流",函数类型恰好是这个契约的最小表达。显式性在接口注释与实现类上,不在包装壳上。

**Q:与 06 的关系?**
06 解决**挂载与归属**(能力 vs 业务适配器),07 解决**契约词汇**(领域词汇直接通行)。本修订是 07 的收尾:词汇清零 + 接口形态统一。两者正交,不改变任何运行逻辑。

## 6. 影响面与实施顺序

### 6.1 影响面

- **生产代码**:删除 `PortContracts.kt`(1 个接口);修改 7 个文件(WorkflowOrchestrator、RootWorkflow、AuthPort、ConnectionPort、BluetoothPort、AndroidBluetoothPort、ConnectionPortAdapter)+ 2 个 translation 各 1 行。
- **测试**:改写 3 个(WorkflowOrchestratorTest、ConnectionPortAdapterTest、AndroidBluetoothPortTest),纯签名与词汇替换。
- **上游**:`decisioncore` 状态机逻辑**零改动**;`ui` 零改动。

### 6.2 实施顺序(每步可独立编译)

1. **执行器层**:`WorkflowOrchestrator` 参数改函数类型 → `RootWorkflow` 改 `::execute` → `WorkflowOrchestratorTest` 改 lambda。此时全仓可编译(port 接口还未剥离继承?不——`CommandPort` 仍存在,其余域不变)。
2. **端口层**:`AuthPort`/`ConnectionPort` 剥离继承 → translation 改 `port::execute` → 删 `PortContracts.kt`。
3. **蓝牙域**:`BluetoothPort` 词汇统一 → `AndroidBluetoothPort` 机械替换 → `ConnectionPortAdapter` 改词汇 → 两个测试同步。
4. **收尾**:全量编译 + 单元测试回归。
