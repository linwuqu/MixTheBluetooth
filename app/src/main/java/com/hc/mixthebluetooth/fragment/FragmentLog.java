package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.hubert.guide.util.LogUtil;
import com.hc.basiclibrary.log.LogUtils;
import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.databinding.FragmentLogBinding;
import com.hc.mixthebluetooth.recyclerData.FragmentLogAdapter;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentLogItem;

import java.util.ArrayList;
import java.util.List;

public class FragmentLog extends BaseFragment<FragmentLogBinding> {

    private FragmentLogAdapter mAdapter;
    private final List<FragmentLogItem> mDataList = new ArrayList<>();

    /**
     * 清屏按钮实现
     */
    private void clear(View v){
        mDataList.clear();
        LogUtils.clearLog();
        mAdapter.notifyDataSetChanged();
    }

    private void initRecycler(){
        mAdapter = new FragmentLogAdapter(getContext(),mDataList,R.layout.item_log_fragment);
       /* viewBinding.recyclerLogFragment.setLayoutManager(new LinearLayoutManager(getContext()));
        viewBinding.recyclerLogFragment.setAdapter(mAdapter);*/
    }

    private void initData() {
        List<String> logArray = LogUtils.getLogList();
        for (String data : logArray) {
            mDataList.add(new FragmentLogItem(
                    Analysis.analysis(data,2,LogUtils.SEPARATOR),
                    Analysis.analysis(data,3,LogUtils.SEPARATOR),
                    Analysis.analysis(data,0,LogUtils.SEPARATOR)));
        }
    }

    @Override
    protected FragmentLogBinding getViewBinding() {
        return FragmentLogBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initAll(View view, Context context) {
        initRecycler();
        initData();
        subscription(StaticConstants.FRAGMENT_STATE_LOG_MESSAGE);
       /* bindOnClickListener(this::clear,viewBinding.clearLogFragment);*/
    }

    @Override
    protected void updateState(String sign, Object o) {
        if (getActivity() == null || !StaticConstants.FRAGMENT_STATE_LOG_MESSAGE.equals(sign) || o == null) return;
        getActivity().runOnUiThread(() -> {
            mDataList.add((FragmentLogItem) o);
            mAdapter.notifyDataSetChanged();
           /* viewBinding.recyclerLogFragment.smoothScrollToPosition(mDataList.size());*/
        });
    }
}
