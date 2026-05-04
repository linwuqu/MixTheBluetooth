package com.hc.mixthebluetooth.activity.single;

/**
 * Commands sent by FragmentMessageNew through CMD_MSG_NEW_CONTROL.
 */
public final class MessageNewCmd {
    private MessageNewCmd() {
    }

    public static final String START_RECORD = "START_RECORD";
    public static final String STOP_RECORD = "STOP_RECORD";
    public static final String EXPORT = "EXPORT";
}
