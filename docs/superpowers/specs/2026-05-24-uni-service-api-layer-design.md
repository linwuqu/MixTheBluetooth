# Uni Service API 层、可插拔实现与验证入口设计

日期：2026-05-24

## 1. 背景

第三阶段和第四阶段已经把 Uni Runtime 从旧 `FragmentMessage` 中抽出来，并开始接入账号 API、环境配置、设备缓存回放 txt 和文件上传。但当前代码仍然有一个结构性问题：

```text
Activity / Uni / Debug
  直接知道 AuthRepository、FileUploadUseCase、CgmReplayUploadUseCase、ApiClient、MockApi 等实现层对象
```

这会让调用方依赖实现细节。后续如果要切换 mock/dev/prod、替换 Retrofit、接真实设备、无板子 replay、或拆分模块，调用方都可能被实现层牵着走。

本阶段目标是引入一层稳定的 **Service API**：

```text
调用方依赖 api/ 的接口
实现方在 impl/ 中实现接口
remote/ 负责服务端通信
local/ 负责本地状态和文件
debug/ 只作为验证窗口
```

核心原则：

```text
activity/debug/uni -> api
impl -> api + remote + local
remote -> Retrofit / OkHttp / 服务端 DTO / mock server
local -> SharedPreferences / 文件系统 / replay sample
api -> 不依赖 impl，不依赖 remote，不依赖 local，尽量不依赖 Android Context
```

## 2. 阶段目标

本阶段不是继续堆功能，而是把已有功能收敛成清晰的接口边界：

```text
Activity / Uni / Debug
  ↓ 只依赖
api/
  ↓ 由 impl/ 注册默认实现
impl/
  ↓ 组合
remote/ + local/
```

完成后应满足：

- UI 不直接依赖 `ApiClient`、`MockApi`、`Retrofit`。
- Debug 验证页不直接 new repository/usecase，而是调用 `api/` 暴露的 service。
- mock/dev/prod 的差异集中在 `remote/ServerEndpoints` 的实现选择上。
- 默认 service 实现尽量复用，不为 mock/dev/prod 写三套 service。
- 设备数据能力用通用 `DeviceDataService` 表达，不把 CGM 放进 service 命名。

## 3. 非目标

本阶段不做：

- 不引入 Hilt/Dagger。
- 不引入 ARouter。
- 不拆分 Gradle module。
- 不重做 UI 美化。
- 不改变第三阶段 `UniFragment + Controller + Profile` 的主结构。
- 不改变 legacy 蓝牙 CMD 协议。

## 4. 总体结构

