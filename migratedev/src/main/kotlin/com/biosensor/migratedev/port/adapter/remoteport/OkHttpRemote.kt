package com.biosensor.migratedev.port.adapter.remoteport

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.time.Duration.Companion.milliseconds

/**
 * HttpRemote 的 OkHttp 实现:共享一个 Retrofit 实例,业务适配器用它创建各自的 typed 接口。
 */
class OkHttpRemote(
    baseUrl: String,
    okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val timeoutMillis: Long = 30_000
) : HttpRemote {

    private val retrofit = Retrofit.Builder().baseUrl(baseUrl).client(okHttp)
        .addConverterFactory(GsonConverterFactory.create()).build()

    override fun <T> api(apiClass: Class<T>): T = retrofit.create(apiClass)

    override suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T> = try {
        HttpOutcome.Success(withTimeout(timeoutMillis.milliseconds) { block() })
    } catch (_: TimeoutCancellationException) {
        HttpOutcome.Timeout
    } catch (failure: IOException) {
        HttpOutcome.Network("网络错误：${failure.message ?: "未知"}")
    } catch (failure: HttpException) {
        HttpOutcome.Http(failure.code(), "服务端错误：${failure.code()}")
    }
}
