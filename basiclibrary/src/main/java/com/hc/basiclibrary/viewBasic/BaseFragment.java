package com.hc.basiclibrary.viewBasic;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.hc.basiclibrary.log.LogUtils;
import com.jeremyliao.liveeventbus.LiveEventBus;

public abstract class BaseFragment<T extends ViewBinding> extends Fragment implements View.OnClickListener {

    protected T viewBinding;

    private View viewCheck;//缓存响应点击事件的view

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewBinding = getViewBinding();
        initAll(viewBinding.getRoot(),getContext());
        return viewBinding.getRoot();
    }

    /**
     * 将数据发送往Activity，需要对应的Activity订阅key值
     * @param key activity需要订阅的key值
     * @param obj 数据主体
     */
    protected void sendDataToActivity(@NonNull String key,@Nullable Object obj){
        LiveEventBus.get(key).post(obj);
    }

    /**
     * 订阅从其他Activity，Fragment Service发来的通知。
     * @param keys 指定的key
     */
    protected void subscription(String ...keys){
        for (String key : keys) {
            LiveEventBus.get(key).observe(this,  data -> updateState(key,data));
        }
    }

    /**
     * 绑定多个View的点击事件
     * @param onClickListener 点击事件接口
     * @param views 需要绑定的View
     */
    protected void bindOnClickListener(View.OnClickListener onClickListener,View ...views){
        for (View view : views) {
            view.setOnClickListener(onClickListener);
        }
    }

    /**
     * 绑定点击事件，需要重写OnClick()方法
     * @param views 需要绑定的View
     */
    protected void bindOnClickListener(View ...views){
        for (View view : views) {
            view.setOnClickListener(this);
        }
    }

    @Override
    public final void onClick(View v) {
        viewCheck = v;
        onClickView(v);
    }

    /**
     * bindClickListener绑定的View点击事件的回调
     * @param v 响应点击事件的view
     */
    protected void onClickView(View v){

    }

    /**
     * 限制，只能在onClickView中使用，其他地方使用无效
     * 传入的view将和响应点击的view比较，id相同返回true
     * @param view 通过bindClickListener()绑定的view
     * @return true 传入的view响应点击，false 传入的view非响应点击事件的view
     */
    protected boolean isCheck(View view){
        return equals(view,viewCheck);
    }

    /**
     * 对比两个View，若两个View的ID一样，则返回true
     */
    protected boolean equals(View view1,View view2){
        if (view1 == null || view2 == null) return false;
        return view1.getId() == view2.getId();
    }

    protected void toastShort(String data){
        if (Looper.getMainLooper() == Looper.myLooper()){
            Toast.makeText(getContext(), data, Toast.LENGTH_SHORT).show();
        }else {
            Activity activity = getActivity();
            if (activity != null){
                activity.runOnUiThread(()-> Toast.makeText(activity, data, Toast.LENGTH_SHORT).show());
            }
        }
    }

    protected void toastLong(String data){
        if (Looper.getMainLooper() == Looper.myLooper()){
            Toast.makeText(getContext(), data, Toast.LENGTH_LONG).show();
        }else {
            Activity activity = getActivity();
            if (activity != null){
                activity.runOnUiThread(()-> Toast.makeText(activity, data, Toast.LENGTH_LONG).show());
            }
        }
    }

    protected void log(String data){
        LogUtils.debug(getClass(),data);
    }

    protected void logWarn(String data){
        LogUtils.warn(getClass(),data);
    }

    protected void logError(String data){
        LogUtils.error(getClass(),data);
    }

    protected abstract T getViewBinding();

    protected abstract void initAll(View view, Context context);

    /**
     * 从其他的Activity Fragment Service发来的信息
     * @param sign 信息类别，与订阅的一致
     * @param o 信息主体
     */
    protected abstract void updateState(String sign,Object o);
}