```text
com.hc.mixthebluetooth
├── api/                              // 对外能力接口：UI、Uni、Debug 只依赖这里
│   ├── AppApi.java                    // app 内部能力门面：返回当前环境下的 service 实例
│   ├── CallResult.java                // 一次 app 内部能力调用的统一结果，不等于服务端 JSON
│   ├── auth/
│   │   ├── AuthService.java            // 账号能力接口：注册、登录、详情、当前用户
│   │   └── AuthUser.java               // app 内部统一用户对象
│   ├── file/
│   │   ├── FileService.java            // 文件传输接口：上传文件，不关心文件如何产生
│   │   └── UploadedFile.java           // 上传成功后的文件信息
│   └── device/
│       └── DeviceDataService.java      // 设备数据接口：生成、读取、上传设备原始数据文件
│
├── impl/                              // 默认实现：把 api 接口接到 remote/local
│   ├── AppApiBootstrap.java            // 根据 EnvConfig 选择 remote，并把 service 安装到 AppApi
│   ├── EnvConfig.java                  // 环境配置与展示信息：env/baseUrl/useMock/debug/remoteName
│   ├── auth/
│   │   └── DefaultAuthService.java      // 用 ServerEndpoints + SessionStore 实现 AuthService
│   ├── file/
│   │   └── DefaultFileService.java      // 用 ServerEndpoints 实现 FileService multipart 上传
│   └── device/
│       └── DefaultDeviceDataService.java // 用 recorder/sample/file service 实现 DeviceDataService
│
├── remote/                            // 服务端通信：Retrofit、服务端 DTO、mock server 替身
│   ├── ServerClient.java               // 创建 Retrofit/OkHttp endpoint
│   ├── ServerEndpoints.java            // Retrofit endpoint 声明：账号、文件等 HTTP 接口
│   ├── ServerResponse.java             // 服务端 code/msg/data/success 外壳
│   ├── ServerModels.java               // 服务端请求/响应 DTO，不给 UI 直接使用
│   └── MockServer.java                 // mock 环境的 endpoint 替身，不联网但返回真实 Call<Response>
│
├── local/                             // 本地状态和文件：SharedPreferences、txt、sample
│   ├── SessionStore.java               // 保存 token/account/phone，不解释业务
│   ├── DeviceDataRecorder.java         // 将设备回放行写成原始 txt
│   └── DeviceReplaySample.java         // 无板子时提供固定设备行样本
│
├── debug/                             // 开发验证 UI：只调用 api，展示结果和测试指引
│   └── VerificationActivity.java
│
├── activity/                          // 正式页面：只调用 api
│   ├── LoginActivity.java
│   └── AccountRegisterActivity.java
│
└── uni/                               // Uni 蓝牙运行时：不接触 remote，必要时调用 api/device
    ├── Controller.java
    ├── Profiles.java
    └── profile/cgm/
        └── CgmProfile.java
```

## 5. api 层设计

`api/` 是对外能力契约层。Activity、Uni、Debug 只允许依赖这里。

### 5.1 AppApi

`AppApi` 是 app 内部能力门面，不是 Retrofit API。

它的职责：

```text
返回当前环境下已经安装好的 AuthService、FileService、DeviceDataService。
```

建议形态：

```java
public final class AppApi {
    public static AuthService auth();

    public static FileService file();

    public static DeviceDataService deviceData();

    public static EnvConfig env();

    public static void install(AuthService auth,
                               FileService file,
                               DeviceDataService deviceData,
                               EnvConfig env);
}
```

调用方只写：

```java
AppApi.auth().login(phone, password, callback);
```

调用方不关心：

```text
MockServer
Retrofit
SessionStore
DeviceDataRecorder
```

### 5.2 CallResult

`CallResult<T>` 表示 app 内部一次能力调用的统一结果。

它不等于服务端 JSON。服务端 JSON 只覆盖 HTTP 正常返回；app 内部调用还可能失败于网络异常、本地文件不存在、设备回放边界不完整、session 保存失败等。

`CallResult` 同时承担异步状态表达，因此不再单独设计 `DeviceDataState` 这类专用状态对象。对于真实设备持续回放，未完成时返回 `pending()`；完成时返回 `ok(file)`；失败时返回 `error(...)`。

建议形态：

```java
public final class CallResult<T> {
    public static final int UNKNOWN = -1;
    public static final int NETWORK = -100;
    public static final int EMPTY_RESPONSE = -101;
    public static final int EMPTY_DATA = -102;
    public static final int LOCAL_FILE_NOT_FOUND = -200;
    public static final int DEVICE_REPLAY_INCOMPLETE = -300;

    public enum State {
        OK,
        PENDING,
        ERROR
    }

    public final State state;
    public final int code;
    public final String message;
    public final T data;
    public final Throwable cause;

    public static <T> CallResult<T> ok(T data);

    public static <T> CallResult<T> pending(String message);

    public static <T> CallResult<T> error(int code, String message, Throwable cause);

    public boolean isOk();

    public boolean isPending();

    public boolean isError();
}
```

语义：

```text
state    OK / PENDING / ERROR
code     app 内部错误码或服务端透传码；本地错误使用负数，避免和服务端 code 冲突
message  给 UI/debug 展示的消息
data     成功数据；PENDING 时通常为空
cause    原始异常，可为空
```

异步接口不再单独定义 `CallCallback` 文件，直接使用 Java 标准库：

