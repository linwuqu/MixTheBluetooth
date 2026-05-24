# Uni 第四阶段：账号 API、环境配置与 CGM 缓存回放上传设计

日期：2026-05-23

## 1. 背景

第三阶段已经把 Uni 运行时收敛为 `UniFragment + uni.Controller + profile/widgets/codec/commands/output` 的结构：

- Fragment 只负责 ViewBinding、生命周期和蓝牙 gateway。
- Controller 作为 Uni 运行时骨架，持有 context、profile、hostView、gateway。
- Profile 负责声明设备 spec、parser、actions、widgets 和 record formatter。
- `Commands` 开始承接设备语义命令和真实指令字符串之间的边界。
- `Output` 承接本地记录，但尚未接入服务端 API。

第四阶段不再是单纯重构，而是把本地设备记录和后端账号/文件系统接起来。核心目标是：在不破坏第三阶段分层的前提下，新增账号注册、环境切换、Retrofit API、mock 测试通道，以及 CGM 设备缓存回放原始文件上传能力。

本阶段对 CGM 数据流的定位需要明确：

- Android 负责通过蓝牙向设备发送 legacy CMD。
- Android 负责接收设备缓存回放原始数据，并保存为与 master 兼容的 `CGM_Cache_data.txt` 文件。
- Android 负责把该原始文件上传给 server。
- server 负责解析原始文件、计算数值数据、生成图表需要的数据。
- Android 图表后续应消费 server API 返回的结构化数据，而不是在本地复刻 master 的 `CA:266` 结果计算逻辑。

因此，第四阶段上传目标只有设备缓存回放文件，不再实现旧 `的CGM_data.txt` 结果文件作为上传协议。

## 2. 总体主线

第四阶段主线定义为：

```text
账号注册/登录
  ↓
保存透明 token/session
  ↓
CGM profile 发送读取缓存命令 ALL\n\r
  ↓
设备返回 Start Playback / raw data / Playback all done
  ↓
Android 保存 CGM_Cache_data.txt
  ↓
通过 Retrofit 上传原始缓存文件
  ↓
server 计算并提供图表数据 API
  ↓
Android 消费 API 数据绘图
```

其中 4A 和 4B 分开设计：

- 4A：API 基础设施、环境配置、mock、注册 Activity、session/token 保存。
- 4B：蓝牙 CMD 等价校验、CGM 缓存回放文件保存、文件上传接入。

这样拆分的原因是：4A 是后端通信基础设施；4B 与设备协议强相关，必须先保证 `ALL\n\r`、`DELETE\n\r`、`TIME,...\n\r` 的发送行为和 master 等价，再接上传。

## 3. 已核实事实

### 3.1 账号 token

接口文档只说明响应格式为：

```json
{
  "code": 0,
  "data": {},
  "msg": "",
  "success": true
}
```

但没有明确 `data` 内是否包含 token，也没有说明 token 是 JWT、session id 还是其他格式。

客户端约定：

- 如果 register/login 响应中存在 token，则保存 token。
- 如果暂时没有 token，则保存 phone、accountId、username 等可用字段。
- token 对客户端是透明字符串，Android 不解析、不验证、不假设其格式。
- 后续请求只负责把 token 以约定 header 或 cookie 方式带回服务端。

是否使用 JWT、Redis session、数据库 session，由服务端决定，客户端不耦合。

### 3.2 蓝牙发送链路

master 旧链路：

```text
FragmentMessage.setSendData()
  -> sendData(new FragmentMessageItem(...))
  -> sendDataToActivity(StaticConstants.DATA_TO_MODULE, item)
  -> CommunicationActivity.update()
  -> mHoldBluetooth.sendData(item.getModule(), item.getByteData().clone())
```

第三阶段新链路：

```text
Controller.handleAction()
  -> gateway.postText(module, text)
  -> sendDataToActivity(StaticConstants.CMD_BT_POST, new BTPackage.BTPost(module, bytes))
  -> CommunicationActivity.onBtPostCommand()
  -> mHoldBluetooth.sendData(post.module, post.bytes.clone())
```

两条链路最终都调用：

```java
mHoldBluetooth.sendData(module, bytes.clone());
```

因此 `CMD_BT_POST` 信道本身可以真实控制蓝牙设备，终点与 master 等价。

