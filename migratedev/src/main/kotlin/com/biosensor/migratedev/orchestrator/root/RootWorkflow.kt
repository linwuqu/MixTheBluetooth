package com.biosensor.migratedev.orchestrator.root

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.biosensor.migratedev.decisioncore.root.RootDecisionCore
import com.biosensor.migratedev.decisioncore.root.RootEffect
import com.biosensor.migratedev.decisioncore.root.RootEvent
import com.biosensor.migratedev.decisioncore.root.RootState
import com.biosensor.migratedev.orchestrator.WorkflowOrchestrator
import com.biosensor.migratedev.port.auth.AuthPort
import com.biosensor.migratedev.port.connection.ConnectionPort
import com.biosensor.migratedev.translation.auth.AuthIntent
import com.biosensor.migratedev.translation.auth.AuthOutput
import com.biosensor.migratedev.translation.auth.AuthTranslation
import com.biosensor.migratedev.translation.connection.ConnectionOutput
import com.biosensor.migratedev.translation.connection.ConnectionTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * 根工作流管理的是父编排器 RootWorkflow.orchestrator
 * 对于不同的业务域（登录、蓝牙连接等）他们各自由不同的子编排器进行管理（AuthTranslation.orchestrator等）
 * 他们是天然隔离的 但是这些子业务需要进行内容交换（session信息）、资源择机释放就使用父编排器 RootWorkflow.orchestrator进行管理
 *
 * 父子编排器并不需要双向通信
 * 父 -> 子:
 * 父通过调用子编排器的方法来下达指令 auth.submit(AuthIntent.Logout)
 * 子 -> 父:
 * 子通过构造时注入的 report 回调 向上输出事件 父在 report 函数中将子输出转化为自己的事件并 dispatch
 *
 * 我们在这里使用ViewModel进行子编排器的管理 即 AuthTranslation: ViewModel() 以此来确保 AutoClose
 * 同时 RootWorkflow: AutoCloseable 亦可以及时回收
 */
class RootWorkflow(
    private val authPort: AuthPort,
    private val connectionPort: ConnectionPort,
    scope: CoroutineScope
) : AutoCloseable {

    private val authStore = ViewModelStore()
    private var auth: AuthTranslation? = null

    private var connectionStore: ViewModelStore? = null
    private var connection: ConnectionTranslation? = null

    private val orchestrator = WorkflowOrchestrator(
        initialState = RootState.Starting,
        decisionCore = RootDecisionCore,
        effectExecutor = ::execute,
        scope = scope,
        logTag = "Root.Workflow"
    )

    val state: StateFlow<RootState> = orchestrator.state

    init {
        orchestrator.dispatch(RootEvent.AppStartedEvent)
    }

    fun authOrNull(): AuthTranslation? = auth

    fun connectionOrNull(): ConnectionTranslation? = connection

    private fun execute(effect: RootEffect): Flow<RootEvent> = flow {
        when (effect) {
            RootEffect.StartAuthEffect -> {
                startAuth()
                emit(RootEvent.AuthStartedEvent)
            }

            is RootEffect.StartConnectionEffect -> {
                startConnection(effect.userId)
                emit(RootEvent.ConnectionStartedEvent)
            }

            RootEffect.ClearAuthSessionEffect -> requireNotNull(auth)
                .submit(AuthIntent.Logout)

            RootEffect.ReleaseConnectionEffect -> {
                releaseConnection()
                emit(RootEvent.ConnectionReleasedEvent)
            }
        }
    }

    private fun startAuth() {
        if (auth != null) return
        auth = ViewModelProvider(
            authStore, AuthTranslation.factory(
                port = authPort, report = ::report
            )
        )[AuthTranslation::class.java]
    }

    private fun startConnection(userId: String) {
        check(connection == null) {
            "ConnectionTranslation 已存在"
        }
        val store = ViewModelStore()
        connectionStore = store
        connection = ViewModelProvider(
            store, ConnectionTranslation.factory(
                userId = userId, port = connectionPort, report = ::report
            )
        )[ConnectionTranslation::class.java]
    }

    private fun releaseConnection() {
        connectionStore?.clear()
        connectionStore = null
        connection = null
    }

    private fun report(output: AuthOutput) {
        orchestrator.dispatch(
            when (output) {
                is AuthOutput.Authenticated -> RootEvent.AuthenticatedEvent(output.userId)

                AuthOutput.SessionCleared -> RootEvent.AuthSessionClearedEvent
            }
        )
    }

    private fun report(output: ConnectionOutput) {
        orchestrator.dispatch(
            when (output) {
                ConnectionOutput.LogoutRequested -> RootEvent.LogoutRequestedEvent

                ConnectionOutput.Stopped -> RootEvent.ConnectionStoppedEvent
            }
        )
    }

    override fun close() {
        orchestrator.close()
        connectionStore?.clear()
        authStore.clear()
        connectionStore = null
        connection = null
        auth = null
    }
}
