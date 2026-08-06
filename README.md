# MixTheBluetooth 开发接力 README

> **⚠ 本文下方章节是旧版 `app/`（Java）模块的接力手册，已不是主战场。当前开发入口见下面"开发入口"小节。**

## 开发入口（2026-08）

**主战场：`migratedev` 模块**（Kotlin，扩展 MVVM：`UI → Translation → Orchestrator ↔ DecisionCore → Port`）。当前分支：`dev-2.1-BypassAuth`。

文档体系在 `docs/SOP/`，按此顺序进入：

1. [docs/SOP/说明/项目树.md](docs/SOP/说明/项目树.md) — 覆盖面快照（先读）
2. [docs/SOP/说明/拓扑.md](docs/SOP/说明/拓扑.md) — 分层与数据流
3. [docs/SOP/说明/词汇表.md](docs/SOP/说明/词汇表.md) — 全库统一词汇
4. [docs/SOP/Workflow/CONTENT.md](docs/SOP/Workflow/CONTENT.md) — 业务说明书（了解一个业务的最小切面）
5. [docs/SOP/架构/CONTENT.md](docs/SOP/架构/CONTENT.md) — 架构设计（过程文档，已施工的可归档删除）
6. [docs/SOP/需求/00-需求清单.md](docs/SOP/需求/00-需求清单.md) — 需求入口

工作流：**需求 → 对话 → 架构 → 施工 → 沉淀**（自动化规则见 `.claude/skills/doc-writer/`）。新代码一律写 `migratedev`；下文旧章节仅作为 `app/` 遗留参考。

---

## 0. 一句话项目

`MixTheBluetooth` 是一个 Android Java 应用：用 BLE 蓝牙连接硬件采集 CGM（连续血糖监测）数据流，上传到后端做模型推理，把预测结果（血糖曲线、统计指标）以"类 Dexcom / LibreView"的视觉风格在 App 内展示给用户。分支 `dev-1.7`（截至 2026-06-15）。

---

## 1. 物理与代码布局

```
Project/
├── MixTheBluetooth/                ← 主工程（Android Studio 项目）
│   ├── app/                        ← :app 模块
│   │   ├── build.gradle            ← 包含 applyApiEnv(debug→static-lan, release→dev)
│   │   ├── config/
│   │   │   ├── env.dev.properties        ← 真实后端
│   │   │   └── env.static-lan.properties ← 局域网固定数据后端
│   │   └── src/main/java/com/hc/mixthebluetooth/
│   │       ├── api/                ← 公共契约（不依赖 Android 实现）
│   │       ├── application/        ← 业务编排
│   │       ├── driver/             ← 驱动能力接口 + Android 实现
│   │       ├── persistence/        ← 加密 SharedPreferences
│   │       ├── runtime/            ← AppContainer / EnvConfig
│   │       └── ui/                 ← Activity/Fragment/View
│   ├── basiclibrary/, bluetoothlibrary/, guide/  ← 子模块
│   ├── docs/superpowers/
│   │   ├── plans/                  ← 按日期的实现计划（*Plan*.md）
│   │   └── specs/                  ← 对应的设计规格（*Design*.md）
│   └── tools/
│       ├── lan_static_backend.py   ← 局域网固定数据 HTTP 后端
│       └── test_lan_static_backend.py
├── BiosensorsLabAI/, CGM-AI/       ← 上游后端 / 模型训练代码
└── sql/                            ← 一些 SQL 脚本
```

**目标包结构**（重构中，2026-06-02 plan 推进中）：`api/`, `application/`, `driver/`, `persistence/`, `runtime/`, `ui/` 是最终允许存在的顶层包；`activity/`, `fragment/`, `impl/`, `local/`, `remote/`, `staticdata/`, `storage/`, `uni/`, `customView/`, `recyclerData/` 是已废弃/迁移中的旧包，新代码不要再往里写。

---

## 2. 架构边界（**重要**，写代码前先看）

