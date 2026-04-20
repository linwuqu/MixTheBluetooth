package com.hc.bluetoothlibrary.classicBluetooth;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.hc.bluetoothlibrary.AllBluetoothManage;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.bluetoothlibrary.IBluetoothStop;
import com.hc.bluetoothlibrary.tootl.IScanCallback;
import com.hc.bluetoothlibrary.tootl.IDataCallback;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.bluetoothlibrary.tootl.ToolClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import cn.hutool.core.util.ArrayUtil;

public class ClassicBluetoothManage {

    private BluetoothAdapter mBtAdapter;//系统蓝牙适配器
    private BluetoothSocket mBluetoothSocket = null;//经典蓝牙socket
    private OutputStream mOutputBluetooth;//输出流
    private InputStream mInputBluetooth;//输入流

    private final Map<String,BluetoothDevice> mModuleMap = new HashMap<>();//过滤一些重复扫描
    private final List<DeviceModule> mModuleArray = new ArrayList<>();//所有的模块集合
    private IScanCallback mIScanCallback;//回调扫描的模块
    private IDataCallback mIDataCallback;//连接数据回调

    private final Context mContext;

    private boolean mIsScanSign = true;//扫描标志，true:空闲，false: 正在扫描中

    private final Handler mTimeHandler = new Handler();//延时handler


    private final List<byte[]> mSendData = new ArrayList<>();//需要发送的数据集合
    private boolean mIsWork = false;//收发线程的工作标志，true：正在工作  false：停止工作
    private Thread mSendThread,mListenerThread;//发送和监听的线程

    private String mConnectedMac = null;//连接了的蓝牙模块物理地址

    private Timer mTimer;//定时器

    private TimerTask mTimerTask;//时间任务

    private int mSectionNumber = 0;

    private IBluetoothStop mIBluetoothStop;//停止文件发送的返回

    private boolean mIsStopSend = false;//停止文件发送或是清除循环发送的缓存

    private byte[] mCacheBytes = null;//在检查接收数据换行情况下所使用，缓存最后一个换行符后面的所有数据

    public ClassicBluetoothManage(Context context){
        this.mContext = context;
        init();
    }


