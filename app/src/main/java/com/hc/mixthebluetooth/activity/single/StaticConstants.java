package com.hc.mixthebluetooth.activity.single;

/**
 * Central event-channel names used by Activity, Fragment, and services.
 *
 * Naming rules:
 * CH_  means Activity -> Fragment state/data push.
 * CMD_ means Fragment -> Activity command.
 * EV_  means event style communication; use it when the direction is not
 *      a stable UI state push.
 *
 * The deprecated constants at the bottom keep the old project working while
 * each Fragment is migrated one by one.
 */
public final class StaticConstants {

    private StaticConstants() {
    }

    // Unified Message: Fragment/Controller -> Activity.
    public static final String CMD_BT_POST = "CMD_BT_POST";

    // Unified Message: Activity -> Fragment/Controller.
    public static final String CH_BT_EVENT = "CH_BT_EVENT";

    // Legacy Activity -> Fragment channels. New UnifiedMessage code should use CH_BT_EVENT.
    public static final String CH_BT_DATA = "CH_BT_DATA";

    // Activity -> Fragment: recording state and export result.
    public static final String CH_REC_STATE = "CH_REC_STATE";
    public static final String CH_REC_EXPORT_RESULT = "CH_REC_EXPORT_RESULT";

    // Fragment -> Activity: one recording sample as a JSON line.
    public static final String EV_REC_SAMPLE = "EV_REC_SAMPLE";

    // Activity -> Fragment: general UI and connection state.
    public static final String CH_SET_CONNECT_STATE = "CH_SET_CONNECT_STATE";
    public static final String CH_SET_NAV_TITLE = "CH_SET_NAV_TITLE";
    public static final String CH_SET_SPEED_VISIBLE = "CH_SET_SPEED_VISIBLE";
    public static final String CH_VELOCITY = "CH_VELOCITY";
    public static final String CH_SENT_BYTES = "CH_SENT_BYTES";
    public static final String CH_STOP_LOOP_SEND = "CH_STOP_LOOP_SEND";
    public static final String CH_LOG_MESSAGE = "CH_LOG_MESSAGE";
    public static final String CH_FRAGMENT_HIDE = "CH_FRAGMENT_HIDE";
    public static final String CH_FRAGMENT_UNHIDE = "CH_FRAGMENT_UNHIDE";

    // Cross-fragment event currently used by FragmentCustom and its children.
    public static final String EV_CUSTOM_NEWLINE = "EV_CUSTOM_NEWLINE";

    // Legacy Fragment -> Activity command. New UnifiedMessage code should use CMD_BT_POST.
    public static final String CMD_SEND_BT_DATA = "CMD_SEND_BT_DATA";
    public static final String CMD_MSG_NEW_CONTROL = "CMD_MSG_NEW_CONTROL";
    public static final String CMD_MSG_NEW_START_RECORD = "CMD_MSG_NEW_START_RECORD";
    public static final String CMD_MSG_NEW_STOP_RECORD = "CMD_MSG_NEW_STOP_RECORD";
    public static final String CMD_MSG_NEW_EXPORT = "CMD_MSG_NEW_EXPORT";
    public static final String CMD_FRAGMENT_DESTROY = "CMD_FRAGMENT_DESTROY";

    // ---------------------------------------------------------------------
    // Compatibility aliases. Old code may keep using these during migration.
    // ---------------------------------------------------------------------

    /** @deprecated Use {@link #CH_BT_DATA}. */
    @Deprecated
    public static final String FRAGMENT_STATE_DATA = CH_BT_DATA;

    /** @deprecated Use {@link #CMD_SEND_BT_DATA}. */
    @Deprecated
    public static final String DATA_TO_MODULE = CMD_SEND_BT_DATA;

    /** @deprecated Use {@link #CH_SET_CONNECT_STATE}. */
    @Deprecated
    public static final String FRAGMENT_STATE_CONNECT_STATE = CH_SET_CONNECT_STATE;