```java
Consumer<CallResult<T>>
```

这样 service 方法签名更少一层自定义类型。如果后续需要取消、进度、多次事件流，再单独引入更强的 callback 或 observable 机制。

### 5.3 AuthService

账号能力接口：

```java
public interface AuthService {
    void register(String username, String password, String phone, Consumer<CallResult<AuthUser>> callback);

    void login(String phoneOrAccount, String password, Consumer<CallResult<AuthUser>> callback);

    void detail(Consumer<CallResult<AuthUser>> callback);

    AuthUser currentUser();

    void clearSession();
}
```

`AuthUser` 是 app 内部统一用户对象：

```java
public final class AuthUser {
    public long accountId;
    public String username;
    public String phone;
    public String token;
}
```

token 是透明字符串。客户端不解析、不假设 JWT。

### 5.4 FileService

文件传输接口，只负责传输，不关心文件如何产生：

```java
public interface FileService {
    void upload(File file, Consumer<CallResult<UploadedFile>> callback);
}
```

`UploadedFile` 是上传成功后的文件信息：

```java
public final class UploadedFile {
    public long fileId;
    public String fileName;
    public String path;
    public String url;
}
```

### 5.5 DeviceDataService

设备数据接口，负责设备原始数据文件的生成、读取和上传：

```java
public interface DeviceDataService {
    void replaySample(Consumer<CallResult<File>> callback);

    void consumeLine(String line, Consumer<CallResult<File>> callback);

    File lastDataFile();

    void uploadLastDataFile(Consumer<CallResult<UploadedFile>> callback);
}
```

注意：

- service 名称不出现 CGM。
- 当前实现可以处理 CGM 设备回放，但这属于 impl/local 细节。
- `consumeLine()` 在设备回放未完成时返回 `CallResult.pending("recording")`，完成时返回 `CallResult.ok(file)`。
- `DeviceDataService` 的调用方只知道“设备原始数据文件”，不知道 `Start Playback`、`Playback all done` 等具体协议细节。

## 6. impl 层设计

`impl/` 是默认实现层。它把 `api/` 的接口接到 `remote/` 和 `local/`。

### 6.1 EnvConfig

`EnvConfig` 是 `BuildConfig` 的实现层包装，同时也是 debug 页面可展示的环境信息对象。不再拆 `EnvInfo`，因为二者字段接近，拆分会增加样板。

职责：

```text
集中解释 env/baseUrl/useMock/debug/remoteName，避免 UI 和 service 到处 import BuildConfig。
```

建议形态：

```java
public final class EnvConfig {
    public final String env;
    public final String baseUrl;
    public final boolean useMock;
    public final boolean debug;
    public final String remoteName;
    public final boolean networkEnabled;

    public static EnvConfig fromBuildConfig();

    public EnvConfig withRemote(String remoteName, boolean networkEnabled);
}
```

`remoteName` 用于 VerificationActivity 展示当前实际 route：

```text
mock -> MockServer(static Call responses)
dev  -> RetrofitServer(baseUrl)
prod -> RetrofitServer(baseUrl)
```

### 6.2 AppApiBootstrap

`AppApiBootstrap` 是启动装配器。

职责：

```text
根据 EnvConfig 选择 remote endpoint，并将默认 service 安装到 AppApi。
```

mock/dev/prod 不需要三套 service。只替换底层 `ServerEndpoints`：

```java
public final class AppApiBootstrap {
    public static void init(Context context) {
        EnvConfig env = EnvConfig.fromBuildConfig();

        ServerEndpoints endpoints;
        if (env.useMock) {
            endpoints = new MockServer();
            env = env.withRemote("MockServer(static Call responses)", false);
        } else {
            endpoints = ServerClient.create(env.baseUrl);
            env = env.withRemote("RetrofitServer(" + env.baseUrl + ")", true);
        }

        SessionStore sessionStore = new SessionStore(context);

        FileService fileService = new DefaultFileService(endpoints);
        DeviceDataService deviceDataService = new DefaultDeviceDataService(
                new DeviceDataRecorder(context),
                new DeviceReplaySample(context),
                fileService
        );
        AuthService authService = new DefaultAuthService(endpoints, sessionStore);

        AppApi.install(authService, fileService, deviceDataService, env);
    }
}
```

