# Uni 第五阶段：验证闭环、环境分层与可诊断运行时设计

日期：2026-05-24

## 1. 背景

第四阶段已经开始把 Uni Runtime 从纯本地蓝牙交互推进到服务端交互：

- 账号注册、登录、token/session 保存。
- `mock/dev/prod` 环境配置。
- Retrofit API 调用。
- CGM 设备缓存回放原始 txt 保存。
- `CGM_Cache_data.txt` 文件上传。

但第四阶段暴露出一个更底层的问题：**能力被写出来了，但真实性和可验证性不足**。

具体表现为：

- 注册 Activity 存在，但登录仍然沿用本地 `admin/1`、`normal/1` 逻辑，账号 API 没有和真实入口合流。
- mock API 能返回数据，但使用者不容易判断 UI 是否真的走到了 mock/Retrofit 路由。
- CGM txt 输出依赖真实蓝牙设备，没有板子时很难确认 recorder、文件内容、上传链路是否正确。
- API 调试缺少统一入口和统一证据，单靠 Logcat 不足以证明 multipart、session、baseUrl、mock/dev/prod 行为正确。

第五阶段不新增大业务，而是补齐第四阶段的验证体系。目标不是“多写 mock”，而是让已有能力形成一个可证明、可调试、可回放的闭环。

## 2. 阶段目标

第五阶段主线是：

```text
账号/文件/CGM 能力
  ↓
统一 Repository / UseCase 入口
  ↓
mock/dev/prod 只替换底层 adapter
  ↓
自动测试验证业务逻辑和 HTTP 请求
  ↓
Diagnostics 页面验证运行时真实路径
  ↓
无板子时通过 CGM fixture replay 验证 txt 与上传
```

本阶段成功后，需要能够回答以下问题：

- 登录页是否真的调用了账号 API，而不是旧本地账号逻辑？
- 当前安装包到底是 `mock`、`dev` 还是 `prod`？
- 注册、登录、详情接口是否保存了 session/token？
- Retrofit 是否真的打到了指定 path？
- 文件上传是否真的是 multipart，字段和文件内容是否正确？
- 没有 CGM 板子时，能否生成一份可查看、可上传、可测试的 `CGM_Cache_data.txt`？
- debug 页面展示的结果是否和单元测试、HTTP 测试使用同一套业务入口？

## 3. 非目标

第五阶段不做以下事情：

- 不美化注册页面。UI 美化后续可以独立处理。
- 不引入完整依赖注入框架，例如 Hilt。当前项目先使用轻量 ServiceLocator/Factory 即可。
- 不把 Diagnostics 做成生产功能。它只用于 debug/mock/dev 验证。
- 不在 Android 本地计算 CGM 图表结果。Android 仍然只上传设备缓存原始文件，图表数据后续消费 server API。
- 不复刻 Swagger UI。Swagger/OpenAPI 应由后端提供，Android 端只提供运行时诊断面板。

## 4. 设计原则

### 4.1 业务只写一次

登录页、注册页、调试页都必须走同一套业务入口。

不允许出现：

```text
LoginActivity 自己写一套登录
RegisterActivity 自己写一套注册
DiagnosticsActivity 自己绕过业务层直接调 MockApi
```

正确方向是：

```text
LoginActivity
RegisterActivity
DiagnosticsActivity
  ↓
AuthRepository
  ↓
AuthApi
  ↓
MockAuthApi 或 RetrofitAuthApi
```

这样 Diagnostics 页面点击一次登录，就等价于真实登录页走的业务逻辑。

### 4.2 mock 只在底层替换

mock/dev/prod 的差异不应该散落在 Activity、Controller、Profile 中。

差异边界应收敛在：

```text
ApiClient / ServiceFactory
DeviceGatewayFactory
FixtureProvider
```

上层只依赖稳定接口：

```text
AuthApi
FileApi
DeviceGateway
```

### 4.3 证据优先

“代码存在”不是完成标准。每个关键链路都要有至少一种可重复证据：

- 单元测试证明业务状态变化。
- MockWebServer 测试证明 HTTP path/body/multipart。
- fixture replay 证明无设备时 txt 行为。
- Diagnostics 页面证明运行时实际环境、session、请求结果。

### 4.4 克制分层

