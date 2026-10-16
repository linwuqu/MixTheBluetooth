package com.biosensor.migratedev.decisioncore.patch

import java.nio.charset.StandardCharsets

/**
 * 贴片码:扫到的原文解析出来的字段。纯数据,不做 IO 也不做业务判断。
 *
 * 字段集合与[贴片编码协议 V1](../../../../../docs/SOP/简报/附件/2026-10-06-贴片编码协议.md) 的 01–07 一一对应,
 * 只是本期用文本而不是 TLV 二进制(架构 13 §D1)。将来切二进制时只改本文件的 parse,
 * 数据库列与界面一行都不用动。
 */
data class PatchCode(
    val schemaVersion: Int,
    /** 贴片编号。本期唯一的业务键。 */
    val patchId: String,
    val modelId: Int?,
    val lotNo: String?,
    val manufacturedOn: String?,
    val expiresOn: String?,
    val maxUseMinutes: Int?,
    val calibrationRef: String?,
    /** 扫码原文,原样入库:不认识的扩展字段靠它不丢。 */
    val raw: String,
)

sealed interface PatchCodeResult {
    data class Ok(val code: PatchCode) : PatchCodeResult

    /** 认得出不是贴片码,或者贴片码本身有毛病。原因直接给用户看。 */
    data class Rejected(val reason: String) : PatchCodeResult
}

/** 载荷头。带版本号,将来换编码方式时旧码能被明确认出来并拒绝,而不是解析出一堆空字段。 */
private const val HEADER_PREFIX = "CGPATCH/"
private const val SUPPORTED_VERSION = 1

/**
 * 解析一枚贴片码。
 *
 * 形如:
 * `CGPATCH/1|patchId=<UUID>|model=1|lot=L260701|mfg=2026-07-01|exp=2027-07-01|maxUseMin=120|calRef=CAL-1`
 *
 * 规矩:
 * - 只认 `CGPATCH/<版本>` 开头,别的码一律拒绝(扫错码要给得出原因,而不是静默失败);
 * - `patchId` 必填,其余可选——缺了是 null,不是 ""(缺与空是两件事,入库要能区分);
 * - 认得的字段值不合法(如 `model=abc`)就拒绝:那是印坏了,不是"没给";
 * - 不认识的键不拒绝,留在 [PatchCode.raw] 里(协议会加字段,旧 App 不该因此罢工)。
 */
fun parsePatchCode(text: String?): PatchCodeResult {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty()) return PatchCodeResult.Rejected("没有扫到内容")

    val segments = raw.split('|')
    val header = segments.first().trim()
    if (!header.startsWith(HEADER_PREFIX)) {
        return PatchCodeResult.Rejected("这不是贴片码")
    }
    val version = header.removePrefix(HEADER_PREFIX).trim().toIntOrNull()
        ?: return PatchCodeResult.Rejected("贴片码版本号读不出来")
    if (version != SUPPORTED_VERSION) {
        return PatchCodeResult.Rejected("贴片码版本 $version 暂不支持,当前 App 只认 $SUPPORTED_VERSION")
    }

    val fields = linkedMapOf<String, String>()
    segments.drop(1).forEach { segment ->
        val separator = segment.indexOf('=')
        if (separator <= 0) return@forEach   // 不成对的段忽略:协议只保证 k=v 段有效
        fields[segment.substring(0, separator).trim()] = segment.substring(separator + 1).trim()
    }

    val patchId = fields["patchId"].orEmpty()
    if (patchId.isEmpty()) return PatchCodeResult.Rejected("贴片码里没有编号")

    // 数字字段:没给是 null;给了但读不出来要拒绝——那是印坏了,不是"没给"
    val modelText = fields.readText("model")
    val modelId = modelText?.toIntOrNull()
    if (modelText != null && modelId == null) {
        return PatchCodeResult.Rejected("贴片型号不是数字")
    }
    val maxUseText = fields.readText("maxUseMin")
    val maxUseMinutes = maxUseText?.toIntOrNull()
    if (maxUseText != null && maxUseMinutes == null) {
        return PatchCodeResult.Rejected("可用时长不是数字")
    }

    return PatchCodeResult.Ok(
        PatchCode(
            schemaVersion = version,
            patchId = patchId,
            modelId = modelId,
            lotNo = fields.readText("lot"),
            manufacturedOn = fields.readText("mfg"),
            expiresOn = fields.readText("exp"),
            maxUseMinutes = maxUseMinutes,
            calibrationRef = fields.readText("calRef"),
            raw = raw,
        )
    )
}

/**
 * 取字段里的文本:没给就是 null,给了空串也当没给——界面与数据库都不需要区分这两种"空"。
 */
private fun Map<String, String>.readText(key: String): String? =
    this[key]?.takeIf { it.isNotEmpty() }

/**
 * 相机回调给的文本可能为空(二进制载荷),这时拿原始字节按 UTF-8 兜一次。
 * 真二进制解出来不会是 `CGPATCH/` 开头,到此为止,不会误认。
 */
fun patchCodeTextFrom(text: String?, rawBytes: ByteArray?): String? = when {
    !text.isNullOrBlank() -> text
    rawBytes != null && rawBytes.isNotEmpty() -> String(rawBytes, StandardCharsets.UTF_8)
    else -> null
}
