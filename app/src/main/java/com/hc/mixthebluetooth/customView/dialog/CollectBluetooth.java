package com.hc.mixthebluetooth.customView.dialog;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.basiclibrary.ioc.OnClick;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.databinding.HintCollectMenuBinding;

public class CollectBluetooth extends LinearLayout {

    private final HintCollectMenuBinding mViewBinding;

    private DeviceModule mDeviceModule;

    private OnCollectCallback mCallback;

    private CommonDialog.Builder mBuilder;

    public CollectBluetooth(Context context) {
        this(context,null);
    }

    public CollectBluetooth(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public CollectBluetooth(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.hint_collect_menu,this);
        mViewBinding = HintCollectMenuBinding.bind(this);
        initView();
    }

    private void initView() {
        Analysis.bindViewListener(this::onClick,mViewBinding.hintCollectAffirm,mViewBinding.hintCollectAffirmState2,
                mViewBinding.hintCollectCancelState2,mViewBinding.hintCollectCancel);
    }

    public CollectBluetooth setBuilder(CommonDialog.Builder mBuilder) {
        this.mBuilder = mBuilder;
        return this;
    }

    public CollectBluetooth setDevice(DeviceModule device){
        this.mDeviceModule = device;
        if (device.isCollect()){
            mViewBinding.hintCollectState2.setVisibility(VISIBLE);
        }else {
            mViewBinding.hintCollectState1.setVisibility(VISIBLE);
        }
        return this;
    }

    public void setCallback(OnCollectCallback mCallback) {
        this.mCallback = mCallback;
    }


    private void onClick(View view){

        int id = view.getId();
        if (id == R.id.hint_collect_affirm) {
            affirm(view);
        } else if (id == R.id.hint_collect_affirm_state2) {
            affirmState2(view);
        } else if (id == R.id.hint_collect_cancel || id == R.id.hint_collect_cancel_state2) {
            cancel();
        }
    }

    private void affirmState2(View view) {
        if (mDeviceModule != null){
            mDeviceModule.setCollectModule(view.getContext(),null);
        }
        if (mBuilder != null){
            mBuilder.dismiss();
        }
        if (mCallback != null){
            mCallback.callback();
        }
    }

    private void cancel() {
        if (mBuilder != null) mBuilder.dismiss();
        Log.w("AppRun","取消。。。");
    }

    private void affirm(View view) {
        if (mDeviceModule != null){
            mDeviceModule.setCollectModule(view.getContext(),mViewBinding.hintCollectEdit.getText().toString().trim().equals("")?mDeviceModule.getOriginalName(view.getContext()):mViewBinding.hintCollectEdit.getText().toString().trim());
        }
        if (mBuilder != null){
            mBuilder.dismiss();
        }
        if (mCallback != null){
            mCallback.callback();
        }
    }


    public interface OnCollectCallback{
        void callback();
    }

}