    /** @deprecated Use {@link #CH_SET_NAV_TITLE}. */
    @Deprecated
    public static final String FRAGMENT_STATE_SEND_SEND_TITLE = CH_SET_NAV_TITLE;

    /** @deprecated Use {@link #CH_LOG_MESSAGE}. */
    @Deprecated
    public static final String FRAGMENT_STATE_LOG_MESSAGE = CH_LOG_MESSAGE;

    /** @deprecated Use {@link #CH_VELOCITY}. */
    @Deprecated
    public static final String FRAGMENT_STATE_SERVICE_VELOCITY = CH_VELOCITY;

    /** @deprecated Use {@link #CH_STOP_LOOP_SEND}. */
    @Deprecated
    public static final String FRAGMENT_STATE_STOP_LOOP_SEND = CH_STOP_LOOP_SEND;

    /**
     * @deprecated Use {@link #CH_SET_SPEED_VISIBLE} with Boolean.TRUE.
     * Kept as a separate string so old subscribers that still listen to
     * FRAGMENT_STATE_1 continue receiving events until they are migrated.
     */
    @Deprecated
    public static final String FRAGMENT_STATE_1 = "FRAGMENT_STATE_1";

    /**
     * @deprecated Use {@link #CH_SET_SPEED_VISIBLE} with Boolean.FALSE.
     * Kept as a separate string so old subscribers that still listen to
     * FRAGMENT_STATE_2 continue receiving events until they are migrated.
     */
    @Deprecated
    public static final String FRAGMENT_STATE_2 = "FRAGMENT_STATE_2";

    /** @deprecated Use {@link #CH_SENT_BYTES}. */
    @Deprecated
    public static final String FRAGMENT_STATE_NUMBER = CH_SENT_BYTES;

    /** @deprecated Use {@link #CH_FRAGMENT_HIDE}. */
    @Deprecated
    public static final String FRAGMENT_THREE_HIDE = CH_FRAGMENT_HIDE;

    /** @deprecated Use {@link #CH_FRAGMENT_UNHIDE}. */
    @Deprecated
    public static final String FRAGMENT_UNHIDDEN = CH_FRAGMENT_UNHIDE;

    /** @deprecated Use {@link #EV_CUSTOM_NEWLINE}. */
    @Deprecated
    public static final String FRAGMENT_CUSTOM_NEWLINE = EV_CUSTOM_NEWLINE;

    /** @deprecated Use {@link #CH_FRAGMENT_HIDE}. */
    @Deprecated
    public static final String FRAGMENT_CUSTOM_HIDE = CH_FRAGMENT_HIDE;

    /** @deprecated Use {@link #CMD_MSG_NEW_CONTROL}. */
    @Deprecated
    public static final String MESSAGE_NEW_CONTROL = CMD_MSG_NEW_CONTROL;

    /** @deprecated Use {@link #CMD_MSG_NEW_START_RECORD}. */
    @Deprecated
    public static final String MESSAGE_NEW_CMD_START_RECORD = CMD_MSG_NEW_START_RECORD;

    /** @deprecated Use {@link #CMD_MSG_NEW_STOP_RECORD}. */
    @Deprecated
    public static final String MESSAGE_NEW_CMD_STOP_RECORD = CMD_MSG_NEW_STOP_RECORD;

    /** @deprecated Use {@link #CMD_MSG_NEW_EXPORT}. */
    @Deprecated
    public static final String MESSAGE_NEW_CMD_EXPORT = CMD_MSG_NEW_EXPORT;

    /** @deprecated Use {@link #CH_REC_STATE}. */
    @Deprecated
    public static final String MESSAGE_NEW_RECORD_STATE = CH_REC_STATE;

    /** @deprecated Use {@link #CH_REC_EXPORT_RESULT}. */
    @Deprecated
    public static final String MESSAGE_NEW_EXPORT_RESULT = CH_REC_EXPORT_RESULT;

    /** @deprecated Use {@link #EV_REC_SAMPLE}. */
    @Deprecated
    public static final String MESSAGE_NEW_SAMPLE_JSONL = EV_REC_SAMPLE;
}
