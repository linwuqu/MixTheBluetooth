package com.hc.mixthebluetooth.activity.tool.message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

/**
 * MessageSender — 发送命令到蓝牙设备的唯一入口
 *
 * 职责：
 *   接收命令字符串（如 "TIME,2026,05,07,14,25,00\r\n"）
 *   → 按用户设置（HEX/字符串、换行符）编码成字节
 *   → 包装成 FragmentMessageItem（显示用的数据结构）
 *   → 交给 runtime 实际发送蓝牙数据
 *   → 在消息列表里追加显示"已发送"的记录
 *
 * 为什么叫 Sender 而不只是 send()？
 *   因为它封装了完整的发送流程，不只是"发蓝牙字节"这一件事：
 *     ① 前置检查（是否为空、是否连接、模块是否存在）
 *     ② 编码转换（字符串 ↔ HEX 字节）
 *     ③ 追加换行（如果用户设置了换行符）
 *     ④ 包装成显示用的 item
 *     ⑤ 发蓝牙
 *     ⑥ 追加到消息列表
 *
 * 典型调用链：
 *   sender.send(CgmCommandSet.startNow())  ← CgmProfile 的按钮绑定
 *     → 检查连接状态
 *     → 编码："TIME,2026,05,07,14,25,00\r\n" → 字节数组
 *     → MessageItemTools.outgoing() 包装成 item
 *     → runtime.sendBtData(item)     ← FragmentRuntime 实际发送
 *     → messageList.addOutgoingItem(item)  ← 消息列表追加显示
 *
 * 依赖：
 *   runtime      — 实际发蓝牙数据和弹 Toast
 *   messageList — 追加显示发送记录
 *   options      — 用户设置的编码方式
 */
public final class MessageSender {

    /** 运行时中介：通过它发蓝牙字节，以及弹出 Toast */
    private final FragmentRuntime runtime;

    /** 消息列表：发送成功后，把这条消息追加到界面列表 */
    private final MessageListController messageList;

    /** 用户设置的发送选项（可动态更新） */
    private MessageOptions options;

    public MessageSender(
            @NonNull FragmentRuntime runtime,
            @NonNull MessageListController messageList,
            @NonNull MessageOptions options
    ) {
        this.runtime = runtime;
        this.messageList = messageList;
        this.options = options;
    }

    /**
     * 更新发送选项。
     * FragmentMessage.refreshOptions() 里会调用这个方法，
     * 实现：用户在设置界面改了选项 → sender 和 pipeline 同步生效。
     */
    public void updateOptions(@NonNull MessageOptions options) {
        this.options = options;
    }

    /**
     * 发送一条命令。
     *
     * 完整流程：
     *
     *   ① 前置检查
     *       raw == null / 空字符串 → toast("不能发送空数据") → return false
     *       runtime.connected() == false → toast("当前状态不可以发送数据") → return false
     *       runtime.module() == null → toast("当前没有可发送的蓝牙模块") → return false
     *
     *   ② 准备文本
     *       如果用户设置了"自动追加换行"，在文本末尾加 \r\n（或 HEX 的 0D0A）
     *
     *   ③ 编码
     *       如果 options.sendHex == true：把 HEX 字符串（如 "FE0C"）转成字节数组
     *       否则：直接用文本的字节数组（按配置的编码格式，如 UTF-8）
     *
     *   ④ 包装
     *       用 MessageItemTools.outgoing() 把字节数组包装成 FragmentMessageItem
     *       FragmentMessageItem 包含：字节数组、时间戳、HEX/字符串显示文本、来源设备模块等
     *
     *   ⑤ 发送
     *       runtime.sendBtData(item) → 交给 runtime 实际发送蓝牙数据
     *
     *   ⑥ 显示
     *       如果 options.showOutgoing == true，在消息列表追加这条发送记录
     *
     * @return true 发送成功，false 发送失败（空数据、未连接等）
     */
    public boolean send(@Nullable String raw) {
        // ① 前置检查
        if (raw == null || raw.trim().isEmpty()) {
            runtime.toast("不能发送空数据");
            return false;
        }

        if (!runtime.connected()) {
            runtime.toast("当前状态不可以发送数据");
            return false;
        }

        DeviceModule module = runtime.module();
        if (module == null) {
            runtime.toast("当前没有可发送的蓝牙模块");
            return false;
        }

        // ② + ③ 准备文本并编码
        String text = prepare(raw);
        byte[] bytes = Analysis.getBytes(text, options.codeFormat, options.sendHex);

        // ④ 包装成 item（包含字节数组、显示文本、时间戳、设备信息）
        FragmentMessageItem item = MessageItemTools.outgoing(
                options.sendHex,
                bytes,
                options.showTime ? Analysis.getTime() : null,
                module,
                options.showOutgoing
        );

        // ⑤ 发送蓝牙数据（通过 runtime → commandSink → Activity → 蓝牙库）
        runtime.sendBtData(item);

        // ⑥ 追加到消息列表显示
        if (options.showOutgoing) {
            messageList.addOutgoingItem(item);
        }

        return true;
    }

    /**
     * 准备文本：根据用户设置追加换行符，并处理 HEX 格式的空格。
     *
     *   options.sendHex == true：
     *       输入 "FE0C" → 去掉空格 → "FE0C"
     *       如果还设置了换行 → "FE0C0D0A"
     *
     *   options.sendHex == false：
     *       输入 "TIME,2026,05,07,14,25,00" → 追加 "\r\n" → "TIME,2026,05,07,14,25,00\r\n"
     */
    @NonNull
    public String prepare(@NonNull String raw) {
        String text = raw;
        if (options.sendNewline) {
            text += options.sendHex ? "0D0A" : "\r\n";
        }
        if (options.sendHex) {
            text = text.replaceAll(" ", "");
        }
        return text;
    }
}