Repository/UseCase 只在有明确收益时引入。

- Repository：用于给 UI 提供稳定业务入口，并隐藏数据来源。
- UseCase：用于承载多个步骤组成的动作，例如文件上传、CGM replay 后上传。
- 单纯工具函数不应为了“架构感”强行变成 UseCase。

## 5. 核心概念

### 5.1 Product Flavor 与当前 env 配置

Android 更标准的环境隔离方式是 `productFlavors`：

```text
mockDebug
devDebug
prodRelease
```

每个 flavor 可以注入不同配置：

```text
API_ENV
API_BASE_URL
USE_MOCK_API
```

推荐最终形态：

```text
mock:
  USE_MOCK_API = true
  API_BASE_URL = http://mock.local/

dev:
  USE_MOCK_API = false
  API_BASE_URL = http://10.0.2.2:8080/

prod:
  USE_MOCK_API = false
  API_BASE_URL = https://api.example.com/
```

但考虑到当前项目仍在分层调整中，第五阶段可以先保留第四阶段的 `-PapiEnv=mock/dev/prod`，并将其视为 flavor 化前的过渡方案。

后续如果环境配置稳定，再把它迁移成：

```groovy
productFlavors {
    mock { ... }
    dev { ... }
    prod { ... }
}
```

### 5.2 Repository

Repository 是 UI 层面对业务数据的统一入口。

以账号为例：

```text
AuthRepository
  register(phone, username, password)
  login(phone, password)
  detail()
  currentSession()
  clearSession()
```

它内部负责：

- 调用 `AuthApi`。
- 解析 `JsonData<AccountInfo>`。
- 保存 `AuthSessionStore`。
- 屏蔽 token 是否存在、token 是否 JWT 等服务端细节。

UI 不应该直接知道 `MockApi`、`Retrofit`、`SharedPreferences`。

### 5.3 UseCase

UseCase 用于描述一个具体业务动作，尤其是包含多个步骤的动作。

适合成为 UseCase 的例子：

```text
FileUploadUseCase
  检查文件
  构造 multipart
  调 FileApi
  返回上传结果

CgmReplayUploadUseCase
  从 fixture 或设备 replay 输入生成 CGM_Cache_data.txt
  校验文件
  调 FileUploadUseCase
```

不适合成为 UseCase 的例子：

```text
setText()
decode()
dp()
单纯调用一个 API 且没有业务状态变化的薄包装
```

### 5.4 Fixture

Fixture 是固定测试样本，不是随处散落的 mock 逻辑。

CGM 需要一份设备回放样本：

```text
cgm_playback_sample.txt
```

它用于三类场景：

```text
单元测试：
  feed sample lines -> recorder -> assert CGM_Cache_data.txt

Diagnostics 页面：
  点击 Replay Sample -> 生成真实 txt -> 显示内容和路径

上传验证：
  使用 sample 生成的 txt -> FileUploadUseCase -> MockApi/MockWebServer/dev server
```

同一份 sample 同时服务自动测试和人工验证，避免为了 mock 重写多套逻辑。

## 6. 推荐文件结构

第五阶段不追求大规模重构，只补关键边界。

推荐结构：

```text
app/src/main/java/com/hc/mixthebluetooth/
├── api/
│   ├── ApiClient.java
│   ├── ApiEnvironment.java
│   ├── ApiModels.java
│   ├── AuthApi.java
│   ├── FileApi.java
│   └── MockApi.java
│
├── auth/
│   ├── AuthRepository.java
│   └── AuthSessionStore.java
│
├── cgm/
│   ├── CgmReplayFixture.java
│   ├── CgmReplayUploadUseCase.java
│   └── CgmSampleAssets.java
│
├── debug/
│   └── DiagnosticsActivity.java
│
├── uni/
│   └── profile/cgm/
│       ├── CgmPlaybackRecorder.java
│       └── CgmProfile.java
│
└── activity/
    ├── LoginActivity.java
    └── AccountRegisterActivity.java
```

说明：

- `api/` 保留 Retrofit、接口定义、mock API。
- `auth/` 放账号业务入口和 session 保存。`AuthSessionStore` 可以从 `api/` 迁入这里。
- `cgm/` 放非 UI、非 uni.Controller 的 CGM 验证/上传流程。
- `debug/` 放诊断页面，避免调试能力混在正式 Activity 中。
- `uni/profile/cgm/` 仍然保留设备 profile 和 recorder，保证 Uni Runtime 不依赖 Retrofit。

