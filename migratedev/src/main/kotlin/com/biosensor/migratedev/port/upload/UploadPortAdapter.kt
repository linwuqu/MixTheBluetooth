package com.biosensor.migratedev.port.upload

import com.biosensor.migratedev.decisioncore.upload.CgmResult
import com.biosensor.migratedev.decisioncore.upload.PredictionStats
import com.biosensor.migratedev.decisioncore.upload.ResultPoint
import com.biosensor.migratedev.decisioncore.upload.ResultUnit
import com.biosensor.migratedev.decisioncore.upload.UploadEffect
import com.biosensor.migratedev.decisioncore.upload.UploadEvent
import com.biosensor.migratedev.port.adapter.localport.FileSpace
import com.biosensor.migratedev.port.adapter.localport.FileStore
import com.biosensor.migratedev.port.adapter.localport.KvStore
import com.biosensor.migratedev.port.adapter.remoteport.ApiError
import com.biosensor.migratedev.port.adapter.remoteport.HttpOutcome
import com.biosensor.migratedev.port.adapter.remoteport.HttpRemote
import com.biosensor.migratedev.port.auth.AuthSessionStore
import com.biosensor.migratedev.port.auth.ServerResponse
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import timber.log.Timber

interface UploadApi {
    @Multipart
    @POST("/api/cgm/v1/dataset/upload")
    suspend fun upload(
        @Header("token") token: String,
        @Part("identify") identify: RequestBody,
        @Part("parentId") parentId: RequestBody,
        @Part("fileSize") fileSize: RequestBody,
        @Part file: MultipartBody.Part,
    ): ServerResponse<DatasetUploadDto>

    @GET("/api/cgm/v1/jobs/{jobId}")
    suspend fun job(
        @Header("token") token: String, @Path("jobId") jobId: Long
    ): ServerResponse<JobDto>

    @GET("/api/cgm/v1/predictions/{resultId}/info")
    suspend fun resultInfo(
        @Header("token") token: String, @Path("resultId") resultId: Long
    ): ServerResponse<ResultInfoDto>

    @GET("/api/cgm/v1/predictions/{resultId}/curve")
    suspend fun resultCurve(
        @Header("token") token: String, @Path("resultId") resultId: Long
    ): ServerResponse<CurveDto>
}

// ── 后端报文:字段名照抄旧 app,少一个都不能算对 ──

data class DatasetUploadDto(
    val datasetId: Long? = null,
    val datasetStatus: String? = null,
    val rawAccountFileId: Long? = null,
    val rowCount: Int? = null,
    val unitCount: Int? = null,
    val jobId: Long? = null,
    val jobNo: String? = null,
    val jobStatus: String? = null,
    val resultId: Long? = null,
    val errorMsg: String? = null,
    val parseWarnings: List<String>? = null,
)

data class JobDto(
    val jobId: Long? = null,
    val jobNo: String? = null,
    val status: String? = null,
    val datasetId: Long? = null,
    val resultId: Long? = null,
    val errorMsg: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
)

data class ResultInfoDto(
    val min: Double? = null,
    val max: Double? = null,
    val mean: Double? = null,
    val std: Double? = null,
    val tir: Double? = null,
    val tirLow: Double? = null,
    val tirHigh: Double? = null,
)

data class CurveDto(
    val resultId: Long? = null, val unitCount: Int? = null, val units: List<UnitDto>? = null
)

data class UnitDto(
    val unit: Int = 0,
    @SerializedName("unit_title") val unitTitle: String? = null,
    @SerializedName("point_count") val pointCount: Int = 0,
    val mard: Double = 0.0,
    val points: List<PointDto>? = null,
)

data class PointDto(
    val index: Int = 0,
    val time: Int = 0,
    val rawTime: String? = null,
    val predicted: Double = 0.0,
    val actual: Double? = null,
)

/**
 * 上传适配器:一次 effect 一次往返。
 *
 * 三件事按状态机的编排做:推文件、查任务、拉结果。轮询间隔在这里(传输节奏),
 * 查几次在状态机(决策);本地文件缺失不发任何请求,直接回 [UploadEvent.FileMissing]。
 */
