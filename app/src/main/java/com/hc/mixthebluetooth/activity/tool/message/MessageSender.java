package com.hc.mixthebluetooth.activity.tool.message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.activity.tool.runtime.FragmentRuntime;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

public final class MessageSender {

    private final FragmentRuntime runtime;
    private final MessageListController messageList;
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

    public void updateOptions(@NonNull MessageOptions options) {
        this.options = options;
    }

    public boolean send(@Nullable String raw) {
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

        String text = prepare(raw);
        byte[] bytes = Analysis.getBytes(text, options.codeFormat, options.sendHex);
        FragmentMessageItem item = MessageItemTools.outgoing(
                options.sendHex,
                bytes,
                options.showTime ? Analysis.getTime() : null,
                module,
                options.showOutgoing
        );
        runtime.sendBtData(item);

        if (options.showOutgoing) {
            messageList.addOutgoingItem(item);
        }

        return true;
    }

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