但第三阶段仍有一个需要修正的细节：master 的 byte 生成使用：

```java
Analysis.getBytes(data, mFragmentParameter.getCodeFormat(getContext()), isSendHex)
```

而当前 `UniFragment.FragmentGateway` 使用 UTF-8：

```java
text.getBytes(StandardCharsets.UTF_8)
```

对于 `ALL\n\r`、`DELETE\n\r`、`TIME,...\n\r` 这类纯 ASCII 命令，两者字节结果相同；但为了保证“重构不改变协议”，第四阶段开始前应让 `UniFragment` 调用 `uni.Codec.encodeText(...)`，再由 `Codec` 统一复用 `Analysis.getBytes(..., FragmentParameter.getCodeFormat(...), false)`。

### 3.3 Legacy CGM 命令

master 血糖页面三个按钮对应的命令是：

```java
data = "ALL" + "\n\r";
data = "DELETE" + "\n\r";
data = "TIME," + formattedTime + "\n\r";
```

其中时间格式为：

```text
yyyy,MM,dd,HH,mm,ss
```

换行顺序是：

```text
\n\r
```

不是常规的 `\r\n`。第四阶段必须保留这个顺序，因为这属于设备协议行为，不能因为代码整洁而修改。

第三阶段的 `Commands.LegacyCgm` 已经把这三个命令集中起来。第四阶段需要做的是把它接入 profile action，并写测试锁住输出字节。

### 3.4 CGM 缓存回放文件

master 的缓存回放由 `getBufferData()` 触发：

```java
private void getBufferData() {
    data = "ALL" + "\n\r";
    setSendData();
}
```

Android 发送 `ALL\n\r` 后，设备固件开始返回缓存回放数据。旧代码通过设备返回的文本标记控制写文件状态：

```text
收到包含 "Start Playback" 的 dataString -> readCache = true
readCache == true 时，每次直接写入 dataString
收到包含 "Playback all done" 的 dataString -> readCache = false
```

这里的 `Start Playback` 和 `Playback all done` 不是 Android 侧 service，也不是 app 主动调用的方法。它们是设备端返回的数据标记。Android 只负责识别这些标记，并在 `readCache == true` 期间保存设备返回的原始文本。

缓存回放文件名：

```text
yyyy-MM-ddCGM_Cache_data.txt
```

写入方式：

```java
Analysis.IO_input_data(dataString, path, "CGM_Cache_data.txt");
```

`IO_input_data()` 会追加写入 `dataString`，然后额外写入一个 `\n`。

第四阶段必须复刻的是这个缓存回放文件，因为它是上传给 server 的实际原始数据。

### 3.5 旧 CGM 监测结果文件不进入第四阶段

master 里还有另一类结果文件：

```text
yyyy-MM-dd的CGM_data.txt
```

它由 `EIS`、`CA`、`CA:266` 等设备数据行驱动，并在 Android 本地做一部分结果整理：

- `EIS` 行：把冒号后的 payload 加入 `dataInIOList`。
- `CA` 行：用于旧页面本地图表数据。
- `CA:266` 行：旧代码认为一次 30s 检测结束，追加当前时间和当前值，并写入结果文件。

这一套逻辑属于旧页面的本地计算和本地图表实现。第四阶段不复刻它作为上传协议，因为当前目标已经调整为：

```text
Android 上传设备缓存回放原始文件
server 计算数值数据
Android 从 API 获取图表数据并绘图
```

因此，第四阶段只保证 `CGM_Cache_data.txt` 与 master 行为兼容。旧 `的CGM_data.txt` 不作为新 UniRuntime 上传链路的一部分。

## 4. 设计原则

第四阶段遵循以下原则：

1. Retrofit/API 不进入 `uni.Controller`。
2. `uni.Output` 或 CGM recorder 负责本地文件，不直接认识 Retrofit。
3. 上传文件只使用设备缓存回放原始 txt，即 `CGM_Cache_data.txt`。
4. 图表数据来自 server API 返回的结构化结果，Android 不在第四阶段复刻 `CA:266` 本地计算。
5. token 是透明凭证，客户端只保存和携带，不解析。
6. mock/dev/prod 环境必须可以切换，mock 不依赖真实服务端。
7. 蓝牙 CMD 行为必须与 master 等价，尤其是字节生成、时间格式和 `\n\r`。
8. `uni.Codec` 是 UniRuntime 唯一编解码入口；新代码不直接散用 `Analysis.getBytes()` 或 `Analysis.getByteToString()`。
9. 不过度拆分。只有独立演化、独立测试、独立复用的模块才新建文件。