class UploadPortAdapter(
    private val kv: KvStore,
    private val files: FileStore,
    private val http: HttpRemote,
    private val clock: Clock = Clock.systemUTC(),
    private val gson: Gson = Gson(),
    /** 查任务前的等待。旧 app 是 1500ms,测试里设 0 免得空等。 */
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
) : UploadPort {

    private val api: UploadApi by lazy { http.api(UploadApi::class.java) }

    /** 日志用的同一套配置,只是缩进:换字段名规则时两处不会走偏。 */
    private val prettyGson: Gson = gson.newBuilder().setPrettyPrinting().create()

    override fun execute(effect: UploadEffect): Flow<UploadEvent> = flow {
        emit(run(effect))
    }.flowOn(Dispatchers.IO)

    private suspend fun run(effect: UploadEffect): UploadEvent {
        // 没登录就没凭据,连本地文件都不必读
        val token = AuthSessionStore.token(kv, gson, clock)
        val event = if (token == null) {
            UploadEvent.TransferFailed("登录状态已失效,请重新登录")
        } else {
            when (effect) {
                is UploadEffect.UploadDataset -> upload(token, effect.filePath)

                is UploadEffect.PollJob -> {
                    delay(pollIntervalMillis)
                    poll(token, effect.jobId)
                }

                is UploadEffect.FetchResult -> fetch(
                    token, effect.jobId, effect.datasetId, effect.resultId
                )
            }
        }
        Timber.tag(UPLOAD_TAG)
            .i("命令=%s 结果=%s", effect::class.simpleName, event::class.simpleName)
        return event
    }

    // ── 推文件 ──

    private suspend fun upload(token: String, filePath: String): UploadEvent {
        val bytes = try {
            files.read(FileSpace.RECEIVED, filePath).buffer().use { it.readByteArray() }
        } catch (_: FileNotFoundException) {
            return UploadEvent.FileMissing
        } catch (failure: IOException) {
            return UploadEvent.TransferFailed("读取本地缓存失败: ${failure.message}")
        }
        val name = filePath.substringAfterLast('/')
        // identify 是后端认这份文件的标识:文件名 + 字节数 + 时间戳
        val response = when (val outcome = http.invoke {
            api.upload(
                token = token,
                identify = "${name}_${bytes.size}_${clock.millis()}".toRequestBody(TEXT_PLAIN),
                parentId = PARENT_ID.toRequestBody(TEXT_PLAIN),
                fileSize = bytes.size.toString().toRequestBody(TEXT_PLAIN),
                file = MultipartBody.Part.createFormData(
                    "file", name, bytes.toRequestBody(OCTET_STREAM)
                ),
            )
        }) {
            is HttpOutcome.Completed -> outcome.data
            is HttpOutcome.Failure -> return outcome.toTransferFailed()
        }
        val data = response.data
        // 四道闸:业务码正常、有内容、数据集校验通过、给了任务号
        return when {
            !response.isOk() -> UploadEvent.DatasetRejected(response.msg ?: "上传被服务端拒绝")

            data == null -> UploadEvent.DatasetRejected("上传响应为空")

            !data.datasetStatus.equals(DATASET_VALID, ignoreCase = true) -> UploadEvent.DatasetRejected(
                data.errorMsg ?: "数据集校验未通过(datasetStatus=${data.datasetStatus})"
            )

            data.jobId == null -> UploadEvent.DatasetRejected(data.errorMsg ?: "上传响应没有任务号")

            else -> UploadEvent.DatasetAccepted(data.jobId, data.datasetId ?: 0L)
        }
    }

    // ── 查任务 ──

    private suspend fun poll(token: String, jobId: Long): UploadEvent {
        val response = when (val outcome = http.invoke { api.job(token, jobId) }) {
            is HttpOutcome.Completed -> outcome.data
            is HttpOutcome.Failure -> return outcome.toTransferFailed()
        }
        val data = response.data
        // 查不通与"还没跑完"都算一次:状态机那份预算管着,这里不区分
        if (!response.isOk() || data == null) {
            return UploadEvent.TransferFailed(response.msg ?: "任务查询失败")
        }
        return when (data.status?.uppercase()) {
            STATUS_FAILED -> UploadEvent.JobFailed(data.errorMsg ?: "后端任务失败")

            STATUS_SUCCESS -> data.resultId?.let { UploadEvent.JobSucceeded(it) }
                ?: UploadEvent.JobFailed("任务已完成但没有结果号")

            else -> UploadEvent.JobPending(data.status.orEmpty())
        }
    }

    // ── 拉结果:info 与 curve 各一条,两条都拿不回来才算失败 ──

    private suspend fun fetch(
        token: String, jobId: Long, datasetId: Long, resultId: Long
    ): UploadEvent {
        val (info, curve) = coroutineScope {
            val info = async { http.invoke { api.resultInfo(token, resultId) } }
            val curve = async { http.invoke { api.resultCurve(token, resultId) } }
            info.await() to curve.await()
        }
        // 报文整份原样打进日志:字段名对不对、少没少字段、单位是什么,只有看原文才知道。
        // DTO 是照旧 app 抄的,抄错了不会报错,只会安静地少一块数据——所以宁可在日志里多一屏。
        Timber.tag(UPLOAD_TAG).i("结果原始报文 info:\n%s", info.prettyJson())
        Timber.tag(UPLOAD_TAG).i("结果原始报文 curve:\n%s", curve.prettyJson())
        val stats = info.dataOrNull()?.toStats()
        val curves = curve.dataOrNull()
        if (stats == null && curves == null) {
            return UploadEvent.TransferFailed(
                info.failureReason() ?: curve.failureReason() ?: "结果拉取失败"
            )
        }
        return UploadEvent.ResultFetched(merge(jobId, datasetId, resultId, stats, curves))
    }

    /** 传输成功且业务码正常时取出 data,否则 null。 */
    private fun <T> HttpOutcome<ServerResponse<T>>.dataOrNull(): T? = when (this) {
        is HttpOutcome.Completed -> data.takeIf { it.isOk() }?.data
        is HttpOutcome.Failure -> null
    }

    /** 原始报文原样缩进打印,连信封(code / success / msg)一起,不做任何裁剪。 */
    private fun HttpOutcome<*>.prettyJson(): String =
        if (this is HttpOutcome.Completed) prettyGson.toJson(data)
        else failureReason() ?: "请求失败"

    /** 传输失败的原因;传输成功时 null。 */
    private fun HttpOutcome<*>.failureReason(): String? =
        (this as? HttpOutcome.Failure)?.error?.let { error ->
            when (error) {
                ApiError.TimeoutError -> "请求超时"
                is ApiError.UnreachableError -> error.msg
                is ApiError.HttpError -> "服务端返回 ${error.code}"
                is ApiError.UnKnowError -> error.msg
            }
        }

    private fun HttpOutcome<*>.toTransferFailed(): UploadEvent =
        UploadEvent.TransferFailed(failureReason() ?: "请求失败")

    private fun ResultInfoDto.toStats(): PredictionStats? {
        if (min == null && max == null && mean == null && std == null) return null
        return PredictionStats(min ?: 0.0, max ?: 0.0, mean ?: 0.0, std ?: 0.0)
    }

    private fun merge(
        jobId: Long, datasetId: Long, resultId: Long, stats: PredictionStats?, curve: CurveDto?,
    ): CgmResult {
        val units = curve?.units.orEmpty().map { unit ->
            ResultUnit(
                unit = unit.unit,
                title = unit.unitTitle,
                pointCount = unit.pointCount,
                mard = unit.mard,
                points = unit.points.orEmpty().map { point ->
                    ResultPoint(
                        index = point.index,
                        time = point.time,
                        rawTime = point.rawTime,
                        predicted = point.predicted,
                        actual = point.actual,
                    )
                },
            )
        }
        return CgmResult(
            resultId = resultId,
            jobId = jobId,
            datasetId = datasetId,
            unitCount = curve?.unitCount ?: units.size,
            pointCount = units.sumOf(ResultUnit::pointCount),
            stats = stats,
            units = units,
        )
    }

    companion object {
        /** 查任务前的等待:旧 app 的 POLL_DELAY_MS。 */
        const val POLL_INTERVAL_MILLIS = 1_500L

        /** 旧 app 的 DEFAULT_PARENT_ID。 */
        private const val PARENT_ID = "0"
        private const val DATASET_VALID = "VALID"
        private const val STATUS_SUCCESS = "SUCCESS"
        private const val STATUS_FAILED = "FAILED"
        private const val UPLOAD_TAG = "Upload.Port"
        private val TEXT_PLAIN = "text/plain".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
