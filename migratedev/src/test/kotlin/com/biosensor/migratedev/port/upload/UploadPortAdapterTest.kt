package com.biosensor.migratedev.port.upload

import com.biosensor.migratedev.decisioncore.auth.AuthSession
import com.biosensor.migratedev.decisioncore.auth.User
import com.biosensor.migratedev.decisioncore.upload.CgmResult
import com.biosensor.migratedev.decisioncore.upload.PredictionStats
import com.biosensor.migratedev.decisioncore.upload.ResultPoint
import com.biosensor.migratedev.decisioncore.upload.ResultUnit
import com.biosensor.migratedev.decisioncore.upload.UploadEffect
import com.biosensor.migratedev.decisioncore.upload.UploadEvent
import com.biosensor.migratedev.port.InMemoryKvStore
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.file.OkioFileStore
import com.biosensor.migratedev.port.adapter.remoteport.ApiError
import com.biosensor.migratedev.port.adapter.remoteport.HttpOutcome
import com.biosensor.migratedev.port.adapter.remoteport.HttpRemote
import com.biosensor.migratedev.port.auth.AuthSessionStore
import com.biosensor.migratedev.port.auth.ServerResponse
import com.google.gson.Gson
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class UploadPortAdapterTest {

    private val path = "cgm/AA_01.txt"
    private val now = 1_700_000_000_000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)

    /** 含一个多字节字符:identify 里的长度必须是字节数,不是字符数。 */
    private val cacheText = "2026-07-15 12:00:00 5.5 正常\n"

    private val kv = InMemoryKvStore()
    private val gson = Gson()
    private lateinit var fileSystem: FakeFileSystem
    private lateinit var files: OkioFileStore

    @Before
    fun setUp() {
        fileSystem = FakeFileSystem()
        files = OkioFileStore(
            fileSystem = fileSystem,
            roots = FileSpace.entries.associateWith { "/app/${it.name.lowercase()}".toPath() }
        )
        writeCacheFile(cacheText)
        kv.values[AuthSessionStore.KEY] = gson.toJson(
            AuthSession(User("7", "alice", "13800000000", null), "token-1", null)
        )
    }

    private fun writeCacheFile(text: String) {
        fileSystem.createDirectories("/app/received/cgm".toPath())
        fileSystem.write("/app/received/$path".toPath()) { writeUtf8(text) }
    }

    private fun adapter(http: FakeHttpRemote) = UploadPortAdapter(
        kv = kv, files = files, http = http, clock = clock, gson = gson, pollIntervalMillis = 0
    )

    private suspend fun upload(api: FakeUploadApi, http: FakeHttpRemote = FakeHttpRemote(api)) =
        adapter(http).execute(UploadEffect.UploadDataset(path)).first()

    private suspend fun poll(api: FakeUploadApi, http: FakeHttpRemote = FakeHttpRemote(api)) =
        adapter(http).execute(UploadEffect.PollJob(JOB_ID)).first()

    private suspend fun fetch(api: FakeUploadApi, http: FakeHttpRemote = FakeHttpRemote(api)) =
        adapter(http).execute(UploadEffect.FetchResult(JOB_ID, DATASET_ID, RESULT_ID)).first()

    // ── 推文件 ──

    @Test
    fun `upload posts the cache file with token and identify`() = runTest {
        val api = FakeUploadApi()

        val result = upload(api)

        assertEquals(UploadEvent.DatasetAccepted(JOB_ID, DATASET_ID), result)
        assertEquals("token-1", api.token)
        assertEquals("0", api.parentId)
        assertEquals("AA_01.txt", api.fileName)
        // identify = 文件名_字节数_时间戳
        assertEquals(
            "AA_01.txt_${cacheText.toByteArray(Charsets.UTF_8).size}_$now", api.identify
        )
        assertEquals(cacheText.toByteArray(Charsets.UTF_8).size.toString(), api.fileSize)
        assertEquals(cacheText, api.fileText)
    }

    @Test
    fun `missing cache file reports before any request`() = runTest {
        fileSystem.delete("/app/received/$path".toPath())
        val api = FakeUploadApi()

        val result = upload(api)

        assertEquals(UploadEvent.FileMissing, result)
        assertNull("不该发出任何请求", api.token)
    }

    @Test
    fun `upload without a session fails before reading the file`() = runTest {
        kv.values.clear()
        val api = FakeUploadApi()

        val result = upload(api)

        assertEquals(UploadEvent.TransferFailed("登录状态已失效,请重新登录"), result)
        assertNull(api.token)
    }

    @Test
    fun `expired session is treated as no session`() = runTest {
        kv.values[AuthSessionStore.KEY] = gson.toJson(
            AuthSession(User("7", "alice", "13800000000", null), "token-1", now - 1)
        )

        val result = upload(FakeUploadApi())

        assertEquals(UploadEvent.TransferFailed("登录状态已失效,请重新登录"), result)
    }

    @Test
    fun `dataset rejected by business code`() = runTest {
        val api = FakeUploadApi().apply {
            uploadResponse = ServerResponse(1001, false, "文件格式不支持", null)
        }

        assertEquals(UploadEvent.DatasetRejected("文件格式不支持"), upload(api))
    }

    @Test
    fun `dataset that is not valid is rejected with the backend message`() = runTest {
        val api = FakeUploadApi().apply {
            uploadResponse = ServerResponse(
                code = 0, success = true, msg = null,
                data = DatasetUploadDto(datasetStatus = "INVALID", errorMsg = "缺少设备号")
            )
        }

        assertEquals(UploadEvent.DatasetRejected("缺少设备号"), upload(api))
    }

    @Test
    fun `dataset without a job id is rejected`() = runTest {
        val api = FakeUploadApi().apply {
            uploadResponse = ServerResponse(
                code = 0, success = true, msg = null,
                data = DatasetUploadDto(datasetStatus = "VALID", jobId = null)
            )
        }

        assertEquals(UploadEvent.DatasetRejected("上传响应没有任务号"), upload(api))
    }

    @Test
    fun `upload transport failure is reported`() = runTest {
        val http = FakeHttpRemote(FakeUploadApi())
        http.enqueue(HttpOutcome.Failure(ApiError.HttpError(500, "boom")))

        assertEquals(UploadEvent.TransferFailed("服务端返回 500"), upload(FakeUploadApi(), http))
    }

    // ── 查任务 ──

    @Test
    fun `pending job is reported as pending`() = runTest {
        val api = FakeUploadApi().apply {
            jobResponse = ServerResponse(0, true, null, JobDto(status = "RUNNING"))
        }

        assertEquals(UploadEvent.JobPending("RUNNING"), poll(api))
    }

    @Test
    fun `job without a status is reported as pending`() = runTest {
        val api = FakeUploadApi().apply {
            jobResponse = ServerResponse(0, true, null, JobDto(status = null))
        }

        assertEquals(UploadEvent.JobPending(""), poll(api))
    }

    @Test
    fun `successful job reports the result id`() = runTest {
        val api = FakeUploadApi().apply {
            jobResponse = ServerResponse(
                0, true, null, JobDto(status = "SUCCESS", resultId = RESULT_ID)
            )
        }

        assertEquals(UploadEvent.JobSucceeded(RESULT_ID), poll(api))
        assertEquals("token-1", api.token)
    }

    @Test
    fun `successful job without a result id is a failure`() = runTest {
        val api = FakeUploadApi().apply {
            jobResponse = ServerResponse(
                0, true, null, JobDto(status = "SUCCESS", resultId = null)
            )
        }

        assertEquals(UploadEvent.JobFailed("任务已完成但没有结果号"), poll(api))
    }

    @Test
    fun `failed job carries the backend reason`() = runTest {
        val api = FakeUploadApi().apply {
            jobResponse = ServerResponse(
                0, true, null, JobDto(status = "FAILED", errorMsg = "曲线点太少")
            )
        }

        assertEquals(UploadEvent.JobFailed("曲线点太少"), poll(api))
    }

    @Test
    fun `job lookup failure counts as a transfer failure`() = runTest {
        // 业务码不正常也算一次查询失败,交给状态机那份次数预算
        val api = FakeUploadApi().apply {
            jobResponse = ServerResponse(500, false, "内部错误", null)
        }

        assertEquals(UploadEvent.TransferFailed("内部错误"), poll(api))
    }

    // ── 拉结果 ──

    @Test
    fun `fetch merges info and curve into one result`() = runTest {
        val api = FakeUploadApi()

        val result = fetch(api)

        assertEquals(
            UploadEvent.ResultFetched(
                CgmResult(
                    resultId = RESULT_ID, jobId = JOB_ID, datasetId = DATASET_ID,
                    unitCount = 1, pointCount = 2,
                    stats = PredictionStats(3.0, 9.0, 6.0, 1.0),
                    units = listOf(
                        ResultUnit(
                            unit = 0, title = "第一单元", pointCount = 2, mard = 0.1,
                            points = listOf(
                                ResultPoint(1, 60, "2026-07-21 12:10:45", 5.5, 5.0),
                                ResultPoint(2, 70, "2026-07-21 12:10:55", 6.5, null)
                            )
                        )
                    )
                )
            ),
            result
        )
    }

    @Test
    fun `fetch keeps the curve when only info fails`() = runTest {
        val api = FakeUploadApi().apply {
            infoResponse = ServerResponse(500, false, "内部错误", null)
        }

        val result = fetch(api) as UploadEvent.ResultFetched

        assertNull(result.result.stats)
        assertEquals(2, result.result.pointCount)
    }

    @Test
    fun `fetch keeps the stats when only the curve fails`() = runTest {
        val api = FakeUploadApi().apply {
            curveResponse = ServerResponse(500, false, "内部错误", null)
        }

        val result = fetch(api) as UploadEvent.ResultFetched

        assertEquals(PredictionStats(3.0, 9.0, 6.0, 1.0), result.result.stats)
        assertTrue(result.result.units.isEmpty())
    }

    @Test
    fun `fetch fails only when both endpoints fail`() = runTest {
        val http = FakeHttpRemote(FakeUploadApi())
        http.enqueue(HttpOutcome.Failure(ApiError.TimeoutError))
        http.enqueue(HttpOutcome.Failure(ApiError.TimeoutError))

        assertEquals(UploadEvent.TransferFailed("请求超时"), fetch(FakeUploadApi(), http))
    }

    @Test
    fun `fetch logs the raw payloads pretty printed`() = runTest {
        val tree = RecordingTree()
        Timber.plant(tree)
        try {
            fetch(FakeUploadApi())
        } finally {
            Timber.uproot(tree)
        }

        // Gson 的换行取平台分隔符(Windows 上是 \r\n),断言前先归一
        val curve = tree.messages.single { it.contains("curve:") }.replace("\r\n", "\n")
        // 打的是后端原文,不是我们的模型:字段名必须是 unit_title / point_count,
        // 而 rawTime 在旧 app 的 DTO 里就是驼峰(只加 @SerializedName 为了显式),别想当然改成下划线
        assertTrue(curve.contains("\"unit_title\": \"第一单元\""))
        assertTrue(curve.contains("\"point_count\": 2"))
        assertTrue(curve.contains("\"rawTime\": \"2026-07-21 12:10:45\""))
        // 缩进过,不是一行压死
        assertTrue("应该是缩进过的 JSON:\n$curve", curve.contains("\n  "))
        val info = tree.messages.single { it.contains("info:") }.replace("\r\n", "\n")
        assertTrue(info.contains("\"mean\": 6.0"))
        // 信封也一起打,不只是 data
        assertTrue(info.contains("\"success\": true"))
    }

    /** 把 Timber 的输出截下来,免得为了断言日志内容去引真机。 */
    private class RecordingTree : Timber.Tree() {
        val messages = mutableListOf<String>()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            messages += message
        }
    }

    private class FakeUploadApi : UploadApi {
        var token: String? = null
        var identify: String? = null
        var parentId: String? = null
        var fileSize: String? = null
        var fileName: String? = null
        var fileText: String? = null

        var uploadResponse: ServerResponse<DatasetUploadDto> = ServerResponse(
            0, true, null,
            DatasetUploadDto(
                datasetId = DATASET_ID, datasetStatus = "VALID",
                rowCount = 2, unitCount = 1, jobId = JOB_ID, jobStatus = "QUEUED"
            )
        )
        var jobResponse: ServerResponse<JobDto> = ServerResponse(
            0, true, null, JobDto(jobId = JOB_ID, status = "SUCCESS", resultId = RESULT_ID)
        )
        var infoResponse: ServerResponse<ResultInfoDto> = ServerResponse(0, true, null, INFO_DTO)
        var curveResponse: ServerResponse<CurveDto> = ServerResponse(0, true, null, CURVE_DTO)

        override suspend fun upload(
            token: String,
            identify: RequestBody,
            parentId: RequestBody,
            fileSize: RequestBody,
            file: MultipartBody.Part,
        ): ServerResponse<DatasetUploadDto> {
            this.token = token
            this.identify = identify.text()
            this.parentId = parentId.text()
            this.fileSize = fileSize.text()
            this.fileName = file.headers?.get("Content-Disposition")
                ?.substringAfter("filename=\"")?.substringBefore('"')
            this.fileText = Buffer().also { file.body.writeTo(it) }.readUtf8()
            return uploadResponse
        }

        override suspend fun job(token: String, jobId: Long): ServerResponse<JobDto> {
            this.token = token
            return jobResponse
        }

        override suspend fun resultInfo(
            token: String, resultId: Long
        ): ServerResponse<ResultInfoDto> = infoResponse

        override suspend fun resultCurve(
            token: String, resultId: Long
        ): ServerResponse<CurveDto> = curveResponse
    }

    /**
     * 两种模式,与 AuthPortAdapterRemoteTest 同款:
     * 预置了 HttpOutcome(enqueue)时直接返回,不执行 block;未预置时执行 block 包成 Completed。
     */
    private class FakeHttpRemote(private val apiImpl: UploadApi) : HttpRemote {
        private val canned = ConcurrentLinkedQueue<HttpOutcome<*>>()

        fun enqueue(outcome: HttpOutcome<*>) {
            canned.add(outcome)
        }

        override fun <T> api(apiClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return apiImpl as T
        }

        override suspend fun <T> invoke(block: suspend () -> T): HttpOutcome<T> {
            val next = canned.poll()
            @Suppress("UNCHECKED_CAST")
            return if (next != null) next as HttpOutcome<T> else HttpOutcome.Completed(block())
        }
    }

    private companion object {
        const val JOB_ID = 42L
        const val DATASET_ID = 7L
        const val RESULT_ID = 99L

        val INFO_DTO = ResultInfoDto(min = 3.0, max = 9.0, mean = 6.0, std = 1.0)
        val CURVE_DTO = CurveDto(
            resultId = RESULT_ID, unitCount = 1,
            units = listOf(
                UnitDto(
                    unit = 0, unitTitle = "第一单元", pointCount = 2, mard = 0.1,
                    points = listOf(
                        PointDto(1, 60, "2026-07-21 12:10:45", 5.5, 5.0),
                        PointDto(2, 70, "2026-07-21 12:10:55", 6.5, null)
                    )
                )
            )
        )
    }
}

/** 表单字段是 RequestBody,取回原文得先写进 Buffer。 */
private fun RequestBody.text(): String = Buffer().also { writeTo(it) }.readUtf8()