这个设计保证：

```text
mock/dev/prod 的业务 service 尽量一致
mock 只是不联网的 ServerEndpoints，但仍返回真实 Call<ServerResponse<T>>
dev/prod 是 Retrofit ServerEndpoints
```

#### 初始化位置

推荐在 Application 初始化：

```java
public class MixBluetoothApplication extends HomeApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        AppApiBootstrap.init(this);
    }
}
```

当前 manifest 使用的是 `com.hc.basiclibrary.viewBasic.HomeApplication`。如果不适合直接修改基础库，可以短期在 `IntroActivity` 或 `LoginActivity` 首次启动时调用：

```java
AppApiBootstrap.init(getApplicationContext());
```

`AppApiBootstrap.init()` 必须幂等，避免多次 Activity 启动重复安装 service。长期建议回到 Application，因为 service 装配属于 app 生命周期，不属于某个页面。

### 6.3 DefaultAuthService

`DefaultAuthService` 实现 `AuthService`。

依赖：

```text
ServerEndpoints
SessionStore
```

职责：

- 调用 register/login/detail endpoint。
- 将 `ServerResponse` 转换成 `CallResult<AuthUser>`。
- 成功时保存 session。
- `currentUser()` 从 `SessionStore` 读取本地用户。

转换规则：

```text
response == null
  -> CallResult.error(CallResult.EMPTY_RESPONSE, "服务端响应为空", null)

response.success == false
  -> CallResult.error(response.code, response.msg, null)

response.data == null
  -> CallResult.error(CallResult.EMPTY_DATA, "账号数据为空", null)

response.success == true && data != null
  -> AuthUser -> SessionStore.save(user) -> CallResult.ok(user)

onFailure(Throwable)
  -> CallResult.error(CallResult.NETWORK, "网络错误", throwable)
```

### 6.4 DefaultFileService

`DefaultFileService` 实现 `FileService`。

依赖：

```text
ServerEndpoints
```

职责：

- 校验本地文件存在。
- 构造 multipart。
- 调用文件上传 endpoint。
- 将 `ServerResponse` 转换成 `CallResult<UploadedFile>`。

转换规则：

```text
file == null 或 !file.exists()
  -> CallResult.error(CallResult.LOCAL_FILE_NOT_FOUND, "文件不存在", null)

server success
  -> UploadedFile -> CallResult.ok(uploadedFile)

server failure
  -> CallResult.error(server.code, server.msg, null)

network failure
  -> CallResult.error(CallResult.NETWORK, "网络错误", throwable)
```

### 6.5 DefaultDeviceDataService

`DefaultDeviceDataService` 实现 `DeviceDataService`。

依赖：

```text
DeviceDataRecorder
DeviceReplaySample
FileService
```

职责：

- `replaySample()`：读取 sample 行，喂给 recorder，生成设备原始 txt。
- `consumeLine()`：消费真实设备返回行，未完成时返回 `CallResult.pending("recording")`，完成时返回 `CallResult.ok(file)`。
- `lastDataFile()`：返回最近生成的设备数据文件。
- `uploadLastDataFile()`：调用 `FileService.upload(file)`。

这里不使用 `CgmDeviceDataService` 这个名字。CGM 是当前 recorder/sample 的内部协议来源，不进入 service 实现类名。

设备回放不完整时：

```text
sample 缺少开始/结束边界
  -> CallResult.error(CallResult.DEVICE_REPLAY_INCOMPLETE, "设备回放数据不完整", null)
```

## 7. remote 层设计

`remote/` 只负责服务端通信。

它不处理 UI，不保存 session，不写设备文件，不做业务判断。

### 7.1 ServerClient

职责：

```text
创建 Retrofit/OkHttp，并根据 baseUrl 创建 ServerEndpoints。
```

