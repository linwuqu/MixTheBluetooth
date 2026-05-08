package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSample;
import com.hc.mixthebluetooth.activity.tool.sample.BluetoothSampleParser;

/**
 * CgmParser — CGM 设备蓝牙文本行的解析器
 *
 * 职责：把蓝牙设备发来的原始文本行，解析成结构化的 CgmSample 对象。
 *
 * 输入：蓝牙设备发来的一行文本（如 "CA:266,99.3"）
 * 输出：对应的 CgmSample 对象，或者 null（表示这行解析不了）
 *
 * 核心设计：
 *   每个 CgmSample 包含：
 *     event   — 事件类型（字符串常量，如 "ca" / "eis" / "cache_start"）
 *     raw     — 原始文本行
 *     status  — 状态文字（某些 event 特有，如 RI 命令）
 *     metrics — 数值指标 Map（key = "primary" 或 "current"，value = 血糖浓度）
 *
 * "读缓存"场景的解析逻辑（readingCache 状态机）：
 *
 *   用户按"读取缓存" → 设备回传历史数据，格式如下：
 *
 *     "Start Playback"           ← 第一行：缓存开始
 *     "CA:266,99.3"             ← 历史记录 #1
 *     "CA:266,100.1"            ← 历史记录 #2
 *     "EIS:foo,bar,101.5,more"  ← 历史记录 #3
 *     "Playback all done"        ← 最后一行：缓存结束
 *
 *   解析器用 readingCache 这个内部状态来区分"正常测量"和"读缓存过程"：
 *
 *     收到 "Start Playback"  → readingCache = true   ← 开启缓存模式
 *     收到其他行              → 如果 readingCache == true，当作缓存行处理
 *     收到 "Playback all done" → readingCache = false  ← 关闭缓存模式
 *
 *   为什么要这样设计？
 *     因为在"读缓存"过程中，设备可能发来一些解析器不认识的历史数据行格式。
 *     有了 readingCache，当解析器认不出某行时，就知道：
 *       "虽然我不认识这行，但当前正在读缓存，所以这行肯定是缓存数据，
 *        要包装成 EVENT_CACHE_LINE 事件，让 CgmFileConsumer 把它写文件。"
 *
 * 解析结果对照表：
 *
 *   原始行                      event            status      metrics
 *   ─────────────────────────────────────────────────────────────────
 *   "Start Playback"            cache_start      null        {}
 *   "Playback all done"         cache_done       null        {}
 *   "RI:status ok"             ri               "RI"        {}
 *   "EIS:foo,bar,99.3,more"    eis              "eis"       {"primary": 99.3f}
 *   "CA:266,99.3"              ca               "ca"         {"primary": 99.3f}
 *   "CA:266,266.0"             current           "current"   {"primary":99.3f,"current":99.3f}
 *   未知行（在读缓存中）        cache_line        null        {}
 *   未知行（不在读缓存中）      —                 —           null（返回 null）
 */
public final class CgmParser implements BluetoothSampleParser {

    /**
     * 读缓存状态机标志。
     *   false — 正常测量模式
     *   true  — 正在读缓存，设备在发历史数据
     *
     * 用这个标志区分：解析器认不出的行，到底是设备乱发的，还是读缓存过程中的历史数据。
     */
    private boolean readingCache;

    @Nullable
    @Override
    public BluetoothSample parse(@Nullable String line) {
        // 清理文本：去除空字符、trim 空格，空行返回 null
        String clean = clean(line);
        if (clean == null) return null;

        // 先尝试用已知格式解析这一行
        CgmSample sample = parseCleanLine(clean);
        if (sample != null) {
            // 解析成功。判断是不是缓存的起止标记，用来更新状态机
            if (CgmSample.EVENT_CACHE_START.equals(sample.event)) {
                readingCache = true;   // ← 开启缓存模式
            } else if (CgmSample.EVENT_CACHE_DONE.equals(sample.event)) {
                readingCache = false;  // ← 关闭缓存模式
            }
            return sample;  // 返回解析结果，交给 SampleConsumerRegistry 广播
        }

        // 以下是 parseCleanLine 认不出这行的情况：
        // 此时 readingCache 标志决定了如何处理这行未知数据

        if (readingCache) {
            // 正在读缓存：虽然不认识这行格式，但它是设备发的历史数据，
            // 要存文件。包装成 EVENT_CACHE_LINE 事件。
            return CgmSample.event(CgmSample.EVENT_CACHE_LINE, clean);
        }

        // 不在读缓存过程中：这是一行解析不了的垃圾数据，直接丢弃
        return null;
    }