`tools/check_architecture_boundaries.py` 是架构守门员。它的规则（最常用的几条）：

| 层级 | 允许依赖 | 禁止依赖 |
|---|---|---|
| `api/` | 同包 | `application/`, `driver/implementation/`, `persistence/Encrypted*`, `ui/`, `activity/`, `fragment/`, Android Activity |
| `application/` | `api/`, `driver/capability/`, `persistence/`（接口） | `ui/`, `activity/`, `fragment/`, `remote/`, `impl/`, `staticdata/` |
| `ui/` | `api/`, `application/`（通过 `AppApi` facade） | `remote/`, `impl/`, `driver/implementation/http/`, `persistence/Encrypted*`, `staticdata/` |
| `driver/implementation/` | 任意（但 UI 禁止依赖它） | — |

**给 UI 层拿服务的方式**：永远通过 `AppApi.xxx()` 拿接口，不要直接 `new`。比如：

```java
// ✅ 对
CgmWorkflow wf = AppApi.cgmWorkflow();
AuthService auth = AppApi.auth();

// ❌ 错（UI 不允许直接 import application 包）
import com.hc.mixthebluetooth.application.cgm.DefaultCgmWorkflow;
```

---

## 3. 环境与构建

### 3.1 API 环境（只有两个）

通过 `app/build.gradle` 把 `app/config/env.<name>.properties` 注入到 `BuildConfig`：

| Gradle 命令 | 注入的环境 | `API_BASE_URL` | 用途 |
|---|---|---|---|
| `./gradlew :app:assembleDebug` | `static-lan` | `http://192.168.6.7:18080/`（LAN 静态后端） | **日常调试**，用固定数据 |
| `./gradlew :app:assembleDebug -PapiEnv=dev` | `dev` | `http://192.168.0.169:8080/`（真实后端） | 接真机/真实推理时用 |
| `./gradlew :app:assembleRelease` | `dev` | 同上 | 发包 |

> 当前两个 `env.*.properties` 里 `API_BASE_URL` 还都是 `192.168.0.169:8080`，跟 plan 里 `static-lan` 期望的 `192.168.6.7:18080` 不一致 —— **改 LAN IP 时同步改两个文件**。`EnvConfig.normalizeEnv()` 只接受 `static-lan` / `dev`，其他值会抛 `IllegalArgumentException`。

### 3.2 启动 LAN 静态后端

```bash
python tools/lan_static_backend.py --host 0.0.0.0 --port 18080
python tools/test_lan_static_backend.py   # 跑测试确认后端 OK
```

### 3.3 关键命令

```bash
./gradlew :app:testDebugUnitTest                              # 跑全部 JVM 单测
./gradlew :app:testDebugUnitTest --tests "<FQN of test>"      # 单个测试类
./gradlew :app:assembleDebug                                  # 默认 static-lan
./gradlew :app:assembleDebug -PapiEnv=dev                     # dev
python tools/check_architecture_boundaries.py                 # 架构守门
python tools/test_lan_static_backend.py                       # LAN 后端烟测
```

**Logcat 抓痕**：
- `tag:BioAI.Http package:com.hc.mixthebluetooth` → HTTP 进出
- 关键字 `Controller` / `RENDER` / `CGM_REPLAY` / `CGM_UPLOAD_POLL` → 业务流
- 关键字 `AppApiBootstrap` → 启动期

---

## 4. 核心数据流（CGM 场景）

蓝牙原始字节 → UI 显示 + 业务处理 + 上传后端 → 渲染后端结果，串成一条主线：