如果实际代码迁移成本较高，可以先保留部分文件在现有位置，但设计边界应按上述结构收敛。

## 7. 登录注册合流设计

当前旧登录逻辑：

```text
admin / 1
normal / 1
```

只能作为本地调试 fallback，不应继续作为主登录链路。

第五阶段目标链路：

```text
LoginActivity
  ↓
AuthRepository.login(phone, password)
  ↓
AuthApi.login()
  ↓
AuthSessionStore.save(accountInfo)
  ↓
MainActivity
```

注册链路：

```text
AccountRegisterActivity
  ↓
AuthRepository.register(phone, username, password)
  ↓
AuthApi.register()
  ↓
AuthSessionStore.save(accountInfo)
  ↓
返回登录页或直接进入 MainActivity
```

旧本地账号可以临时保留，但必须显式命名并隔离，例如：

```text
LocalDebugLogin
```

不能让使用者误以为 `admin/1` 代表 API 登录成功。

## 8. CGM 无板子验证设计

CGM 回放验证要覆盖三件事：

1. 设备输入如何进入 recorder。
2. recorder 如何生成 `CGM_Cache_data.txt`。
3. 生成的 txt 如何上传。

推荐输入样本：

```text
Start Playback
EIS:1,1000,0.12
CA:1,0.08
CA:2,0.09
Playback all done
```

注意：

- `Start Playback` 和 `Playback all done` 是设备返回的边界标记。
- Android 不主动发送这两个字符串。
- Android 主动发送的是 legacy CMD，例如 `ALL\n\r`。
- sample 只是模拟设备回包，不改变真实协议。

测试链路：

```text
CgmReplayFixture.read()
  ↓
CgmPlaybackRecorder.onLine(line)
  ↓
CGM_Cache_data.txt
  ↓
FileUploadUseCase.uploadRootFile(file)
```

Diagnostics 页面也走这条链路，而不是手写一份临时文件绕过 recorder。

## 9. Diagnostics 页面设计

Diagnostics 页面是 debug-only 的运行时证据面板。

它应该展示：

```text
环境：
  API_ENV
  API_BASE_URL
  USE_MOCK_API

Session：
  accountId
  phone
  token 是否存在

Auth：
  register
  login
  detail
  clear session

CGM：
  replay sample
  查看生成 txt 路径
  查看 txt 内容摘要
  上传 txt

Network：
  最近一次 request path
  最近一次 response code/msg
```

Diagnostics 不能绕过正式业务层：

```text
DiagnosticsActivity
  ↓
AuthRepository / CgmReplayUploadUseCase
  ↓
AuthApi / FileApi / CgmPlaybackRecorder
```

这样它才是“探针”，不是另一套平行实现。

## 10. 测试策略

### 10.1 AuthRepository 单元测试

测试目标：

- register 成功后保存 session。
- login 成功后保存 session。
- 没有 token 时仍保存 accountId/phone。
- clearSession 后 session 清空。

依赖：

```text
FakeAuthApi
MemorySessionStore
```

### 10.2 Retrofit HTTP 测试

使用 `MockWebServer` 验证真实 Retrofit 请求。

测试目标：

- `login()` 请求 path 是 `/api/account/v1/login`。
- `register()` 请求 path 是 `/api/account/v1/register`。
- 请求 body 包含正确 phone/password/username。
- 上传请求是 multipart。
- multipart field 包含 `fileName`、`identify`、`parentId`、`fileSize`。
- multipart file part 名称为 `file`。
- 文件内容和本地 `CGM_Cache_data.txt` 一致。

### 10.3 CgmPlaybackRecorder 测试

测试目标：

- 收到 `Start Playback` 后开始记录。
- `readCache == true` 时逐行写入。
- 收到 `Playback all done` 后停止记录并返回文件。
- 输出文件内容和 legacy 格式逐字节一致。

### 10.4 Fixture Replay 测试

测试目标：

- 从 sample fixture 读取行。
- 喂给 recorder。
- 生成 `CGM_Cache_data.txt`。
- 文件非空且内容可预测。