    /**
     * 公共静态入口：解析一行文本，返回 CgmSample（如果能解析的话）。
     * 与 instance 方法的区别：这个不需要维护 readingCache 状态，
     * 适合一次性解析（不关心上下文）的场景。
     */
    @Nullable
    public static CgmSample parseLine(@Nullable String line) {
        String clean = clean(line);
        if (clean == null) return null;
        return parseCleanLine(clean);
    }

    /**
     * 核心解析逻辑：根据关键字识别行格式。
     *
     * 识别顺序很重要：关键字有重叠时，先写在前面的先匹配。
     * 例如 "CA:266" 同时包含 "CA" 和 "266"，但 "CA" 分支先写，所以走 CA 分支。
     */
    @Nullable
    private static CgmSample parseCleanLine(String clean) {
        // "Start Playback" — 读缓存开始标记
        if (clean.contains("Start Playback")) {
            return CgmSample.event(CgmSample.EVENT_CACHE_START, clean);
        }

        // "Playback all done" — 读缓存结束标记
        if (clean.contains("Playback all done")) {
            return CgmSample.event(CgmSample.EVENT_CACHE_DONE, clean);
        }

        // "RI" — 设备状态信息（如 RI:status ok）
        if (clean.contains("RI")) {
            return CgmSample.status(CgmSample.EVENT_RI, clean, clean);
        }

        // "EIS:..." — 电化学阻抗谱数据，格式如 "EIS:foo,bar,99.3,more"
        //             第 3 个逗号分隔的值是血糖浓度
        if (clean.contains("EIS")) {
            String[] values = valuesAfterColon(clean);
            if (values.length > 2) {
                Float value = parseFloat(values[2]);
                if (value != null) {
                    return CgmSample.metric(CgmSample.EVENT_EIS, clean, CgmSample.METRIC_PRIMARY, value);
                }
            }
            return null;
        }

        // "CA:..." — 血糖浓度数据
        //   CA:266,99.3  → 主血糖值，metrics = {"primary": 99.3f}
        //   CA:266,266.0 → 实时电流值（专门标记为 EVENT_CURRENT）
        if (clean.contains("CA")) {
            String[] values = valuesAfterColon(clean);
            if (values.length > 1) {
                Float value = parseFloat(values[1]);
                if (value != null) {
                    if (clean.contains("CA:266")) {
                        // CA:266 是特殊格式：第二个字段是电流值，同时也是当前血糖值
                        return CgmSample.current(clean, value);
                    }
                    return CgmSample.metric(CgmSample.EVENT_CA, clean, CgmSample.METRIC_PRIMARY, value);
                }
            }
        }

        // 以上都不匹配：解析不了，返回 null
        return null;
    }

    /**
     * 清理文本行：去除空字符、trim 首尾空格，空行返回 null。
     */
    @Nullable
    private static String clean(@Nullable String line) {
        if (line == null) return null;
        String clean = line.replace("\u0000", "").trim();
        return clean.isEmpty() ? null : clean;
    }

    /**
     * 从冒号后面取逗号分隔的值数组。
     * 例如 "EIS:foo,bar,99.3,more" → ["foo", "bar", "99.3", "more"]
     */
    private static String[] valuesAfterColon(String line) {
        String[] parts = line.split(":");
        if (parts.length < 2) return new String[0];
        return parts[1].split(",");
    }

    /**
     * 安全转浮点数：解析失败返回 null（而不是抛异常）。
     */
    @Nullable
    private static Float parseFloat(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
