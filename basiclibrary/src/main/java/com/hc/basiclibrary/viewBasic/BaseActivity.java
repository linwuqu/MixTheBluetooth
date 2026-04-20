package com.hc.basiclibrary.viewBasic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

import com.hc.basiclibrary.log.LogUtils;
import com.hc.basiclibrary.viewBasic.tool.Utility;
import com.jeremyliao.liveeventbus.LiveEventBus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public abstract class BaseActivity<T extends ViewBinding> extends AppCompatActivity implements View.OnClickListener {
    protected T viewBinding;
    private Bundle extras;
    private boolean isAlive = true;//是否处于前台
    private View viewCheck;//缓存响应点击的view

    @Override
    protected final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
        * Thread类是JVM中用来管理线程的一个类,currentTread()返回现在正在执行的线程信息，setUncaughtExceptionHandler捕获线程异常
        * */
        Thread.currentThread().setUncaughtExceptionHandler(new MyUncaughtExceptionHandler());
        /*
        viewBinding来自getViewBinding(),而这个方法来着类T，类T继承ViewBinding，这会有个View类，
        view类是Android的一个超类，这个类几乎包含了所有的屏幕类型，每一个view都有一个用于绘画 的画布，这个画布可以进行任意的扩展，一个view占用一个矩形框。
        * */
        viewBinding = getViewBinding();
        setContentView(viewBinding.getRoot());
        /*
        * 定义一个抽象方法，执行功能从这里开始,就是这个initAll()方法
        * */
        initAll();
    }

    /**
     * 订阅从其他Activity Fragment Service发来的数据
     * @param keys 数据标志
     */

    protected void subscription(String ...keys){
        for (String key : keys) {
            LiveEventBus.get(key).observe(this,data-> update(key,data));
        }
    }

    protected void startActivity(Class<?> clazz){
        Intent intent = new Intent(viewBinding.getRoot().getContext(),clazz);
        if (extras != null )intent.putExtras(extras);
        startActivity(intent);
    }
    /**
     * 返回Bundle Activity -> Activity 传递数据的载体
     */
    protected Bundle getExtras(){
        if (extras == null ) extras = new Bundle();
        return extras;
    }

    protected void log(String data){
        LogUtils.debug(viewBinding.getClass(),data);
    }

    protected void logWarn(String data){
        LogUtils.warn(getClass(),data);
    }

    protected void logError(String data){
        LogUtils.error(getClass(),data);
    }

    protected void toastShort(String data){
        if (Looper.getMainLooper() == Looper.myLooper()) {
            Toast.makeText(this, data, Toast.LENGTH_SHORT).show();
        }else {
            runOnUiThread(() -> Toast.makeText(this, data, Toast.LENGTH_SHORT).show());
        }
    }
    
    protected void toastLong(String data){
        if (Looper.getMainLooper() == Looper.myLooper()){
            Toast.makeText(this, data, Toast.LENGTH_LONG).show();
        }else {
            runOnUiThread(()-> Toast.makeText(this, data, Toast.LENGTH_LONG).show());
        }
    }

    protected void toastShortAlive(String data){
        if (isAlive) toastShort(data);
    }

    /**
     *
     * @param key 由key值识别保存 将数据传递到下一个Activity中的数据
     * @param data 保存的数据，支持类型,String int float char boolean double byte long short
     *                                 byte[] String[] int[] Parcelable
     */
    protected void setTransferData(@NonNull String key,Object data){
        if (extras == null) extras = new Bundle();
        if (data instanceof String){
            extras.putString(key,(String) data);
        } else if (data instanceof Integer){
            extras.putInt(key, (Integer) data);
        } else if (data instanceof Float){
            extras.putFloat(key, (Float) data);
        } else if (data instanceof Character){
            extras.putChar(key, (Character) data);
        } else if (data instanceof Boolean){
            extras.putBoolean(key, (Boolean) data);
        } else if (data instanceof Double){
            extras.putDouble(key, (Double) data);
        } else if (data instanceof Byte){
            extras.putByte(key, (Byte) data);
        } else if (data instanceof Long){
            extras.putLong(key, (Long) data);
        } else if(data instanceof Short){
            extras.putShort(key, (Short) data);
        } else if (data instanceof byte[]){
            extras.putByteArray(key, (byte[]) data);
        } else if (data instanceof String[]){
            extras.putStringArray(key, (String[]) data);
        } else if (data instanceof int[]){
            extras.putIntArray(key, (int[]) data);
        } else if (data instanceof Parcelable){
            extras.putParcelable(key, (Parcelable) data);
        } else{
            throw new RuntimeException("data 类型没有收录，请使用 getExtras() 方法赋值");
        }
    }

    @SuppressWarnings("unchecked")
    protected <E> E getData(@NonNull String key,Class<E> type,Object ...defaultValue){
        Object data;
        if (String.class.equals(type)) data =  getIntent().getStringExtra(key);
        else if (Integer.class.equals(type)) data =  getIntent().getIntExtra(key,defaultValue.length>0? (int)defaultValue[0]:0);
        else if (Float.class.equals(type)) data = getIntent().getFloatExtra(key,defaultValue.length>0?(float)defaultValue[0] : 0);
        else if (Character.class.equals(type)) data = getIntent().getCharExtra(key,defaultValue.length>0?(char)defaultValue[0]:'0');
        else if (Boolean.class.equals(type)) data = getIntent().getBooleanExtra(key, defaultValue.length > 0 && (boolean) defaultValue[0]);
        else if (Double.class.equals(type)) data = getIntent().getDoubleExtra(key,defaultValue.length>0?(double)defaultValue[0]:0);
        else if (Byte.class.equals(type)) data = getIntent().getByteExtra(key,defaultValue.length>0?(byte)defaultValue[0]:(byte)0);
        else if (Long.class.equals(type)) data = getIntent().getLongExtra(key,defaultValue.length>0?(long)defaultValue[0]:0);
        else if (Short.class.equals(type)) data = getIntent().getShortExtra(key,defaultValue.length>0?(Short)defaultValue[0]:(short)0);
        else if (byte[].class.equals(type)) data = getIntent().getByteArrayExtra(key);
        else if (String[].class.equals(type)) data = getIntent().getStringArrayExtra(key);
        else if (int[].class.equals(type)) data = getIntent().getIntArrayExtra(key);
        else if (Parcelable.class.equals(type)) data = getIntent().getParcelableExtra(key);
        else throw new RuntimeException("没有收录"+type.getSimpleName()+"类型，请使用getIntent().get"+type.getSimpleName()+"Extra() 方法获取数值");
        return (E) data;
    }

    protected abstract void initAll();

    protected abstract T getViewBinding();

    /**
     * 将数据发送到fragment，需要对应的fragment订阅相应key值
     * @param key fragment需要订阅的key值
     * @param obj 数据主体
     */
    protected void sendDataToFragment(@NonNull String key,@Nullable Object obj){
        LiveEventBus.get(key).post(obj);
    }

    /**
     * 绑定多个点击事件
     * @param onClickListener 点击事件接口
     * @param views 需要绑定点击事件的多个View
     */
    protected void bindClickListener(View.OnClickListener onClickListener,View ...views){
        for (View view : views) {
            view.setOnClickListener(onClickListener);
        }
    }

    /**
     * 绑定多个点击事件，但必须重写OnClick方法
     * @param views 需要绑定的多个View
     */
    protected void bindClickListener(View ...views){
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

    /**
     * 重写此方法，实现监听从其他Activity Fragment Service发来的各种数据
     * 需要先订阅 subscription
     * @param sign 标志，用于识别各种类型信息
     * @param data 消息主体
     */
    protected void update(String sign,Object data){

    }

    private class MyUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        //当有未捕获的异常的时候会调用
        //Throwable : 其实Exception和Error父类
        @Override
        public void uncaughtException(@NonNull Thread thread, Throwable ex) {
            //将异常保存到文件中
            try {
                //异常文件log.txt，可以判断返回给我们的服务器
                ex.printStackTrace(new PrintStream(new File("log.txt")));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            Writer w = new StringWriter();
            ex.printStackTrace(new PrintWriter(w));
            String smsg = w.toString();
            Utility.saveNew(viewBinding.getRoot().getContext(),smsg);
            smsg += "     -------这是一处错误(%&*@#$)";
            LogUtils.error(getClass(), smsg);
            Utility.save(viewBinding.getRoot().getContext(),smsg);
            //保存文件之后，自杀,myPid() : 获取自己的pid
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAlive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAlive = true;
    }


}
