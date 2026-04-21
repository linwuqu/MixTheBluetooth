package com.hc.mixthebluetooth.activity.tool;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.hc.basiclibrary.log.LogUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;

public class Analysis {

    private static long time = 0;//记录下时间戳，用于2.0蓝牙全速发送数据时计算速度

    public static String getByteToString(byte[] bytes,String code,boolean isHex,boolean isExamineNewline){
        try {
            if (isHex) {
                return bytesToHexString(bytes);
            } else {
                if (isExamineNewline){
                    bytes[bytes.length-1] = 0;
                    bytes[bytes.length-2] = 0;
                }
                return new String(bytes, 0, bytes.length, code);
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    //String字符串的互转
    public static String changeHexString(boolean isChangeHex,String string){
        if (string == null||string.isEmpty()){
            return "";
        }
        if (isChangeHex) {
            try {
                return bytesToHexString(string.getBytes("GBK"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return "";
        }else {
            return hexStringToString(string);
        }
    }

    //byte数组转String
    private static String bytesToHexString(byte[] bArray) {
        StringBuilder sb = new StringBuilder(bArray.length);
        String sTemp;
        for (byte b : bArray) {
            sTemp = Integer.toHexString(0xFF & b);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase());
        }
        int length = sb.length();
        if (length == 1||length == 0){
            return sb.toString();
        }
        if (length%2==1){
            sb.insert(length-1," ");
            length= length-1;
        }
        for (int i = length;i>0;i=i-2){
            sb.insert(i," ");
        }
        return sb.toString();
    }


    public static String hexStringToString(String s) {
        if (s == null || s.equals("")) return null;

        s = s.replace(" ", "");
        byte[] baKeyword = new byte[s.length() / 2];
        for (int i = 0; i < baKeyword.length; i++) {
            try {
                baKeyword[i] = (byte) (0xff & Integer.parseInt(
                        s.substring(i * 2, i * 2 + 2), 16));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try {
            s = new String(baKeyword, "gbk");
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        return s;
    }

    public static boolean detection(String str){
        int number = str.length();
        String scopeStr = "0123456789AaBbCcDdEeFf ";
        for (int i= 1;i<=number;i++){
            if (!scopeStr.contains(str.substring(i-1,i))) return true;
        }
        return false;
    }

    public static void setHex(String text, int start, int before, int count, EditText editText){
        if (before > 0) return;
        if (text == null || text.equals("")) return;
        String temp = text.substring(start,start+count);
        if (detection(temp)){
            //Log.d("AppRun","temp.size is "+temp.length()+" count is "+count);
            String newStr = text.substring(0,start)+text.substring(start+count);
            editText.setText(newStr.toUpperCase());
            editText.setSelection(newStr.length());
        }else {
            editText.setText(text.toUpperCase());
            editText.setSelection(text.length());
        }
    }

    /**
     * 将字符串转为byte数组
     * @param data:字符串
     * @param code:编码格式 "GBK" "UTF"
     * @param isHex:是否16进制
     * @return  返回byte数组
     */
    public static byte[] getBytes(String data,String code,boolean isHex){
        byte[] buff = null;
        if (!isHex) {
            try {
                buff =data.getBytes(code);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        else {
            buff = hexString2ByteArray(data);
        }
        return buff;
    }

    /**
     * 将16进制字符串转换为byte[]
     */
    public static byte[] hexString2ByteArray(String bs) {
        if (bs == null) return null;

        int bsLength = bs.length();
        if (bsLength % 2 != 0) {
            bs = "0"+bs;
            bsLength = bs.length();
        }
        byte[] cs = new byte[bsLength / 2];
        String st;
        for (int i = 0; i < bsLength; i = i + 2) {
            st = bs.substring(i, i + 2);
            cs[i / 2] = (byte) Integer.parseInt(st, 16);
        }
        return cs;
    }

    /**
     * 按顺序提取出字符串中的 0-9 a-b A-B
     * @return 返回提取的内容
     */
    public static String getFiltrationHexString(String data){
        data = data.replaceAll(" ","");
        String[] strings = data.split("");
        StringBuilder builder = new StringBuilder();
        for (String string : strings) {
            if (string == null || string.equals("") || string.length() == 0) continue;
            char c = string.charAt(0);
            if (c>='0'&&c<='9'||c>='a'&&c<='f'||c>='A'&&c<='F'){
                builder.append(string);
            }
        }
        return builder.toString().replaceAll(" ","");
    }


    //按间隔取出，例如analysis("123aa456aa789aa000",2,"aa") ->"789"
    //最多3个间隔，4个字符串，从0开始取
    public static String analysis(final String data,int number,String key){
        if (number == 0) return data.substring(0,data.indexOf(key));
        if (number == 1) {
            String string = data.substring(data.indexOf(key) + key.length());
            return string.substring(0,string.indexOf(key));
        }
        if (number == 2){
            int length2 = analysis(data,1,key).length();
            int length1 = analysis(data,0,key).length();
            String string = data.substring(length1+length2+key.length()*2);
            return string.substring(0,string.indexOf(key));
        }else {
            int length = analysis(data,0,key).length()+analysis(data,1,key).length()+analysis(data,2,key).length()+key.length()*3;
            return data.substring(length);
        }
    }

    //判断GPS是否开启，GPS或者AGPS开启一个就认为是开启的
    public static boolean isOpenGPS(final Context context) {
        LocationManager locationManager
                = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean gps = true,network = true;
        // GPS定位
        if (locationManager != null) gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 网络服务定位
        if (locationManager != null) network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gps || network;
    }

    public static String getTime(){
        //获取系统的 日期
        Calendar calendar=Calendar.getInstance();

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        String secondStr = second<10?"0"+second:second+"";
        String minuteStr = minute<10?"0"+minute:minute+"";
        String hourStr = hour<10?"0"+hour:hour+"";

        return hourStr+":"+minuteStr+":"+secondStr+" ";
    }

    /**
     * 动态改变view的高度动画效果
     * 原理:动画改变view LayoutParams.height的值
     * @param view 要进行高度改变动画的view
     * @param startHeight 动画前的view的高度
     * @param endHeight 动画后的view的高度
     */
    public static void changeViewHeightAnimatorStart(final View view, final int startHeight, final int endHeight){
        if(view!=null&&startHeight>=0&&endHeight>=0){
            ValueAnimator animator=ValueAnimator.ofInt(startHeight,endHeight);
            animator.setDuration(200);
            animator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams params=view.getLayoutParams();
                params.height= (int) animation.getAnimatedValue();
                view.setLayoutParams(params);
            });
            animator.start();
        }
    }

    /**
     * 批量绑定View点击事件
     * @param listener 点击事件监听
     * @param views 需要绑定的view数组
     */
    public static void bindViewListener(@NonNull View.OnClickListener listener, View ...views){
        for (View view : views) {
            view.setOnClickListener(listener);
        }
    }


    /**
     * 计算发送速度
     * @param dataLength 发送数据的量
     * @param setTime 是否规定时间，规定时间为200ms
     * @return 速度
     */
    public static String getSpeed(int dataLength,boolean setTime,boolean end){
        if (!setTime) return (dataLength*5)+"B/s";
        if (end){
            time = 0;
            return "0B/s";
        }
        if (time == 0){
            time = System.currentTimeMillis();
            return  "0B/s";
        }
        long sendTime = System.currentTimeMillis()-time;
        Log.w("AppRun","时间间隔: "+sendTime);
        time = System.currentTimeMillis();
        int speed = (int) (dataLength/(sendTime/1000f));
        return speed+"B/s";
    }



    @SuppressWarnings("All")
    private static byte[] readFile(File file) throws Exception{
        int length = (int) file.length();
        byte[] bytes = new byte[length];
        LogUtils.warn(Analysis.class,"创建数组,长度为: "+length);

        FileInputStream in = new FileInputStream(file);
        in.read(bytes);
        in.close();
        return bytes;
    }


    /**
     * 调用子线程执行读取文件操作
     * @param file 指定的文件
     * @param activity 在Activity或Fragment中创建的Handler
     * @param callback 回调文件数据
     */
    public static void readFileDate(File file, Activity activity, IFileDataTypeByte callback){
        correction();
        new Thread(() -> {
            try {
                byte[] bytes = Analysis.readFile(file);
                activity.runOnUiThread(()->callback.callbackFileData(true,bytes));
            } catch (Exception e) {
                activity.runOnUiThread(()->callback.callbackFileData(false,null));
                e.printStackTrace();
            }
        }).start();
    }

    private static void correction(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P){
            return;
        }

        Log.w("AppRun","执行correction");

        try{
            Object sVmRuntime = null;
            Method forName = Class.class.getDeclaredMethod("forName",String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod",
                    String.class,Class[].class);
            Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null,"dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass,"getRuntime",null);
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass,"setHiddenApiExemptions",new Class[]{String[].class});
            if(getRuntime != null) sVmRuntime = getRuntime.invoke(null);
            if(setHiddenApiExemptions != null) setHiddenApiExemptions.invoke(sVmRuntime,new Object[]{new String[]{"L"}});
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public interface IFileDataTypeByte{
        void callbackFileData(boolean readResults, byte[] fileDate);
    }
    /*
    * data:需要添加的信息
    * prefix：信息前缀码
    * 功能：判断是否需要再添加的信息前添0
    * */
    public static String isGetZero(String data,String prefix){

        String information ="";

        if (data.length()==3)
        {
            information = prefix+data;
        }
        else if (data.length()==2){
            information = prefix + "0"+data;
        }
        else {
            information = "信息输入格式有误，请检查后重新输入";
        }
        return information;
    }

    /*
    * 将数据写入到手机内部内存
    * */
    public static void IO_input_data(String dataString,String strFilePath,String filenameString)
    {
        try {

            //判断路径是否存在，不存在创建，这儿也是个坑，若没有这段代码，很可能会写入失败,安卓11开始更新了
            File cacheFile = new File(strFilePath);
            // 上级文件不存在，进行文件夹的创建
            if (!cacheFile.exists()) { // 先根据路径创建文件夹
                cacheFile.mkdirs();
            }
            LocalDate today = null;
            DateTimeFormatter formatter = null;
            String currentString = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                today = LocalDate.now();
            }
            String filename =today.toString() + filenameString;

            boolean createResult = false;
            File file = new File(strFilePath, filename);//在创建文件
            try {
                if (!file.exists()) {
                    createResult = file.createNewFile();
                    Log.e("TAG", "文件创建成功");
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            Log.i("msg",strFilePath);
            //设置写模式
            file.canWrite();
            //追加写入：
            OutputStream ff = new FileOutputStream(file,true);
            //把传输进来的String写入文件，然后换行
            ff.write(dataString.getBytes());
            ff.write("\n".getBytes());
            ff.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
