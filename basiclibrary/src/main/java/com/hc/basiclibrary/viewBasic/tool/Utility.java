package com.hc.basiclibrary.viewBasic.tool;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Calendar;

public class Utility {

    public static void save(Context context, String inputText){
        FileOutputStream out;
        BufferedWriter writer = null;
        try {
            out = context.openFileOutput("errlog",Context.MODE_APPEND| Context.MODE_PRIVATE);
            writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(inputText);
            out.write("\r\n".getBytes());
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try {
                if (writer != null){
                    writer.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public static void saveNew(Context context, String inputText){
        FileOutputStream out ;
        BufferedWriter writer = null;
        try {
            out = context.openFileOutput("errNewLog",Context.MODE_PRIVATE);
            writer = new BufferedWriter(new OutputStreamWriter(out));
            out.write("\r\n".getBytes());
            writer.write(inputText+"------最新异常,时间："+getTime());
            out.write("\r\n".getBytes());
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try {
                if (writer != null){
                    writer.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public static String load(Context context,String file){
        FileInputStream in;
        BufferedReader reader = null;
        StringBuilder content = new StringBuilder();
        try{
            in = context.openFileInput(file);
            reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null){
                content.append(line);
            }
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            if(reader != null){
                try{
                    reader.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        if(!content.toString().isEmpty()) return  content.toString();
        return "没有记录到异常";
    }

    private static String getTime(){
        //获取系统的 日期
        Calendar calendar=Calendar.getInstance();

        int month = calendar.get(Calendar.MONTH)+1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        String secondStr = second<10?"0"+second:second+"";
        String minuteStr = minute<10?"0"+minute:minute+"";
        String hourStr = hour<10?"0"+hour:hour+"";
        String dayStr = day<10?"0"+day:day+"";
        String monthStr = month<10?"0"+month:month+"";

        return monthStr+"月"+dayStr+"日 "+hourStr+":"+minuteStr+":"+secondStr;
    }

}
