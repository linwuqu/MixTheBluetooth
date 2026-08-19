# 故障分析 12:CGM 通信静默根因(实测验证,2026-08-07)

> 状态:**根因已确认 + 修复已实施 + 真机实测通过**(2026-08-07)。本文 §1-§3 记录"连接成功后 Cgm 无法通信、Cgm.Port 零日志"的完整证据链;§4 为修复方向,已逐条实施并在 §6 补充实测结论。修复实施后本文件退役并并入工作流 03。

## 1. 现象

用户实测,三个现象相互印证:

1. 连接蓝牙设备成功,进入 Cgm 页面,但读缓存/对时/删除均无法通信,**`Cgm.Port` tag 日志一条都没有**;
2. Cgm 页按返回键回到连接页后**死页**:显示"设备已连接"但什么都不能点(设备列表禁点、"退出登录"点了无反应);
3. 发起读缓存后**无超时自动停止**:从发现问题到 adb 调试超过 5 分钟,UI 一直卡"命令发送中"。

## 2. 实测证据(真机 logcat,2026-08-07 16:32)

手机 OPPO(IBPFJ7Q8MVTG5TZP)+ 设备 48:87:2D:C5:75:A1(自动绑定连接):

```text
16:32:18.668 AppRunAllBluetoothManage: 48:87:2D:C5:75:A1 模块连接成功   ← 蓝牙库连接成功
16:32:18.674 AppRunAllBluetoothManage: 断开BLE蓝牙                     ← 6 毫秒后!主动断开
16:32:18.674 BluetoothGatt: cancelOpen() + close() + unregisterApp()
16:32:18.694 Connection.Workflow: Connecting → Connected (DeviceConnected)   ← 业务层才知道连上
16:32:18.699 Root.Workflow: RunningConnection → RunningCgm (CgmConnectedEvent)
16:32:18.713 Root.Workflow: RunningCgm → RunningCgm (CgmStartedEvent)        ← Cgm 页面挂载
```

点击「读缓存」(16:32:34):

```text
16:32:34.090 Cgm.Read: previous=Idle event=ReadRequested new=Sending effects=[StartRead]
```

**之后没有任何后续**:无 CommandAccepted、无 DeviceDisconnected、无 CommandTimeout、无 Cgm.Port 日志、无 GATT 写数据。UI 永久卡在"命令发送中"。

## 3. 根因链(四层)

### 3.1 主因(蓝牙层):连接成功瞬间被主动断开

施工批次 1(d381279"蓝牙Port扩展")重构了 `AndroidBluetoothPort.connect`,为"统一事件通道 + 消费权转移"(架构 07 §1.4)引入独立 `Channel` 与转发 collector:

- 重构前(a4ee129):`session.output = channel`(callbackFlow 自带通道),无 collector,连接成功后 flow 保持 open——**连接生命周期 = flow 生命周期**,断开只由 Disconnect effect / 连接丢失驱动。
- 重构后(d381279):`session.output = Channel()`,加 collector,收到 `Connected` 即 `close()` 让出消费者地位给命令 flow(**这是有意设计**)。

但 **`awaitClose` 里的旧清理逻辑没有同步调整**:

```kotlin
// AndroidBluetoothPort.kt:293-307
awaitClose {
    timeout.cancel()
    collector.cancel()
    val ownsConnection = synchronized(lock) {
        if (connectionSession === session) { connectionSession = null; true } else false
    }
    if (ownsConnection) {
        runCatching { client.disconnect(deviceId) }   // ← 连接成功退出也执行!
    }
}
```

旧语义下 `awaitClose` 只在"连接真正结束"时触发,断开是合理的;新语义下 **"收到 Connected 正常让位"也触发 close → awaitClose → 断开刚建立的连接并把 `connectionSession` 置 null**。实测 6ms 的"连接成功 → 断开BLE蓝牙"正是这一段。

后续影响:`sendData`(AndroidBluetoothPort.kt:147-154)要求 `connectionSession?.takeIf { it.connected }` 存活——会话已被清空 → `ConnectFailed("未连接,无法发送")`。且主动断开不触发 `errorDisconnect` 回调,业务层(连接页)毫无感知,掩盖了问题。

### 3.2 放大(适配器层):readFlow 对"未连接"静默吞掉,状态机死挂

`CgmPortAdapter.readFlow`(CgmPortAdapter.kt:60-85)对 `ConnectFailed` 落入 `else -> Unit` 丢弃;且 sendData flow 立即结束时 `withTimeoutOrNull` 返回**非 null** → **不 emit CommandTimeout**:

```kotlin
val completed = withTimeoutOrNull(commandTimeoutMillis) { ...collect... }
if (completed == null) emit(CgmReadEvent.CommandTimeout(session.id))  // completed 非 null → 不超时
```

