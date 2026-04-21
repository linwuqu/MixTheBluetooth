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
import com.hc.mixthebluetooth.databinding.HintHidMenuBinding;
import com.hc.mixthebluetooth.storage.Storage;

public class HintHID extends LinearLayout {

    private CommonDialog.Builder mBuilder;

    private final HintHidMenuBinding mViewBinding;

    private static boolean isShow = true;

    private DismissListener listener;

    public HintHID(Context context) {
        this(context,null);
    }

    public HintHID(Context context, AttributeSet attrs) {
        this(context, attrs,0);
    }

    public HintHID(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        inflate(context, R.layout.hint_hid_menu,this);
        mViewBinding = HintHidMenuBinding.bind(this);
        initView();
    }

    private void initView() {
        Analysis.bindViewListener(this::onClock,mViewBinding.hintHidDownload,mViewBinding.hintHidRight,
                mViewBinding.hintHidNoShow,mViewBinding.hintHidNoShowText);
    }

    public void setBuilder(CommonDialog.Builder mBuilder) {
        this.mBuilder = mBuilder;
    }

    public boolean isShow() {
        if (isShow){
            isShow = false;
            return true;
        }
        return false;
    }

    public void setOnDismissListener(DismissListener listener){
        this.listener = listener;
    }


    private void onClock(View view){
        int id = view.getId();
        if (id == R.id.hint_hid_download) {
            mBuilder.dismiss();
            view.getContext().startActivity(new Intent(view.getContext(), HIDActivity.class));
            setNoShowPopWindow();
        } else if (id == R.id.hint_hid_right) {
            mBuilder.dismiss();
            setNoShowPopWindow();
            callback();
        } else if (id == R.id.hint_hid_no_show || id == R.id.hint_hid_no_show_text) {
            mViewBinding.hintHidNoShow.setChecked(!mViewBinding.hintHidNoShow.isChecked());
        }
    }

    private void setNoShowPopWindow(){
        if (mViewBinding.hintHidNoShow.isChecked()){
            Storage storage = new Storage(getContext());
            storage.saveFirstTime();
        }
    }

    public interface DismissListener{
        void dismiss();
    }

    private void callback(){
        if (listener == null) return;
        listener.dismiss();
    }

}
