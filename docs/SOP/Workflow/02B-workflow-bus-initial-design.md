# 工作流 02 补充：RootWorkflow 初步设计

> 本文是代码设计草案，不直接修改现有 Kotlin。
>
> 当前实现见：[02A-ui-translation-current-state.md](./02A-ui-translation-current-state.md)。

## 1. 参考的成熟模式

本设计只吸收两个模式：

1. [Spotify Mobius](https://spotify.github.io/mobius/concepts/) 的循环：

   ```text
   Event + Model → Update → New Model + Effect
   ```

2. [Square Workflow](https://square.github.io/workflow/kotlin/api/htmlMultiModule/workflow-core/com.squareup.workflow1/-workflow/index.html) 的父子关系：

   ```text
   父 Workflow 决定子 Workflow 是否存在
   子 Workflow 只向父 Workflow 报告业务结果
   ```

项目继续使用 Compose Navigation，不引入这两个库。

## 2. 最终只增加一个根流程

```text
RootWorkflow
├── RootState
├── RootEvent
├── RootEffect
├── AuthTranslation
└── ConnectionTranslation
```

职责如下：

```text
Auth 页面
→ AuthTranslation
→ AuthTranslation 完成自己的状态机
→ 向 RootWorkflow 报告结果

Connection 页面
→ ConnectionTranslation
→ ConnectionTranslation 完成自己的状态机
→ 向 RootWorkflow 报告结果

RootWorkflow
→ 决定保留哪个 Translation
→ 决定目标页面
```

页面操作不需要全部经过 RootWorkflow。

例如：

```text
输入手机号、提交登录
→ 仍然直接进入 AuthTranslation

登录成功
→ 才向 RootWorkflow 报告 AuthenticatedEvent
```

RootWorkflow 只处理会改变页面或子任务生命周期的事件。

## 3. 统一命名

所有进入 RootWorkflow 的消息统一使用 `Event` 后缀：

```kotlin
sealed interface RootEvent {
    data object AppStartedEvent : RootEvent
    data object AuthRequiredEvent : RootEvent

    data class AuthenticatedEvent(
        val userId: String
    ) : RootEvent

    data object ConnectionReadyEvent : RootEvent
    data object LogoutRequestedEvent : RootEvent
    data object ConnectionStoppedEvent : RootEvent
    data object SessionClearedEvent : RootEvent
}
```

不再区分：

```text
PageSignal
TaskSignal
SystemSignal
NavigationSignal
```

这些只是 Event 的不同来源，不需要变成四套总线协议。

所有 RootWorkflow 要求外部执行的动作统一使用 `Effect` 后缀：

```kotlin
sealed interface RootEffect {
    data object StartAuthEffect : RootEffect

    data class StartConnectionEffect(
        val userId: String
    ) : RootEffect

    data object ClearAuthSessionEffect : RootEffect
    data object ReleaseConnectionEffect : RootEffect
}
```

固定方向：

```text
RootEvent → RootWorkflow → RootEffect
```

## 4. RootState

```kotlin
sealed interface RootState {
    data object Starting : RootState
    data object RestoringSession : RootState
    data object AwaitingAuthentication : RootState

    data class PreparingConnection(
        val userId: String
    ) : RootState

    data class Connecting(
        val userId: String
    ) : RootState

    data class EndingConnection(
        val userId: String
    ) : RootState

    data class ClearingSession(
        val userId: String
    ) : RootState
}
```

RootState 只表达当前跨任务流程，不保存：

- 登录表单字段；
- AuthUiState；
- 扫描设备列表；
- RSSI；
- ConnectionUiState；
- NavController。

## 5. RootState 直接决定页面

```kotlin
sealed interface RootScreen {
    data object Auth : RootScreen

    data class Connection(
        val userId: String
    ) : RootScreen
}

fun RootState.screen(): RootScreen {
    return when (this) {
        RootState.Starting,
        RootState.RestoringSession,
        RootState.AwaitingAuthentication ->
            RootScreen.Auth

        is RootState.PreparingConnection ->
            RootScreen.Auth

        is RootState.Connecting ->
            RootScreen.Connection(userId)

        is RootState.EndingConnection ->
            RootScreen.Connection(userId)

        is RootState.ClearingSession ->
            RootScreen.Connection(userId)
    }
}
```

登录成功后不立即切页。

```text
AuthenticatedEvent
→ 启动 ConnectionTranslation
→ ConnectionReadyEvent
→ RootState 进入 Connecting
→ 目标页面才变为 Connection
```

因此不会先进入 Connection 页面，再等待对应 Translation 创建。

## 6. RootState 直接决定子任务

不建立通用 PCB。当前只有两个明确的子任务：

```kotlin
enum class RootChild {
    Auth,
    Connection
}

fun RootState.requiredChildren(): Set<RootChild> {
    return when (this) {
        RootState.Starting ->
            emptySet()

        RootState.RestoringSession,
        RootState.AwaitingAuthentication ->
            setOf(RootChild.Auth)

        is RootState.PreparingConnection,
        is RootState.Connecting,
        is RootState.EndingConnection,
        is RootState.ClearingSession ->
            setOf(
                RootChild.Auth,
                RootChild.Connection
            )
    }
}
```

这里不是第二套生命周期执行器，而是一个可测试的**保留规则**：

```text
RootChild 存在于 requiredChildren
→ 该状态下对应 Translation 必须仍被 RootChildren 持有

RootChild 从 requiredChildren 消失
→ RootDecisionCore 必须已经产生释放它的 Effect
```

实际启动、挂起、停止和释放仍然只由 `RootEffect` 执行。测试需要检查
`newState.requiredChildren()` 与 Effects 是否一致，避免出现“状态认为子任务存在，
实际对象却已经销毁”的双重所有权。

Auth 页面消失时：

```text
RootScreen = Connection
requiredChildren = { Auth, Connection }
```

所以页面已经删除，但 AuthTranslation 仍然保留。

退出登录完成后：

```text
RootScreen = Auth
requiredChildren = { Auth }
```

ConnectionTranslation 才被销毁。

## 7. RootDecisionCore

复用项目现有的 `DecisionCore` 和 `Transition`，不再定义一份
`RootTransition`：

```kotlin
object RootDecisionCore :
    DecisionCore<RootState, RootEvent, RootEffect> {

    override fun reduce(
        state: RootState,
        event: RootEvent
    ): Transition<RootState, RootEffect> {
        return when (event) {
            RootEvent.AppStartedEvent ->
                onAppStarted(state)

            RootEvent.AuthRequiredEvent ->
                onAuthRequired(state)

            is RootEvent.AuthenticatedEvent ->
                onAuthenticated(state, event)

            RootEvent.ConnectionReadyEvent ->
                onConnectionReady(state)

            RootEvent.LogoutRequestedEvent ->
                onLogoutRequested(state)

            RootEvent.ConnectionStoppedEvent ->
                onConnectionStopped(state)

            RootEvent.SessionClearedEvent ->
                onSessionCleared(state)
        }
    }
}
```

### 7.1 启动 Auth

```kotlin
private fun onAppStarted(
    state: RootState
): Transition<RootState, RootEffect> {
    return when (state) {
        RootState.Starting ->
            Transition(
                newState = RootState.RestoringSession,
                effects = listOf(
                    RootEffect.StartAuthEffect
                )
            )

        else -> Transition(state)
    }
}
```

### 7.2 本地没有 Session

```kotlin
private fun onAuthRequired(
    state: RootState
): Transition<RootState, RootEffect> {
    return when (state) {
        RootState.RestoringSession ->
            Transition(
                RootState.AwaitingAuthentication
            )

        else -> Transition(state)
    }
}
```

### 7.3 登录或 Session 恢复成功

```kotlin
private fun onAuthenticated(
    state: RootState,
    event: RootEvent.AuthenticatedEvent
): Transition<RootState, RootEffect> {
    return when (state) {
        RootState.RestoringSession,
        RootState.AwaitingAuthentication ->
            Transition(
                newState = RootState.PreparingConnection(
                    event.userId
                ),
                effects = listOf(
                    RootEffect.StartConnectionEffect(
                        event.userId
                    )
                )
            )

        else -> Transition(state)
    }
}
```

### 7.4 ConnectionTranslation 已准备好

```kotlin
private fun onConnectionReady(
    state: RootState
): Transition<RootState, RootEffect> {
    return when (state) {
        is RootState.PreparingConnection ->
            Transition(
                newState = RootState.Connecting(
                    state.userId
                )
            )

        else -> Transition(state)
    }
}
```

此时 `screen()` 才从 Auth 变为 Connection。
AuthTranslation 只是不再被页面观察；它没有正在执行的 AuthEffect，
所以保留实例就是“挂起”，不需要再发明 `SuspendAuthEffect`。

### 7.5 退出登录

```kotlin
private fun onLogoutRequested(
    state: RootState
): Transition<RootState, RootEffect> {
    return when (state) {
        is RootState.Connecting ->
            Transition(
                RootState.EndingConnection(
                    state.userId
                )
            )

        else -> Transition(state)
    }
}
```

### 7.6 连接资源停止后清理 Session

```kotlin
private fun onConnectionStopped(
    state: RootState
): Transition<RootState, RootEffect> {
    return when (state) {
        is RootState.EndingConnection ->
            Transition(
                newState = RootState.ClearingSession(
                    state.userId
                ),
                effects = listOf(
                    RootEffect.ClearAuthSessionEffect
                )
            )

        else -> Transition(state)
    }
}
```

### 7.7 Session 清理完成

```kotlin
private fun onSessionCleared(
    state: RootState
): Transition<RootState, RootEffect> {
    return when (state) {
        is RootState.ClearingSession ->
            Transition(
                newState =
                    RootState.AwaitingAuthentication,
                effects = listOf(
                    RootEffect.ReleaseConnectionEffect
                )
            )

        else -> Transition(state)
    }
}
```

此时：

```text
screen() = Auth
requiredChildren() = { Auth }
```

ConnectionTranslation 被清理，Navigation 回到 Auth。

## 8. RootWorkflow

根流程不再自己实现 Channel。项目现有的 `WorkflowOrchestrator`
已经完成了单线程事件排队、状态发布和异步 Effect 回环，直接复用：

```kotlin
class RootWorkflow(
    private val children: RootChildren,
    scope: CoroutineScope
) : AutoCloseable {
    private val orchestrator =
        WorkflowOrchestrator(
            initialState = RootState.Starting,
            decisionCore = RootDecisionCore,
            effectExecutor =
                RootEffectExecutor(children),
            scope = scope,
            logTag = "Root.Workflow"
        )

    val state: StateFlow<RootState> =
        orchestrator.state

    init {
        children.bindRootReporter(
            orchestrator::dispatch
        )
        orchestrator.dispatch(
            RootEvent.AppStartedEvent
        )
    }

    fun dispatch(event: RootEvent) {
        orchestrator.dispatch(event)
    }

    fun authOrNull(): AuthTranslation? =
        children.authOrNull()

    fun connectionOrNull():
        ConnectionTranslation? =
        children.connectionOrNull()

    override fun close() {
        children.close()
        orchestrator.close()
    }
}
```

这里没有第二套总线：

```text
Auth / Connection 的跨任务输出
→ RootChildren 绑定的 reporter
→ WorkflowOrchestrator.dispatch(RootEvent)
→ 现有 Channel
→ RootDecisionCore
```

`bindRootReporter()` 必须在任何 Translation 创建前调用一次。这样子任务
可以主动报告结果，但不能直接读取或修改 RootState。

## 9. 子 Translation 如何报告

AuthTranslation 保留自己的：

```text
AuthIntent
AuthEvent
AuthState
AuthEffect
AuthUiState
```

只增加一个根流程输出协议：

```kotlin
sealed interface AuthOutput {
    data object AuthRequired : AuthOutput

    data class Authenticated(
        val userId: String
    ) : AuthOutput

    data object SessionCleared : AuthOutput
}
```

Auth 子任务适配器负责转换：

```kotlin
fun AuthOutput.toRootEvent(): RootEvent {
    return when (this) {
        AuthOutput.AuthRequired ->
            RootEvent.AuthRequiredEvent

        is AuthOutput.Authenticated ->
            RootEvent.AuthenticatedEvent(userId)

        AuthOutput.SessionCleared ->
            RootEvent.SessionClearedEvent
    }
}
```

ConnectionTranslation 同样只输出根流程关心的事实：

```kotlin
sealed interface ConnectionOutput {
    data object LogoutRequested :
        ConnectionOutput

    data object Stopped : ConnectionOutput
}

fun ConnectionOutput.toRootEvent(): RootEvent {
    return when (this) {
        ConnectionOutput.LogoutRequested ->
            RootEvent.LogoutRequestedEvent

        ConnectionOutput.Stopped ->
            RootEvent.ConnectionStoppedEvent
    }
}
```

`ConnectionReadyEvent` 不是蓝牙业务结果，而是 `DefaultRootChildren`
成功创建 ConnectionTranslation 后报告的生命周期事实。

扫描列表变化、连接进度和局部失败仍由 ConnectionTranslation 自己处理，不进入 RootWorkflow。

具体通信不用再建立一条 Flow 总线。创建 Translation 时直接传入一个
类型明确的 reporter：

```kotlin
class AuthTranslation internal constructor(
    port: AuthPort,
    private val report: (AuthOutput) -> Unit
) : ViewModel() {
    // 本地状态机进入跨任务节点时调用一次 report(...)
}

class ConnectionTranslation internal constructor(
    userId: String,
    port: ConnectionPort,
    private val report: (ConnectionOutput) -> Unit
) : ViewModel() {
    // 本地清理完成时 report(ConnectionOutput.Stopped)
}
```

报告发生在本地状态迁移完成后，而不是由 UI 根据 `UiState` 猜测：

```text
Auth 状态机确认没有 Session
→ report(AuthRequired)

Auth 状态机确认 Session 已保存或恢复
→ report(Authenticated(userId))

Connection 状态机确认扫描、连接资源已停止
→ report(Stopped)
```

退出按钮的完整局部行为是：

```text
页面 submit(ConnectionIntent.Logout)
→ ConnectionTranslation 接受退出任务
→ report(ConnectionOutput.LogoutRequested)
→ ConnectionTranslation 继续执行自己的停止扫描、断开连接等 Effect
→ 清理完成后 report(ConnectionOutput.Stopped)
```

根总线不会再反向发送一次“停止 Connection”的命令，因此不会形成
`Logout → RootEvent → Logout` 的重复触发。

完整性由下面这条固定链路保证：

```text
DefaultRootChildren 先 bindRootReporter
→ 再创建 Translation
→ Translation 只能调用自己的强类型 reporter
→ Output 被立即转换成唯一的 RootEvent
→ WorkflowOrchestrator.dispatch 使用现有 Channel 排队
```

页面操作不进入这个 reporter，仍调用各自的 `submit(Intent)`；系统事实直接调用
`RootWorkflow.dispatch(RootEvent)`；Navigation 只消费 `RootState.screen()`，
不向总线回报“跳转完成”。因此不存在四路消息先合并、再担心漏收的问题。

## 10. RootEffectExecutor

继续实现项目现有的 `EffectExecutor`，不自己持有协程：

```kotlin
class RootEffectExecutor(
    private val children: RootChildren
) : EffectExecutor<RootEffect, RootEvent> {

    override fun execute(
        effect: RootEffect
    ): Flow<RootEvent> = flow {
        when (effect) {
            RootEffect.StartAuthEffect ->
                children.startAuth()

            is RootEffect.StartConnectionEffect ->
                children.startConnection(effect.userId)

            RootEffect.ClearAuthSessionEffect ->
                children.clearAuthSession()

            RootEffect.ReleaseConnectionEffect ->
                children.releaseConnection()
        }
    }
}
```

这些方法完成的是“发出命令”，完成结果由 Translation 通过绑定好的
reporter 异步送回，所以这个 `Flow` 不需要伪造同步 Result。

这里没有通用 `TaskRuntime`。`RootChildren` 的接口只给出根流程真正需要的
能力，具体持有方式放在实现中：

```kotlin
interface RootChildren : AutoCloseable {
    fun bindRootReporter(
        report: (RootEvent) -> Unit
    )

    suspend fun startAuth()
    suspend fun startConnection(userId: String)
    suspend fun clearAuthSession()
    suspend fun releaseConnection()

    fun authOrNull(): AuthTranslation?
    fun connectionOrNull(): ConnectionTranslation?
}
```

`DefaultRootChildren` 内部明确只有两个槽位，每个槽位使用独立的
`ViewModelStore`：

```kotlin
class DefaultRootChildren(
    private val authPort: AuthPort,
    private val connectionPort: ConnectionPort
) : RootChildren {
    private var report: ((RootEvent) -> Unit)? =
        null

    private val authStore = ViewModelStore()
    private var auth: AuthTranslation? = null

    private var connectionStore:
        ViewModelStore? = null
    private var connection:
        ConnectionTranslation? = null

    override fun bindRootReporter(
        report: (RootEvent) -> Unit
    ) {
        check(this.report == null)
        this.report = report
    }

    override suspend fun startAuth() {
        if (auth != null) return
        auth = createAuth(
            store = authStore,
            port = authPort,
            report = ::reportAuthOutput
        )
    }

    override suspend fun startConnection(
        userId: String
    ) {
        if (connection != null) return
        val store = ViewModelStore()
        connectionStore = store
        connection = createConnection(
            store = store,
            userId = userId,
            port = connectionPort,
            report = ::reportConnectionOutput
        )
        requireNotNull(report).invoke(
            RootEvent.ConnectionReadyEvent
        )
    }

    override suspend fun clearAuthSession() {
        auth?.submit(AuthIntent.Logout)
    }

    override suspend fun releaseConnection() {
        connectionStore?.clear()
        connectionStore = null
        connection = null
    }

    private fun reportAuthOutput(
        output: AuthOutput
    ) {
        requireNotNull(report).invoke(
            output.toRootEvent()
        )
    }

    private fun reportConnectionOutput(
        output: ConnectionOutput
    ) {
        requireNotNull(report).invoke(
            output.toRootEvent()
        )
    }

    // authOrNull、connectionOrNull、close 省略
}
```

`createAuth()` 和 `createConnection()` 内部使用 `ViewModelProvider`，factory
也是 `DefaultRootChildren` 的私有实现细节，不再由 `AppGraph` 暴露。

两个独立 `ViewModelStore` 的意义很具体：

```text
Auth 页面被替换
→ AuthStore 不 clear
→ AuthTranslation 保留

SessionClearedEvent
→ ReleaseConnectionEffect
→ 只 clear ConnectionStore
→ ConnectionTranslation.onCleared()
→ 它内部的 WorkflowOrchestrator.close()
```

后续如果出现第三个真实子任务，再增加第三个明确入口，不提前设计通用任务操作系统。

## 11. Navigation 只渲染 RootScreen

```kotlin
@Composable
fun RootNavigationEffect(
    navController: NavHostController,
    rootState: RootState
) {
    val screen = rootState.screen()

    LaunchedEffect(screen) {
        when (screen) {
            RootScreen.Auth ->
                navController.navigate(AUTH_ROUTE) {
                    popUpTo(SESSION_GRAPH) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }

            is RootScreen.Connection ->
                navController.navigate(
                    connectionRoute(screen.userId)
                ) {
                    popUpTo(AUTH_ROUTE) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
        }
    }
}
```

Navigation 不监听 AuthUiState 或 ConnectionUiState。

它只把：

```text
RootState.screen()
```

同步到 NavController。

## 12. AppMain 的目标形态

```kotlin
@Composable
fun AppMain(graph: AppGraph) {
    val navController = rememberNavController()
    val root = graph.rootWorkflow
    val rootState by root.state
        .collectAsStateWithLifecycle()

    RootNavigationEffect(
        navController = navController,
        rootState = rootState
    )

    NavHost(
        navController = navController,
        startDestination = SESSION_GRAPH
    ) {
        navigation(
            route = SESSION_GRAPH,
            startDestination = AUTH_ROUTE
        ) {
            composable(AUTH_ROUTE) {
                val auth = root.authOrNull()
                if (auth == null) {
                    AppLoading()
                } else {
                    AuthRoute(translation = auth)
                }
            }

            composable(CONNECTION_ROUTE) {
                val connection = requireNotNull(
                    root.connectionOrNull()
                ) {
                    "Connection 页面只能在 " +
                        "ConnectionReadyEvent 后进入"
                }
                ConnectionRoute(
                    translation = connection
                )
            }
        }
    }
}
```

AppMain 不创建 Translation、不持有 factory、不监听业务 UiState，也不决定何时跳转。
启动阶段 AuthTranslation 尚未创建的极短窗口只显示 `AppLoading()`；Connection
只有在 `ConnectionReadyEvent` 后才会成为目标页面，因此取不到实例属于违反根状态机约束，
应立即暴露错误，而不是静默创建第二个实例。

## 13. 两条完整流程

### 13.1 登录

```text
AppStartedEvent
→ RootState.RestoringSession
→ StartAuthEffect

AuthTranslation 报告 AuthRequired
→ AuthRequiredEvent
→ RootState.AwaitingAuthentication
→ 显示 Auth 页面

AuthTranslation 报告 Authenticated(userId)
→ AuthenticatedEvent
→ RootState.PreparingConnection
→ StartConnectionEffect

DefaultRootChildren 创建 ConnectionTranslation 后报告 Ready
→ ConnectionReadyEvent
→ RootState.Connecting
→ screen() 变为 Connection
→ Navigation 替换页面
→ AuthTranslation 保留，但不再被页面观察
```

### 13.2 退出登录

```text
Connection 页面提交 ConnectionIntent.Logout
→ ConnectionTranslation 报告 LogoutRequested
→ LogoutRequestedEvent
→ RootState.EndingConnection
→ ConnectionTranslation 继续完成本地清理

ConnectionTranslation 报告 Stopped
→ ConnectionStoppedEvent
→ RootState.ClearingSession(userId)
→ 仍显示 Connection 页面
→ ClearAuthSessionEffect
→ 要求保留的 AuthTranslation 清理 Session

AuthTranslation 报告 SessionCleared
→ SessionClearedEvent
→ RootState.AwaitingAuthentication
→ screen() 变为 Auth
→ Navigation 清理 Connection 页面
→ ReleaseConnectionEffect
→ requiredChildren() 删除 Connection
```

## 14. 实际文件位置

沿用现有拓扑，不增加 `bus/`、`runtime/` 或 `session/` 等平行目录：

```text
migratedev/src/main/kotlin/com/biosensor/migratedev/
├── decisioncore/
│   └── root/
│       └── RootDecision.kt
├── orchestrator/
│   └── root/
│       ├── RootWorkflow.kt
│       ├── RootEffectExecutor.kt
│       ├── RootChildren.kt
│       └── DefaultRootChildren.kt
├── translation/
│   ├── auth/
│   │   └── AuthTranslation.kt
│   └── connection/
│       └── ConnectionTranslation.kt
├── ui/
│   └── AppMain.kt
└── AppGraph.kt
```

各文件只承担以下内容：

```text
RootDecision.kt
→ RootState、RootEvent、RootEffect、RootDecisionCore
→ 与现有 AuthDecision.kt、ConnectionDecision.kt 的写法一致

RootChildren.kt
→ 只放根流程需要的子 Translation 能力接口

DefaultRootChildren.kt
→ 两个 ViewModelStore、私有 factory、Output 到 RootEvent 的适配

RootEffectExecutor.kt
→ RootEffect 到 RootChildren 方法的路由

RootWorkflow.kt
→ 组装现有 WorkflowOrchestrator，并对 AppMain 暴露根状态和当前 Translation

AuthTranslation.kt / ConnectionTranslation.kt
→ 在原文件内增加各自 Output 和 reporter
→ 不为了一个很短的协议再拆额外文件

AppMain.kt
→ NavHost、路由声明、RootState 到 Navigation 的同步

AppGraph.kt
→ 创建 Port、DefaultRootChildren 和 RootWorkflow
→ 删除 authTranslationFactory、connectionTranslationFactory
```

`RootNavigationEffect` 先保留为 `AppMain.kt` 内的私有函数。只有导航规则增长到
值得独立测试时再拆文件，当前不为了文件树对称而拆分。

## 15. 当前结论

这一版不再设计：

- PageSignal、TaskSignal、SystemSignal、NavigationSignal；
- SignalEnvelope；
- 通用 PCB；
- generation；
- TaskRuntime；
- NavigationCommand 回执；
- 通用任务注册表。

只保留：

```text
RootState
RootEvent
RootEffect
RootDecisionCore
RootWorkflow
RootChildren
RootNavigationEffect
```

它可以直接建立在现有 Translation、DecisionCore 和 EffectExecutor 之上，不需要先重写全部业务层。

可行性依据不是概念相似，而是现有代码已经具备对应接缝：

1. `WorkflowOrchestrator` 已经提供唯一 Channel、顺序 reduce 和异步 Effect，
   RootWorkflow 只组装它，不修改通用事件循环。
2. Auth 和 Connection 已经各自拥有 DecisionCore 与 EffectExecutor，
   根流程不接管登录、扫描和连接细节。
3. 两个 Translation 已经在 `onCleared()` 中关闭自己的 Orchestrator，
   独立 `ViewModelStore.clear()` 可以完成真正的单任务销毁。
4. Compose Navigation 已经具备 `popUpTo`，只需把跳转依据从两个 UiState
   改成唯一的 `RootState.screen()`。
5. `AppGraph` 最终只暴露 `rootWorkflow`；Translation factory、实例缓存和销毁
   都收进 `DefaultRootChildren`，不会继续污染组合根。

实施前只需把三条约束写成测试：

```text
同一个跨任务结果只能 report 一次
RootEvent 必须按进入 Channel 的顺序 reduce
SessionClearedEvent 前不得切 Auth、不得 release Connection
```

因此这不是引入新框架，而是利用现有状态机补上一层很薄的根协调。
