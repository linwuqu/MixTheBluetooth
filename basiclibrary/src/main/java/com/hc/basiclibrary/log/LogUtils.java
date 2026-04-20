package com.hc.basiclibrary.log;

import android.annotation.SuppressLint;
import android.icu.text.SimpleDateFormat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LogUtils {

    private static final List<String> mLogList = new ArrayList<>();

    private static final String TAG = "AppRun";//日志前缀

    public static final String SEPARATOR = "#%SEPARATOR%#";//分隔符

    private LogUtils(){}

    public static void error(Class<?> c, String log){
        Log.e(TAG+c.getSimpleName(),log);
        mLogList.add("e"+SEPARATOR+getTime()+SEPARATOR+c.getSimpleName()+":"+SEPARATOR+log);
    }

    public static void warn(Class<?> c,String log){
        Log.w(TAG+c.getSimpleName(),log);
        mLogList.add("w"+SEPARATOR+getTime()+SEPARATOR+c.getSimpleName()+":"+SEPARATOR+log);
    }

    public static void debug(Class<?> c,String log){
        Log.d(TAG+c.getSimpleName(),log);
        mLogList.add("d"+SEPARATOR+getTime()+SEPARATOR+c.getSimpleName()+":"+SEPARATOR+log);
    }

    public static List<String> getLogList() {
        return mLogList;
    }

    public static void clearLog(){
        mLogList.clear();
    }


    private static String getTime(){
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date date = new Date(System.currentTimeMillis());
        return format.format(date)+"/";
    }
}
