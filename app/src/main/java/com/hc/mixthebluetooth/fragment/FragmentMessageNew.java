package com.hc.mixthebluetooth.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.databinding.FragmentMessageNewBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentMessAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.util.ArrayList;
import java.util.List;

public class FragmentMessageNew extends BaseFragment<FragmentMessageNewBinding> {

    private FragmentMessAdapter mAdapter;
    private final List<FragmentMessageItem> mDataList = new ArrayList<>();
    private DeviceModule module;

    @Override
    protected void initAll(View view, Context context) {
        initRecycler();
        initData();
    }

    private void initRecycler() {
        mAdapter = new FragmentMessAdapter(getContext(), mDataList, R.layout.item_message_fragment);
        viewBinding.recyclerMessageNew.setLayoutManager(new LinearLayoutManager(getContext()));
        viewBinding.recyclerMessageNew.setAdapter(mAdapter);
    }

    private void initData() {
        subscription(StaticConstants.FRAGMENT_STATE_DATA);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void updateState(String sign, Object o) {
        if (StaticConstants.FRAGMENT_STATE_DATA.equals(sign)) {
            if (o instanceof DeviceModule) {
                module = (DeviceModule) o;
                return;
            }

            if (o instanceof Object[]) {
                Object[] objects = (Object[]) o;
                if (objects.length < 2) return;
                byte[] data = (byte[]) objects[1];

                mDataList.add(new FragmentMessageItem(new String(data), null, false, module, false));
                mAdapter.notifyDataSetChanged();
                viewBinding.recyclerMessageNew.smoothScrollToPosition(mDataList.size() - 1);
            }
        }
    }

    @Override
    protected FragmentMessageNewBinding getViewBinding() {
        return FragmentMessageNewBinding.inflate(getLayoutInflater());
    }


}
