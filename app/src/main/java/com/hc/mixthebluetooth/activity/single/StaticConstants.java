package com.hc.mixthebluetooth.activity.single;

/**
 * description: 存放静态值
 */
public class StaticConstants {

    /***********   CommunicationActivity   **********/

    //FragmentThree隐藏状态
    public static final String FRAGMENT_THREE_HIDE = "FRAGMENT_THREE_HIDE";
    public static final String FRAGMENT_CUSTOM_HIDE = "FRAGMENT_CUSTOM_HIDE";

    //FragmentThree非隐藏状态
    public static final String FRAGMENT_UNHIDDEN = "FRAGMENT_UNHIDDEN";

    //三个fragment发送数据到Activity
    public static final String DATA_TO_MODULE = "DATA_TO_MODULE";

    //fragmentMessage显示接收速度
    public static final String FRAGMENT_STATE_1 = "FRAGMENT_STATE_1";

    //fragmentMessage隐藏接收速度
    public static final String FRAGMENT_STATE_2 = "FRAGMENT_STATE_2";

    //activity发往fragment数据，包括连接成功模块信息
    public static final String FRAGMENT_STATE_DATA = "FRAGMENT_STATE_DATA";

    //从activity上已发送的数据
    public static final String FRAGMENT_STATE_NUMBER = "FRAGMENT_STATE_NUMBER";

    //将连接状态发给FragmentThree
    public static final String FRAGMENT_STATE_CONNECT_STATE = "FRAGMENT_STATE_CONNECT_STATE";

    //将activity头部传递到自定义按钮的fragment，用于判断是否可以发送数据
    public static final String FRAGMENT_STATE_SEND_SEND_TITLE = "FRAGMENT_STATE_SEND_SEND_TITLE";

    //将日志信息发送到fragmentLog上
    public static final String FRAGMENT_STATE_LOG_MESSAGE = "FRAGMENT_STATE_LOG_MESSAGE";

    //读取实时速度
    public static final String FRAGMENT_STATE_SERVICE_VELOCITY = "FRAGMENT_STATE_SERVICE_VELOCITY";

    //停止循环发送
    public static final String FRAGMENT_STATE_STOP_LOOP_SEND = "FRAGMENT_STATE_STOP_LOOP_SEND";


    /************  FragmentCustom  **************/

    //将FragmentCustom设置的是否新行传递到子Fragment
    public static final String FRAGMENT_CUSTOM_NEWLINE = "FRAGMENT_CUSTOM_NEWLINE";




    private StaticConstants(){}

}
