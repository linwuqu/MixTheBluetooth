package com.hc.mixthebluetooth.activity.single;

/**
 * 静态常量集中管理。
 *
 * 信道命名规范：
 *   CH_  前缀 — Activity → Fragment 方向
 *   CMD_ 前缀 — Fragment → Activity 方向（指令）
 *   EV_  前缀 — Activity ↔ Fragment 双向事件
 *
 * 信道分类：
 *   CH_BT_*        — Activity → Fragment 的蓝牙数据信道
 *   CH_REC_*       — Activity → Fragment 的录制/导出信道
 *   CH_SET_*       — Activity → Fragment 的设置状态信道
 *   CH_FRAGMENT_*  — Fragment → Activity 的 Fragment 事件信道
 *   CH_CTRL_*      — Fragment → Activity 的控制指令信道
 *   CH_SUB_*       — Fragment → Activity 的订阅类指令信道
 */
public final class StaticConstants {

    private StaticConstants() {}

    // ═══════════════════════════════════════════════════════════════════════════
    //  方向：Activity → Fragment
    // ═══════════════════════════════════════════════════════════════════════════

    // ─── 蓝牙数据信道（替代原 FRAGMENT_STATE_DATA）──────────────────────────

    /**
     * 蓝牙数据推送（类型安全包装器 BTPackage）。
     * payload: BTPackage（BTData | Connected | Disconnected | Velocity | Log）
     */
    public static final String CH_BT_DATA = "CH_BT_DATA";

    // ─── 录制 / 导出信道 ──────────────────────────────────────────────────

    /**
     * 录制状态变更推送
     * payload: Boolean
     */
    public static final String CH_REC_STATE = "CH_REC_STATE";

    /**
     * 导出结果推送
     * payload: String (absolute path or error message)
     */
    public static final String CH_REC_EXPORT_RESULT = "CH_REC_EXPORT_RESULT";

    /**
     * 单条录制样本推送（JSON Line）
     * payload: String
     */
    public static final String CH_REC_SAMPLE = "CH_REC_SAMPLE";

    // ─── 设置 / 状态信道 ──────────────────────────────────────────────────

    /**
     * 连接状态推送（给 FragmentThree）
     * payload: String ("已连接" | "连接中" | "断线了")
     */
    public static final String CH_SET_CONNECT_STATE = "CH_SET_CONNECT_STATE";

    /**
     * Fragment 隐藏状态变更
     * payload: null
     */
    public static final String CH_FRAGMENT_HIDE   = "CH_FRAGMENT_HIDE";
    public static final String CH_FRAGMENT_UNHIDE = "CH_FRAGMENT_UNHIDE";

    /**
     * 发送 Title 引用推送（给自定义按钮 Fragment 判断连接状态）
     * payload: DefaultNavigationBar
     */
    public static final String CH_SET_NAV_TITLE = "CH_SET_NAV_TITLE";

    /**
     * FragmentMessage 显示速度（接收速度）
     * payload: Boolean (true=显示, false=隐藏)
     */
    public static final String CH_SET_SPEED_VISIBLE = "CH_SET_SPEED_VISIBLE";

    /**
     * 实时速度推送
     * payload: Integer (bytes/s)
     */
    public static final String CH_VELOCITY = "CH_VELOCITY";

    /**
     * 已发送字节数推送
     * payload: Integer
     */
    public static final String CH_SENT_BYTES = "CH_SENT_BYTES";

    /**
     * 停止循环发送推送
     * payload: null
     */
    public static final String CH_STOP_LOOP_SEND = "CH_STOP_LOOP_SEND";

    /**
     * 日志消息推送
     * payload: FragmentLogItem
     */
    public static final String CH_LOG_MESSAGE = "CH_LOG_MESSAGE";

    /**
     * FragmentCustom → 子 Fragment 传递换行设置
     * payload: Boolean
     */
    public static final String EV_CUSTOM_NEWLINE = "EV_CUSTOM_NEWLINE";

    // ═══════════════════════════════════════════════════════════════════════════
    //  方向：Fragment → Activity
    // ═══════════════════════════════════════════════════════════════════════════

    // ─── 发送数据到蓝牙设备 ───────────────────────────────────────────────

    /**
     * 发送蓝牙数据（携带 DeviceModule 和 byte[]）
     * payload: FragmentMessageItem（内部含 module + byteData）
     */
    public static final String CMD_SEND_BT_DATA = "CMD_SEND_BT_DATA";

    // ─── MessageNew 录制控制 ──────────────────────────────────────────────

    /**
     * MessageNew 录制控制指令
     * payload: String — 见 MessageNewCmd 枚举
     */
    public static final String CMD_MSG_NEW_CONTROL = "CMD_MSG_NEW_CONTROL";

    // ─── 通用 Fragment 事件 ─────────────────────────────────────────────