debug/dev 时可以接：

```text
HttpLoggingInterceptor
后续可选 Chucker
```

### 7.2 ServerEndpoints

职责：

```text
声明服务端 HTTP endpoint。
```

可以包含账号和文件 endpoint：

```java
public interface ServerEndpoints {
    Call<ServerResponse<ServerModels.AccountResp>> login(ServerModels.LoginReq req);

    Call<ServerResponse<ServerModels.AccountResp>> register(ServerModels.RegisterReq req);

    Call<ServerResponse<ServerModels.AccountResp>> detail();

    Call<ServerResponse<ServerModels.FileResp>> upload(...);
}
```

### 7.3 ServerResponse

服务端 JSON 外壳：

```java
public final class ServerResponse<T> {
    public int code;
    public boolean success;
    public String msg;
    public T data;
}
```

它只在 remote/impl 之间流转，不给 UI 直接使用。

### 7.4 ServerModels

服务端请求/响应 DTO，例如：

```text
LoginReq
RegisterReq
AccountResp
FileResp
```

DTO 命名跟服务端协议对齐，不作为 app 内部模型。

### 7.5 MockServer

mock 环境的 endpoint 替身。

职责：

```text
实现与 ServerEndpoints 等价的接口，但不发网络；每个方法仍返回 Call<ServerResponse<T>>。
```

mock 和 dev/prod 的分歧只发生在：

```text
ServerEndpoints endpoints = useMock ? new MockServer() : ServerClient.create(baseUrl)
```

上层 `DefaultAuthService`、`DefaultFileService` 不需要为 mock 另写一套。

MockServer 不应该返回裸 response，也不应该绕过 service 层。它要像一个静态 server：

```text
login()
  -> FakeCall<ServerResponse<AccountResp>>

upload()
  -> FakeCall<ServerResponse<FileResp>>
```

这样 mock 和 Retrofit 的调用路径保持一致：

```text
DefaultAuthService -> ServerEndpoints.login() -> Call<ServerResponse<AccountResp>>
DefaultFileService -> ServerEndpoints.upload() -> Call<ServerResponse<FileResp>>
```

区别只是：

```text
mock: FakeCall 立即返回静态内容，不产生网络请求
dev/prod: Retrofit Call 通过 OkHttp 发真实 HTTP
```

## 8. local 层设计

`local/` 只负责 Android 本地状态、本地文件和本地样本。

### 8.1 SessionStore

职责：

```text
保存 token/account/phone 等本地登录态。
```

不负责：

```text
调用接口
判断登录是否成功
解释 token 是否 JWT
```

### 8.2 DeviceDataRecorder

职责：

```text
将设备返回行写成原始设备数据 txt。
```

当前内部仍可复用 legacy 语义：

```text
Start Playback -> 开始记录
Playback all done -> 完成记录
recording 时逐行写入
```

但调用方不直接依赖这些字符串。

### 8.3 DeviceReplaySample

职责：

```text
无板子时提供固定设备行样本。
```

用途：

- debug 页面 replay。
- 单元测试 replay。
- dev 联调前验证 txt 和上传链路。

## 9. debug 验证入口

`debug/VerificationActivity` 是开发验证 UI。

边界：

```text
只调用 api/，展示当前环境、调用结果、生成文件、测试指引。
```

它不直接依赖：

```text
DefaultAuthService
ServerClient
MockServer
DeviceDataRecorder
SessionStore
```

页面应清楚展示：

```text
[Environment]
env / baseUrl / useMock / debug / remoteName / networkEnabled

[Auth]
login/register/detail/currentUser
显示 CallResult<AuthUser>

[Device Data]
replay sample
last file path / size / first lines
upload last file

[Network Observation]
mock: 不会发网络请求
dev/prod: 使用 Android Studio Network Inspector 查看真实 HTTP
```

这解决两个问题：

1. mock 模式下看不到网络请求是正常的，页面要明确说明。
2. dev 模式下应该能在 Network Inspector 看到 Retrofit 请求。

