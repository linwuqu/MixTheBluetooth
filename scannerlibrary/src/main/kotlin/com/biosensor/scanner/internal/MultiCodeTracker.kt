package com.biosensor.scanner.internal

import com.biosensor.scanner.api.ScanResult

/**
 * 画面里有多枚码时,"哪几枚该画框、能不能自动确认"由它说了算。
 *
 * 规则:
 * - 连续 [accept] 到 `confirmationFrames` 帧的码才上框——单帧误检不上屏,框也就不会闪。
 * - 框里的码连续缺席 2 帧才摘掉——同一枚码漏检一帧,框不该跟着闪。
 * - 单一枚码**孤单地**待够 `confirmationFrames * 4` 帧,自动确认(沿用 `ResultStabilizer` 的冷却语义)。
 *   比上框的阈值长,是因为检测器找第二枚码往往慢一两帧:等短了就会在"该让用户选"的画面上抢跑。
 * - 框里稳定出现过两枚及以上 → **闩住**,此后必须点击,直到画面彻底空掉 [BLANK_FRAMES_TO_REARM] 帧才复位。
 *   闩住之后哪怕只剩一枚也要点:多码出现过,就说明这一屏不是"对着扫"的场景。
 *
 * 纯逻辑,不碰 Android,单测在 JVM 上跑。
 */
internal class MultiCodeTracker(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    /** 一枚正在画框的码。点选时回给上层的,是 [result]——最近一帧的那份。 */
    data class Box(val key: ResultKey, val result: ScanResult)

    /** [boxes] 按"第一次被看见"的顺序,顺序稳定,框不会互相换位。 */
    data class Outcome(val boxes: List<Box>, val autoConfirmed: ScanResult?)

    private val streak = LinkedHashMap<ResultKey, Int>()
    private val misses = HashMap<ResultKey, Int>()
    private val latest = LinkedHashMap<ResultKey, ScanResult>()
    private val shown = LinkedHashSet<ResultKey>()

    private var needsTap = false
    private var blankFrames = 0
    private var loneKey: ResultKey? = null
    private var loneFrames = 0
    private var lastAutoKey: ResultKey? = null
    private var lastAutoAt = Long.MIN_VALUE

    // accept 在分析线程,select 在主线程(手指点下去那一刻)。内部是几个 Map,
    // 不加锁的话并发读 HashMap 是能读出乱子的,而这里临界区短得可以忽略。
    @Synchronized
    fun accept(
        frame: List<ScanResult>,
        confirmationFrames: Int,
        duplicateCooldownMillis: Long,
    ): Outcome {
        // 同一帧里同一枚码只算一次:多码检测偶尔会对同一枚码吐出两条结果。
        val present = LinkedHashMap<ResultKey, ScanResult>()
        for (result in frame) present.putIfAbsent(ResultKey.from(result), result)

        for ((key, result) in present) {
            streak[key] = (streak[key] ?: 0) + 1
            misses.remove(key)
            latest[key] = result
        }
        // 拷一份再改:下面会从 streak 里删。
        for (key in streak.keys.toList()) {
            if (key in present) continue
            val missed = (misses[key] ?: 0) + 1
            misses[key] = missed
            if (missed >= MISSES_TO_DROP) forget(key)
        }

        for ((key, count) in streak) {
            if (count >= confirmationFrames) shown.add(key)
        }

        // 拿 shown 而不是 present 判多码:单帧的边角反光不该把整个场景闩成"必须点击"。
        if (shown.size >= 2) needsTap = true
        blankFrames = if (present.isEmpty()) blankFrames + 1 else 0
        if (needsTap && blankFrames >= BLANK_FRAMES_TO_REARM) needsTap = false

        val lone = if (needsTap) null else shown.singleOrNull()
        if (lone != null && lone == loneKey) {
            loneFrames += 1
        } else {
            loneKey = lone
            loneFrames = if (lone != null) 1 else 0
        }

        val boxes = shown.mapNotNull { key -> latest[key]?.let { Box(key, it) } }
        return Outcome(boxes, autoConfirm(lone, confirmationFrames, duplicateCooldownMillis))
    }

    /** 点击某枚框。[key] 不在框里(还没攒够帧数)就什么都不给。 */
    @Synchronized
    fun select(key: ResultKey): ScanResult? = if (key in shown) latest[key] else null

    @Synchronized
    fun reset() {
        streak.clear()
        misses.clear()
        latest.clear()
        shown.clear()
        needsTap = false
        blankFrames = 0
        loneKey = null
        loneFrames = 0
        lastAutoKey = null
        lastAutoAt = Long.MIN_VALUE
    }

    private fun autoConfirm(
        lone: ResultKey?,
        confirmationFrames: Int,
        duplicateCooldownMillis: Long,
    ): ScanResult? {
        if (lone == null || loneFrames < confirmationFrames * AUTO_CONFIRM_FRAME_FACTOR) return null
        val result = latest[lone] ?: return null
        val now = nowMillis()
        if (lone == lastAutoKey && elapsed(now, lastAutoAt) < duplicateCooldownMillis) return null
        lastAutoKey = lone
        lastAutoAt = now
        return result
    }

    private fun forget(key: ResultKey) {
        streak.remove(key)
        misses.remove(key)
        latest.remove(key)
        shown.remove(key)
        if (loneKey == key) {
            loneKey = null
            loneFrames = 0
        }
    }

    private fun elapsed(now: Long, then: Long): Long =
        if (then == Long.MIN_VALUE) Long.MAX_VALUE else (now - then).coerceAtLeast(0L)

    private companion object {
        /** 漏检的容忍帧数。 */
        const val MISSES_TO_DROP = 2

        /** 画面彻底空掉这么多帧,"必须点击"才复位。 */
        const val BLANK_FRAMES_TO_REARM = 8

        /** 自动确认比上框多等几倍帧数,留给检测器找出第二枚码的时间。 */
        const val AUTO_CONFIRM_FRAME_FACTOR = 4
    }
}
