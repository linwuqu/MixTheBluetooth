package com.hc.bluetoothlibrary;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.hc.bluetoothlibrary.bleBluetooth.BleBluetoothManage;
import com.hc.bluetoothlibrary.classicBluetooth.ClassicBluetoothManage;
import com.hc.bluetoothlibrary.tootl.IDataCallback;
import com.hc.bluetoothlibrary.tootl.IScanCallback;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.bluetoothlibrary.tootl.ToolClass;

import java.util.ArrayList;
import java.util.List;

public class AllBluetoothManage {

    private final Context mContext;
    private final ClassicBluetoothManage mClassicManage;//2.0蓝牙
    private final BleBluetoothManage mBleManage;//ble蓝牙
    private final List<DeviceModule> mClassicBluetoothArray = new ArrayList<>();
    private final List<DeviceModule> mScanAllModuleArray = new ArrayList<>();//备份所有被扫描出来的模块
    private IDataCallback mIDataCallback;
    private final IBluetooth mIBluetooth;

    private enum State{refresh,leisure}//是否处于扫描状态
    private State mState = State.leisure;

    private boolean mUpdateTheLimit = false;//限制频繁更新列表
    private final Handler mTimeHandler = new Handler();//时间控制

    public enum SendFileVelocity{LOW,HEIGHT,SUPER,MAX,CUSTOM}//发送速度的档次，对应波特率9600,115200,230400,460800,与自定义发送值

    public AllBluetoothManage(Context context,IBluetooth iBluetooth){
        this.mContext = context;
        mClassicManage = new ClassicBluetoothManage(context);
        mBleManage = new BleBluetoothManage(context);
        this.mIBluetooth = iBluetooth;
        ModuleParameters.init(context);
        setIDataCallback();
    }

    //综合扫描（扫描完成时会检查是否有BLE模块名字乱码，若有则启动BLE扫描，通过解析BLE的广播包来获取名字）
    public boolean mixScan(){
        if (mState == State.refresh) return false;
        mState = State.refresh;
        mClassicBluetoothArray.clear();
        mScanAllModuleArray.clear();
        mClassicManage.scanBluetooth(new IScanCallback() {
            @Override
            public void stopScan() {
                //扫描结束
                log("classic扫描结束","w");
                //检验是否有乱码
                testMessyCode();
            }

            @Override
            public void updateRecycler(DeviceModule deviceModule) {
                //更新Recycler的数据
                if (deviceModule != null) mClassicBluetoothArray.add(deviceModule);
                callbackActivity(deviceModule,false);
            }
        });
        return true;
    }

    //ble扫描
    public boolean bleScan(){
        if (mState == State.refresh) return false;
        mState = State.refresh;
        mScanAllModuleArray.clear();
        mBleManage.scanBluetooth(new IScanCallback() {
            @Override
            public void stopScan() {
                log("ble扫描结束","w");
                mIBluetooth.updateEnd();
                mState = State.leisure;
            }

            @Override
            public void updateRecycler(DeviceModule deviceModule) {
                callbackActivity(deviceModule,true);
            }
        });
        return true;
    }

    public void stopScan(){
        try{
            mIBluetooth.updateEnd();
            mClassicManage.stopScan();
            mBleManage.stopScan();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            mState = State.leisure;
        }
    }


    //连接蓝牙
    public void connect(final DeviceModule deviceModule){

        //连接前，先停下所有扫描
        stopScan();

        if (deviceModule.isBLE()){
            log("进入ble的连接方式","w");
            if (mBleManage.getMac() == null) {
                mBleManage.connectBluetooth(deviceModule, mIDataCallback);
            }
        }else {
            log("进入2.0的连接方式","w");
            if (mClassicManage.getMac() == null) mClassicManage.connectBluetooth(deviceModule.getMac(),mIDataCallback);
        }
    }

    //断开蓝牙
    public void disconnect(DeviceModule deviceModule){
        if (deviceModule != null) {
            if (deviceModule.isBLE()) {
                log("断开BLE蓝牙", "w");
                mBleManage.disConnectBluetooth();
            } else {
                mClassicManage.disconnectBluetooth();
            }
        }else {
            if (mBleManage != null) mBleManage.disConnectBluetooth();
            if (mClassicManage != null) mClassicManage.disconnectBluetooth();
        }
    }

    //发送数据
    public void sendData(DeviceModule deviceModule,byte[] data){
        if (deviceModule.isBLE()){
            mBleManage.sendData(data);
        }else {
            mClassicManage.sendData(data);
        }
    }

    /**
     * 设置BLE模块的MTU
     * @param deviceModule：设备（模块）
     * @param mtu：指定的MTU
     */
    public void setMTU(DeviceModule deviceModule,int mtu){
        if (deviceModule.isBLE()){
            mBleManage.setMTU(mtu);
        }
    }

    /**
     * 停止发送数据
     * @param deviceModule;设备
     * @param callback：回调
     */
    public void stopSend(DeviceModule deviceModule,IBluetoothStop callback){
        if(deviceModule != null) {
            if (deviceModule.isBLE()) {
                mBleManage.stopSend(callback);
            } else {
                mClassicManage.stopSend(callback);
            }
        }else {
            if (mBleManage != null) mBleManage.stopSend(callback);
            if (mClassicManage != null) mClassicManage.stopSend(callback);
        }
    }

    //查看是否打开蓝牙，如果已开启，则返回true，否则false
    public boolean isStartBluetooth(){
        return mClassicManage.startBluetooth();
    }