结果:readFlow 正常结束但**零事件**,`CgmReadDecision` 的 Sending 状态收不到任何事件 → 永久挂着,UI 卡"命令发送中",无失败提示、无超时、无日志。

### 3.3 测试盲区:单测全绿但副作用漏网

`AndroidBluetoothPortTest.kt:133-194`(`connect stops native scan before calling legacy client`):

- 断言了"收到 Connected 后 flow 结束"(第 181 行 `connection.join()`)
- **未断言 `client.disconnectedDeviceIds` 为空**——而超时测试(第 197-234 行)专门断言了 `disconnect` 被调用

测试验证了事件流行为,没验证副作用(会话保持、不主动断开),因此"连接成功即断开"测试全绿。

### 3.4 日志盲区:观测手段缺失

`CgmPortAdapter` 全类只有 `writeFileFlow` 一处日志(CgmPortAdapter.kt:149 落盘)。readFlow/shortFlow/deleteFlow 任何前置失败(未连接/超时/发送失败)都无日志——"Cgm.Port 一条日志没看见"不是异常,而是必然。

### 3.5 现象 2 根因一:导航栈未清,Cgm 页可返回到连接页

`AppMain.kt` 的 CGM 导航:

```kotlin
navController.navigate("cgm/${Uri.encode(screen.deviceId)}") {
    popUpTo(CONNECTION_ROUTE) { inclusive = false }   // ← 连接页保留在栈中
    launchSingleTop = true
}
```

`inclusive = false` 使后退栈为 `session → auth → connection → cgm`,返回键可退回连接页。文档 §10.4 当时决定"Cgm 页面退出可暂不做,返回由导航栈管"——该决定导致:返回后页面栈与 Root 状态(RunningCgm)错位,且连接页处于"已连接"假态(见 3.6)。**Cgm 页应当成为栈底页面(不可返回),或返回时触发登出/断开整理逻辑**。

### 3.6 现象 2 根因二:连接页"已连接"假态 + 全禁用

连接被 3.1 拆断是**主动断开**(`manager.disconnect`),不触发旧库 `errorDisconnect` 回调 → `ConnectionState` 永久停在 `Connected`(实测返回后 UI 仍显示"设备已连接")。连接页 UI:

- 设备列表 `canInteract = phase == Scanning || phase == Failed`(ConnectionScreen.kt:42-43)→ **Connected 态列表与刷新全部禁用**
- 唯一可点的"退出登录"→ `LogoutRequestedEvent` → Root reduce 只在 `RunningConnection` 处理,`RunningCgm` 落入 `else -> unchanged`(RootDecision.kt)→ **点了无任何反应**

页面栈回来了、状态没回来,连接页成为死页。

### 3.7 现象 3:超时机制被零事件静默绕过(3.2 的用户可见后果)

用户感知"没有超时自动停止"正是 3.2 的最终表现:readFlow 对 `ConnectFailed` 丢弃且 flow 立即结束时 `withTimeoutOrNull` 返回非 null → **不 emit CommandTimeout** → 状态机无事件可收,永久 Sending,超时/重试/停止机制全部失效。即使 3.1 修复,超时语义也要同步修(见 4.2)。

### 3.8 修复实测新发现(2026-08-07 16:50):旧库回调强转 Activity 崩溃

修复 3.1 后连接保持、指令可发,但设备回放数据瞬间崩溃:

```text
FATAL EXCEPTION: main
ClassCastException: MigrateDevApplication cannot be cast to Activity
    at AllBluetoothManage$5.readNumber(AllBluetoothManage.java:330)
    at BleBluetoothManage$1.handleMessage(BleBluetoothManage.java:150)
```

旧库 `AllBluetoothManage`/`ClassicBluetoothManage` 的多个回调 `((Activity) mContext).runOnUiThread(...)`——它假定宿主是 Activity。新宿主(架构重构后)传的是 `applicationContext`。此前连接 6ms 即断,该回调从未触发过,是**修复 3.1 暴露的存量缺陷**。修复:改为主线程 Handler(`mUiHandler.post`,与 runOnUiThread 语义等价),涉及 AllBluetoothManage.reading/readNumber、ClassicBluetoothManage.accessRate/updateUi、TaskThread。ClassicBluetoothManage 的 `startActivityForResult`(开蓝牙引导)仍要求 Activity,经典路径功能性引导,暂不动。

### 3.9 修复实测新发现(2026-08-07 16:53):回放快于写确认,确认前数据被 Sending 丢弃

16:53 实测(`Sending → Receiving(RecordsProduced)` 先于 `CommandAccepted` 到达):

```text
16:52:57.442 Cgm.Read: Sending → Sending (RecordsProduced)   ← 确认前数据
16:52:57.443 Cgm.Read: Sending → Receiving (CommandAccepted)
```

