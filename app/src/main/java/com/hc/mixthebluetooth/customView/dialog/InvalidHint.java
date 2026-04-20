package com.hc.mixthebluetooth.customView.dialog;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.HIDActivity;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.databinding.HintInvalidMenuBinding;
import com.hc.mixthebluetooth.storage.Storage;

public class InvalidHint extends LinearLayout {

    private final HintInvalidMenuBinding mViewBinding;

    private CommonDialog.Builder mBuilder;

    private Storage mStorage;

    public InvalidHint(Context context) {
        this(context,null);
    }

    public InvalidHint(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public InvalidHint(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.hint_invalid_menu,this);
        mViewBinding = HintInvalidMenuBinding.bind(this);
        initView();
    }

    private void initView() {
        Analysis.bindViewListener(this::onClick,mViewBinding.hintInvalidAffirm,mViewBinding.hintInvalidCancel,
                mViewBinding.hintInvalidHintCheck,mViewBinding.hintInvalidHint,mViewBinding.hintInvalidHid);
    }

    private void onClick(View view){
        int id = view.getId();
        if (id == R.id.hint_invalid_affirm) {
            affirm();
        } else if (id == R.id.hint_invalid_cancel) {
            cancel();
        } else if (id == R.id.hint_invalid_hid) {
            mBuilder.getContext().startActivity(new Intent(mBuilder.getContext(), HIDActivity.class));
            affirm();
        } else if (id == R.id.hint_invalid_hint || id == R.id.hint_invalid_hint_check) {
            mViewBinding.hintInvalidHintCheck.toggle();
        }
    }

    public InvalidHint setBuilder(CommonDialog.Builder mBuilder) {
        this.mBuilder = mBuilder;
        return this;
    }

    private void cancel() {
        if (mBuilder != null) mBuilder.dismiss();
    }

    private void affirm() {
        if (mBuilder != null){
            saveData();
            mBuilder.dismiss();
        }
    }

    private void saveData(){
        if (mViewBinding.hintInvalidHintCheck.isChecked()) {
            if (mStorage == null) mStorage = new Storage(mBuilder.getContext());
            mStorage.saveInvalidAT();
        }
    }

}
