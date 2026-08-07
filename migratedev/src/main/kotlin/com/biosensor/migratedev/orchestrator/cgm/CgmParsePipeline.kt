package com.biosensor.migratedev.orchestrator.cgm

import com.biosensor.migratedev.decisioncore.cgm.CgmRecord
import com.biosensor.migratedev.decisioncore.cgm.MarkerKind
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

/** 管道接口:每次输入一个字节块,输出该块产出的完整记录。状态(尾巴)在实现内。 */
fun interface CgmParsePipeline {
    suspend fun parse(bytes: ByteArray): List<CgmRecord>
}

/** 顺序管道:解码 → 切行 → 记录解析,一次调用即流水线一步。 */
class SequentialCgmParsePipeline : CgmParsePipeline {
    private val decoder = SessionTextDecoder()
    private val assembler = LineAssembler()
    private val parser = RecordParser()

    override suspend fun parse(bytes: ByteArray): List<CgmRecord> {
        val text = when (val out = decoder.accept(bytes)) {
            is SessionTextDecoder.Output.Text -> out.text
            is SessionTextDecoder.Output.Failed -> return emptyList()   // 本块解码失败:丢弃,等待下块
        }
        return assembler.accept(text).mapNotNull(parser::parse)
    }
}

/** ① 字节 → 文本:管"多字节字符被块切开"的尾巴。 */
class SessionTextDecoder(
    private val charsetName: String = "GBK"   // 沿用旧 app 默认编码,会话内固定
) {
    private val decoder: CharsetDecoder = Charset.forName(charsetName).newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)       // 非法序列直接报错,不静默替换
        .onUnmappableCharacter(CodingErrorAction.REPORT)

    private var pendingBytes: ByteArray = byteArrayOf()   // 尾巴:解码器没消费完的字节

    sealed interface Output {
        data class Text(val text: String) : Output
        data class Failed(val message: String) : Output
    }

    fun accept(bytes: ByteArray): Output {
        val input = ByteBuffer.wrap(pendingBytes + bytes)
        val output = CharBuffer.allocate((pendingBytes.size + bytes.size) * 2)
        return try {
            while (true) {
                val result = decoder.decode(input, output, false)   // 增量式:可能留尾巴
                when {
                    result.isUnderflow -> break                     // 正常结束,剩字节即尾巴
                    result.isOverflow -> break                      // 输出满(2x 容量理论不触发)
                    else -> {
                        // 非法序列:跳过而非卡死(REPORT 语义 = 不替换、不阻塞)。
                        // 注意:非 endOfInput 下 decode 不消费非法字节,必须手动推进。
                        input.position(input.position() + maxOf(1, result.length()))
                    }
                }
            }
            val tail = input.slice()               // 未消费字节 = 被切开的字符尾巴
            val tailBytes = ByteArray(tail.remaining())
            tail.get(tailBytes)
            pendingBytes = tailBytes
            output.flip()
            Output.Text(output.toString().filter { it.code != 0 })
        } catch (e: CharacterCodingException) {
            Output.Failed("解码失败($charsetName): ${e.javaClass.simpleName}")
        }
    }
}

/** ② 文本 → 行:管"行被块切开"的尾巴。 */
class LineAssembler {
    private var pendingText: String = ""     // 尾巴:没等来 \n 的半行

    fun accept(text: String): List<String> {
        val joined = pendingText + text
        if (joined.isEmpty()) return emptyList()
        val parts = joined.split('\n')
        if (parts.size == 1) {               // 整段都是半行
            pendingText = joined
            return emptyList()
        }
        val complete = parts.dropLast(1)
            .map { it.trimEnd('\r') }        // 清 \r(设备行尾可能是 \r\n)
            .filter { it.isNotEmpty() }      // 块边界产生的空行丢弃
        pendingText = parts.last()
        return complete
    }
}

/** ③ 行 → 结构化记录:保留序号/时间索引元数据;未知行(如 RI)丢弃。 */
class RecordParser {
    fun parse(line: String): CgmRecord? = when {
        line == "Start Playback" -> CgmRecord.Marker(MarkerKind.START, line)
        line == "Playback all done" -> CgmRecord.Marker(MarkerKind.END, line)

        else -> {
            val eis = EIS_PATTERN.matchEntire(line)
            if (eis != null) {
                CgmRecord.Eis(
                    seq = eis.groupValues[1].toInt(),
                    ts = eis.groupValues[2].toInt(),
                    v1 = eis.groupValues[3], v2 = eis.groupValues[4],
                    v3 = eis.groupValues[5], v4 = eis.groupValues[6],
                    text = line
                )
            } else {
                val ca = CA_PATTERN.matchEntire(line)
                if (ca != null) {
                    CgmRecord.Ca(seq = ca.groupValues[1].toInt(), value = ca.groupValues[2], text = line)
                } else when {
                    line.startsWith("LOG:") -> CgmRecord.Log(line)
                    line.startsWith("summarize:") -> CgmRecord.Summarize(line)
                    else -> null            // 未知行(RI 等)→ 丢弃,不产生记录
                }
            }
        }
    }

    companion object {
        val EIS_PATTERN = Regex("""EIS:(\d+),(\d+),([^,]+),([^,]+),([^,]+),([^,]+)""")
        val CA_PATTERN = Regex("""CA:(\d+),([^,]+)""")
    }
}
