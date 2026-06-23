package com.hc.mixthebluetooth.ui.cgm;

import android.content.Context;
import android.graphics.Color;
import android.widget.TextView;

import com.hc.basiclibrary.recyclerAdapterBasic.ItemClickListener;
import com.hc.basiclibrary.recyclerAdapterBasic.RecyclerCommonAdapter;
import com.hc.basiclibrary.recyclerAdapterBasic.ViewHolder;
import com.hc.mixthebluetooth.R;

import java.util.List;

public class MessageAdapter extends RecyclerCommonAdapter<MessageItem> {

    private static final int COLOR_RECV = Color.parseColor("#79D0A5");
    private static final int COLOR_SEND = Color.parseColor("#FF7C10");

    public MessageAdapter(Context context, List<MessageItem> fragmentMessageItems, int layoutId) {
        super(context, fragmentMessageItems, layoutId);
    }

    @Override
    protected void convert(ViewHolder holder, MessageItem item, int position, ItemClickListener itemClickListener) {
        boolean isReceived = " <- ".equals(item.getSign());
        int signColor = isReceived ? COLOR_RECV : COLOR_SEND;

        holder.setText(R.id.item_message_fragment_time, item.getTime());
        holder.setTextAndColor(R.id.item_message_fragment_sign, item.getSign(), signColor);
        holder.setText(R.id.item_message_fragment_data, item.getData());
    }
}