    /**
     * 指定发送速度
     * @param deviceModule:模块设备
     * @param v:发送速度等级，从1到4，分别对应波特率9600，115200，230400，460800 与5，和指定参数发送速度单位k/s
     */
    public void setSendFileVelocity(DeviceModule deviceModule,int ...v){

        SendFileVelocity velocity;

        switch (v[0]){
            case 1:
                velocity = SendFileVelocity.LOW;
                break;
            case 2:
                velocity = SendFileVelocity.HEIGHT;
                break;
            case 3:
                velocity = SendFileVelocity.SUPER;
                break;
            case 4:
                velocity = SendFileVelocity.MAX;
                break;
            case 5:
                velocity = SendFileVelocity.CUSTOM;
                break;
                default:
                    velocity = SendFileVelocity.LOW;
                    log("设置文件发送速度失败,没有这个选项:"+v[0],"e");
        }

        if (deviceModule.isBLE()){
            mBleManage.setSendFileVelocity(velocity,v[0]==5?v[1]:0);
        }else {
            mClassicManage.setSendFileVelocity(velocity);
        }

    }

    private synchronized void callbackActivity(DeviceModule deviceModule,boolean cooling){
        if (mIBluetooth != null){

            //当2.0扫描模式时，不需要预防频繁更新
            if ((cooling || mUpdateTheLimit) && deviceModule == null) return;

            //防止频繁更新数据，设置每更新一次，冷却200ms
            if (deviceModule == null) {
                mUpdateTheLimit = true;
                mTimeHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() { mUpdateTheLimit = false;
                    }
                }, 200);
            }

            mIBluetooth.updateList(deviceModule);
            if (deviceModule != null) mScanAllModuleArray.add(deviceModule);//备份所有被扫描并传输到Activity的模块
        }
    }

    //测试是否蓝牙名称有乱码
    private void testMessyCode() {
        final List<DeviceModule> list = getMessyCodeArray();
        mBleManage.scanBluetooth(list, true, new IScanCallback() {
            @Override
            public void stopScan() {
                log("=====解码=====","w");
                for (DeviceModule deviceModule : list) {
                    log("name: "+deviceModule.getName());
                    if (mIBluetooth != null){
                        mIBluetooth.updateMessyCode(deviceModule);
                    }
                }
                if (mIBluetooth != null) mIBluetooth.updateEnd();
                mState = State.leisure;
            }

            @Override
            public void updateRecycler(DeviceModule deviceModule) {

            }
        });
    }

    //所有连接的蓝牙数据都回调于此..
    private void setIDataCallback(){
        mIDataCallback = new IDataCallback() {

            @Override
            public void readData(byte[] data, String mac) {
                if (mIBluetooth != null) mIBluetooth.readData(mac,data);
            }

            @Override
            public void connectionFail(String mac, String cause) {
                log(mac+" 模块连接失败,原因是: "+cause,"e");
                errorDisconnect(mac);
            }

            @Override
            public void connectionSucceed(String mac) {
                log(mac+" 模块连接成功");
                for (DeviceModule deviceModule : mScanAllModuleArray) {
                    if (deviceModule.getMac().equals(mac)){
                        if (mIBluetooth != null) mIBluetooth.connectSucceed(deviceModule);
                    }
                }
            }

            @Override
            public void reading(final boolean isStart) {
                ((Activity)mContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mIBluetooth != null) mIBluetooth.reading(isStart);
                    }
                });
            }

            @Override
            public void errorDisconnect(String mac) {
                DeviceModule deviceModule = getDeviceModule(mac);
                if (mIBluetooth != null) mIBluetooth.errorDisconnect(deviceModule);
                if (deviceModule != null && deviceModule.isBLE()) {
                    mBleManage.disConnectBluetooth();
                }else {
                    mClassicManage.disconnectBluetooth();
                }
            }

            @Override
            public void readNumber(final int number) {
                ((Activity)mContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mIBluetooth != null) mIBluetooth.readNumber(number);
                    }
                });
            }

            @Override
            public void readLog(String className, String data, String lv) {
                if (mIBluetooth != null) {
                    mIBluetooth.readLog(className, data, lv);
                }else {
                    log("mIBluetooth is null", "w");
                }
                //Log.e("AppRun"+getClass().getSimpleName(),"AllBlue接收到信息,传往single");
            }

            @Override
            public void readVelocity(int velocity) {
                if (mIBluetooth != null) mIBluetooth.readVelocity(velocity);
            }

            @Override
            public void callbackMTU(int mtu) {
                if (mIBluetooth != null) mIBluetooth.callbackMTU(mtu);
            }
        };
    }

    private DeviceModule getDeviceModule(String mac){
        for (DeviceModule deviceModule : mClassicBluetoothArray) {
            if (deviceModule.getMac().equals(mac)){
                return deviceModule;
            }
        }
        for (DeviceModule deviceModule : mScanAllModuleArray) {
            if (deviceModule.getMac().equals(mac)){
                return deviceModule;
            }
        }
        return null;
    }

    private List<DeviceModule> getMessyCodeArray(){
        List<DeviceModule> list = new ArrayList<>();
        for (DeviceModule deviceModule : mClassicBluetoothArray) {
            if (deviceModule.isBLE() && ToolClass.pattern(deviceModule.getName())){
                list.add(deviceModule);
            }
        }
        return list;
    }


    private void log(String log){
        Log.d("AppRun"+getClass().getSimpleName(),log);
        if (mIBluetooth != null){
            mIBluetooth.readLog(getClass().getSimpleName(),log,"d");
        }
    }
    private void log(String log,String lv){
        if (lv.equals("e")){
            Log.e("AppRun"+getClass().getSimpleName(),log);
        }else {
            Log.w("AppRun"+getClass().getSimpleName(),log);
        }
        if (mIBluetooth != null){
            mIBluetooth.readLog(getClass().getSimpleName(),log,lv);
        }
    }
}
