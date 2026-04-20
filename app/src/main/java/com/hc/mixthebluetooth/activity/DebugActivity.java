package com.hc.mixthebluetooth.activity;


import android.view.View;


import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.basiclibrary.viewBasic.tool.Utility;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.databinding.ActivityDebugBinding;
import com.hc.mixthebluetooth.storage.Storage;

public class DebugActivity extends BaseActivity<ActivityDebugBinding> {

    private Storage mStorage;

    @Override
    public void initAll() {
        initView();
        initTitle();
        initData();
    }

    @Override
    protected ActivityDebugBinding getViewBinding() {
        return ActivityDebugBinding.inflate(getLayoutInflater());
    }

    private void initData() {

    }

    private void initView() {
        bindClickListener(viewBinding.debugRead,viewBinding.debugDevelopmentMode);
        if (mStorage == null) mStorage = new Storage(this);
        if(mStorage.getData(HoldBluetooth.DEVELOPMENT_MODE_KEY)){
            viewBinding.debugDevelopmentMode.staysOn();
        }else {
            viewBinding.debugDevelopmentMode.closed();
        }
    }

    @Override
    public void onClickView(View v) {
        if (isCheck(viewBinding.debugRead)){
            viewBinding.bugLog.setText(Utility.load(this,"errNewLog"));
        }else if(isCheck(viewBinding.debugDevelopmentMode)){
            mStorage.saveData(HoldBluetooth.DEVELOPMENT_MODE_KEY,!viewBinding.debugDevelopmentMode.isChick());
            viewBinding.debugDevelopmentMode.toggle();
        }
    }

    private void initTitle(){
        new DefaultNavigationBar
                .Builder(this, findViewById(R.id.debug_activity))
                .setTitle("Bug日志")
                .hideLeftText()
                .hideRightText()
                .builer();
    }

}
