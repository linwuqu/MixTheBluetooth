package com.biosensor.migratedev.port.patch

import com.biosensor.migratedev.database.PatchBinding as PatchBindingRow
import com.biosensor.migratedev.decisioncore.patch.PatchBinding
import com.biosensor.migratedev.decisioncore.patch.PatchCode
import com.biosensor.migratedev.decisioncore.patch.PatchEffect
import com.biosensor.migratedev.decisioncore.patch.PatchEvent
import com.biosensor.migratedev.port.adapter.localport.SqlStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

/**
 * 贴片绑定的落库适配器。
 *
 * 只做两件事:把 effect 翻成一条 SQL、把库里那行翻成领域类型。去重靠主键,
 * 不靠这里查一遍再决定插不插——同片重扫是 `INSERT OR REPLACE`,只刷新 `boundAtMillis`。
 */
class PatchPortAdapter(
    private val sql: SqlStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PatchPort {

    override fun execute(effect: PatchEffect): Flow<PatchEvent> = when (effect) {
        is PatchEffect.LoadBindings -> loadBindings(effect.userId, effect.deviceId)

        is PatchEffect.Bind -> bind(effect.userId, effect.deviceId, effect.code)
    }

    private fun loadBindings(userId: String, deviceId: String): Flow<PatchEvent> = flow {
        val result = runCatching {
            sql.patchBinding.selectByDevice(userId, deviceId).executeAsList()
        }.fold(onSuccess = { rows ->
            PatchEvent.BindingsLoaded(rows.map { it.toBinding() })
        }, onFailure = {
            PatchEvent.LoadFailed(it.message ?: "读取贴片绑定失败")
        })
        Timber.tag(PATCH_TAG).i(
            "读绑定 设备=%s 结果=%s", deviceId, result::class.simpleName
        )
        emit(result)
    }.flowOn(Dispatchers.IO)

    private fun bind(userId: String, deviceId: String, code: PatchCode): Flow<PatchEvent> = flow {
        val result = runCatching {
            sql.patchBinding.upsert(
                userId = userId,
                deviceId = deviceId,
                patchId = code.patchId,
                modelId = code.modelId?.toLong(),
                lotNo = code.lotNo,
                manufacturedOn = code.manufacturedOn,
                expiresOn = code.expiresOn,
                maxUseMinutes = code.maxUseMinutes?.toLong(),
                calibrationRef = code.calibrationRef,
                rawCode = code.raw,
                boundAtMillis = nowMillis(),
            )
        }.fold(onSuccess = {
            PatchEvent.Bound
        }, onFailure = {
            PatchEvent.BindFailed(it.message ?: "保存贴片绑定失败")
        })
        Timber.tag(PATCH_TAG).i(
            "写绑定 设备=%s 贴片=%s 结果=%s", deviceId, code.patchId, result::class.simpleName
        )
        emit(result)
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val PATCH_TAG = "Patch.Binding"
    }
}

/** 库里那行 → 领域类型。决策核与界面都不认生成的库类型,只在这里过一道。 */
private fun PatchBindingRow.toBinding(): PatchBinding = PatchBinding(
    patchId = patchId,
    modelId = modelId,
    lotNo = lotNo,
    manufacturedOn = manufacturedOn,
    expiresOn = expiresOn,
    maxUseMinutes = maxUseMinutes,
    calibrationRef = calibrationRef,
    rawCode = rawCode,
    boundAtMillis = boundAtMillis,
)
