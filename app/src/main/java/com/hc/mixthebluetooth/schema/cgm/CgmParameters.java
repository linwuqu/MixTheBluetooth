package com.hc.mixthebluetooth.schema.cgm;

import androidx.annotation.NonNull;

/**
 * CgmParameters — 设备参数的数据容器
 *
 * 作用：
 *   封装用户在 CgmParameterDialog 里填写的 5 个设备参数，
 *   传给 CgmCommandSet.buildParameters() 拼成设备能识别的二进制命令。
 *
 * 5 个参数的含义（来自设备协议文档）：
 *
 *   controlRatio
 *     控制比率（4位数字，前导0补足到4位）
 *     例：用户填 60 → 拼成 "0060"
 *
 *   extractionTime
 *     萃取时间（固定 "0010" 前缀 + 时间值）
 *
 *   highLevelTime
 *     高电平时间（"010" 前缀 + 时间值，前导0补足）
 *
 *   voltage
 *     电压（固定 "011" 前缀 + 电压值）
 *
 *   detectionTime
 *     检测时间（"100" 前缀 + 时间值）
 *
 * 为什么设计成不可变（final 字段，没有 setter）？
 *   参数容器在构造之后就固定不变，确保在多线程环境下不会意外被修改。
 *   如果需要修改，创建新的 CgmParameters 实例。
 *
 * 生命周期：
 *   用户在对话框填参数 → new CgmParameters(...)
 *     → CgmCommandSet.buildParameters(params)
 *     → sender.send(buildParameters(params))
 *     → 命令发送到设备
 */
public final class CgmParameters {

    /** 控制比率（4位字符串） */
    @NonNull
    public final String controlRatio;

    /** 萃取时间 */
    @NonNull
    public final String extractionTime;

    /** 高电平时间 */
    @NonNull
    public final String highLevelTime;

    /** 电压 */
    @NonNull
    public final String voltage;

    /** 检测时间 */
    @NonNull
    public final String detectionTime;

    public CgmParameters(
            @NonNull String controlRatio,
            @NonNull String extractionTime,
            @NonNull String highLevelTime,
            @NonNull String voltage,
            @NonNull String detectionTime
    ) {
        this.controlRatio = controlRatio;
        this.extractionTime = extractionTime;
        this.highLevelTime = highLevelTime;
        this.voltage = voltage;
        this.detectionTime = detectionTime;
    }
}
