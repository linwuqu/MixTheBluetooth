# migratedev 贴片接入规格

日期：2026-10-06。状态：施工清单，尚未修改业务代码。

## 1. 当前代码依据

| 文件 | 当前行为 |
|---|---|
| AppGraph.kt | 挂载本地、HTTP、蓝牙和业务端口 |
| RootWorkflow.kt | CGM 子流程当前只接收 deviceId |
| DeviceBinding.sq | 保存账号与设备绑定 |
| AuthDecision.kt | 恢复本地会话后调用远端验证 |
| AuthPortAdapter.kt | 本地网络会话期限为七天 |
| CgmContracts.kt | 区分 EIS、CA、LOG、summarize |
| CgmParsePipeline.kt | 未知行被丢弃 |
| CacheValidators.kt | 按 LOG 校验段内序号与点数 |
| CgmPortAdapter.kt | 发 TIME/ALL；TXT 只保存原始记录 |
| CgmReadDecision.kt | 当前不自动删除设备缓存 |
| ScannerView.kt | 整帧解码；只取第一个结果 |
| ScanResult.kt | 提供 text 与 rawBytes |

文件位于 migratedev/src/main/kotlin/com/biosensor/migratedev 与 scannerlibrary/src/main/kotlin/com/biosensor/scanner。数据库文件位于 migratedev/src/main/sqldelight/com/biosensor/migratedev/database。

TIME 当前接受任意设备应答。读前对时超时后仍发 ALL。因此，时间匹配只能提供候选，不能证明贴片归属。

## 2. 新增职责

| 组件 | 职责 |
|---|---|
| PatchTranslation | 接收启用、换片和确认动作；输出页面状态 |
| PatchDecision | 决定能否启用、换片、结束与到期 |
| PatchPortAdapter | 校验码包；调用本地存储；返回事件 |
| MeasurementRoundAssembler | 将 EIS、CA、summarize 组为测量轮次 |
| RoundArchive | 保存原始数据、绑定快照和标注导出 |
| SyncPort | 上传本地待同步事件；处理重试与冲突 |

沿用 UI → Translation → Orchestrator ↔ DecisionCore → Port。通用扫描库不读写业务表。UI 不直接写数据库。

## 3. 页面与账号

CGM 页新增贴片卡、扫码启用、换片和新轮次确认。

扫码作为 CGM 内的子页面。返回时关闭子页面，不触发 CgmRoute 的退出 App 行为。

RootWorkflow 将 userId 与 deviceId 一起传入 CGM 和贴片流程。

首次联网登录成功时，在 TinkKvStore 保存本地采集身份。它与网络 token 分开。断网或 token 到期不阻塞本地采集。服务器明确拒绝账号后停止新激活。

## 4. 绑定与基线

扫码通过校验后，先读取旧缓存。基线保存各轮 sourceKey 与完成状态。

一枚贴片只创建一个 patchUse。同一设备只允许一个 ACTIVE 周期。

换片事务同时结束旧周期并创建新周期。失败时恢复旧状态。

首次读取的新轮次进入批量确认页。基线内的轮次不自动勾选。旧周期内的未完成轮次收到补齐内容后，仍按原轮次处理。

已有轮次恢复原 useId。换账号不转移轮次归属。业务查询按 userId 过滤。

## 5. 轮次身份

```text
sourceKey = SHA-256(
  UTF-8("CGMROUND/1" + LF + deviceId + LF + 首条 EIS LOG 原文 + LF)
)
roundId = sourceKey
pointKey = roundId + LOG段号 + 记录类型 + seq
```

完整轮次另算 rawHash。只包含本轮原始行。行尾统一为 LF；不包含 Playback 边界或 PATCH_META。

同一 sourceKey、同一 rawHash表示重复回放。

未完成轮次可补齐。完整轮次内容变化时标记 CONFLICT。保留两份接收证据，不覆盖原归属。

现有 deviceId 来源为蓝牙 MAC。本期沿用该来源。量产固件应提供稳定设备编号，并保留地址映射。

## 6. 存储与导出

