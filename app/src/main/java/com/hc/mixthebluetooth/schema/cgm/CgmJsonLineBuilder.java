package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;

/**
 * CgmJsonLineBuilder — 将 CgmSample 转换成 JSON 行字符串
 *
 * 作用：
 *   CgmRecorderConsumer 把采到的血糖样本写入 JSONL 录波文件。
 *   每条记录转成一行 JSON，方便用 Python / Excel / 数据库工具处理。
 *
 * 输出格式示例：
 *   {
 *     "tMs":1715076300000,        // 写入时的系统时间戳（毫秒）
 *     "mac":"AA:BB:CC:DD:EE:FF", // 蓝牙设备 MAC 地址
 *     "name":"CGM_Device",        // 蓝牙设备名称
 *     "event":"current",          // 事件类型
 *     "status":"current",         // 状态文字
 *     "primary":99.3,             // 主血糖值（可能为 null）
 *     "current":99.3,            // 实时血糖值（可能为 null）
 *     "raw":"CA:266,99.3"        // 原始文本行
 *   }
 *
 * 为什么手动拼 JSON 而不是用 Gson/Jackson？
 *   简单：字段少，类型简单，手动拼足够，且避免引入新的 JSON 库依赖。
 *   但注意：手动拼要自己做转义，escapeJson() 负责处理 \ " \n \r \t 等特殊字符。
 *
 * 依赖：
 *   CgmRecorderConsumer 在录波时调用：
 *     recorder.appendLine(CgmJsonLineBuilder.build(runtime.module(), cgmSample));
 */
public final class CgmJsonLineBuilder {

    private CgmJsonLineBuilder() {}

    /**
     * 把一个 CgmSample 转成 JSON 行。
     *
     * @param module  当前蓝牙设备模块（可能为 null）
     * @param sample  血糖样本
     * @return 一行完整的 JSON 字符串（不含换行符）
     */
    @NonNull
    public static String build(@Nullable DeviceModule module, @NonNull CgmSample sample) {
        String mac = module != null ? module.getMac() : "";
        String name = module != null ? module.getName() : "";
        Float primary = sample.metrics().get(CgmSample.METRIC_PRIMARY);
        Float current = sample.metrics().get(CgmSample.METRIC_CURRENT);

        return "{"
                + "\"tMs\":" + System.currentTimeMillis()
                + ",\"mac\":\"" + escapeJson(mac) + "\""
                + ",\"name\":\"" + escapeJson(name) + "\""
                + ",\"event\":\"" + escapeJson(sample.event) + "\""
                + ",\"status\":\"" + escapeJson(sample.status) + "\""
                + ",\"primary\":" + (primary == null ? "null" : primary)
                + ",\"current\":" + (current == null ? "null" : current)
                + ",\"raw\":\"" + escapeJson(sample.raw) + "\""
                + "}";
    }

    /**
     * JSON 字符串转义。
     * 防止 raw 文本里包含的特殊字符破坏 JSON 格式。
     *
     * 处理：\ → \\, " → \", 换行 → \n, 回车 → \r, Tab → \t
     */
    @NonNull
    public static String escapeJson(@Nullable String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
