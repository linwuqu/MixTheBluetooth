package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.databinding.FragmentLogBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentLogAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentLogItem;

import java.util.ArrayList;
import java.util.List;

public class FragmentLog extends BTFragment<FragmentLogBinding> {

    private FragmentLogAdapter mAdapter;
    private final List<FragmentLogItem> mDataList = new ArrayList<>();

    @Override
    protected void initChannels() {
        register(StaticConstants.CH_LOG_MESSAGE);
    }

    @Override
    protected void initAllImpl(View view, Context context) {
        initRecycler();

        onLogMessage(new FragmentLogItem("FragmentLog", "Mock log from FragmentLog.initAllImpl", "w"));
    }

    private void initRecycler() {
        mAdapter = new FragmentLogAdapter(getContext(), mDataList, R.layout.item_log_fragment);
        viewBinding.recyclerLog.setLayoutManager(new LinearLayoutManager(getContext()));
        viewBinding.recyclerLog.setAdapter(mAdapter);
    }

    @Override
    protected void onLogMessage(FragmentLogItem item) {
        if (getActivity() == null || item == null) return;

        logWarn("FragmentLog receive: " + item.getName() + " / " + item.getData());


        mDataList.add(item);
        mAdapter.notifyItemInserted(mDataList.size() - 1);
        viewBinding.recyclerLog.smoothScrollToPosition(mDataList.size() - 1);
    }

    @Override
    protected FragmentLogBinding getViewBinding() {
        return FragmentLogBinding.inflate(getLayoutInflater());
    }
}