```
BLE onReadData(byte[])
  └─→ CgmActivity.publishBtData()                          ← 把字节透传给 Fragment
        └─→ CgmController.onBtData()
              ├─→ Codec.decode(bytes) → rawText + displayText
              ├─→ MessageAdapter（消息列表渲染）
              ├─→ CgmProfile.CgmRawLineConsumer.onLine()
              │     └─→ AppApi.cgmWorkflow().onDeviceText(text, callback)
              │           └─→ DefaultCgmWorkflow
              │                 ├─→ CgmCacheSyncBuffer.acceptChunk(text)  ← 拼行
              │                 ├─→ validate()  ← 校验 Start/End marker
              │                 │     ├─ valid → writeTo(recorder) + 上传 + 轮询
              │                 │     └─ invalid → 重发 ALL
              │                 └─→ acceptDeviceLine("Log Cleared") → 删除确认
              └─→ BluetoothSampleParser.parse() → onSample()
                    └─→ MetricWidget.onSample()                       ← LINE/GAUGE/VALUE/STATS 图表
```

CGM 最终结果（`CgmResult`）回来时：

```
CgmController.onCgmResult(result)
  └─→ for each MetricWidget: widget.onCgmResult(result)
        └─→ CgmResultMetricWidget
              ├─→ CgmGlucoseChart.setData(result.units[i].points)        ← 自绘 CGM 风格曲线
              ├─→ 2x2 卡片网格：Average / Time High / Time Low / Range
              └─→ 时间范围 tabs（按 unit 切换）
```

### 4.1 关键不变量

- **CGM workflow 不是状态机**，是三个函数组：`acceptChunk` / `validate` / `writeTo` + `markDeleteSent` / `acceptDeviceLine`。`Phase` 只是守卫/调试用。新逻辑继续按这个模型加，不要把 `Phase` 升级成大枚举。
- **`markDeleteSent()` 之前**收到的 `Log Cleared` 行一律忽略 —— 没有发送 DELETE 就不能确认删除。
- **`acceptChunk` 必须能处理**（详见 `CgmCacheSyncBufferTest`）：跨 chunk 切行、无尾换行的 `Playback all done`、多 payload 行、坏 payload（要求重发）。
- **时间戳格式** `rawTime`：`yyyy-MM-dd HH:mm:ss`（19 位）或 `yyyy-MM-dd HH:mm`（16 位），`Locale.US` 解析。
- **血糖范围带**（mmol/L，硬编码在 `CgmGlucoseChart`）：`VERY_LOW 2.8` / `LOW 3.9` / `HIGH 7.8` / `VERY_HIGH 11.1`。

---

## 5. 关键文件导览

| 路径 | 作用 | 改之前要读 |
|---|---|---|
| `app/src/main/java/com/hc/mixthebluetooth/runtime/AppApiBootstrap.java` | 启动期装配 `AppApi` | 整个文件 |
| `app/src/main/java/com/hc/mixthebluetooth/runtime/EnvConfig.java` | 两种环境的契约 | — |
| `app/src/main/java/com/hc/mixthebluetooth/api/AppApi.java` | UI 拿服务的 facade | — |
| `app/src/main/java/com/hc/mixthebluetooth/api/cgm/CgmResult.java` | 后端→UI 的 CGM 结果契约 | 字段含义（参考 `CgmResult.Summary/Unit/Point`） |
| `app/src/main/java/com/hc/mixthebluetooth/application/cgm/DefaultCgmWorkflow.java` | CGM 业务流 | `CgmCacheSyncBufferTest` 列出允许行为 |
| `app/src/main/java/com/hc/mixthebluetooth/application/cgm/CgmCacheSyncBuffer.java` | 缓存拼行+校验+删除确认 | 三个函数组 |
| `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmActivity.java` | 蓝牙回调入口 | 只做事件分发 |
| `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmFragment.java` | 页面根 + Gateway | `BindingHost` / `FragmentGateway` 是关键 |
| `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmController.java` | 业务编排（MVC 中的 C） | 单文件方法多，按区块读 |
| `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmProfile.java` | 蓝牙 profile（parser/command/widget 注册） | 跟 `Profiles.cgm()` 配对看 |
| `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmWidgets.java` | **5 种 widget 工厂**（LINE/GAUGE/VALUE/STATS/CGM_RESULT） | 加新 widget 看这里 |
| `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmGlucoseChart.java` | **自绘**的 CGM 风格图表（不用 MPAndroidChart） | 调色板+`RANGE_*` 常量在顶部 |
| `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/CgmCommands.java` | 蓝牙命令字符串（`ALL\n\r` / `DELETE\n\r`） | — |
| `app/src/main/java/com/hc/mixthebluetooth/ui/cgm/StaticConstants.java` | 通道/事件 key | — |

