package com.hc.mixthebluetooth.customView.dialog;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.tool.Analysis;
import com.hc.mixthebluetooth.databinding.HintSetButtonMenuBinding;

public class SetButton extends LinearLayout {

    private final HintSetButtonMenuBinding mViewBinding;

    private OnCollectCallback mCallback;

    private CommonDialog.Builder mBuilder;

    public SetButton(Context context) {
        this(context,null);
    }

    public SetButton(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public SetButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.hint_set_button_menu,this);
        mViewBinding = HintSetButtonMenuBinding.bind(this);
        initView();
    }

    public SetButton setBuilder(CommonDialog.Builder mBuilder) {
        this.mBuilder = mBuilder;
        return this;
    }

    public void setCallback(OnCollectCallback mCallback) {
        this.mCallback = mCallback;
    }


    public void showMove(boolean isClick){
        mViewBinding.hintHideLinear.setVisibility(VISIBLE);
        mViewBinding.hintHideClick.setChecked(!isClick);
        mViewBinding.hintHideLongClick.setChecked(isClick);
        setHideTimeLinear(isClick);
    }

    public SetButton setEditText(String name,String content){
        mViewBinding.hintSetButtonName.setText(name);
        mViewBinding.hintSetButtonContent.setText(content);
        return this;
    }

    public SetButton setTime(int time){
        mViewBinding.hintHideTime.setText(String.valueOf(time));
        return this;
    }

    private void setHideTimeLinear(boolean isClick) {
        if (isClick){
            mViewBinding.hintHideTimeLinear.setVisibility(VISIBLE);
        }else {
            mViewBinding.hintHideTimeLinear.setVisibility(GONE);
        }
    }




    private void onClick(View view){
        int id = view.getId();
        if (id == R.id.hint_set_button_affirm) {
            affirm();
        } else if (id == R.id.hint_set_button_cancel) {
            cancel();
        }
    }

    private void cancel() {
        if (mBuilder != null)
            mBuilder.dismiss();
    }

    private void affirm() {
        if (mViewBinding.hintSetButtonName.getText().toString().trim().equals("")){
            Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mViewBinding.hintSetButtonContent.getText().toString().trim().equals("")){
            Toast.makeText(getContext(), "内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mViewBinding.hintHideLongClick.isChecked() && mViewBinding.hintHideTime.getText().toString().trim().equals("")){
            Toast.makeText(getContext(), "时间不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mViewBinding.hintHideLongClick.isChecked() && Integer.parseInt(mViewBinding.hintHideTime.getText().toString().trim())<10){
            Toast.makeText(getContext(), "发送间隔不要小于10毫秒", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mBuilder != null){
            mBuilder.dismiss();
        }
        if (mCallback != null ){
            if (mViewBinding.hintHideLinear.getVisibility() == View.GONE)
                mCallback.callback(mViewBinding.hintSetButtonName.getText().toString().trim(),mViewBinding.hintSetButtonContent.getText().toString().trim());
            else {
                mCallback.callLongClick(mViewBinding.hintSetButtonName.getText().toString().trim(), mViewBinding.hintSetButtonContent.getText().toString().trim(),
                        mViewBinding.hintHideLongClick.isChecked(), mViewBinding.hintHideTime.getText().toString().trim());
                Toast.makeText(getContext(), "设置完成，关闭\"设置方向按钮\"后，就可以使用此按键了", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void upDateView(View view){
        mViewBinding.hintHideClick.toggle();
        mViewBinding.hintHideLongClick.toggle();
        setHideTimeLinear(mViewBinding.hintHideLongClick.isChecked());
    }

    private void initView() {
        Analysis.bindViewListener(this::onClick,mViewBinding.hintSetButtonAffirm,mViewBinding.hintSetButtonCancel);
        Analysis.bindViewListener(this::upDateView,mViewBinding.hintHideLongClick,mViewBinding.hintHideClick);
    }

    public interface OnCollectCallback{
        void callback(String name,String content);
        void callLongClick(String name,String content,boolean isLongClick,String time);
    }

}