## 5. 非目标

第四阶段不做以下事情：

- 不实现无感设备发现和自动绑定 profile。
- 不重写所有旧 Fragment。
- 不把所有设备协议抽象成完整协议框架。
- 不把 CMD 设计成所有设备共享的固定字符串集合。
- 不在 Android 端解析 JWT 或实现 token 验证。
- 不实现完整网盘文件管理 UI。
- 不一次性实现文件夹树、重命名、头像上传等所有接口。
- 不让 UniRuntime 直接依赖 Retrofit。
- 不在 UniRuntime 新链路中复刻 `的CGM_data.txt` 结果文件。
- 不在第四阶段实现本地 CGM 数值计算和本地图表算法。

无感设备发现、设备能力识别、自动 profile 绑定可以作为第五阶段或后续协议阶段。

## 6. 目标文件树

第四阶段目标结构：

```text
app/src/main/java/com/hc/mixthebluetooth/
├── api/
│   ├── ApiClient.java
│   ├── ApiEnvironment.java
│   ├── ApiModels.java
│   ├── AuthApi.java
│   ├── FileApi.java
│   ├── AuthSessionStore.java
│   └── MockApi.java
│
├── activity/
│   └── AccountRegisterActivity.java
│
└── uni/
    ├── Codec.java
    ├── Commands.java
    ├── Output.java
    └── profile/
        ├── eis/
        │   └── EisProfile.java
        └── cgm/
            ├── CgmProfile.java
            └── CgmPlaybackRecorder.java
```

说明：

- `api/` 承接 Retrofit、接口模型、环境配置、session 和 mock。
- `activity/AccountRegisterActivity.java` 承接注册页面。
- `uni.Codec` 是 UniRuntime 编解码唯一入口，内部兼容复用旧 `Analysis`。
- `uni/profile/cgm/CgmProfile.java` 声明 CGM 设备动作。
- `uni/profile/cgm/CgmPlaybackRecorder.java` 承接 `Start Playback` 到 `Playback all done` 之间的原始缓存文件写入。
- `Output` 可继续服务 EIS 等本地记录场景，但 CGM 上传链路以 `CgmPlaybackRecorder` 的原始缓存文件为准。

## 7. 4A：API 与注册设计

### 7.1 ApiEnvironment

环境配置建议放在：

```text
app/config/
├── env.mock.properties
├── env.dev.properties
└── env.prod.properties
```

示例：

```properties
API_ENV=dev
API_BASE_URL=http://10.0.2.2:8080/
USE_MOCK_API=false
```

说明：

- 接口文档 HOST 是 `http://127.0.0.1:8080`。
- Android 模拟器访问宿主机通常使用 `http://10.0.2.2:8080/`。
- 真机测试需要局域网 IP 或公网 dev server。
- prod 环境使用正式域名。

Gradle 在构建时读取当前 env，并生成：

```java
BuildConfig.API_ENV
BuildConfig.API_BASE_URL
BuildConfig.USE_MOCK_API
```

第一版可以使用一个 active env 配置，不强制引入复杂 product flavors。若后续需要并行安装 mock/dev/prod，再升级 flavors。

### 7.2 ApiClient

`ApiClient` 负责：

- 创建 Retrofit。
- 设置 baseUrl。
- 提供 JSON converter。
- 注入 token header 或 cookie。
- 根据 `USE_MOCK_API` 返回 mock 或真实 API。

推荐入口：

```java
ApiClient client = ApiClient.get(context);
client.authApi();
client.fileApi();
```

这里保持简单，不引入复杂依赖注入框架。

### 7.3 ApiModels

`ApiModels.java` 集中放第一阶段需要的接口模型：

```text
JsonData<T>
AccountRegisterReq
AccountLoginReq
AccountInfo
FileUploadResp
```

因为第一版模型数量少，集中到一个文件更易读。后续模型变多再按领域拆分。

`JsonData<T>` 对应接口统一响应：

```text
code
data
msg
success
```

