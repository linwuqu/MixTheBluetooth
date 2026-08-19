package com.hc.bluetoothlibrary.classicBluetooth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class TaskThread {

    public interface WorkCallBack{
        void succeed();
        boolean work() throws Exception;
        void error(Exception e);
    }

    private WorkCallBack call;
    // 主线程调度:context 可能是 Application(新宿主传入),不能强转 Activity 用 runOnUiThread
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    public TaskThread(Context context){
    }

    public void setWorkCall(WorkCallBack call){
        this.call = call;
        start();
    }

    private void start(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(call != null){
                    final boolean b;
                    try {
                        b = call.work();
                    } catch (final Exception e) {
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                call.error(e);
                            }
                        });
                        e.printStackTrace();
                        return;
                    }
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (b){
                                call.succeed();
                            }
                        }
                    });
                }
            }
        }).start();
    }

}
