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

class OkHttpRemote(
    baseUrl: String,
    okHttp: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build(),
    private val timeoutMillis: Long = 30_000
) : HttpRemote {
    private val retrofit = Retrofit.Builder().baseUrl(baseUrl).client(okHttp).addConverterFactory(
        GsonConverterFactory.create()
    ).build()

    override fun <T> api(apiClass: Class<T>): T = retrofit.create(apiClass)

    override suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T> = try {
        HttpOutcome.Completed(withTimeout(timeoutMillis.milliseconds) { block() })
    } catch (_: TimeoutCancellationException) {
        HttpOutcome.Failure(ApiError.TimeoutError)
    } catch (e: IOException) {
        HttpOutcome.Failure(ApiError.UnreachableError(e.message ?: "网络错误"))
    } catch (e: HttpException) {
        HttpOutcome.Failure(ApiError.HttpError(e.code(), e.message ?: "服务端错误"))
    } catch (e: Exception) {
        HttpOutcome.Failure(ApiError.UnKnowError(e.message ?: "未知错误"))
    }
}
