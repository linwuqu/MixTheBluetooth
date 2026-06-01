# UniFragment 真实 HTTP CGM 闭环联调设计

日期：2026-05-30

## 1. 背景

当前 `dev-1.5` 已经具备账号、文件上传、设备缓存文件生成、`UniFragment`
控制器和 widget 框架，但仍然存在联调真实性不足的问题：

- 默认环境仍容易走 mock 或 debug 验证页，测试人员可能误以为已经走真实后端。
- `VerificationActivity` 这类 debug 页和旧碎片页容易把测试路径带偏。
- 登录、注册、上传、拉取 CGM 结果、widget 展示尚未形成一条清晰闭环。
- logcat 需要能看到真实 HTTP 请求、响应、类名、接口、pretty body 和必要的截断。

本阶段目标是建立一条以 `UniFragment` 为核心的真实联调闭环：

```text
注册 -> 登录 -> 进入真实页面 -> 连接真实板子 -> 同步时间/读取缓存
  -> 板子产生真实 txt -> 上传真实 txt -> 轮询 /api/cgm/v1
  -> 解析后端 JSON -> UniFragment widgets 展示点图与统计
```

## 2. 目标

本阶段完成后，需要满足：

- 注册、登录、上传、CGM 结果查询都走真实 Retrofit/OkHttp 请求。
- 账号使用一组静态联调账号，便于复现。
- 板子数据保持真实来源，不模拟设备回包，不手写假 txt。
- 读取缓存形成的真实 txt 上传到 `/api/test/v1/upload`。
- 上传成功后删除本地 txt；上传失败时保留文件，方便排查。
- 上传后轮询固定端点 `/api/cgm/v1`，不额外设计 query 参数。
- `/api/cgm/v1` 响应按接口文档和已确认 JSON 结构解析。
- `summaryJson.units[].points[]` 用于点图，同时尽量消费统计字段、unit 字段和状态字段。
- `UniFragment` 的 CGM profile/widget 负责展示结果，不使用 `FragmentIonAnalysis` 等旧碎片页。
- logcat 能完整证明每一次 API 调用、请求体、响应体、文件信息和 widget 更新。

## 3. 非目标

本阶段不做：

- 不模拟蓝牙设备数据包。
- 不生成 `Start Playback / CGM:... / Playback all done` 这类假回包。
- 不直接手写缓存 txt 绕过真实板子。
- 不处理 `FragmentIonAnalysis`、`FragmentCustom` 等非 `UniFragment` 旧页面。
- 不新增 `/api/cgm/v1` 的 `jobId`、`fileId`、`datasetId` 等查询参数。
- 不在 Android 本地推算 CGM 结果；CGM 预测结果来自 `/api/cgm/v1`。
- 不保留容易误导测试人员的运行时 mock/debug 页面。

## 4. 关键约束

### 4.1 设备数据必须真实

设备侧只允许这条路径：

```text
真实蓝牙板子
  -> 用户点击 UniFragment 的同步时间/读取缓存命令
  -> 设备真实返回缓存数据
  -> Controller / CgmRawLineConsumer / DeviceDataRecorder
  -> 生成真实 txt 文件
```

禁止为了联调方便新增设备包模拟器、假 byte[]、假 txt writer。

### 4.2 接口响应以文档为准

账号和普通文件接口当前文档/现有代码使用：

```json
{
  "code": 0,
  "success": true,
  "msg": "",
  "data": {}
}
```

