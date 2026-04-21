package com.hc.bluetoothlibrary.tootl;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * data: 2021-09-28
 * description: 修正自定义速度
 */
public class VelocityCorrection {

    private static final List<Integer> velocityArray = new ArrayList<>();

    private static boolean isGather = true;//收集数据

    private static int differenceValue = 0;

    private static int headData = 0;//去除一开始发送的2个数据

    /**
     * 获取12个实时速率
     * @param velocity 实时速率
     * @param standardVelocity 标准速率
     */
    public synchronized static boolean getVelocity(int velocity, int standardVelocity){
        //Log.w("AppRunService","收集数据....");
        if (headData<2){//去除开始发送的2个数据
            headData++;
            return false;
        }

        if (isGather){
            if (velocity == 0) return false;
            velocityArray.add(velocity);
        }

        if (isGather && velocityArray.size()>=12){
            isGather = false;
            return handlerData(standardVelocity);
        }
        return false;
    }

    /**
     * 获取差额修正值
     * @param time 当前的延时
     * @return 返回需要修正的时间值
     */
    public static int getDifferenceValue(int standardVelocity, int time) {

        if (time == 0){
            differenceValue = 0;
            return 0;
        }
        float value = (float)differenceValue/standardVelocity;
        int temp = (int) (time + time*value*5);
        if (time>12 && Math.abs(temp - time)<=3) temp = time - 5;
        if (time<=12 && Math.abs(temp - time)<=3) temp = time - 3;
        differenceValue = 0;
        Log.w("AppRunService","修正一次速率,延时为: "+temp+" time is "+time);
        return Math.max(temp,0);
    }

    /**
     * 开始记录数据
     */
    public static void setGather(){
        isGather = true;
        headData = 0;
    }

    /**
     * 处理数据
     * @param standardVelocity 标准速率
     */
    private static boolean handlerData(int standardVelocity) {
        int maxPosition = 0,minPosition = 0,max = velocityArray.get(0),min = velocityArray.get(0);
        int averageVelocity = 0;
        for (int i=0;i<velocityArray.size();i++){
            if (velocityArray.get(i)>max){ max = velocityArray.get(i); maxPosition = i;}
            if (velocityArray.get(i)<min){min = velocityArray.get(i);minPosition = i;}
        }
        velocityArray.remove(maxPosition);
        velocityArray.remove(minPosition);
        for (Integer integer : velocityArray) {
            averageVelocity += integer;
        }
        averageVelocity = averageVelocity/10;
        if (averageVelocity>standardVelocity*1024-1024){
            Log.w("AppRunService","不需要修正...");
            return false;
        }

        if (averageVelocity < standardVelocity*1024-512){
            differenceValue = -(standardVelocity*1024+512-averageVelocity)/1024
                    -((standardVelocity*1024+512-averageVelocity)%1024)>512?1:0;
            Log.w("AppRunService","提高速率: "+differenceValue);
        }
        return true;
    }

}