### 5.1 加新功能的最小路径

- **加一种新指标 widget** → 改 `CgmWidgets.java`：`WidgetKind` 加枚举 + `Builder` 工厂方法 + 一个新 `private static final class XxxMetricWidget implements MetricWidget`，到 `create()` 注册。
- **加新页面** → 新建 `ui/<feature>/XxxActivity.java` + `<feature>/XxxFragment.java`，`AndroidManifest.xml` 注册。
- **加新业务能力** → `api/` 加接口 → `application/` 加实现 → `runtime/AppApiBootstrap` 注入 → `AppApi` 暴露 → UI 调 `AppApi.xxx()`。**任何时候 UI 都不能直接 `import` `application/` 或 `driver/implementation/`**。
- **加新 HTTP 端点** → `driver/implementation/http/endpoint/BioAiEndpoints.java` 加 Retrofit 方法 + DTO。

---

## 6. 命名/风格约定

- **包名**：`com.hc.mixthebluetooth.<layer>.<feature>`（例如 `api.cgm`, `ui.cgm`, `application.cgm`）。
- **Activity 命名**：`XxxActivity extends BaseActivity<...Binding>`，ViewBinding 通过 `getViewBinding()` 注入。
- **Widget/View 命名**：`XxxMetricWidget implements MetricWidget`，`view()` / `onSample()` / `reset()` 三件套；CGM 专用渲染用 `onCgmResult()`。
- **日志**：用 `ApiTraceLogger.text(OWNER, API_xxx, "tag", "details")`，`OWNER` 顶部常量，`API_xxx` 列出枚举（`BT_SEND` / `BT_RECV` / `DEVICE_CONNECT` / `RENDER` / `CGM_REPLAY` / `CGM_UPLOAD_POLL` 等）。**不要直接 `Log.d`**，否则 Logcat 抓不到。
- **DTO/契约字段**：保持后端 JSON 字段名一致（snake_case 后端 → 同步 DTO 用 `@SerializedName`），UI 层契约（`CgmResult`）一律 camelCase。

---

## 7. 已知坑 & 易错点

1. **`final` 字段的"明确赋值"陷阱**。`CgmResultMetricWidget` 里有 8 个 `private final TextView`，之前踩过坑：把字段赋值放在 helper 方法里再返回，javac 不可靠地分析"明确赋值"。**规则**：要么在构造函数体里**直接**逐个赋值，要么拆 helper 时返回多值 `Pair`（用 `android.util.Pair`，不要 `androidx.core.util.Pair`，后者需要 `androidx.core` 依赖，doc 缺失时易漏）。当前 `CgmResultMetricWidget` 已用 `android.util.Pair`。
2. **`Playback all done` 可能没有尾换行**。`CgmCacheSyncBuffer` 必须能在 chunk 边界切完最后一行。写新解析器时跟着 `CgmCacheSyncBufferTest` 跑。
3. **多 fragment 共享 `static-lan`/`dev` 时只改一个 env 文件是不够的**：要 `grep -r API_BASE_URL app/config/` 一起改。
4. **架构守门员会在 CI 拦你**。`application/` 里写了 `import ...ui.cgm...` 就跑不过 `python tools/check_architecture_boundaries.py`。
5. **不要在 `ui/cgm/` 里 `import` `com.hc.mixthebluetooth.application.*`**。同样也不要 `import ...driver.implementation.http.*` 或 `...persistence.Encrypted*`。需要服务时一律 `AppApi.xxx()`。
6. **测试要走 `MockWebServer`**，不要自己 mock 整个 `Retrofit`：`com.squareup.okhttp3:mockwebserver:4.12.0` 已经在 `testImplementation`。
7. **依赖完整性**：`androidx.security:security-crypto:1.0.0` 在 plan 2.1 里要加，**未加之前不要写 `EncryptedSharedPreferences`**。当前 build.gradle 里**还没有**这个依赖。
8. **`app/src/main/java/com/hc/mixthebluetooth/MixBluetoothApplication.java`** 是 `Application` 子类，启动时 `AppApiBootstrap.init(this)`。改了启动逻辑要 review 这个文件。