使用[绑定表 SQL](./2026-10-06-贴片绑定表.sql)。转为 SQLDelight .sq 与增量迁移。保留 deviceBinding。每次数据库连接启用外键。

SqlStore 新增查询对象和事务方法。SqliteStore 使用原 LocalDatabase，不建立第二份数据库。

归档顺序固定为：

1. 保存 RECEIVED/cgm/{sessionId}.txt。
2. 在事务中保存轮次、使用周期快照和 syncOutbox。
3. 生成 OUTGOING/cgm/{sessionId}.annotated.txt。
4. 导出成功后将 archiveState 改为 READY。

导出失败时保留 EXPORT_PENDING。App 用原始文件和数据库重建导出。

FileStore 增加原子写入：先写临时文件，再在同一文件分区替换目标文件。

CgmContracts 新增 PatchUseSnapshot、PatchMeta 和轮次结构。RecordParser 识别 PATCH_META。结构校验跳过它，不增加 EIS / CA 点数。

启用、换片、读缓存和删除操作互斥。业务成功必须等待相应持久化事件，不能由 UI 回调提前宣布。

## 7. PATCH_META

每个已结束轮次，在 summarize 后紧接一条单行 UTF-8 JSON。

| 字段 | 用途 |
|---|---|
| v | 导出协议版本 |
| roundId | 测量轮次 |
| useId | 贴片使用周期 |
| deviceId | 数据来源设备 |
| patch | 编号、型号、批次、日期、使用时长和校准引用的快照 |
| activatedAt | 启用时间 |
| bindingStatus | CONFIRMED / UNBOUND |
| validity | VALID / EXPIRED / UNKNOWN |
| bindingMethod | USER_CONFIRMED / FIRMWARE_REF |

未关联轮次 useId 与 patch 为 null。已关联但超期的轮次保留归属，validity 为 EXPIRED。

缺 summarize 的尾轮不补造元数据收尾。summarize 原值保持不变。

外部导入时重算轮次身份。外部 PATCH_META 不能直接改写本地使用历史。

完整格式见[导出示例](./2026-10-06-PATCH_META格式示例.txt)。它取自 07-21 第一份回放。演示设备、贴片和寿命均标记为 exampleOnly。

## 8. 本期扫码配置

```kotlin
ScannerConfig(
    formats = setOf(BarcodeFormat.DATA_MATRIX),
    confirmationFrames = 2,
    duplicateCooldownMillis = 1_500L,
    maxSymbolsPerFrame = 1,
    targetResolution = Size(1920, 1080),
)
```

migratedev 引入 scannerlibrary，并声明 CAMERA 权限。页面处理运行时授权。

进入确认页后 stop()。离开页面后忽略旧回调。永久销毁视图时 release()。

当前框仅为绘图。本期要求画面只有一码。多码场景上线前增加位置筛选。1500 ms 冷却不能替代数据库查重。

## 9. 量产固件接入

固件增加绑定、查询当前周期和返回轮次引用的能力。

App 先持久化激活请求，再发送 useId。设备 ACK 必须返回已保存的 useId。ACK 后提交本地启用事务。

超时或重启后，查询设备当前 useId，恢复未完成的激活请求。

```text
summarize:<原内容>
PATCH_REF:<measurementId>,<useId>
```

measurementId 在采集时生成，重复回放不变。设备重启保留 useId。

App 用 PATCH_REF 查 patchUse，并生成 PATCH_META。跨手机接管使用周期时，必须恢复相同 useId 和贴片快照。

固件的一次性激活记录与服务端唯一约束共同处理跨机复用。脱机数据库仅保证本机去重。

## 10. 已执行检查

SQL 参考结构通过内存 SQLite 检查：

- 保留旧设备表。
- 拒绝同片复用和设备同时启用两片。
- 拒绝重复轮次和不存在的使用周期。
- 拒绝账号或设备不匹配。
- 禁止修改使用周期身份。
- 换片失败后保留旧 ACTIVE 周期。

示例保留原始 464 行，只在唯一 summarize 后新增一行。未完成尾轮保持原样。

这些结果只验证方案附件。App 接入、固件 ACK 和实物扫码仍需执行验收。