后续图表 API 返回的服务端计算结果也应先落成 Java model，再交给 widget 或 chart 消费。Android 不直接消费散乱 JSON 字符串。

### 7.4 AuthApi

第一版只接入三个账号接口：

```text
POST /api/account/v1/register
POST /api/account/v1/login
GET  /api/account/v1/detail
```

注册请求字段：

```text
username
password
phone
avatarUrl optional
```

登录请求字段：

```text
phone
password
```

### 7.5 AuthSessionStore

`AuthSessionStore` 基于 SharedPreferences 保存：

```text
token
accountId
username
phone
avatarUrl
lastLoginAt
```

保存策略：

- token 存在就保存。
- accountId 存在就保存。
- 其他字段可选保存。
- 不因为缺 token 判定注册失败，是否成功以 `JsonData.success/code` 为准。

后续请求携带 token 的方式建议先预留两种：

```text
Authorization: Bearer <token>
Cookie: token=<token>
```

实际采用哪一种应以服务端约定为准。若接口文档仍未明确，第一版可以在 `ApiClient` 中集中实现，方便后续切换。

### 7.6 MockApi

mock 模式目标：

- 不启动后端也能测试注册页面。
- 不上传真实文件也能测试上传流程。
- 返回结构与真实 `JsonData` 一致。

mock 数据：

```text
register -> success true, mock accountId, mock token
login    -> success true, mock accountId, mock token
detail   -> mock account info
upload   -> success true, mock file id/path
```

mock 不进入业务代码分支。业务代码只通过 `ApiClient` 拿 API。

### 7.7 AccountRegisterActivity

第一版注册页只做必要字段：

```text
手机号
用户名
密码
注册按钮
状态提示
```

行为：

1. 输入校验：phone、username、password 不能为空。
2. 点击注册后禁用按钮，显示 loading。
3. 调用 `AuthApi.register()`。
4. 成功后保存 session。
5. 失败时显示 `msg` 或通用错误。

是否在注册成功后自动 login，取决于后端 register 是否返回 token：

- 如果 register 返回 token：直接保存并进入已登录状态。
- 如果 register 不返回 token：提示注册成功，并可引导登录。

第一版可以只做注册，不强制新增完整登录页；但 `AuthApi.login()` 和 `AuthSessionStore` 要先具备。

## 8. 4B：CGM 缓存回放上传设计

### 8.1 Codec 作为唯一编解码入口

`UniFragment.FragmentGateway` 不应直接调用 `text.getBytes(StandardCharsets.UTF_8)`，也不应直接散用旧 `Analysis.getBytes()`。第四阶段应让新 UniRuntime 统一调用：

```java
Codec.encodeText(context, text)
```

`Codec.encodeText(...)` 内部复用旧项目编码配置：

```java
Analysis.getBytes(
        text,
        FragmentParameter.getInstance().getCodeFormat(context),
        false
)
```

这样可以做到：

- 新代码只有一个编解码入口。
- 旧编码设置继续生效。
- 发送 bytes 与 master 规则一致。
- 后续替换 `Analysis` 时只需要改 `Codec`。

同理，接收数据时 Controller 继续使用 `Codec.decode(...)`，不在 Controller 内重写 byte decode。

### 8.2 CGM Profile Actions

`CgmProfile` 声明三个设备动作：

```text
同步时间    -> Commands.LegacyCgm.syncTime(new Date())
读取缓存    -> Commands.LegacyCgm.readCache()
删除缓存    -> Commands.LegacyCgm.deleteCache()
```

这些 action 是 CGM profile 的能力，不是所有蓝牙设备的全局能力。

`Commands.Capability` 可以保留语义枚举：

```text
SYNC_TIME
READ_CACHE
DELETE_CACHE
```

但具体字符串由 `LegacyCgm` encoder 决定。

### 8.3 CgmPlaybackRecorder

`CgmPlaybackRecorder` 负责复刻 master 的缓存回放写入状态：

```text
Start Playback -> readCache = true
Playback all done -> readCache = false
readCache == true -> append raw dataString
```

这里的 `Start Playback` 和 `Playback all done` 是设备返回文本，不是 Android service。Android 侧不实现 playback service，只实现识别设备标记和写文件。

输出文件名保持：

```text
yyyy-MM-ddCGM_Cache_data.txt
```