    /**
     * Fragment 销毁通知（用于 Activity 清理资源）
     * payload: String (fragment tag or class name)
     */
    public static final String CMD_FRAGMENT_DESTROY = "CMD_FRAGMENT_DESTROY";

    // ═══════════════════════════════════════════════════════════════════════════
    //  兼容别名 — 保留旧名指向新名，迁移期使用
    // ═══════════════════════════════════════════════════════════════════════════

    /** @deprecated 使用 {@link #CH_BT_DATA} */
    @Deprecated
    public static final String FRAGMENT_STATE_DATA = CH_BT_DATA;

    /** @deprecated 使用 {@link #CH_SET_CONNECT_STATE} */
    @Deprecated
    public static final String FRAGMENT_STATE_CONNECT_STATE = CH_SET_CONNECT_STATE;

    /** @deprecated 使用 {@link #CH_SET_NAV_TITLE} */
    @Deprecated
    public static final String FRAGMENT_STATE_SEND_SEND_TITLE = CH_SET_NAV_TITLE;

    /** @deprecated 使用 {@link #CH_LOG_MESSAGE} */
    @Deprecated
    public static final String FRAGMENT_STATE_LOG_MESSAGE = CH_LOG_MESSAGE;

    /** @deprecated 使用 {@link #CH_VELOCITY} */
    @Deprecated
    public static final String FRAGMENT_STATE_SERVICE_VELOCITY = CH_VELOCITY;

    /** @deprecated 使用 {@link #CH_STOP_LOOP_SEND} */
    @Deprecated
    public static final String FRAGMENT_STATE_STOP_LOOP_SEND = CH_STOP_LOOP_SEND;

    /** @deprecated 使用 {@link #CH_SET_SPEED_VISIBLE} */
    @Deprecated
    public static final String FRAGMENT_STATE_1 = CH_SET_SPEED_VISIBLE;

    /** @deprecated 使用 {@link #CH_SET_SPEED_VISIBLE} */
    @Deprecated
    public static final String FRAGMENT_STATE_2 = CH_SET_SPEED_VISIBLE;

    /** @deprecated 使用 {@link #CH_SENT_BYTES} */
    @Deprecated
    public static final String FRAGMENT_STATE_NUMBER = CH_SENT_BYTES;

    /** @deprecated 使用 {@link #CH_FRAGMENT_HIDE} */
    @Deprecated
    public static final String FRAGMENT_THREE_HIDE = CH_FRAGMENT_HIDE;

    /** @deprecated 使用 {@link #CH_FRAGMENT_UNHIDE} */
    @Deprecated
    public static final String FRAGMENT_UNHIDDEN = CH_FRAGMENT_UNHIDE;

    /** @deprecated 使用 {@link #EV_CUSTOM_NEWLINE} */
    @Deprecated
    public static final String FRAGMENT_CUSTOM_NEWLINE = EV_CUSTOM_NEWLINE;

    // ─── MessageNew 兼容 ─────────────────────────────────────────────────

    /** @deprecated 使用 {@link #CMD_MSG_NEW_CONTROL} */
    @Deprecated
    public static final String MESSAGE_NEW_CONTROL = CMD_MSG_NEW_CONTROL;

    /** @deprecated 使用 {@link #CH_REC_SAMPLE} */
    @Deprecated
    public static final String MESSAGE_NEW_SAMPLE_JSONL = CH_REC_SAMPLE;

    /** @deprecated 使用 {@link #CH_REC_STATE} */
    @Deprecated
    public static final String MESSAGE_NEW_RECORD_STATE = CH_REC_STATE;

    /** @deprecated 使用 {@link #CH_REC_EXPORT_RESULT} */
    @Deprecated
    public static final String MESSAGE_NEW_EXPORT_RESULT = CH_REC_EXPORT_RESULT;

    /** @deprecated 使用 {@link #CMD_MSG_NEW_CONTROL} */
    @Deprecated
    public static final String MESSAGE_NEW_CMD_START_RECORD = MessageNewCmd.START_RECORD;

    /** @deprecated 使用 {@link #CMD_MSG_NEW_CONTROL} */
    @Deprecated
    public static final String MESSAGE_NEW_CMD_STOP_RECORD = MessageNewCmd.STOP_RECORD;

    /** @deprecated 使用 {@link #CMD_MSG_NEW_CONTROL} */
    @Deprecated
    public static final String MESSAGE_NEW_CMD_EXPORT = MessageNewCmd.EXPORT;

    /** @deprecated 使用 {@link #CMD_SEND_BT_DATA} */
    @Deprecated
    public static final String DATA_TO_MODULE = CMD_SEND_BT_DATA;

    // ─── FragmentCustom 兼容 ─────────────────────────────────────────────

    /** @deprecated 使用 {@link #CH_FRAGMENT_HIDE} */
    @Deprecated
    public static final String FRAGMENT_CUSTOM_HIDE = CH_FRAGMENT_HIDE;
}
