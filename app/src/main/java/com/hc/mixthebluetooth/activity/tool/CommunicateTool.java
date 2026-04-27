package com.hc.mixthebluetooth.activity.tool;

import android.annotation.SuppressLint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通信编解码工具 — 统一管理蓝牙数据的编解码、行缓冲、JSON构建。
 *
 * 设计原则：
 * - 编解码逻辑只在这里，不再散落在各个 Fragment 中
 * - 支持 GBK/UTF-8/Unicode/ASCII 四种编码
 * - 行缓冲自动处理 \\n / \\r\\n 断行
 * - JSON 构建使用 StringBuilder 而非字符串拼接，兼顾性能与可读性
 *
 * 使用示例：
 *
 * <pre>{@code
 * // 1. 解码蓝牙字节流为字符串
 * String raw = CommunicateTool.decode(bytes, "GBK");
 * if (raw == null) return;
 *
 * // 2. 提取行缓冲中的完整行
 * String line;
 * while ((line = tool.pollLine(raw)) != null) {
 *     processLine(line);
 * }
 *
 * // 3. 发送字符串时编码为字节
 * byte[] payload = CommunicateTool.encode(command, "GBK", false);
 * sendDataToActivity(CMD_SEND_BT_DATA, new FragmentMessageItem(false, payload, null, true, module, false));
 *
 * // 4. 构建 JSON Line
 * String json = CommunicateTool.buildSampleJson(tool.getStartTimeMs(), module, values, rawLine);
 * }</pre>
 *
 * 注意：Fragment 使用时传入 Context 以获取当前编码格式，
 * Activity 使用时直接传入编码字符串。
 */
public class CommunicateTool {

    private final StringBuilder rxBuffer = new StringBuilder();
    private long rxBytes = 0;
    private long linesOk = 0;
    private long linesFail = 0;

    // ─── 编解码 ─────────────────────────────────────────────────────────