写入规则保持：

```text
append dataString
append "\n"
```

这里优先兼容，不擅自改成 JSON、CSV 或去空行。

`CgmPlaybackRecorder` 的输出文件就是第四阶段上传给 server 的主文件。

### 8.4 不生成本地结果文件

第四阶段不新增 `CgmLegacyTextExporter`，也不在 UniRuntime 新链路里生成：

```text
yyyy-MM-dd的CGM_data.txt
```

原因：

- 该文件来自旧页面本地计算逻辑。
- 当前目标是上传设备缓存回放原始数据。
- 数值计算和图表数据生成由 server 完成。
- Android 后续通过 API 获取图表数据并绘制。

旧 Fragment 若仍保留原行为，不在第四阶段修改。新 Uni CGM 链路只实现缓存回放文件。

### 8.5 Java Model 与输出格式

第四阶段不把 JSONL 或 txt 作为内部真相。更合理的原则是：

```text
API response JSON
  ↓
ApiModels 中的 Java model
  ↓
Controller/widget/chart 消费 Java model
```

对于设备缓存上传链路，Android 不需要先把原始缓存转换成 Java 数值对象，因为这一步由 server 负责。Android 只需要保证原始文件完整、格式与 master 兼容。

如果未来需要本地导出不同格式，应采用明确的 printer/serializer，而不是到处手写字符串：

```java
interface RecordPrinter<T> {
    String print(T record);
}
```

但这不是第四阶段必须实现的内容。第四阶段只实现：

```text
raw playback dataString -> CGM_Cache_data.txt -> FileApi.upload(file)
```

### 8.6 FileApi

文件上传接口：

```text
POST /api/file/v1/upload
```

接口文档中 `file` 类型为 `string(binary)`，但请求数据类型写的是：

```text
application/x-www-form-urlencoded,application/json
```

这存在歧义。Retrofit 第一版建议按文件上传常规实现：

```java
@Multipart
@POST("/api/file/v1/upload")
Call<JsonData<FileUploadResp>> upload(
        @Part("fileName") RequestBody fileName,
        @Part("identify") RequestBody identify,
        @Part("parentId") RequestBody parentId,
        @Part("fileSize") RequestBody fileSize,
        @Part MultipartBody.Part file
);
```

如果后端实际要求 JSON + base64 或其他格式，只需要替换 `FileApi` 和 upload request builder，不影响 `CgmPlaybackRecorder` 和 `Controller`。

### 8.7 上传流程

推荐流程：

```text
用户点击读取缓存
  ↓
CgmProfile 发送 ALL\n\r
  ↓
设备返回 Start Playback
  ↓
CgmPlaybackRecorder 开始写 CGM_Cache_data.txt
  ↓
设备返回 Playback all done
  ↓
CgmPlaybackRecorder 结束写入并暴露文件路径
  ↓
UploadUseCase 或 Activity 调用 FileApi.upload()
  ↓
ApiClient 自动携带 token
  ↓
服务端返回 JsonData
  ↓
UI 显示上传成功/失败
```

第一版上传入口可以先放在注册/调试页面或 Uni 页面底部按钮中，但不要把 Retrofit 调用写进 `Controller`。

## 9. 测试与验证

### 9.1 单元测试

必须覆盖：

- `Commands.LegacyCgm.readCache()` 输出 `ALL\n\r`。
- `Commands.LegacyCgm.deleteCache()` 输出 `DELETE\n\r`。
- `Commands.LegacyCgm.syncTime()` 输出 `TIME,yyyy,MM,dd,HH,mm,ss\n\r`。
- `Codec.encodeText(...)` 与 `Analysis.getBytes(..., code, false)` 一致。
- `CgmPlaybackRecorder` 对 `Start Playback` / `Playback all done` 的状态切换。
- `CgmPlaybackRecorder` 在 playback 期间写入原始 `dataString`。
- `CgmPlaybackRecorder` 输出文件名匹配 `yyyy-MM-ddCGM_Cache_data.txt`。
- `AuthSessionStore` 可以保存和读取 token/account 信息。
- `MockApi` 返回的 `JsonData` 结构与真实接口模型一致。

### 9.2 集成验证

4A 验证：

