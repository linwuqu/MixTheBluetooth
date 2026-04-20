package com.hc.mixthebluetooth.customView.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.databinding.HintCollectMenuBinding;


public class SetMtu extends LinearLayout {

    private final HintCollectMenuBinding mViewBinding;

    private MTUCallback mCallback;

    private CommonDialog.Builder mBuilder;

    public SetMtu(Context context) {
        this(context,null);
    }

    public SetMtu(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public SetMtu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.hint_collect_menu,this);
        mViewBinding = HintCollectMenuBinding.bind(this);
        setView();
    }

    @SuppressLint("SetTextI18n")
    private void setView() {
        mViewBinding.hintCollectState1.setVisibility(View.VISIBLE);
        mViewBinding.hintCollectTitle.setText("设置MTU");
        mViewBinding.hintCollectExplain.setText("设置MTU的大小");
        mViewBinding.hintCollectEdit.setHint("23 - 512");
        mViewBinding.hintCollectEdit.setKeyListener(new NumberKeyListener() {
            @Override
            @NonNull
            protected char[] getAcceptedChars() {
                return new char[] { '1', '2', '3', '4', '5', '6', '7', '8','9', '0' };
            }
            @Override
            public int getInputType() {
                // TODO Auto-generated method stub
                return android.text.InputType.TYPE_CLASS_PHONE;
            }
        });//设置纯数字输入

        //绑定点击事件
        Analysis.bindViewListener(this::onClick,mViewBinding.hintCollectCancel,mViewBinding.hintCollectAffirm);
    }


    private void onClick(View view){
        int id = view.getId();
        if (id == R.id.hint_collect_affirm) {
            affirm();
        } else if (id == R.id.hint_collect_cancel) {
            cancel();
        }
    }

    public SetMtu setBuilder(CommonDialog.Builder mBuilder) {
        this.mBuilder = mBuilder;
        return this;
    }

    public void setCallback(MTUCallback mCallback) {
        this.mCallback = mCallback;
    }

    private void cancel() {
        if (mBuilder != null) mBuilder.dismiss();
    }

    private void affirm() {
        int mtu = Integer.parseInt(mViewBinding.hintCollectEdit.getText().toString().trim());
        if (mCallback != null) mCallback.setMTU(mtu);
        cancel();
    }

    public interface MTUCallback{
        void setMTU(int mtu);
    }

}
