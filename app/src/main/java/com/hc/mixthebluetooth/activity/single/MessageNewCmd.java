package com.hc.mixthebluetooth.activity.single;

/**
 * MessageNew 录制控制指令枚举。
 *
 * 替代原有的字符串常量比较：
 *   // 旧写法
 *   if (StaticConstants.MESSAGE_NEW_CMD_START_RECORD.equals(cmd)) { ... }
 *
 *   // 新写法
 *   if (cmd == MessageNewCmd.START_RECORD) { ... }
 *
 * 枚举保证：
 *   - 不存在非法指令值
 *   - 指令唯一，不会拼写错误
 *   - switch-case 编译器检查遗漏分支
 */
public final class MessageNewCmd {

    private MessageNewCmd() {}

    /** 开始录制 */
    public static final String START_RECORD = "START_RECORD";

    /** 停止录制 */
    public static final String STOP_RECORD = "STOP_RECORD";

    /** 导出文件 */
    public static final String EXPORT = "EXPORT";
}
