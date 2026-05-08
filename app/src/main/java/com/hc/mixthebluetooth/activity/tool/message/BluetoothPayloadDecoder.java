package com.hc.mixthebluetooth.activity.tool.message;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.Analysis;

/**
 * BluetoothPayloadDecoder — 蓝牙字节载荷解码器
 *
 * 作用：
 *   把蓝牙收到的原始字节数组，转换成可读的文本字符串。
 *   这是 MessagePipelineController 流水线里的第一步（字节 → 文本）。
 *
 * 典型调用链：
 *   pipeline.onBtData(module, bytes)
 *     → BluetoothPayloadDecoder.decodeResult(bytes, codeFormat, options)
 *     → 返回 Result { text: String, endsWithLineBreak: boolean }
 *     → handleLine(module, text)
 *
 * 解码流程：
 *   ① 检测结尾换行符：检查字节数组末尾是否有 \r\n 或 \n 或 \r
 *   ② 按编码格式（UTF-8 / GBK / ASCII / HEX）把字节转成文本
 *   ③ 根据 Options 设置，决定是否：
 *       - 去掉末尾的 \r\n
 *       - 清除空字符（\u0000）
 *       - trim 首尾空格
 *
 * Result 的两个字段：
 *   text              — 解码后的文本（可能为空字符串）
 *   endsWithLineBreak — 原始字节是否以换行符结尾
 *       用于 MessageListController 决定是否合并多批收到的数据
 *
 * Options（解码选项）：
 *   hex              — 是否按 HEX 格式解码（字节每两位当一个十六进制数字）
 *   removeTrailingCrLf — 是否去掉末尾的 \r\n
 *   cleanNull        — 是否清除空字符 \u0000
 *   trim             — 是否 trim 首尾空格
 */
public final class BluetoothPayloadDecoder {

    private BluetoothPayloadDecoder() {}

    /**
     * 简化入口：用 Context 获取编码格式，按默认选项解码。
     */
    @NonNull
    public static Result decodeResult(@Nullable Context context, @Nullable byte[] raw) {
        if (context == null) return new Result("", false);
        if (raw == null || raw.length == 0) return new Result("", false);
        String code = FragmentParameter.getInstance().getCodeFormat(context);
        return decodeResult(raw, code);
    }

    @NonNull
    public static Result decodeResult(@Nullable byte[] raw, @Nullable String code) {
        return decodeResult(raw, code, new Options.Builder().build());
    }

    /**
     * 核心解码方法。
     *
     * @param raw     原始字节数组
     * @param code    编码格式（"UTF-8" / "GBK" / "ASCII" 等）
     * @param options 解码选项（见 Options 类的注释）
     */
    @NonNull
    public static Result decodeResult(@Nullable byte[] raw, @Nullable String code, @NonNull Options options) {
        if (raw == null || raw.length == 0) return new Result("", false);

        // 检测末尾换行符类型（用于 Result.endsWithLineBreak）
        boolean hasCrLf = endsWithCrLf(raw);
        boolean hasLineBreak = hasCrLf || endsWithLf(raw) || endsWithCr(raw);

        // 克隆一份，避免修改原始数组
        byte[] copy = raw.clone();
        String safeCode = code != null ? code : "";

        // 把字节数组转成字符串（内部调用 Analysis.getByteToString）
        String text = Analysis.getByteToString(
                copy,
                safeCode,
                options.hex,
                options.removeTrailingCrLf && hasCrLf  // 是否去掉末尾 \r\n
        );

        if (text == null) return new Result("", hasLineBreak);

        // 按选项清理文本
        if (options.cleanNull) {
            text = text.replace("\u0000", "");
        }
        if (options.trim) {
            text = text.trim();
        }

        return new Result(text, hasLineBreak);
    }

    // ── 换行符检测 ───────────────────────────────────────────

    public static boolean endsWithLf(@Nullable byte[] raw) {
        return raw != null && raw.length >= 1 && raw[raw.length - 1] == 10;
    }

    public static boolean endsWithCr(@Nullable byte[] raw) {
        return raw != null && raw.length >= 1 && raw[raw.length - 1] == 13;
    }

    public static boolean endsWithCrLf(@Nullable byte[] raw) {
        return raw != null && raw.length >= 2 && raw[raw.length - 2] == 13 && raw[raw.length - 1] == 10;
    }

    // ── Result ─────────────────────────────────────────────

    /**
     * 解码结果容器。
     *
     * @param text              解码后的文本（可能为空）
     * @param endsWithLineBreak 原始字节是否以换行符结尾
     */
    public static final class Result {
        @NonNull
        public final String text;
        public final boolean endsWithLineBreak;

        private Result(@NonNull String text, boolean endsWithLineBreak) {
            this.text = text;
            this.endsWithLineBreak = endsWithLineBreak;
        }

        /** 文本是否为空（用于 pipeline 决定是否跳过处理） */
        public boolean isEmpty() {
            return text.isEmpty();
        }
    }

    // ── Options ───────────────────────────────────────────

    /**
     * 解码选项。
     *
     * 为什么用 Builder 模式？
     *   只有 hex 是经常变化的，其他选项大多数时候用默认值。
     *   Builder 模式让调用方只需要指定需要改的字段，其他用默认值。
     *
     * MessageOptions.decoderOptions() 默认配置：
     *   hex = false              （按配置的编码格式解码，不按 HEX）
     *   removeTrailingCrLf = true（去掉末尾 \r\n）
     *   cleanNull = true         （清除 \u0000）
     *   trim = true             （trim 首尾空格）
     */
    public static final class Options {
        /** 是否按 HEX 格式解码（每两个十六进制字符当一个字节） */
        public final boolean hex;

        /** 是否去掉末尾的 \r\n */
        public final boolean removeTrailingCrLf;

        /** 是否清除空字符 \u0000 */
        public final boolean cleanNull;

        /** 是否 trim 首尾空格 */
        public final boolean trim;

        private Options(@NonNull Builder builder) {
            this.hex = builder.hex;
            this.removeTrailingCrLf = builder.removeTrailingCrLf;
            this.cleanNull = builder.cleanNull;
            this.trim = builder.trim;
        }

        public static final class Builder {
            private boolean hex = false;
            private boolean removeTrailingCrLf = true;
            private boolean cleanNull = true;
            private boolean trim = true;

            public Builder hex(boolean hex) { this.hex = hex; return this; }
            public Builder removeTrailingCrLf(boolean v) { this.removeTrailingCrLf = v; return this; }
            public Builder cleanNull(boolean v) { this.cleanNull = v; return this; }
            public Builder trim(boolean v) { this.trim = v; return this; }

            public Options build() { return new Options(this); }
        }
    }
}
