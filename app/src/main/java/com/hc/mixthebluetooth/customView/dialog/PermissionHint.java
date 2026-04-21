package com.hc.mixthebluetooth.customView.dialog;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.databinding.HintPermissionMenuBinding;


public class PermissionHint extends LinearLayout {

    private final HintPermissionMenuBinding mViewBinding;

    private CommonDialog.Builder mBuilder;

    private PermissionHintCallback mCallback;

    private final static String mHint = "你禁止了授权，请在手机设置里面授权，否则App将无法使用";

    public PermissionHint(Context context) {
        this(context,null);
    }

    public PermissionHint(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public PermissionHint(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.hint_permission_menu,this);
        mViewBinding = HintPermissionMenuBinding.bind(this);
        initView();
    }

    private void initView() {
        Analysis.bindViewListener(this::onClick,mViewBinding.hintPermissionAffirm,mViewBinding.hintPermissionCancel);
    }



    private void onClick(View view){
        int id = view.getId();
        if (id == R.id.hint_permission_affirm) {
            affirm();
        } else if (id == R.id.hint_permission_cancel) {
            cancel();
        }
    }

    public PermissionHint setBuilder(CommonDialog.Builder mBuilder) {
        this.mBuilder = mBuilder;
        return this;
    }

    public void setCallback(PermissionHintCallback callback){
        this.mCallback = callback;
    }

    public PermissionHint setPermission(boolean permission){
        if (!permission) {
            mViewBinding.hintPermissionTextHint.setText(mHint);
            mViewBinding.hintPermissionAffirm.setVisibility(GONE);
            mViewBinding.hintPermissionCancel.setText("退出");
            mViewBinding.hintPermissionCancel.setBackgroundResource(R.drawable.cancel_back_off2);
        }
        return this;
    }



    private void cancel() {
        if (mBuilder != null) mBuilder.dismiss();
        if (mCallback != null) mCallback.callback(false);
    }

    private void affirm() {
        if (mBuilder != null) mBuilder.dismiss();
        if (mCallback != null) mCallback.callback(true);
    }

    public interface PermissionHintCallback{
        void callback(boolean permission);
    }

}