    /**
     * 解码字节数组为字符串。
     * @param raw    原始字节
     * @param code   编码格式："GBK" / "UTF-8" / "Unicode" / "ASCII"
     * @return       解码后的字符串，失败返回 null
     */
    @Nullable
    public static String decode(@Nullable byte[] raw, @NonNull String code) {
        if (raw == null || raw.length == 0) return null;
        try {
            Charset cs = resolveCharset(code);
            String s = new String(raw, cs);
            return s.replace("\u0000", "");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * 编码字符串为字节数组。
     * @param data      待编码字符串
     * @param code      编码格式："GBK" / "UTF-8" / "Unicode" / "ASCII"
     * @param isHex     是否为16进制字符串模式
     * @return          编码后的字节数组
     */
    @Nullable
    public static byte[] encode(@Nullable String data, @NonNull String code, boolean isHex) {
        if (data == null) return null;
        if (isHex) {
            return hexStringToByteArray(data);
        }
        try {
            return data.getBytes(code);
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * 编码字符串为字节数组（使用 Context 获取当前编码）。
     */
    @Nullable
    public static byte[] encode(@Nullable String data, @NonNull android.content.Context ctx, boolean isHex) {
        return encode(data, FragmentParameter.getInstance().getCodeFormat(ctx), isHex);
    }

    // ─── 行缓冲 ─────────────────────────────────────────────────────────

    /**
     * 向缓冲区追加数据，返回缓冲区中第一条完整行。
     * 完整行定义：以 \\n 结尾（或 \\r\\n），返回内容不含换行符。
     * 多次调用可提取多行：while ((line = pollLine(...)) != null) { ... }
     *
     * @param chunk 新接收的数据块（已解码的字符串）
     * @return      一行完整数据，或 null（无可用行）
     */
    @Nullable
    public String pollLine(@NonNull String chunk) {
        rxBuffer.append(chunk);
        int lf = rxBuffer.indexOf("\n");
        if (lf < 1) return null;

        int end = lf;
        if (lf > 0 && rxBuffer.charAt(lf - 1) == '\r') {
            end = lf - 1;
        }
        String line = rxBuffer.substring(0, end).trim();
        rxBuffer.delete(0, lf + 1);
        return line;
    }

    /**
     * 清空行缓冲。
     */
    public void clearBuffer() {
        rxBuffer.setLength(0);
    }

    // ─── 统计 ────────────────────────────────────────────────────────────

    public void addRxBytes(int n) { rxBytes += n; }
    public void incLinesOk()      { linesOk++; }
    public void incLinesFail()    { linesFail++; }
    public long getRxBytes()      { return rxBytes; }
    public long getLinesOk()      { return linesOk; }
    public long getLinesFail()    { return linesFail; }
    public long getLinesTotal()   { return linesOk + linesFail; }

    public void resetStats() {
        rxBytes = 0; linesOk = 0; linesFail = 0;
    }

    // ─── JSON 构建 ─────────────────────────────────────────────────────

    /**
     * 构建样本 JSON Line。
     * 示例输出：
     * {"tMs":1712345678901,"mac":"AA:BB:CC:DD:EE:FF","name":"HC-BLE","ohm":1234.5,"us":0.0812,"raw":"1234.5Ω, 0.0812uS"}
     *
     * @param startMs   起始时间戳（毫秒）
     * @param module    蓝牙模块
     * @param values    数据字段名值对
     * @param rawLine   原始行字符串
     * @return          JSON Line 字符串
     */
    @NonNull
    public static String buildJsonLine(long startMs,
                                        @Nullable DeviceModule module,
                                        @NonNull Pair... values) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"tMs\":").append(startMs);

        if (module != null) {
            sb.append(",\"mac\":\"").append(escapeJson(module.getMac())).append("\"");
            sb.append(",\"name\":\"").append(escapeJson(module.getName())).append("\"");
        }

        for (Pair p : values) {
            sb.append(",\"").append(escapeJson(p.key)).append("\":");
            if (p.value instanceof String) {
                sb.append("\"").append(escapeJson((String) p.value)).append("\"");
            } else {
                sb.append(p.value);
            }
        }

        if (rawLine != null) {
            sb.append(",\"raw\":\"").append(escapeJson(rawLine)).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    // 键值对容器
    public static class Pair {
        @NonNull public final String key;
        @NonNull public final Object value;
        public Pair(@NonNull String key, @NonNull Object value) {
            this.key = key; this.value = value;
        }
    }

    // ─── 工具方法 ──────────────────────────────────────────────────────

    /**
     * 判断字符串是否符合 EIS 数据格式。
     * 格式：1234.5Ω, 0.0812uS
     */
    @Nullable
    public static float[] parseEisLine(@Nullable String line) {
        if (TextUtils.isEmpty(line)) return null;

        int idx = line.lastIndexOf("dataString:");
        if (idx >= 0) line = line.substring(idx + "dataString:".length());
        line = line.replace("\u0000", "").trim();

        Pattern p = Pattern.compile(
            "\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*Ω\\s*,\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)\\s*(?:uS|µS)\\s*",
            Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(line);
        if (!m.find()) return null;
        try {
            float ohm = Float.parseFloat(m.group(1));
            float us  = Float.parseFloat(m.group(2));
            return new float[]{ohm, us};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从字节数组解析 EIS 数据（当数据以二进制格式传输时）。
     * 格式：每帧 20 字节 = 5 × float32，顺序：OCP, EIS, Ion, Speed, Rate
     */
    @NonNull
    public static float[] parseBinaryEisFrame(@Nullable byte[] data) {
        float[] result = new float[5];
        if (data == null || data.length < 20) return result;
        for (int i = 0; i < 5; i++) {
            int bits = data[i * 4] & 0xFF
                    | (data[i * 4 + 1] & 0xFF) << 8
                    | (data[i * 4 + 2] & 0xFF) << 16
                    | (data[i * 4 + 3] & 0xFF) << 24;
            result[i] = Float.intBitsToFloat(bits);
        }
        return result;
    }

    /**
     * 判断字节数组是否以 \\r\\n 结尾（用于换行检测）。
     */
    public static boolean endsWithNewline(@Nullable byte[] data) {
        if (data == null || data.length < 2) return false;
        return data[data.length - 2] == 13 && data[data.length - 1] == 10;
    }

    /**
     * 获取带时间戳的发送项。
     */
    @NonNull
    public static FragmentMessageItem buildSendItem(@Nullable byte[] payload,
                                                     @NonNull DeviceModule module,
                                                     boolean showTime,
                                                     boolean showMine,
                                                     boolean isHex) {
        String time = showTime ? getTime() : null;
        return new FragmentMessageItem(isHex, payload, time, true, module, showMine);
    }

    @NonNull
    public static String getTime() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int h = c.get(java.util.Calendar.HOUR_OF_DAY);
        int m = c.get(java.util.Calendar.MINUTE);
        int s = c.get(java.util.Calendar.SECOND);
        return String.format(Locale.getDefault(), "%02d:%02d:%02d ", h, m, s);
    }

    // ─── 私有辅助方法 ──────────────────────────────────────────────────

    private static Charset resolveCharset(@NonNull String code) {
        switch (code.toUpperCase().replace("-", "").replace("_", "")) {
            case "GBK":     return StandardCharsets.GBK;
            case "UTF8":
            case "UTF8":    return StandardCharsets.UTF_8;
            case "UNICODE": return StandardCharsets.UTF_16;
            case "ASCII":   return StandardCharsets.US_ASCII;
            default:        return StandardCharsets.UTF_8;
        }
    }

    @Nullable
    private static byte[] hexStringToByteArray(@Nullable String bs) {
        if (bs == null) return null;
        bs = bs.replaceAll("\\s", "");
        int len = bs.length();
        if (len % 2 != 0) bs = "0" + bs;
        len = bs.length();
        byte[] cs = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            cs[i / 2] = (byte) Integer.parseInt(bs.substring(i, i + 2), 16);
        }
        return cs;
    }

    private static String escapeJson(@Nullable String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