---

## 8. 当前进度（按 plan）

| Plan | 状态 | 关键改动 |
|---|---|---|
| 2026-05-04 fragment-message-pipeline | ✅ 已完成 | Fragment/消息管道统一 |
| 2026-05-08 unified-message-profile | ✅ 已完成 | profile 架构 |
| 2026-05-09 unified-message-widget | ✅ 已完成 | widget 架构（`CgmWidgets.create()` 五种 widget） |
| 2026-05-23~24 uni-runtime 第三/四/五阶段 | ✅ 已完成 | 运行时分层 |
| 2026-05-30 real-http-cgm-loop | ✅ 已完成 | 真实 HTTP CGM 回路 |
| 2026-06-01 lan-static-http-loop | ✅ 已完成 | LAN 静态后端回路 |
| 2026-06-02 architecture-boundary | 🟡 **进行中** | 11 个 Phase，目前 ~Phase 3/4。`api/` 已成型，`application/` 还在迁移 |
| 2026-06-03 cgm-cache-sync | 🟡 **下一步** | 用 `CgmCacheSyncBuffer` 替代旧 `CgmReplayCompletionDetector` |

> **下一步上手**：优先读 `docs/superpowers/plans/2026-06-02-architecture-boundary-reorganization.md`（在做的事），其次 `2026-06-03-cgm-cache-sync.md`（要做的下一件事）。每个 plan 都有同名 `*Design*.md` 规格做对照。

---

## 9. 让 Cursor 接着干活的推荐指令模板

新开一段对话时，建议把这段发给它（再加你这次的具体任务）：

```
请按顺序读：
1. /Users/kh/Library/Mobile Documents/com~apple~CloudDocs/Project/MixTheBluetooth/README.md（项目接力手册）
2. /Users/kh/Library/Mobile Documents/com~apple~CloudDocs/Project/MixTheBluetooth/docs/superpowers/plans/<最新 plan 文件>
3. 同名 Design 规格（在 docs/superpowers/specs/）

约束：
- 只在 api/、application/、driver/、persistence/、runtime/、ui/ 顶层包下写代码
- 业务能力走 api 接口 + AppApi facade，不要从 ui 直接 import application 或 driver.implementation
- 新写代码必须能通过 python tools/check_architecture_boundaries.py
- 给 CGM workflow 加新行为按"三个函数组"模型（acceptChunk/validate/writeTo + markDeleteSent/acceptDeviceLine），不要升级 Phase 成大状态机
- 改动前先确认现有测试，扩展而不是替换
- 不要去碰 .codegraph/daemon.pid 和 app/src/main/java/com/hc/mixthebluetooth/fragment/UniFragment.java（已知的脏文件，等用户明确指示再处理）
```

---

## 10. 变更记录（本次会话）

- 修 `CgmWidgets.java`：`CgmResultMetricWidget` 构造里 `final` 字段赋值的 javac 误判问题。从 helper 内赋值改成构造函数体直接逐字段赋值；helper `makeStatCard()` 返回 `android.util.Pair<TextView, TextView>`。删除无用的 `makeStatsGrid()`。
- 删了 `import androidx.core.util.Pair`（改为 `android.util.Pair`）。
- 新建本 README。