`/api/cgm/v1` 已确认使用：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "resultId": 789,
    "jobId": 456,
    "datasetId": 123,
    "pointCount": 10,
    "unitCount": 1,
    "predictionMin": 4.12,
    "predictionMax": 9.87,
    "predictionMean": 6.54,
    "predictionStd": 1.23,
    "avgMard": 16.3,
    "mardStd": null,
    "summaryJson": {
      "avg_mard": 16.3,
      "mard_std": null,
      "point_count": 10,
      "unit_count": 1,
      "prediction_stats": {
        "min": 4.12,
        "max": 9.87,
        "mean": 6.54,
        "std": 1.23
      },
      "units": [
        {
          "unit": 1,
          "unit_title": "lyc-5V-1h",
          "point_count": 10,
          "mard": 16.3,
          "points": [
            { "index": 0, "time": 33, "predicted": 6.21, "actual": 5.4 }
          ]
        }
      ]
    },
    "status": "GENERATED",
    "gmtCreate": "2026-05-14T10:01:07"
  }
}
```

实现时不新增与文档不一致的通用响应 wrapper。`/api/cgm/v1` 不再再套一层
`ServerResponse` 风格封装；如果实现层需要 Java DTO，也只能按文档字段一一对应，
不能再造第二种通用返回模型。app 内部可以继续用 `CallResult` 表达业务调用结果，
但 remote DTO 必须贴合接口文档字段。

### 4.3 `/api/cgm/v1` 固定轮询

`/api/cgm/v1` 第一版只做固定端点轮询：

```java
@GET("/api/cgm/v1")
Call<文档契约对象> cgm();
```

不加 `@QueryMap`，不预留复杂参数，不把 `jobId/fileId/datasetId` 设计成
第一版请求条件。后端如果未来改契约，再按真实文档调整。

## 5. 联调账号

使用静态账号，便于测试人员和后端复现。

建议默认值：

```text
username: bioai-dev-user
phone: 18800000001
password: 123456
```

闭环入口先调用注册：

- 注册成功：继续登录。
- 注册返回“账号已存在”或同类业务错误：记录 log，继续登录。
- 注册网络错误或响应结构错误：停止闭环，提示错误。

随后调用登录：

- 登录成功：保存 session/token，进入主流程。
- 登录失败：停止闭环，logcat 打印请求、响应和业务错误。

## 6. 接口路径

### 6.1 账号

按接口文档：

```text
POST /api/account/v1/register
POST /api/account/v1/login
GET  /api/account/v1/detail
```

`detail` 可作为登录后验证 token/session 的可选步骤。

### 6.2 上传真实 txt

本闭环使用测试上传接口：

```text
POST /api/test/v1/upload
```

文件来自真实板子拉取后的 txt。上传时记录：

- 文件绝对路径。
- 文件名。
- 文件大小。
- 前若干行预览。
- multipart part 名称。
- HTTP 响应 body。

上传成功后删除本地 txt。删除结果也必须 log。

如果 `/api/test/v1/upload` 后端只接受一个 `file` 参数，Android 端按
multipart file part 实现；不再沿用 `/api/file/v1/upload` 的
`fileName/identify/parentId/fileSize` 扩展字段，除非后端文档更新。

### 6.3 查询 CGM 结果

固定轮询：

```text
GET /api/cgm/v1
```

轮询建议：

- 每 2 秒一次。
- 最多 30 次。
- `data.status == "GENERATED"` 视为成功。
- 非 GENERATED 时继续轮询并记录状态。
- HTTP 失败、响应为空、字段解析失败时停止并展示错误。

## 7. 数据消费设计

`/api/cgm/v1` 的数据尽量全部进入 `UniFragment` 的 widget 层。

### 7.1 点图

主图使用：

```text
x = point.time
y = point.predicted
unit = mmol/L
```

`actual` 作为对照点或第二组数据展示。样式参考用户给的 CGM 图：

- y 轴范围默认 3 到 15 mmol/L。
- 参考线：3.9 和 7.8。
- predicted 用主色点。
- actual 用辅助色点。
- tooltip/marker 展示 predicted、actual、time、index。

第一版可以展示 20-30 个点，真实后端返回多少就画多少，超过数量时按
widget 能力做窗口展示或分页后续处理。

### 7.2 统计 widget

消费并展示：

- `pointCount`
- `unitCount`
- `predictionMin`
- `predictionMax`
- `predictionMean`
- `predictionStd`
- `avgMard`
- `mardStd`
- `status`
- `gmtCreate`

如果 `summaryJson.prediction_stats` 和顶层 prediction 字段同时存在：

- 优先展示顶层字段。
- log 中记录 summaryJson 字段，便于比对。
- 两者不一致时 log warning，不在 UI 阻断。

### 7.3 unit 信息 widget

对每个 unit 消费：

- `unit`
- `unit_title`
- `point_count`
- `mard`
- `points.size`

第一版 UI 可以展示第一个 unit，并在 log 中打印所有 unit 摘要。
如果后续后端返回多个 unit，再扩展为多个图或切换控件。

## 8. UniFragment 集成方式

只扩展 `UniFragment` 当前使用的 `CgmProfile` 和 `Widgets`：

```text
UniFragment
  -> Controller
  -> CgmProfile
  -> CgmRawLineConsumer
  -> DeviceDataRecorder
  -> /api/test/v1/upload
  -> /api/cgm/v1 polling
  -> CGM widgets
```

旧页面不参与：

- 不从 `FragmentIonAnalysis` 取随机数据。
- 不把 CGM 图表放到旧碎片页。
- 不通过 `VerificationActivity` 触发闭环。

## 9. 日志设计

所有关键类必须在 log 中注明类名、接口和阶段。

示例：

```text
[DefaultAuthService] API POST /api/account/v1/register
[DefaultAuthService] request body:
{
  "username": "bioai-dev-user",
  "password": "******",
  "phone": "18800000001"
}

