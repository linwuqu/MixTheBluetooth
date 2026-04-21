package com.hc.mixthebluetooth.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.databinding.ActivityHidBinding;

public class HIDActivity extends BaseActivity<ActivityHidBinding> {


    @Override
    public void initAll() {
        setTitle();
        initView();
    }

    private void initView() {
        bindClickListener(viewBinding.aHintWebDownload1,viewBinding.aHintWebDownload2,viewBinding.aHintDownloadFramework);
        bindClickListener(this::startImage,viewBinding.aHintImage1,viewBinding.aHintImage2,
                viewBinding.aHintImage3,viewBinding.aHintImage4);
    }

    @Override
    protected ActivityHidBinding getViewBinding() {
        return ActivityHidBinding.inflate(getLayoutInflater());
    }

    public void onClickView(View view){
        if (isCheck(viewBinding.aHintWebDownload1) || isCheck(viewBinding.aHintWebDownload2)) setText("http://www.hc01.com/download");
        if (isCheck(viewBinding.aHintDownloadFramework)) setText("https://pan.baidu.com/s/1oO97-wgyiQh8P9DHHFNt8A");
    }


    private void startImage(View view){
        int id = view.getId();
        if (id == R.id.a_hint_image_1) startDialog(120/676f, R.drawable.hid_download_web);
        if (id == R.id.a_hint_image_2) startDialog(242/490f, R.drawable.hid_framework);
        if (id == R.id.a_hint_image_3) startDialog(119/672f, R.drawable.hint_ch340);
        if (id == R.id.a_hint_image_4) startDialog(516/784f, R.drawable.hint_hc_t_software);
    }

    /**
     * 点击图片，将图片放大，图片宽铺满屏幕，高随着宽放大倍率增长
     * @param ratio 图片高的倍率
     * @param drawable 指定的图片
     */
    private void startDialog(float ratio,int drawable) {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width =dm.widthPixels;
        int height = (int)(width*ratio);
        CommonDialog.Builder builder = new CommonDialog.Builder(this, R.style.dialog_dim);
        builder.setView(R.layout.dialog_image).fullWidth().loadAnimation().create().show();
        ImageView imageView = builder.getView(R.id.dialog_image);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(width,height));
        imageView.setImageResource(drawable);
    }

    private void setText(String data){
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("HC蓝牙",data);
        manager.setPrimaryClip(clipData);
        toastLong("复制成功，请发送到电脑上打开此链接");
    }


    //设置头部
    private void setTitle() {
         new DefaultNavigationBar
                .Builder(this,(ViewGroup)findViewById(R.id.hid_name))
                .setTitle("下载步骤")
                .builer();
    }



}
