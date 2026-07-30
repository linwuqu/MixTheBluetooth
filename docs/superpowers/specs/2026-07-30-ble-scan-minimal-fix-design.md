# BLE 扫描最小修复设计

## 目标

在不约简或重构现有蓝牙架构的前提下，让 `migratedev` 在当前 Android 14（API 34）测试设备上收到并展示 BLE 扫描结果，同时保证 20 秒扫描轮次结束后能够继续扫描。

## 已确认的问题

1. `BLUETOOTH_SCAN` 没有声明 `neverForLocation`，而 `ACCESS_FINE_LOCATION` 又只声明到 API 30。当前 Android 12+ 权限配置没有完整表达“不使用扫描结果推导位置”。
2. 迁移层向旧蓝牙库传入 `Application Context`，但旧库的扫描回调将它强制转换为 `Activity`。
3. 旧库调用扫描完成回调后才将内部状态从 `refresh` 改为 `leisure`。迁移层使用 `Dispatchers.Main.immediate` 在回调栈内立即开始下一轮，因此下一次 `bleScan()` 返回 `false`。
4. 旧库的系统 `ScanCallback.onScanFailed(errorCode)` 只显示 Toast，没有进入工作流日志；本次先保证扫描成功，不扩展日志架构。

## 方案

### 权限

应用不使用 BLE 扫描结果推导物理位置，因此：

- 给 `BLUETOOTH_SCAN` 增加 `android:usesPermissionFlags="neverForLocation"`。
- 保留 `ACCESS_FINE_LOCATION` 的 `maxSdkVersion="30"`，维持 Android 11 及以下兼容性。

### 扫描结果线程切换

旧库不再依赖 `Activity.runOnUiThread`。扫描回调通过主线程 `Handler` 投递设备结果和失败提示，使传入的 `Application Context` 合法。

### 连续扫描轮次

扫描完成时，不在 `Main.immediate` 的当前回调栈内直接调用下一轮。将重启投递到主线程消息队列，使旧库有机会先执行 `mState = leisure`，然后再调用 `beginRound`。

该修复不改变 20 秒轮次长度、不改状态机模型，也不重写旧蓝牙库。

## 测试

先添加回归测试，证明扫描完成回调发生时不会同步调用第二次 `startScan()`，而是在当前调用栈退出后重启。测试必须在生产代码修改前失败。

配置和 Android 框架 Context 行为通过以下方式验证：

1. 运行 `migratedev` 单元测试。
2. 构建并安装 debug APK。
3. 在 PJH110 上授予附近设备权限并进入连接页。
4. 检查 Logcat 是否出现 `DevicesUpdated`。
5. 检查 20 秒后是否出现下一轮 `ScanStarted`，且不再紧跟 `ScanFailed`。
6. 使用 `dumpsys bluetooth_manager` 确认 `com.biosensor.migratedev` 的扫描结果计数大于 0。

## 成功标准

- 至少一个附近 BLE 设备进入 `DevicesUpdated` 并显示在界面。
- 系统蓝牙统计中应用的 `Total number of results` 大于 0。
- 首轮 20 秒结束后能够启动下一轮，不产生由旧库忙状态导致的 `ScanFailed`。
- 应用不发生 `ClassCastException`。

## 非目标

- 不约简蓝牙端口或状态机。
- 不替换旧蓝牙库。
- 不调整广播过滤规则。
- 不修改连接、传输或协议处理逻辑。