[DefaultFileService] API POST /api/test/v1/upload
[DefaultFileService] file path=/storage/.../CGM_Cache_data.txt
[DefaultFileService] file size=12345
[DefaultFileService] file preview:
...

[DefaultCgmService] API GET /api/cgm/v1 poll=3
[DefaultCgmService] status=GENERATED pointCount=30 unitCount=1
[CgmWidgetBinder] render points=30 predictionMean=6.54 avgMard=16.3
```

HTTP body pretty print：

- JSON 请求体 pretty print。
- JSON 响应体 pretty print。
- 密码、token 等敏感字段脱敏。
- 超长 body 截断，并明确打印 `... truncated ...`。
- multipart 文件不完整打印二进制内容，只打印元信息和文本预览。

## 10. mock/debug 清理

为避免误测，本阶段计划清理运行时 mock/debug 路径：

- 删除 `VerificationActivity`。
- 删除登录页 debug 按钮和跳转。
- 删除运行时 `MockServer` 分支。
- 删除本地 `admin/1`、`normal/1` 登录兜底。
- 默认联调路径应明确走 `dev` 真实 HTTP。

单元测试可以继续使用 `MockWebServer`，因为它只存在于测试环境，不进入
app 运行时。

## 11. 错误处理

### 11.1 注册

- 账号已存在：继续登录。
- 网络失败：停止闭环。
- 响应为空或字段不完整：停止闭环。

### 11.2 登录

- 登录成功：保存 session/token。
- 登录失败：停止闭环。
- token 为空：记录 warning；如果后端允许无 token 继续，则继续，否则停止。

### 11.3 上传

- 上传成功：删除本地 txt，然后开始轮询 `/api/cgm/v1`。
- 上传失败：保留本地 txt，展示错误。
- 文件不存在：提示重新读取缓存。

### 11.4 轮询

- `GENERATED`：解析并更新 widgets。
- 其他状态：继续轮询，直到达到次数上限。
- 超时：展示超时，并保留最后一次响应摘要。
- 响应 JSON 字段缺失：log 缺失字段，UI 展示可用字段，不因非核心字段中断。

核心字段：

- `code`
- `message`
- `data.status`
- `data.summaryJson.units`
- `data.summaryJson.units[].points`

## 12. 测试策略

### 12.1 构建验证

```powershell
.\gradlew.bat :app:assembleDebug -PapiEnv=dev
.\gradlew.bat :app:testDebugUnitTest
```

### 12.2 HTTP 契约测试

使用 `MockWebServer` 验证：

- register path 和 JSON body。
- login path 和 JSON body。
- `/api/test/v1/upload` multipart file part。
- `/api/cgm/v1` 固定 GET 请求，不带 query。
- `/api/cgm/v1` JSON 可解析为文档模型。

### 12.3 CGM 结果消费测试

给定一份静态 `/api/cgm/v1` JSON：

- 能解析顶层结果。
- 能解析 `summaryJson.prediction_stats`。
- 能解析 `units`。
- 能解析 `points`。
- 能生成 widget 所需的点图数据和统计数据。

### 12.4 人工闭环验收

人工验收步骤：

1. 安装 dev debug 包。
2. 点击静态账号注册/登录入口。
3. 连接真实板子。
4. 在 `UniFragment` 点击同步时间。
5. 点击读取缓存。
6. 等待真实 txt 形成。
7. 观察 logcat 中 `/api/test/v1/upload` 请求。
8. 观察上传成功后本地 txt 删除日志。
9. 观察 `/api/cgm/v1` 轮询日志。
10. 观察 `UniFragment` widgets 展示点图和统计。

## 13. 成功标准

- app 运行时不再有容易误用的 mock/debug 验证页。
- 静态账号可以完成真实注册/登录链路。
- 真实板子拉取的 txt 可以自动上传到 `/api/test/v1/upload`。
- 上传成功后本地 txt 会删除。
- app 会固定轮询 `/api/cgm/v1`，不带额外 query。
- `/api/cgm/v1` 返回的点、统计、unit、状态字段都能被解析和展示或记录。
- logcat 可以证明每一步是真实 HTTP，而不是本地 mock。
- `UniFragment` 是唯一 CGM 展示入口，旧碎片页不参与本闭环。

## 14. 待确认事项

以下事项不阻塞第一版实现，但需要后端或联调时确认：

- `/api/test/v1/upload` 的 multipart 参数名是否就是 `file`。
- `/api/test/v1/upload` 上传成功后是否会立即触发后端生成 `/api/cgm/v1` 结果。
- `/api/cgm/v1` 非 GENERATED 时可能返回哪些 `status`。
- `/api/cgm/v1` 失败时的 `code/message` 约定。
- 多 unit 返回时，第一版 UI 展示第一个 unit 是否足够。
