package com.hc.mixthebluetooth.activity.tool.message;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.util.ArrayList;
import java.util.List;

public class MessageListController {

    private final RecyclerView recyclerView;
    private final List<FragmentMessageItem> dataList;
    private final FragmentMessAdapter adapter;

    public MessageListController(@NonNull Context context, @NonNull RecyclerView recyclerView, int itemLayout) {
        this(context, recyclerView, itemLayout, new ArrayList<>(), new LinearLayoutManager(context));
    }

    public MessageListController(
            @NonNull Context context,
            @NonNull RecyclerView recyclerView,
            int itemLayout,
            @NonNull List<FragmentMessageItem> dataList,
            @NonNull RecyclerView.LayoutManager layoutManager
    ) {
        this.recyclerView = recyclerView;
        this.dataList = dataList;
        this.adapter = new FragmentMessAdapter(context, dataList, itemLayout);

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
    }

    public void addIncomingText(@NonNull String text, @Nullable DeviceModule module) {
        MessageItemTools.appendIncoming(dataList, text, false, null, module, false);
        adapter.notifyItemInserted(dataList.size() - 1);
        scrollToBottom();
    }

    public void addOutgoingText(@NonNull String text, @Nullable DeviceModule module) {
        dataList.add(MessageItemTools.outgoingText(text, null, module, true));
        adapter.notifyItemInserted(dataList.size() - 1);
        scrollToBottom();
    }

    public void appendIncomingText(
            @NonNull String text,
            boolean endsWithLineBreak,
            @Nullable String time,
            @Nullable DeviceModule module,
            boolean showData
    ) {
        MessageItemTools.appendIncoming(dataList, text, endsWithLineBreak, time, module, showData);
    }

    public void appendOrMergeIncomingText(
            @NonNull String text,
            boolean endsWithLineBreak,
            @Nullable String time,
            @Nullable DeviceModule module,
            boolean showData
    ) {
        MessageItemTools.appendOrMergeIncoming(dataList, text, endsWithLineBreak, time, module, showData);
    }

    public void addOutgoingItem(@NonNull FragmentMessageItem item) {
        dataList.add(item);
        adapter.notifyItemInserted(dataList.size() - 1);
        scrollToBottom();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void notifyDataSetChangedAndScrollToBottom() {
        adapter.notifyDataSetChanged();
        scrollToBottom();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void clear() {
        dataList.clear();
        adapter.notifyDataSetChanged();
    }

    public int size() {
        return dataList.size();
    }

    private void scrollToBottom() {
        if (!dataList.isEmpty()) {
            recyclerView.smoothScrollToPosition(dataList.size() - 1);
        }
    }
}
