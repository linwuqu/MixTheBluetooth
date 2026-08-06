package com.biosensor.migratedev.port.adapter.bluetoothport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 竞态验证:复刻共享扫描(10 号文档)之前"单活动扫描互斥"的 scanDevices 语义,
 * 验证 flatMapLatest 的反复切换是否真的会撞上"蓝牙扫描已经在进行中"。
 *
 * 旧版语义([git 5d23fac 的 AndroidBluetoothPort]):
 *  - 全局只有一份活动扫描 activeScan,第二个订阅者直接 close("蓝牙扫描已经在进行中") 被拒绝;
 *  - 切换/失败/连接打断都会触发"清理活动扫描"。
 * 该测试在真实 kotlinx.coroutines 的 flatMapLatest 上复刻这一互斥,看切换 500 次会不会被拒。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanSwitchRaceInvestigationTest {

    /** 复刻旧版 AndroidBluetoothPort 的扫描互斥:单活动扫描,第二个订阅者被拒绝。 */
    private class SingleScanPort {
        private val lock = Any()
        private var active: Active? = null
        var rejections = 0

        fun scanDevices(): Flow<Int> = callbackFlow {
            val accepted = synchronized(lock) {
                if (active != null) false
                else {
                    active = Active(channel)
                    true
                }
            }
            if (!accepted) {
                rejections++
                close(IllegalStateException("蓝牙扫描已经在进行中"))
                return@callbackFlow
            }
            val emitting = launch {
                while (true) {
                    trySend(1)
                    delay(1)
                }
            }
            awaitClose {
                emitting.cancel()
                synchronized(lock) {
                    if (active?.output === channel) active = null
                }
            }
        }

        private class Active(val output: SendChannel<Int>)
    }

    @Test
    fun `flatMapLatest 反复切换不会撞上单活动扫描互斥`() = runTest {
        val port = SingleScanPort()
        val trigger = MutableStateFlow(0)
        val errors = mutableListOf<Throwable>()
        val collecting = launch {
            // 与真实结构一致:catch 在 inner 内部(scanFlow 的 catch 就在 scanDevices 之后)
            trigger.flatMapLatest { port.scanDevices().catch { errors += it } }
                .collect()
        }
        repeat(500) { i ->
            trigger.value = i + 1
            runCurrent()
        }
        collecting.cancel()

        assertEquals(0, port.rejections)
        assertEquals(0, errors.size)
    }

    /** 第一次订阅立即失败(模拟限流),之后恢复正常:值变化能恢复订阅,且不撞互斥。 */
    private class FailOncePort {
        private val lock = Any()
        private var active: Active? = null
        var rejections = 0
        var subscriptions = 0

        fun scanDevices(): Flow<Int> = callbackFlow {
            val accepted = synchronized(lock) {
                if (active != null) false
                else {
                    active = Active(channel)
                    true
                }
            }
            if (!accepted) {
                rejections++
                close(IllegalStateException("蓝牙扫描已经在进行中"))
                return@callbackFlow
            }
            subscriptions++
            if (subscriptions == 1) {
                // 模拟限流:刚启动就失败(旧版由 finishActiveScan 清理活动扫描)
                synchronized(lock) {
                    if (active?.output === channel) active = null
                }
                close(IllegalStateException("蓝牙扫描过于频繁"))
                return@callbackFlow
            }
            val emitting = launch {
                while (true) {
                    trySend(1)
                    delay(1)
                }
            }
            awaitClose {
                emitting.cancel()
                synchronized(lock) {
                    if (active?.output === channel) active = null
                }
            }
        }

        private class Active(val output: SendChannel<Int>)
    }

    @Test
    fun `失败后值变化可恢复订阅且不撞互斥`() = runTest {
        val port = FailOncePort()
        val trigger = MutableStateFlow(0)
        val errors = mutableListOf<Throwable>()
        val collecting = launch {
            trigger.flatMapLatest { port.scanDevices().catch { errors += it } }
                .collect()
        }
        runCurrent()          // 第一次订阅:模拟限流失败,异常被 inner 的 catch 吞掉
        assertEquals(1, port.subscriptions)
        assertEquals(1, errors.size)

        trigger.value = 1    // 用户刷新:产生新值 → 重新订阅
        runCurrent()
        assertEquals(2, port.subscriptions)
        assertEquals(0, port.rejections)
        collecting.cancel()
    }
}
