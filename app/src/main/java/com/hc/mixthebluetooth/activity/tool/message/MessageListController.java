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

    private final Context context;
    private final RecyclerView recyclerView;
    private final List<FragmentMessageItem> dataList = new ArrayList<>();
    private final FragmentMessAdapter adapter;

    public MessageListController(@NonNull Context context, @NonNull RecyclerView recyclerView, int itemLayout) {
        this.context = context;
        this.recyclerView = recyclerView;
        this.adapter = new FragmentMessAdapter(context, dataList, itemLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
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
