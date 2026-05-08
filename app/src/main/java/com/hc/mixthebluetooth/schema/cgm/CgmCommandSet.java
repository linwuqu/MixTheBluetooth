package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CgmCommandSet — CGM 设备的命令常量集
 *
 * 作用：
 *   把 CGM 设备需要用到的所有命令，封装成一个个静态方法，
 *   返回格式化好的命令字符串。
 *
 * 为什么用静态方法而不是常量 String？
 *   因为有些命令需要动态参数（如 startNow() 需要当前时间）。
 *   用静态方法可以在方法内部拼装动态内容，返回完整的命令字符串。
 *
 * 命令一览：
 *
 *   startNow()
 *     发送"开始测量"命令，同时附带当前系统时间。
 *     设备收到后，用这个时间作为测量起点，开始实时监测。
 *     格式："TIME,yyyy,MM,dd,HH,mm,ss\r\n"
 *     例如："TIME,2026,05,07,14,30,00\r\n"
 *
 *   startWithTime(formattedTime)
 *     指定时间格式的"开始测量"命令。
 *     startNow() 内部调用这个方法，把系统时间格式化后传入。
 *
 *   readCache()
 *     发送"读取所有缓存记录"命令。
 *     设备收到后，开始回传内部存储的历史血糖数据。
 *     格式："ALL\r\n"
 *     设备回传格式：Start Playback → 多行历史数据 → Playback all done
 *
 *   deleteCache()
 *     发送"删除缓存"命令。
 *     设备收到后，删除内部存储的历史血糖数据。
 *     格式："DELETE\r\n"
 *
 *   buildParameters(params)
 *     发送"设置设备参数"命令。
 *     params 是用户在对话框里填的 5 个参数（controlRatio / extractionTime /
 *     highLevelTime / voltage / detectionTime）。
 *     这些参数被拼成一个二进制字符串，设备解析后调整内部配置。
 *
 * 典型调用：
 *   sender.send(CgmCommandSet.startNow())
 *     → "TIME,2026,05,07,14,30,00\r\n"
 *     → MessageSender.send() 编码成字节 → runtime.sendBtData() → 蓝牙发送
 *
 * 为什么命令末尾有 \r\n？
 *   蓝牙设备用文本协议，\r\n 是行结束标记。
 *   设备收到带有 \r\n 的数据后，才认为这条命令完整，开始处理。
 *   如果不发送 \r\n，设备可能一直等待后续数据。
 */
public final class CgmCommandSet {

    private CgmCommandSet() {}

    /**
     * "开始测量"命令。
     * 把当前系统时间格式化后，拼成 TIME 命令。
     * 时间格式：yyyy,MM,dd,HH,mm,ss（如 2026,05,07,14,30,00）
     */
    @NonNull
    public static String startNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy,MM,dd,HH,mm,ss", Locale.getDefault());
        return startWithTime(sdf.format(new Date()));
    }

    /**
     * 指定时间的"开始测量"命令。
     */
    @NonNull
    public static String startWithTime(@NonNull String formattedTime) {
        return "TIME," + formattedTime + "\n\r";
    }

    /** "读取所有缓存记录"命令 */
    @NonNull
    public static String readCache() {
        return "ALL\n\r";
    }

    /** "删除缓存"命令 */
    @NonNull
    public static String deleteCache() {
        return "DELETE\n\r";
    }

    /**
     * "设置设备参数"命令。
     *
     * 5 个参数被拼成一个二进制字符串：
     *   controlRatio（4位，前导0补足） + "0010" + extractionTime
     *   + highLevelTime（前导0补足，"010"开头） + "011" + voltage
     *   + detectionTime（"100"开头）
     *
     * 这是设备协议规定的二进制格式，不是可读文本。
     */
    @NonNull
    public static String buildParameters(@NonNull CgmParameters params) {
        return padLeft(params.controlRatio, "000")
                + "0010" + params.extractionTime
                + padLeft(params.highLevelTime, "010")
                + "011" + params.voltage
                + padLeft(params.detectionTime, "100");
    }

    /**
     * 左侧补零。
     * 如果 value 长度不足 prefix.length()，在前面补 prefix 的字符直到达到长度。
     * 例如 padLeft("5", "000") → "005"，padLeft("25", "000") → "025"
     */
    @NonNull
    static String padLeft(@NonNull String value, @NonNull String prefix) {
        String clean = value.trim();
        if (clean.length() >= prefix.length()) return clean;
        return prefix.substring(0, prefix.length() - clean.length()) + clean;
    }
}
