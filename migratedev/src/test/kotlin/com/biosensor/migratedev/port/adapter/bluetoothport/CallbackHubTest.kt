package com.biosensor.migratedev.port.adapter.bluetoothport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分发器单元测试:用真实 callbackFlow 充当订阅者(ProducerScope),验证注册/注销/广播语义。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallbackHubTest {

    @Test
    fun `dispatch reaches all registered handlers`() = runTest {
        val hub = CallbackHub<Int>()
        val a = mutableListOf<Int>()
        val b = mutableListOf<Int>()
        val flowA: Flow<Int> = callbackFlow {
            hub.register(this) { a += it }
            awaitClose { hub.unregister(this) }
        }
        val flowB: Flow<Int> = callbackFlow {
            hub.register(this) { b += it }
            awaitClose { hub.unregister(this) }
        }
        val jobA = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flowA.collect { } }
        val jobB = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flowB.collect { } }
        runCurrent()
        hub.dispatch(1)
        hub.dispatch(2)
        runCurrent()

        assertEquals(listOf(1, 2), a)
        assertEquals(listOf(1, 2), b)
        jobA.cancelAndJoin()
        jobB.cancelAndJoin()
    }

    @Test
    fun `unregister stops delivery`() = runTest {
        val hub = CallbackHub<Int>()
        val received = mutableListOf<Int>()
        val flow: Flow<Int> = callbackFlow {
            hub.register(this) { received += it }
            awaitClose { hub.unregister(this) }
        }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
        runCurrent()
        hub.dispatch(1)
        job.cancelAndJoin()
        runCurrent()   // awaitClose 已执行(unregister)
        hub.dispatch(2)
        runCurrent()

        assertEquals(listOf(1), received)
    }

    @Test
    fun `same scope re-register replaces handler`() = runTest {
        // 同一 callbackFlow 内两次 register:键=scope,后注册覆盖先注册
        val hub = CallbackHub<Int>()
        val received = mutableListOf<Int>()
        val flow: Flow<Int> = callbackFlow {
            hub.register(this) { received += it * 10 }
            hub.register(this) { received += it }
            awaitClose { hub.unregister(this) }
        }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { } }
        runCurrent()
        hub.dispatch(5)
        runCurrent()

        assertEquals(listOf(5), received)
        job.cancelAndJoin()
    }
}