### 10.5 Diagnostics 冒烟测试

不要求完整 UI 自动化，但至少编译验证：

```text
mockDebug:
  Diagnostics 可以打开
  mock register/login/upload 不访问网络

devDebug:
  Diagnostics 显示 dev baseUrl
  请求交给 Retrofit
```

## 11. 环境矩阵

| 环境 | API 来源 | 蓝牙来源 | CGM txt 来源 | 目标 |
| --- | --- | --- | --- | --- |
| mock | MockApi | Fixture/Replay | Fixture 生成 | 本地无网可验证 |
| dev | Retrofit dev server | 真实设备或 Fixture | 真实设备或 Fixture | 联调后端 |
| prod | Retrofit prod server | 真实设备 | 真实设备 | 正式使用 |

mock 环境不应该访问网络。

dev 环境允许没有板子，通过 fixture 验证上传链路。

prod 环境不暴露 Diagnostics 入口，也不打包测试 fixture。

## 12. 渐进实施顺序

### 12.1 先收敛账号入口

- 新增 `AuthRepository`。
- `LoginActivity` 改为调用 `AuthRepository.login()`。
- `AccountRegisterActivity` 改为调用 `AuthRepository.register()`。
- 旧 `admin/1` 登录改名为本地调试 fallback。

验证：

- `AuthRepositoryTest` 通过。
- mock 环境登录后 session 可见。

### 12.2 再补 CGM fixture replay

- 新增 `cgm_playback_sample.txt`。
- 新增 fixture reader。
- Diagnostics 支持 replay sample。
- replay 必须走 `CgmPlaybackRecorder`。

验证：

- sample replay 生成 `CGM_Cache_data.txt`。
- 文件内容可查看、可断言。

### 12.3 再补 HTTP 真实性测试

- 引入 `MockWebServer`。
- 覆盖 auth path/body。
- 覆盖 upload multipart。

验证：

- Retrofit 真实发请求到 MockWebServer。
- 测试断言请求细节。

### 12.4 最后整理 Diagnostics

- Diagnostics 显示环境、session、最近请求结果。
- Diagnostics 调用正式 Repository/UseCase。
- prod 不暴露 Diagnostics 入口。

验证：

- mock/dev 构建可打开。
- prod 构建不从 UI 暴露入口。

## 13. 成功标准

第五阶段完成后，应满足：

- 登录、注册、调试页不再各自实现账号逻辑，而是统一走 `AuthRepository`。
- mock/dev/prod 差异集中在底层 factory/client，不污染 UI 和 Uni Controller。
- 没有 CGM 板子时，仍可通过 fixture 生成真实 `CGM_Cache_data.txt`。
- `CGM_Cache_data.txt` 可以在 Diagnostics 中查看路径、内容摘要，并上传。
- Retrofit 请求 path/body/multipart 有 MockWebServer 测试证明。
- session/token 保存逻辑有单元测试证明。
- Diagnostics 页面只是探针，不是平行业务实现。

## 14. 风险与取舍

### 14.1 不立即引入 Hilt

Hilt 能让依赖注入更标准，但当前项目还在从 legacy Activity/Fragment 迁移中。过早引入 Hilt 会扩大改动面。

第五阶段先使用轻量 Factory/ServiceLocator。等 Repository/UseCase 边界稳定后，再考虑 Hilt。

### 14.2 不立即强制 productFlavors

`productFlavors` 是更 Android-native 的方向，但会影响构建矩阵、资源目录和 CI 命令。

第五阶段可以继续使用 `-PapiEnv=mock/dev/prod`，但 spec 中明确 flavor 是后续升级目标。

### 14.3 Diagnostics 不等于 Swagger

后端接口文档和 OpenAPI 应由 server 提供。Android Diagnostics 只证明客户端运行时实际走了哪条链路。

二者互补，不互相替代。

## 15. 结论

第五阶段不是继续堆功能，而是给第四阶段补上工程可信度。

核心变化是：

```text
从“写了 API 和 recorder”
变成“API、recorder、upload 都能被自动测试和人工诊断证明”
```

这会让后续接真实后端、真实板子、图表 API 时更稳：每一层都能单独验证，出问题时也能定位是 UI、Repository、Retrofit、设备回放、txt 生成，还是服务端协议不一致。