## 10. activity 与 uni 的接入方式

### 10.1 Activity

登录页：

```java
AppApi.auth().login(phone, password, result -> {
    if (result.ok) {
        // enter MainActivity
    } else {
        // show result.message
    }
});
```

注册页：

```java
AppApi.auth().register(username, password, phone, result -> {
    // show result
});
```

Activity 不 import：

```text
ServerClient
MockServer
SessionStore
DefaultAuthService
```

### 10.2 Uni

Uni Runtime 原则上不接触 `remote/`。

如果设备数据需要进入统一上传链路，可以通过：

```java
AppApi.deviceData().consumeLine(line, callback);
```

或者由 Activity gateway 接收 Uni 事件后调用 `DeviceDataService`。

Controller/Profile 不应该直接依赖 Retrofit 或服务端 DTO。

## 11. 环境区分

当前仍可保留第四阶段的：

```text
-PapiEnv=mock/dev/prod
app/config/env.mock.properties
app/config/env.dev.properties
app/config/env.prod.properties
```

实现层通过 `EnvConfig` 读取 BuildConfig：

```text
mock:
  useMock = true
  endpoints = MockServer

dev:
  useMock = false
  endpoints = ServerClient.create(devBaseUrl)

prod:
  useMock = false
  endpoints = ServerClient.create(prodBaseUrl)
```

后续如果环境稳定，可以再迁移到 Android `productFlavors`，但本阶段不强制。

## 12. 测试策略

### 12.1 api contract 测试

目标：

```text
AuthService 登录/注册成功后返回 CallResult<AuthUser>
FileService 上传成功后返回 CallResult<UploadedFile>
DeviceDataService replay sample 后返回 CallResult<File>
```

测试可以使用：

```text
MockServer
MemorySessionStore
TemporaryFolder
```

### 12.2 remote HTTP contract 测试

使用 MockWebServer 验证 Retrofit 请求：

```text
POST /api/account/v1/login
POST /api/account/v1/register
GET  /api/account/v1/detail
POST /api/file/v1/upload
multipart field 和文件内容正确
```

这证明 dev/prod 的 HTTP 路由真实存在。

### 12.3 debug 手动验证

mockDebug：

```text
打开 VerificationActivity
确认 env=mock/useMock=true
点击 login/register/replay/upload
确认页面提示不会发网络请求
确认返回 CallResult 成功
```

devDebug：

```text
打开 Android Studio Network Inspector
打开 VerificationActivity
点击 login/upload
确认 Network Inspector 出现真实 HTTP 请求
```

## 13. 成功标准

完成后应满足：

- `activity/`、`debug/`、`uni/` 不直接 import `remote/`、`local/`、`impl/`。
- 对外能力集中在 `api/`。
- mock/dev/prod 只替换 `ServerEndpoints`，service 实现尽量复用。
- 服务端 JSON 使用 `ServerResponse<T>`，app 内部调用使用 `CallResult<T>`，语义清楚。
- `CallResult` 内置 pending 和本地错误码，不为设备回放单独设计状态对象。
- mock 环境仍通过 `Call<ServerResponse<T>>` 模拟真实 endpoint 调用路径。
- 设备数据接口不出现 CGM 名称。
- VerificationActivity 能明确说明当前环境、mock 是否发网络、dev 如何观察 HTTP。
- 自动测试覆盖 service contract 和 remote HTTP contract。

## 14. 结论

本设计把结构收敛为：

```text
api     对外能力接口
impl    默认能力实现
remote  服务端通信
local   本地状态和文件
debug   开发验证 UI
```

调用方只依赖 `api/`，实现细节沉到 `impl/remote/local`。mock/dev/prod 的差异集中在 `ServerEndpoints` 的选择上，而不是分散到 UI、Uni 或 Diagnostics 里。

这个结构比直接引入大框架更小，也更适合当前项目：先把接口边界和验证路径做清楚，再决定后续是否需要更强的 DI 或模块化工具。