设备回放可能快于 GATT 写确认(`onDataSent` 异步)。`CgmReadDecision` 只在 `Receiving` 累积数据,`Sending` 状态收到 `RecordsProduced` 被忽略 → **缓存开头(含 START 行)丢失** → `CacheValidators.validate` 不完整 → 触发 `RetryRead` 重读 → 每次重读再丢 → 3 次后 Failed。修复:readFlow 对确认前收到的数据**缓冲**,`CommandAccepted` 后先补发缓冲再继续(数据顺序保持,状态机不变)。

## 4. 修复方向(已全部实施,逐条状态见 §6 实测结论)

1. **主因**:connect 的 `awaitClose` 只在"连接未建立"或"会话被替换"时清理——连接成功(收到 Connected)后让位不得断开、不得清空 `connectionSession`;断开仍由 Disconnect effect / 连接丢失驱动。可加一个 `session.connected` 判断:ownsConnection 清理仅当 `!session.connected`(与超时分支同款条件)。
2. **放大**:readFlow/shortFlow/deleteFlow 对 `ConnectFailed` 显式映射(如 emit `DeviceDisconnected` 或独立失败事件),且 command flow 异常结束(无任何事件)时兜底 emit `CommandTimeout`,杜绝静默死挂——这是现象 3(无超时停)的直接修复。
3. **导航栈**:CGM 导航改 `popUpTo(CONNECTION_ROUTE) { inclusive = true }`(连接页移出栈,返回键在 Cgm 页直接退出 app),或 popUpTo(SESSION_GRAPH) 清栈;同时确认返回/退出时连接与 cgm 子翻译的整理(断开、清 binding 流程按业务定)。
4. **Root 状态一致性**:`RunningCgm` 下 `LogoutRequestedEvent` 的处理(退出 → 断开 + 清理,或明确禁止该状态下登出);返回连接页的路径一旦不可达,此项可暂缓。
5. **测试**:`connect stops native scan...` 补充断言——Connected 后 `client.disconnectedDeviceIds` 为空、且 sendData 仍能走通(会话保持);新增"连接成功 → sendData 收到 DataSent/DataReceived"的链路用例;readFlow 零事件时兜底超时的用例。
6. **日志**:readFlow/shortFlow/deleteFlow 起始与终止各打一行(发指令字节数/结束原因),失败路径(ConnectFailed/Disconnected/Timeout)必须可见。
7. **旧库回调强转 Activity**(3.8):`((Activity) mContext).runOnUiThread` 改主线程 Handler,AllBluetoothManage.reading/readNumber、ClassicBluetoothManage.accessRate/updateUi、TaskThread 全部替换。
8. **回放竞态**(3.9):readFlow 确认前数据缓冲,`CommandAccepted` 后先补发缓冲再继续(顺序保持,状态机不动)。
9. **超时窗与大数据缓存**(已实施,见 §6.1):实测缓存回放超过 60s 命令超时窗 → `RetryRead` 重读;重读语义(继续收 vs 从头回放)、去重与 60s 窗口是否适配大缓存,待本轮实测结果后定。

## 5. 验证清单(修复后)

> 状态:除第 4 条(导航栈,见 §6.8 说明)外全部真机验证通过。

1. ✅ logcat:连接成功后不再出现"断开BLE蓝牙"紧跟在"模块连接成功"后
2. ✅ 点「读缓存」:`Cgm.Read` 出现 `Sending → Receiving`(CommandAccepted),设备回放数据时 `RecordsProduced` 上报
3. ✅ `Cgm.Port` 出现落盘日志,`Cgm.Read` 到 Stopped,双闸门完成
4. ⏳ Cgm 页按返回键应退出 app(栈已清),不再回到死页连接页 —— 见 §6.8 遗留说明
5. ✅ 关闭设备电源再读:应在有限次数内出现明确失败/超时提示,而非永久转圈(实测:svc bluetooth disable 场景 15s 连接超时 → ReconnectFailed → Cgm Failed 闭环,不悬挂)

## 6. 修复实施与真机实测结论(2026-08-07 晚轮)

### 6.1 空闲超时替代总时长窗(§4.9)

- **现象**:缓存回放实测 2m37s,远超 60s 命令总时长窗 → 必超时 → `RetryRead` 重读 → 重读再超时 → 3 次 Failed。这是 RetryRead 的第一层主因。
- **修复**:`readFlow/shortFlow/deleteFlow` 的 `.timeout()` 改为**空闲超时**语义——距上次事件超过 60s 无数据才判超时,数据流进行中永不超时(`Flow.timeout` 操作符自带此语义)。
- **实测**:2m37s 大缓存回放一次传对,无重传(测试 `read flow does not time out while data keeps flowing` 固化 5 块 × 30ms > 100ms 阈值的场景)。

### 6.2 空回放是合法完成(非重传)

