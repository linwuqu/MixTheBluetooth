package com.hc.mixthebluetooth.activity.tool.message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.util.List;

/**
 * Shared helpers for the message RecyclerView item model.
 * <p>
 * FragmentMessage and FragmentMessageNew should create and merge
 * FragmentMessageItem through this class so incoming/outgoing list behavior
 * stays consistent across pages.
 */
public final class MessageItemTools {

    private MessageItemTools() {
    }

    // -------------------- 输入侧 ---------------------
    // bluetooth -> app (isMyData: false)
    // 创建设备发来的文本消息
    @NonNull
    public static FragmentMessageItem incoming(
            @NonNull String text,
            @Nullable String time,
            @Nullable DeviceModule module,
            boolean showData
    ) {
        return new FragmentMessageItem(text, time, false, module, showData);
    }

    // 直接新增一条 incoming 消息
    public static void appendIncoming(
            @NonNull List<FragmentMessageItem> items,
            @NonNull String text,
            boolean endsWithLineBreak,
            @Nullable String time,
            @Nullable DeviceModule module,
            boolean showData
    ) {
        FragmentMessageItem item = incoming(text, time, module, showData);
        item.setDataEndNewline(endsWithLineBreak);
        items.add(item);
    }

    // 如果上一条 incoming 还没有换行结束，就合并到上一条；否则新增一条
    public static void appendOrMergeIncoming(
            @NonNull List<FragmentMessageItem> items,
            @NonNull String text,
            boolean endsWithLineBreak,
            @Nullable String time,
            @Nullable DeviceModule module,
            boolean showData
    ) {
        if (!items.isEmpty()) {
            FragmentMessageItem last = items.get(items.size() - 1);
            if (last.isAddible()) {
                last.addData(text, time);
                last.setDataEndNewline(endsWithLineBreak);
                return;
            }
        }

        appendIncoming(items, text, endsWithLineBreak, time, module, showData);
    }

    // -------------------- 输出侧 ---------------------
    // app -> bluetooth (isMyData: true)
    // 创建 App 发给设备的二进制/十六进制消息
    @NonNull
    public static FragmentMessageItem outgoing(
            boolean hex,
            @NonNull byte[] bytes,
            @Nullable String time,
            @Nullable DeviceModule module,
            boolean showData
    ) {
        return new FragmentMessageItem(hex, bytes, time, true, module, showData);
    }

    // 创建 App 发给设备的纯文本消息
    @NonNull
    public static FragmentMessageItem outgoingText(
            @NonNull String text,
            @Nullable String time,
            @Nullable DeviceModule module,
            boolean showData
    ) {
        return new FragmentMessageItem(text, time, true, module, showData);
    }
}