    //扫描蓝牙
    public void scanBluetooth(IScanCallback iScanCallback){
        if (mIScanCallback == null) mIScanCallback = iScanCallback;
        if (mIsScanSign) {
            log("操作扫描...");
            initBroadcast();
            listClear();
            mBtAdapter.startDiscovery();
            mIsScanSign = false;
            mTimeHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    log("时间到，停止扫描,mIScanCallback: "+(mIScanCallback == null), "e");
                    mBtAdapter.cancelDiscovery();
                    mIsScanSign = true;
                    unBroadcast();
                    if (mIScanCallback != null) mIScanCallback.stopScan();
                }
            }, 10*1000);
        }
    }

    public void stopScan() throws Exception{
        if (!mIsScanSign){
            log("主动停止..");
            mTimeHandler.removeMessages(0);
            mBtAdapter.cancelDiscovery();
            unBroadcast();
            mIsScanSign = true;
        }
    }

    //连接蓝牙
    public void connectBluetooth(String address,IDataCallback iDataCallback){
        this.mIDataCallback = iDataCallback;
        mConnectedMac = address;
        log("开始连接2.0蓝牙，地址是："+address,"w");
        connect(mBtAdapter.getRemoteDevice(address));
    }

    //发送数据
    public void sendData(byte[] data){
        if (mBluetoothSocket == null){
            Toast.makeText(mContext, "请连上蓝牙再发送数据", Toast.LENGTH_SHORT).show();
            return;
        }
        mIsStopSend = false;
        mSendData.add(data);
    }

    //断开蓝牙
    public void disconnectBluetooth(){
        close();
    }

    //获取当前连接的Mac
    public String getMac(){
        return mConnectedMac;
    }

    //获取蓝牙集合
    public List<DeviceModule> getDevicesArray(){
        return mModuleArray;
    }

    //打开蓝牙
    public boolean startBluetooth(){
        if (mBtAdapter == null || !mBtAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((Activity)mContext).startActivityForResult(enableBtIntent, 1);
            setStartBluetoothBroad();
            return false;
        }
        return true;
    }

    /**
     * 停止发送蓝牙
     * @param iBluetoothStop:停止完成的回调
     */
    public void stopSend(IBluetoothStop iBluetoothStop){
        mIBluetoothStop = iBluetoothStop;
        mIsStopSend = true;
        log("停止发送","w");
    }

    /**
     * 设置文件传输速度
     * @param velocity：low,height,super,max
     */
    public void setSendFileVelocity(AllBluetoothManage.SendFileVelocity velocity) {
        switch (velocity){
            case LOW:
                ModuleParameters.setSendFileDelayedTime(1104);//960 B/s      ->    0.94k/s
                break;
            case HEIGHT:
                ModuleParameters.setSendFileDelayedTime(120);// 5120 10240 B/s  -> 7.5 k/s
                break;
            case SUPER:
                ModuleParameters.setSendFileDelayedTime(60);// 15360 20480 B/s  -> 17.5 k/s
                break;
            case MAX:
                ModuleParameters.setSendFileDelayedTime(20);// 46080 B/s    ->     45 k/s
                break;
            case CUSTOM:
                ModuleParameters.setSendFileDelayedTime(0);//全速发送 无法预测速度
                break;
        }
    }

    /**************************下面方法是此类的基本实现*********************************/

    // 查找到设备和搜索完成action监听器
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // 查找到设备action

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                int rssi = 10;

                // 得到蓝牙设备
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) return;

                if (intent.getExtras() != null) rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);

                // 如果是已配对的则略过，已得到显示，其余的在添加到列表中进行显示
                //添加到已配对设备列表
                addModule(device, device.getBondState() == BluetoothDevice.BOND_BONDED,rssi);
                // 搜索完成action
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (mModuleArray.size() == 0) {
                    String noDevices = "没有找到新设备";
                    mModuleArray.add(new DeviceModule(noDevices,null));
                }
                if (mIScanCallback != null) mIScanCallback.stopScan();
                log("搜索完成","e");
            }
        }
    };

    //监听断线广播
    private final BroadcastReceiver mConnectListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action!= null && action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                if (mIDataCallback != null) {
                    log("监听到蓝牙断线","e");
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    mIDataCallback.errorDisconnect(device != null?device.getAddress():mConnectedMac);
                }
            }
        }
    };

    private BroadcastReceiver receiver;
    private void setStartBluetoothBroad(){
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
                    int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                    if (blueState == BluetoothAdapter.STATE_ON){
                        log("注销广播..");
                        mContext.unregisterReceiver(receiver);
                        if (!ToolClass.isOpenGPS(mContext)) startLocation();
                    }
                }
            }
        };
        mContext.registerReceiver(receiver,new IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED));
    }



    //初始化数据
    private void init(){
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    //清除所有扫描到的蓝牙缓存
    private void listClear(){
        mModuleArray.clear();
        mModuleMap.clear();
    }

    //判断与添加模块
    private void addModule(BluetoothDevice device,boolean isBeenConnected,int rssi){
        int size = mModuleMap.size();
        mModuleMap.put(device.getAddress(),device);
        if (mModuleMap.size()>size){
            DeviceModule deviceModule = new DeviceModule(device.getName(),device,isBeenConnected,mContext,rssi);
            mModuleArray.add(deviceModule);
            updateList(deviceModule);
        }else {
            for (DeviceModule module : mModuleArray) {
                if (module.getMac().equals(device.getAddress())){
                    module.setRssi(rssi);
                    updateList(null);
                }
            }
        }
    }

    private void updateList(DeviceModule deviceModule){
        //更新Recycler
        //log("更新Recycler: "+deviceModule.getName());
        if (mIScanCallback != null) mIScanCallback.updateRecycler(deviceModule);
    }



    //建立socket连接 bluetoothDevice：蓝牙的地址
    private void connect(final BluetoothDevice bluetoothDevice){

        try{
            if (!mIsScanSign){
                scanBluetooth(null);//连接蓝牙前，必须将蓝牙扫描停掉
                log("停止扫描蓝牙");
            }
            // 用服务号得到socket
            log("2.0蓝牙的UUID是:00001101-0000-1000-8000-00805F9B34FB","w");
            threadConnect(bluetoothDevice);
        }catch(Exception e){
            e.printStackTrace();
            Writer w = new StringWriter();
            e.printStackTrace(new PrintWriter(w));
            Toast.makeText(mContext, "连接失败！", Toast.LENGTH_SHORT).show();
            log("建立socket失败："+w.toString(),"e");
            if (mIDataCallback != null) mIDataCallback.connectionFail(mConnectedMac,e.toString());
        }
    }

    private void threadConnect(BluetoothDevice bluetoothDevice) throws Exception{

        mBluetoothSocket = bluetoothDevice
                .createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));//经典蓝牙的连接UUID

        new TaskThread(mContext).setWorkCall(new TaskThread.WorkCallBack() {
            @Override
            public void succeed() {
                log("蓝牙socket连接成功..");
                mIsWork = true;
                if (mIDataCallback != null) mIDataCallback.connectionSucceed(mConnectedMac);
                setSendThread();
                setListenerThread();
                mContext.registerReceiver(mConnectListener,new IntentFilter(
                        BluetoothDevice.ACTION_ACL_DISCONNECTED));//注册断线广播
            }

            @Override
            public boolean work() throws Exception {
                log("准备开始建立socket连接...");
                mBluetoothSocket.connect();
                return true;
            }

            @Override
            public void error(Exception e) {
                close();
                Writer w = new StringWriter();
                e.printStackTrace(new PrintWriter(w));
                log("连接失败: "+w.toString(),"e");
                if (mIDataCallback != null) mIDataCallback.connectionFail(mConnectedMac,e.toString());
            }
        });
    }


    //发送数据的线程
    private void setSendThread(){
        mSendThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] bytes = new byte[1024];
                int position = 0;
                while (mIsWork){
                    if (mSendData.size()>0){
                        try {
                            loopSend(position,bytes);
                            if (mIsStopSend && mSendData.size() != 0) mSendData.clear();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        mSendThread.start();
        log("发送线程就绪..");
    }


    //接收线程
    private void setListenerThread(){
        try {
            mInputBluetooth = mBluetoothSocket.getInputStream();
        } catch (IOException e) {
            Toast.makeText(mContext, "设置监听模块数据的socket失败，请重新连接", Toast.LENGTH_SHORT).show();
            Writer w = new StringWriter();
            e.printStackTrace(new PrintWriter(w));
            log("设置监听失败："+w.toString());
            e.printStackTrace();
            return;
        }
        mListenerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int arrayLength = ModuleParameters.getClassicReadBuff();
                byte[] bytes = new byte[arrayLength];
                byte[] dataByte = null;
                int length;
                int temp;
                while (mIsWork){
                    if (bytes.length != ModuleParameters.getClassicReadBuff()){
                        bytes = new byte[ModuleParameters.getClassicReadBuff()];
                    }
                    if (mInputBluetooth != null){
                        try {
                            if (mInputBluetooth.available() != 0){
                                do {
                                    length = mInputBluetooth.read(bytes);
                                    //log("长度: "+length);
                                    accessRate(length);//计算实时速率
                                    temp = lengthArray(dataByte);
                                    if (temp > 200) mIDataCallback.reading(true);
                                    if (arrayLength-temp<length && dataByte!= null) {
                                        if (ModuleParameters.isCheckNewline()) dataByte = checkNewline(dataByte);
                                        dataSubmitted(dataByte, null);
                                        dataByte = null;
                                    }
                                    if (mCacheBytes != null) {
                                        dataByte = addByteArray(dataByte,mCacheBytes,mCacheBytes.length);
                                        mCacheBytes = null;
                                    }
                                    dataByte = addByteArray(dataByte, bytes,length);
                                    clearArray(bytes);
                                    delayed(ModuleParameters.getTime());//短时间内没数据才退出
                                } while (mInputBluetooth.available() != 0);
                                dataSubmitted(dataByte,bytes);
                                dataByte = null;
                                mIDataCallback.reading(false);
                                stopAccessRate();//停止计算速度
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        mListenerThread.start();
        log("接收线程就绪..");
    }

    //分段判断延时
    private void delayed(int ms) throws Exception{
        for (int i = 0; i<ms;i++) {
            Thread.sleep(1);
            if (mInputBluetooth.available() != 0) return;
        }
    }

    /**
     * 处于队列发送中，循环发送
     * @param position 用于防止重复创建，传入0，记录发送的位置
     * @param bytes 用于防止重复创建，无传入意义
     * @throws Exception I/O异常
     */
    @SuppressWarnings("All")
    private synchronized void loopSend(int position,byte[] bytes) throws Exception{
        if (mOutputBluetooth == null) mOutputBluetooth = mBluetoothSocket.getOutputStream();

        //过多的发送动作，可能导致发送延迟，所以将超过5包数据整合一包发送
        dataIntegration();
        byte[] dataByte = mSendData.get(0);


        if (dataByte != null && dataByte.length>1024){
            do {
                System.arraycopy(dataByte, position, bytes, 0, 1024);
                position += 1024;
                mOutputBluetooth.write(bytes);
                //延时，过快有可能导致模块接收出错
                //理论上，不管加不加延时，手机都能准确的发送出数据
                if(ModuleParameters.getSendFileDelayedTime() > 1000){
                    for (int i = 0;i<16;i++){
                        mIDataCallback.readNumber(bytes.length/16);
                        Thread.sleep(ModuleParameters.getSendFileDelayedTime()/16);
                    }
                }else if(ModuleParameters.getSendFileDelayedTime() == 0){
                    mIDataCallback.readNumber(bytes.length);//不加入延时，看看最高速度
                } else {
                    int commonFactor = getMaxCommonFactor(bytes.length,ModuleParameters.getSendFileDelayedTime());
                    for(int i = 0;i<commonFactor;i++) {
                        mIDataCallback.readNumber(bytes.length/commonFactor);
                        Thread.sleep(ModuleParameters.getSendFileDelayedTime()/commonFactor);
                    }
                }
            }while ((position+1024)<=dataByte.length && !mIsStopSend);
            if (mIsStopSend){
                mOutputBluetooth.flush();
                mSendData.remove(0);
                callbackStopSend();
                return;
            }
            byte [] temp = new byte[dataByte.length - position];
            System.arraycopy(dataByte,position,temp,0,dataByte.length - position);
            mOutputBluetooth.write(temp);
            mIDataCallback.readNumber(temp.length);
        }else if (dataByte != null && dataByte.length>0) {
            mOutputBluetooth.write(dataByte);
            mIDataCallback.readNumber(dataByte.length);
        }
        mOutputBluetooth.flush();
        mSendData.remove(0);
        if (ModuleParameters.getLevel() > 0){//设置发送间隔等级，从0到10，
            Thread.sleep(ModuleParameters.getLevel()*10);//最高,多延时100ms
        }
    }

    /**
     * 整合队列数组，当数据队列超过5时，将整合所有数据到一个byte数组中，存于数据队列开头
     */
    private synchronized void dataIntegration() {
        if (mSendData.size() < 5) return;
        int allLength = 0;
        for (byte[] data : mSendData) {
            allLength += data.length;
        }
        byte[] bytes = new byte[allLength];
        int nowLength = 0;
        for (int i =0 ;i<mSendData.size();i++){
            System.arraycopy(mSendData.get(i),0,bytes,nowLength,mSendData.get(i).length);
            nowLength += mSendData.get(i).length;
        }
        mSendData.clear();
        mSendData.add(bytes);
    }

    /**
     * 停止发送的回调
     */
    private void callbackStopSend() {
        mIsStopSend = false;
        if (mIBluetoothStop != null){
            mTimeHandler.post(new Runnable() {
                @Override
                public void run() {
                   mIBluetoothStop.completeStop();
                }
            });
        }
    }

    //开启计算实时速率
    private void accessRate(final int length) {
        if (mTimerTask == null ){
            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    ((Activity)mContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mIDataCallback.readVelocity(mSectionNumber*5);
                            mSectionNumber = 0;
                        }
                    });
                }
            };
            if (mTimer == null) mTimer = new Timer();
            mTimer.schedule(mTimerTask,200,200);//延迟200ms开启循环，每隔200ms循环一次
        }
        mSectionNumber += length;
    }

    //停止实时速率计算
    private void stopAccessRate(){
        if (mTimerTask != null) mTimerTask.cancel();
        mTimerTask = null;
        mSectionNumber = 0;
    }

    private void dataSubmitted(byte[] bytes_1,byte[] bytes_2) throws Exception{
        updateUi(bytes_1.clone());
        if (bytes_2 != null) clearArray(bytes_2);
    }

    private byte[] checkNewline(byte[] dataBytes){
        byte newline = 10;
        int position = ArrayUtil.lastIndexOf(dataBytes,newline);
        if (position == -1) return dataBytes;


        byte[] usableBytes = new byte[position+1];
        mCacheBytes = new byte[dataBytes.length-position-1];

        System.arraycopy(dataBytes,0,usableBytes,0,usableBytes.length);
        System.arraycopy(dataBytes,position+1,mCacheBytes,0,mCacheBytes.length);

        return usableBytes;
    }

    //更新activity的UI
    private void updateUi(final byte[] data){
        if (mIDataCallback != null){
            ((Activity)mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mIDataCallback.readData(data,mConnectedMac);
                }
            });

        }
    }


    //断开连接
    private void close(){
        mIsWork = false;
        mConnectedMac = null;
        closeThread();
        try {
            mContext.unregisterReceiver(mConnectListener);
        }catch (Exception e){
            e.printStackTrace();
        }
        try {
            if (mOutputBluetooth!=null) mOutputBluetooth.close();
            mOutputBluetooth = null;

            if (mInputBluetooth != null) mInputBluetooth.close();
            mInputBluetooth = null;

            if (mBluetoothSocket!=null) mBluetoothSocket.close();
            mBluetoothSocket = null;

            log("成功断开蓝牙");
        } catch (IOException e) {
            log("断开蓝牙失败...","e");
            e.printStackTrace();
            Writer w = new StringWriter();
            e.printStackTrace(new PrintWriter(w));
            log("断开蓝牙失败："+w.toString(),"e");
        }finally {
            try{
                if (mBluetoothSocket != null){
                    mBluetoothSocket.close();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    //关闭收发线程
    private void closeThread(){
        if (mSendThread != null) mSendThread.interrupt();
        mSendThread = null;
        if (mListenerThread != null) mListenerThread.interrupt();
        mListenerThread = null;
        log("关闭线程..");
    }

    //初始化广播
    private void initBroadcast(){
        mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        // 注册查找结束action接收器
        //filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

        mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        log("注册广播接收器..");
    }

    //注销广播
    private void unBroadcast(){
        mContext.unregisterReceiver(mReceiver);
        log("注销广播接收器..");
    }

    private byte[] addByteArray(byte[] byte_1, byte[] byte_2,int length){
        int buffLength = 0;
        if (byte_1 != null) buffLength = byte_1.length;
        byte[] byte_3 = new byte[buffLength+length];

        if (byte_1 != null) System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);

        System.arraycopy(byte_2, 0, byte_3, buffLength, length);
        return byte_3;
    }

    private void clearArray(byte[] as){
        Arrays.fill(as, (byte) 0);
    }

    private int lengthArray(byte[] as){
        if (as == null) return 0;
        return as.length;
    }

    /**
     * 获取最大公约数（公因数）
     * @param num1 数值一
     * @param num2 数值二
     * @return 返回数值一跟二的最大公约数（公因数）
     */
    private int getMaxCommonFactor(int num1,int num2){
        if (num1 == 0 || num2 == 0) return 0;
        int temp;
        do {
            temp = num1 % num2;
            num1 = num2;
            num2 = temp;
        }while (num2 != 0);
        return num1;
    }

    private void startLocation(){
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext,AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        builder.setTitle("提示")
                .setMessage("请前往打开手机的位置权限!")
                .setCancelable(false)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        ((Activity)mContext).startActivityForResult(intent, 10);
                    }
                }).show();
    }

    private void log(String str){
        Log.d("AppRunClassicManage",str);
        if (mIDataCallback != null){
            mIDataCallback.readLog(getClass().getSimpleName(),str,"d");
        }
    }
    private void log(String str,String lv){
        if (lv.equals("e")) {
            Log.e("AppRunClassicManage", str);
        }else {
            Log.w("AppRunClassicManage", str);
        }
        if (mIDataCallback != null){
            mIDataCallback.readLog(getClass().getSimpleName(),str,lv);
        }
    }
}