- **现象**:缓存被 DELETE 消费后,ALL 回放 = 只有 `Start Playback`/`Playback all done` 边界 marker、零数据——此前被校验链判"不完整"→ 重读死循环。
- **修复**:判空回放为合法完成(CacheValidators 链容忍零记录边界)。
- **实测**:空回放 1.4s 完成 → 落盘 → Stopped。

### 6.3 轮数 = LOG 段数

- **疑问**:UI 点数持续递增但轮数恒 1。轮数指 `LOG:<轮数>`——协议按 EIS 段(95 点)/CA 段(268 点)分两段回放,一次读取一个 LOG 段号,点数递增而轮数不变是正常协议形态。
- **修复**:轮数公式 `accumulated.count { it is Log }`(LOG 段数),点数取累计记录数,两者解耦展示。

### 6.4 快照保留:传完会话照常关闭,UI 只读保留

- **诉求**:传完直接清会话不合理——用户传完要看结果;但信道安全关闭要求会话结束。折中:UI 保留只读快照,实际会话已关闭。
- **修复**:`Completed.records`/`Stopped.lastRecords` 携带数据快照;双闸门(DELETE ack + 落盘 FileWritten)到齐 → `Stopped(session, records)`,命令期消费者让位释放。
- **实测**:Stopped 后 UI 显示"读取完成(会话已关闭,数据保留)",点数/轮数看板保留,数据文件已落盘。

### 6.5 断线自动重连(不静默失败)

- **消费权转移发现**(本轮最大发现):连接成功后连接期消费者让位给命令期消费者,**连接域从此听不到断线事件**——实测 `svc bluetooth disable` 后仅 `Cgm.Read` 收到断线,`Connection.Workflow` 无日志。因此断线重连不能由连接域自驱动,必须 Cgm 域上报。
- **完整链路**:Cgm 命令 flow 发现断线(emit `DeviceDisconnected` + throw `SessionEnded` 立即终止)→ 状态机 `Reconnecting`(保留累积,无 effect)→ report `ConnectionLost` → Root 转发 → `connection.submit(Reconnect)` → 连接域按当前态补 `DeviceDisconnected` 信号(Connected 态)或直接 `ConnectRequested`(Failed 态)→ onTransition 检到 Connected → ConnectionFailed 自动重连(绕过 requestConnection 守卫,重连不受一次性限制)→ 连接成功 → report `Connected` → Root 转发 `DeviceReconnected` → Cgm 自动 `RetryRead` 重读 → 完成。
- **失败闭环**:重连 15s 连接超时 → `ReconnectFailed` → Cgm `Failed` + `StopRuntime(RECONNECT_FAILED)`——不悬挂。
- **实测日志链**(17:31:53.737 → 17:31:57.373):`Sending→Reconnecting` → `ConnectionFailed→Connecting` → 17:31:55.893 重连成功 → `Reconnecting→Receiving [RetryRead]` → `Completed` → `Stopped`。Root 全程 `RunningCgm`(CgmConnectedEvent 幂等,不重复挂载)。
- **根因迭代**:第一版设计为"连接域自驱动"(删掉 Reconnect intent),真机实测推翻——消费权转移后自驱动永不触发;恢复 Root 转发 + Reconnect intent 后闭环成立。

### 6.6 重读从头累积(拼接缺陷)

- **缺陷**:断线时旧累积停在段中间(如 3 条含 1 条 EIS),重读全量回放后追加拼接 → 第一段结算 1/95 失败。
- **修复**:重读 = 全量重放,`onAccepted(Failed→Receiving)` 与 `onReconnected` 均**空累积从头开始**;删除在完成后才发,旧数据由新回放覆盖,去重器兜底。

### 6.7 readFlow 断线后立即终止(SessionEnded)

- **现象**(实测 17:27:37 兜底超时日志):断线后蓝牙事件流**仍保持**(命令期消费者),readFlow 继续 collect 60s → 占用命令期消费权(新命令 flow 饿死)+ 空闲超时打兜底日志。
- **修复**:`Disconnected`/`ConnectFailed` 分支 emit 终止事件后 `throw SessionEnded`(私有 Exception)立即收尾;测试 `read flow ends immediately after disconnect while bluetooth flow stays open` 固化"蓝牙流 awaitCancellation 不结束,readFlow 仍立即结束"。

### 6.8 遗留说明

- 导航栈(§3.5/§4.3):Cgm 页返回路径暂未改(页面栈问题独立于本次静默通信链路);验证清单第 4 条待后续导航批处理。
- 文档 §3.8 的旧库 Activity 强转修复、§3.9 的回放竞态缓冲(确认前数据 pending 缓冲)均已在 16:50/16:53 实测轮完成,本文件作退役引用。
- 单测:169 个全绿(含 §6 各节的固化用例);APK 已装真机验证。