- mock 环境下注册成功。
- dev 环境下 baseUrl 指向 `10.0.2.2:8080` 或指定 dev server。
- token 存储后能被 `ApiClient` 读取。

4B 验证：

- 点击 CGM 三个动作时发送字节与 master 一致。
- 点击读取缓存后能发送 `ALL\n\r`。
- 收到 `Start Playback` 后开始写 `CGM_Cache_data.txt`。
- 收到 `Playback all done` 后停止写入。
- 生成的缓存回放 txt 文件名与 master 一致。
- 生成的缓存回放 txt 内容与 master 规则一致。
- mock upload 返回成功。
- dev upload 能请求到 `/api/file/v1/upload`。

### 9.3 编译验证

每个阶段完成后至少运行：

```text
./gradlew.bat :app:compileDebugJavaWithJavac
./gradlew.bat :app:testDebugUnitTest
```

如果添加 Retrofit/Gson/OkHttp 依赖，还需要确认依赖解析和 minSdk 兼容。

## 10. 分阶段实施建议

### 4A-1：环境与 API 骨架

新增：

```text
api/ApiEnvironment.java
api/ApiClient.java
api/ApiModels.java
api/AuthApi.java
api/FileApi.java
api/MockApi.java
api/AuthSessionStore.java
```

先让 mock 编译通过。

### 4A-2：注册 Activity

新增：

```text
activity/AccountRegisterActivity.java
layout/activity_account_register.xml
```

先接 mock，再接 dev。

### 4B-1：CMD 与 Codec 等价修正

新增或完善：

```text
uni.Codec.encodeText(...)
```

修正 `UniFragment.FragmentGateway` 的 byte 生成方式，让它调用 `Codec.encodeText(...)`。

为 `Commands.LegacyCgm` 和 `Codec.encodeText(...)` 补充测试。

### 4B-2：CGM 缓存回放文件

新增：

```text
uni/profile/cgm/CgmPlaybackRecorder.java
```

实现 `Start Playback` / `Playback all done` 状态机，并写入 `CGM_Cache_data.txt`。

先用单元测试锁住 master 格式。

### 4B-3：文件上传

实现 `FileApi.upload()` 和 upload request builder。

先上传本地测试 txt，再接 `CgmPlaybackRecorder` 产物。

### 4B-4：服务端图表数据入口预留

第四阶段可以只在 spec 和模型层预留方向，不强制实现完整图表 API。

后续绘图链路应为：

```text
server computed data API
  ↓
ApiModels 中的 Java model
  ↓
Uni widget/chart
```

## 11. 风险与待确认

1. 接口文档没有明确 token 字段位置，需要真实响应确认。
2. 接口文档没有明确 token 携带方式，需要服务端确认 header/cookie。
3. `/api/file/v1/upload` 文档对文件上传 content type 描述不够明确，需要用 dev server 验证 multipart 是否可用。
4. `Start Playback` 和 `Playback all done` 是设备返回文本，必须用真实设备或模拟数据确认大小写和完整内容。
5. `\n\r` 不是常规换行顺序，但属于设备协议，应保持。
6. 旧 `的CGM_data.txt` 不进入第四阶段上传链路，如果后续仍有本地结果文件需求，需要单独设计，不应和缓存回放上传混在一起。
7. 第四阶段如果同时做注册、真实上传、CGM profile 全量迁移、服务端图表 API 消费，工作量会偏大，建议严格按 4A/4B 分段验收。

## 12. 成功标准

第四阶段完成后应满足：

- mock/dev/prod 环境有清晰配置入口。
- 注册 Activity 可在 mock 下完整跑通。
- Retrofit 能调用账号接口。
- token/session 能保存并被 API 层读取。
- `CMD_BT_POST` 发送路径与 master 蓝牙发送终点等价。
- `uni.Codec` 成为 UniRuntime 新代码唯一编解码入口。
- CGM 三个命令的真实 bytes 与 master 一致。
- `ALL\n\r` 能触发设备缓存回放请求。
- `Start Playback` 到 `Playback all done` 期间能生成 `CGM_Cache_data.txt`。
- 缓存回放 txt 文件名和内容格式与 master 兼容。
- 文件上传通过 `api/FileApi` 完成，不污染 `uni.Controller`。
- UniRuntime 仍保持第三阶段分层，不反向依赖 Activity、Retrofit 或具体后端实现。
