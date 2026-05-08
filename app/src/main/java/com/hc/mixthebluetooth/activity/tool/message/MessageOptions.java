package com.hc.mixthebluetooth.activity.tool.message;

import androidx.annotation.NonNull;

/**
 * MessageOptions — 用户在消息界面配置的发送/接收选项
 * <p>
 * 作用：
 * 保存用户在设置面板里配置的参数，影响 MessageSender（发送）和
 * MessagePipelineController（接收/解码）的行为。
 * <p>
 * 各选项说明：
 * <p>
 * showOutgoing
 * 是否在消息列表里显示发送出去的消息。
 * true → sender.send() 成功后会 messageList.addOutgoingItem() 追加显示
 * false → 发送成功但不在列表里显示
 * <p>
 * showTime
 * 是否在消息列表里显示时间戳。
 * true → 每条消息后面显示时间（格式 "HH:mm:ss"）
 * false → 不显示时间
 * <p>
 * sendHex
 * 发送模式：HEX 字符串 vs 普通文本。
 * true  → 输入 "FE0C" 会被当作十六进制字符串，转成字节数组发送
 * false → 输入 "TIME,..." 当作普通文本，按 codeFormat 编码后发送
 * <p>
 * readHex
 * 接收模式：是否按 HEX 格式解码收到的字节。
 * true  → 把每两个十六进制字符当一个字节
 * false → 按 codeFormat（UTF-8 / GBK 等）正常解码
 * <p>
 * autoClear
 * 是否自动清屏。
 * true → 收到超过 AUTO_CLEAR_READ_BYTES（40万）字节后，自动清空消息列表
 * false → 不自动清屏
 * <p>
 * sendNewline
 * 发送时是否自动追加换行符。
 * true → 发送前自动追加 \r\n（或 HEX 模式的 0D0A）
 * false → 不追加
 * <p>
 * codeFormat
 * 字符编码格式：UTF-8 / GBK / ASCII 等。
 * 用于：发送时把文本编码成字节，接收时把字节解码成文本。
 * <p>
 * 为什么 sendHex 和 readHex 分开？
 * 因为发送和接收的编码方式可能不同。设备可能发来的是 UTF-8 文本，
 * 但我们用 HEX 发送命令。这种情况需要分开配置。
 * <p>
 * 生命周期：
 * initOptions() 从持久化存储加载 → options 传给 sender 和 pipeline
 * refreshOptions() 重新加载 → options 同步更新到 sender 和 pipeline
 */
public final class MessageOptions {

    /**
     * 是否在消息列表显示发送出去的消息
     */
    public final boolean showOutgoing;

    /**
     * 是否显示时间戳
     */
    public final boolean showTime;

    /**
     * 是否以 HEX 格式发送
     */
    public final boolean sendHex;

    /**
     * 是否以 HEX 格式接收
     */
    public final boolean readHex;

    /**
     * 是否自动清屏（收到大量数据时）
     */
    public final boolean autoClear;

    /**
     * 是否自动追加换行符
     */
    public final boolean sendNewline;

    /**
     * 字符编码格式（UTF-8 / GBK / ASCII 等）
     */
    @NonNull
    public final String codeFormat;

    public MessageOptions(
            boolean showOutgoing,
            boolean showTime,
            boolean sendHex,
            boolean readHex,
            boolean autoClear,
            boolean sendNewline,
            @NonNull String codeFormat
    ) {
        this.showOutgoing = showOutgoing;
        this.showTime = showTime;
        this.sendHex = sendHex;
        this.readHex = readHex;
        this.autoClear = autoClear;
        this.sendNewline = sendNewline;
        this.codeFormat = codeFormat;
    }

    /**
     * 返回默认配置（全部选项关闭）。
     * 用于 MessagePipelineController 构造时没有从存储加载选项的兜底。
     */
    @NonNull
    public static MessageOptions defaults(@NonNull String codeFormat) {
        return new MessageOptions(false, false, false, false, false, false, codeFormat);
    }

    /**
     * 生成 BluetoothPayloadDecoder 的解码选项。
     *
     * @param removeTrailingCrLf 是否去掉末尾换行符
     */
    @NonNull
    public BluetoothPayloadDecoder.Options decoderOptions(boolean removeTrailingCrLf) {
        return new BluetoothPayloadDecoder.Options.Builder()
                .hex(readHex)
                .removeTrailingCrLf(removeTrailingCrLf)
                .cleanNull(true)
                .trim(true)
                .build();
    }
}
