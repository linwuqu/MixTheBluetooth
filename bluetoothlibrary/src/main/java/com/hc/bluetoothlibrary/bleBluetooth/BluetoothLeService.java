package com.hc.bluetoothlibrary.bleBluetooth;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.hc.bluetoothlibrary.AllBluetoothManage;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.bluetoothlibrary.IBluetoothStop;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.bluetoothlibrary.tootl.VelocityCorrection;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/*
* 蓝牙服务，通过调用类DownloadBinder里的方法，实现控制服务
* 0000ffe1-0000-1000-8000-00805f9b34fb 这个UUID是汇承特有的UUID
* 00002902-0000-1000-8000-00805f9b34fb 这个是设置监听模块数据的UUID
* */
public class BluetoothLeService extends Service {
    //蓝牙的特征值，发送
    private final static String SERVICE_EIGENVALUE_SEND = "0000ffe1-0000-1000-8000-00805f9b34fb";
    //蓝牙的特征值，接收
    private final static String SERVICE_EIGENVALUE_READ = "00002902-0000-1000-8000-00805f9b34fb";

    private Handler mHandler;
    private final DownloadBinder mBinder = new DownloadBinder();

    private BluetoothGatt mBluetoothGatt;

    private final Handler mTimeHandler = new Handler();

    private BluetoothGattCharacteristic mNeedCharacteristic;

    private boolean sendDataSign = true;//发送的标志位

    private DeviceModule mDeiceModule;

    private Timer mTimer;//定时器

    private TimerTask mTimerTask;//时间任务

    private int mSectionNumber = 0;

    private boolean mIsStartHeightPriority = false;

    private IBluetoothStop mIBluetoothStop;//停止文件发送的返回

    private boolean mIsStopSend = false;//停止文件发送或是清除循环发送的缓存

    private int mMTU = 20;//模块的MTU;

    private final List<byte[]> mDataBuff = new ArrayList<>();//looper

    private int mSendDataLength = 0;//记录单次发送数据的长度，用于与MTU对比，若小于MTU则发送完成回调长度此长度。

    private HandlerThread mHandlerThread;

    private Handler mChildThreadHandler;//运行在子线程的Handler

    private AllBluetoothManage.SendFileVelocity mSendFileVelocity = AllBluetoothManage.SendFileVelocity.LOW;

    //控制服务的类
    class DownloadBinder extends Binder {

        void setHandler(Handler handler){
            if (mHandler == null) mHandler = handler;
            log("服务创建成功");
        }

        BluetoothDevice getDevice(){
            if (mBluetoothGatt != null) return mBluetoothGatt.getDevice();
            return null;
        }

        void connect(Context context, DeviceModule device){
            log("连接..");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mBluetoothGatt = device.getDevice().connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_2M);
            }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mBluetoothGatt = device.getDevice().connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }else {
                mBluetoothGatt = device.getDevice().connectGatt(context, false, gattCallback);
            }
            mDeiceModule = device;

            initThread();
        }

        void setMTU(int mtu){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (mtu < 23) {
                    log("设置MTU: " + mBluetoothGatt.requestMtu(23), "w");
                }else {
                    log("设置MTU: " + mBluetoothGatt.requestMtu(mtu), "w");
                }
            }else {
                sendHandler(BleBluetoothManage.SERVICE_READ_MTU,-2);
            }
        }

        synchronized void sendMultiple(byte[] data){
            if (mNeedCharacteristic == null){
                log("错误，没有拿到写入特征","e");
                return;
            }
            //发送前记录发送缓存区是否为空
            //当发送区缓存小于2，可认为发送区即将被清空，所以往发送缓存区加入数据后，需要提交发送请求信号
            boolean isSendBuffNull = mDataBuff.size() < 2;
            mDataBuff.addAll(getSendDataByte(data));
            log("发送一次,"+isSendBuffNull,"w");
            if(isSendBuffNull) mChildThreadHandler.sendEmptyMessage(0);//加入判断，在还有缓存时，不通知发送
        }

        //接收完成通知
        void receiveComplete(){
            //log("定时器停止..","w");
            if (mTimerTask != null) mTimerTask.cancel();
            mTimerTask = null;
            mSectionNumber = 0;
        }

        void stopSend(IBluetoothStop iBluetoothStop){
            mIBluetoothStop = iBluetoothStop;
            if(mDataBuff.size() >0) {
                mIsStopSend = true;
                log("执行停止发送...");
            }
        }

        void disconnect(){
            //断开连接
            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
        }

        /**
         * 设置发送速率 文件发送，需要将模块MTU设置最大，需要动态计算实时速率，即延时大小
         * @param velocity:low,height,super,max
         * @param speed 非自定义速率speed为0，自定义速率时，speed为指定速度
         *              速率 [1k/s , 16k/s]
         */
        void setSendFileVelocity(AllBluetoothManage.SendFileVelocity velocity, final int speed) {
            mSendFileVelocity = velocity;
            setMTU(512);//直接将MTU的值拉到最大
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    setSendVelocity(speed);
                }
            },500);
        }
    }

    //蓝牙连接完的所有回调
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            if(newState == 133){
                log("出现133问题，需要扫描重连","e");
                sendHandler(BleBluetoothManage.SERVICE_CONNECT_FAIL,"出现133错误");
            }
            if (newState == BluetoothGatt.STATE_CONNECTED){
                log("连接成功，开始获取服务UUID");

                if (detectionGatt()) return;//检测到gatt为空，退出

                mTimeHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (detectionGatt()) return;//检测到gatt为空，退出
                        mBluetoothGatt.discoverServices();//扫描服务
                    }
                },1000);//坑：设置延迟时间过短，很可能发现不了服务
                mTimeHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (detectionGatt()) return;//检测到gatt为空，退出
                        log("获取服务UUID超时，断开重连","e");
                        sendHandler(BleBluetoothManage.SERVICE_ERROR_DISCONNECT,null);
                    }
                },5000);


            }else if (newState == BluetoothGatt.STATE_DISCONNECTED){
                log("蓝牙断开","e");
                sendHandler(BleBluetoothManage.SERVICE_ERROR_DISCONNECT,null);
            }

        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS){
                //所有app发送给模块数据成功的回调都在这里
                //log("接收到数据回调...");
                //log("正确的UUID: "+characteristic.getUuid());//0000ffe1-0000-1000-8000-00805f9b34fb
                if (characteristic.getValue().length == 0){
                    sendDataSign = true;//等到发送数据回调成功才可以继续发送
                    return;
                }

                int length = mSendDataLength;
                if (length == mMTU) length =  characteristic.getValue().length;
                sendHandler(BleBluetoothManage.SERVICE_SEND_DATA_NUMBER, String.valueOf(length));
                sendDataSign = true;//等到发送数据回调成功才可以继续发送
            }else log("status is "+status,"e");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor,
                                      int status) {
            if (status == BluetoothGatt.GATT_SUCCESS){
                //mBluetoothGatt.writeDescriptor(descriptor);
                //来到这里，才算真正的建立连接
                log("设置监听成功,可以发送数据了...");
                log("服务中连接成功，给与的返回名称是->"+gatt.getDevice().getName());
                log("服务中连接成功，给与的返回地址是->"+gatt.getDevice().getAddress());
                sendHandler(BleBluetoothManage.SERVICE_CONNECT_SUCCEED,null);
            }

        }


        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            //扫描到蓝牙服务的回调
            // （思想：获取该模块的所有服务，然后再轮询服务下面的所有特征的UUID，再与汇承的UUID比较
            // 　　　　找到汇承的UUID后，建立监听模块数据的回调才算完成真正的连接。）

            if (detectionGatt()) return;//检测GATT是否又成空

            mTimeHandler.removeMessages(0);
            List<BluetoothGattService> servicesLists = mBluetoothGatt.getServices();//获取模块的所有服务
            log("扫描到服务的个数:"+servicesLists.size());
            int i = 0;

            for (final BluetoothGattService servicesList : servicesLists) {
                ++i;
                log("-----------打印服务----------");
                log(i+"号服务的uuid: "+servicesList.getUuid().toString());
                List<BluetoothGattCharacteristic> gattCharacteristics = servicesList
                        .getCharacteristics();//获取单个服务下的所有特征

                int j=0;
                log("----------打印特征-----------");
                for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    ++j;
                    if (gattCharacteristic.getUuid().toString().equals(SERVICE_EIGENVALUE_SEND)){//汇承蓝牙的UUID
                        log(i+"号服务的第"+j+"个特征"+gattCharacteristic.getUuid().toString(),"w");
                        mDeiceModule.setUUID(servicesList.getUuid().toString(),gattCharacteristic.getUuid().toString());//存下特征
                        mNeedCharacteristic = gattCharacteristic;
                        log("发送特征："+mNeedCharacteristic.getUuid().toString(),"w");
                        mBluetoothGatt.setCharacteristicNotification(mNeedCharacteristic, true);
                        mTimeHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                BluetoothGattDescriptor clientConfig = mNeedCharacteristic
                                        .getDescriptor(UUID.fromString(SERVICE_EIGENVALUE_READ));//这个收取数据的UUID
                                if (clientConfig != null) {
                                    //BluetoothGatt.getService(service)
                                    clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);//设置接收模式
                                    mBluetoothGatt.writeDescriptor(clientConfig);//必须是设置这个才能监听模块数据
                                }else {
                                    log("备用方法测试","w");
                                    BluetoothGattService linkLossService = gatt.getService(servicesList.getUuid());
                                    setNotification(mBluetoothGatt,linkLossService.getCharacteristic(UUID.fromString(SERVICE_EIGENVALUE_READ)),true);
                                }
                            }
                        },200);
                    }else {
                        log(i + "号服务的第" + j + "个特征" + gattCharacteristic.getUuid().toString());
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt,characteristic);
            //模块发送的所有数据都会回调到这里
            //蓝牙发送给app的回调
            sendHandler(characteristic.getValue());//给handler更新UI
            accessRate(characteristic.getValue().length);//获取实时速率
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (gatt.getServices() == null || gatt.getServices().size() == 0 ) return;
            if (status == BluetoothGatt.GATT_SUCCESS){
                mMTU = mtu - 3;
                log("mtu is "+mtu,"e");
                sendHandler(BleBluetoothManage.SERVICE_READ_MTU,mtu);
            }else {
                log("MTU设置失败: "+status);
                sendHandler(BleBluetoothManage.SERVICE_READ_MTU,-1);
            }
        }
    };

    /**
     * 此方法运行在子线程中
     * @throws InterruptedException 线程休眠异常
     */
    @SuppressWarnings("All")
    private synchronized void permanentThreadSendData() throws Exception {
        if (mDataBuff.size() == 0 || mDataBuff.get(0) == null) return;

        int interval = -3;

        mNeedCharacteristic.setValue(mDataBuff.get(0));
        mSendDataLength = mDataBuff.get(0).length;//记录下此次发送包的长度
        sendDataSign = !mBluetoothGatt.writeCharacteristic(mNeedCharacteristic);//蓝牙发送数据，一次顶多20字节

        if (sendDataSign){
            Thread.sleep(400);
            if (!ModuleParameters.isSendFile()) mDataBuff.remove(0);//非发送文件情况下
            return;
        }

        //各种波特率下的额外延时与发送文件时的延时
        if (ModuleParameters.isSendFile()) {//传输文件
            for (;interval<0 && !sendDataSign;interval++) Thread.sleep(3);
            Thread.sleep(3+ModuleParameters.getSendFileDelayedTime());//公共延时，减少发送出错的可能
        }else {//常规通信
            //设置发送间隔等级，从0到10，最高,多延时100ms
            if (ModuleParameters.getLevel() != 0) Thread.sleep(ModuleParameters.getLevel() * 10);
            Thread.sleep(5 + 10 * ModuleParameters.getState());//根据发送状态，调整通用延时大小5-25ms
        }

        //从发送到接收到发送完成的回调，一般小于10ms，所以在发送文件的情况下，一般不会进入循环
        while (!sendDataSign){
            Thread.sleep(5);
            interval++;
            if (interval==4) {
                mNeedCharacteristic.setValue(new byte[0]);//额外发送会导致发包重复，所以发一个空包去提醒
                sendDataSign = !mBluetoothGatt.writeCharacteristic(mNeedCharacteristic);
                log("额外发送一次,"+sendDataSign,"w");
            }
            if (interval==50) {
                mNeedCharacteristic.setValue(new byte[0]);//额外发送会导致发包重复，所以发一个空包去提醒
                sendDataSign = !mBluetoothGatt.writeCharacteristic(mNeedCharacteristic);
                log("额外发送一次,"+sendDataSign,"e");
            }
            if (interval == 100){
                sendDataSign = true;
                log("无法发送，跳过这个包的发送","e");
            }
        }
        if(mDataBuff.size() != 0) mDataBuff.remove(0);
        callbackStopSend();
    }


    /**
     * 停止发送的回调
     */
    private void callbackStopSend() {
        if (mIsStopSend){
            mIsStopSend = false;
            mDataBuff.clear();
            if (mIBluetoothStop != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mIBluetoothStop.completeStop();
                    }
                });
            }
        }
    }

    /**
     * 设置在波特率9600下的发送速度
     * @param speed 发送的数据到模块的速度，单位k/s
     */
    private void setSendVelocity(final int speed) {
        int delayed;
        switch (mSendFileVelocity){
            case LOW://速率在 800 - 1000 b/s
                //在9600下，每秒传输约等于1000字节，即1B/ms,所以每个mtu有多大，就给多少ms
                ModuleParameters.setSendFileDelayedTime(mMTU);
                break;
            case HEIGHT://速率在 7000 - 8000 b/s
                delayed = mMTU/10;
                ModuleParameters.setSendFileDelayedTime(delayed);
                break;
            case SUPER://速率在 12000 - 15000 b/s
                delayed = mMTU/25;
                ModuleParameters.setSendFileDelayedTime(delayed);
                break;
            case MAX://速率在20000 b/s以上，测试04极限速率约为24390 b/s 约为23.8 k/s
                ModuleParameters.setSendFileDelayedTime(0);
                break;
            case CUSTOM://自定义速率
                VelocityCorrection.setGather();
                ModuleParameters.setSendFileDelayedTime(getDelayedTime(speed));
                break;
        }
    }

    /**
     * 获取指定发送速率的时间间隔
     * @param speed 指定的发送速率，单位k/s
     * @return 返回对应的大至时间间隔 单位ms
     */
    private int getDelayedTime(int speed){
        return Math.max((mMTU / speed - 3), 0);
    }

    private boolean detectionGatt(){
        if (mBluetoothGatt ==null){
            log("出现未知错误，服务关闭，GATT is null","e");
            sendHandler(BleBluetoothManage.SERVICE_CONNECT_FAIL,"未知错误");
            stopSelf();
            return true;
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log("开启服务..");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log("服务关闭..");

        //停止子线程，正在执行的任务不会强制停止，正在排队的任务将被丢弃
        if(mHandlerThread != null) mHandlerThread.quitSafely();
    }//GPIO_Init(GPIOC,&GPIO_Initstructure);改为GPIO_Init(GPIOB,&GPIO_Initstructure);

    private void accessRate(final int length) {
        if (mTimerTask == null ){
            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    sendHandler(BleBluetoothManage.SERVICE_READ_VELOCITY,mSectionNumber*5);
                    if ( !mIsStartHeightPriority && mSectionNumber >400) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            if(detectionGatt()) return;//设置传输模式之前的判定
                            log("设置高速传输模式: " + mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH), "w");
                        } else {
                            Toast.makeText(BluetoothLeService.this, "抱歉，Android6.0以下手机不支持蓝牙高速传输，可能出现会丢包现象", Toast.LENGTH_LONG).show();
                        }
                        mIsStartHeightPriority = true;
                    }
                    mSectionNumber = 0;
                }
            };
            if (mTimer == null) mTimer = new Timer();
            mTimer.schedule(mTimerTask,200,200);//延迟100ms开启循环，每隔100ms循环一次
        }
        mSectionNumber += length;
    }

    //将数据分包
    private int[] dataSeparate(int len) {
        int[] lens = new int[2];
        lens[0]=len/mMTU;
        lens[1]=len%mMTU;
        return lens;
    }

    //将String字符串分包为List byte数组
    private List<byte[]> getSendDataByte(byte[] buff){
        List<byte[]> listSendData = new ArrayList<>();
        int[] sendDataLength = dataSeparate(buff.length);
        for(int i=0;i<sendDataLength[0];i++) {
            byte[] dataFor20 = new byte[mMTU];
            System.arraycopy(buff, i * mMTU, dataFor20, 0, mMTU);
            listSendData.add(dataFor20);
        }

        if(sendDataLength[1]>0) {
            byte[] lastData = new byte[sendDataLength[1]];
            System.arraycopy(buff, sendDataLength[0] * mMTU, lastData, 0, sendDataLength[1]);
            listSendData.add(lastData);
        }
        return listSendData;
    }

    //监听到的数据通过handler发送回activity
    private void sendHandler(byte[] data){
        if (mHandler == null){
            log("错误，返回信息的handler为空","e");
            return;
        }

        Message message = mHandler.obtainMessage();
        message.what = BleBluetoothManage.SERVICE_CALLBACK;
        message.obj = data.clone();
        mHandler.sendMessage(message);

    }

    private void sendHandler(int type,Object data){
        if (mHandler != null){
            Message message = mHandler.obtainMessage();
            message.what = type;
            if (data != null) message.obj = data;
            mHandler.sendMessage(message);
        }
    }


    public void setNotification(final BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, boolean enable) {
        if (gatt == null || characteristic == null) {
            log("gatt == null || characteristic == null");
            return;
        }
        boolean success = gatt.setCharacteristicNotification(characteristic, enable);
        Log.e("TAG", "setNotification: " + success);
        if (success) {
            for (final BluetoothGattDescriptor dp : characteristic.getDescriptors()) {
                if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    dp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    log("路线1");
                } else if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    dp.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    log("路线2");
                }else {
                    log("没有走");
                }
                mTimeHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        gatt.writeDescriptor(dp);
                        log("监听的特征是: "+dp.getUuid().toString());
                    }
                },1000);

                /*mTimeHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mBluetoothGatt.writeDescriptor(dp);
                        log("监听的特征是: "+dp.getUuid().toString());
                    }
                },5000);*/
            }
        }
    }

    /**
     * 初始化一个特殊的子线程HandlerThread，此线程拥有Looper
     * 并初始化一个Handler，赋予HandlerThread的Looper，使这个
     * Handler运行在子线程中。
     */
    private void initThread(){
        mHandlerThread = new HandlerThread("HandlerThread");
        mHandlerThread.start();//启动这个子线程
        log("执行initThread...");
        mChildThreadHandler = new Handler(mHandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                try {
                    synchronized (this) {
                        while (mDataBuff.size() != 0 && mBluetoothGatt != null) permanentThreadSendData();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    log("发生错误: "+e.getMessage());
                }
                return false;
            }
        });
    }

    private void sendLog(String data,String lv){
        if (mHandler == null) return;
        String str = BleBluetoothManage.SERVICE_SEPARATOR;
        Message message = mHandler.obtainMessage();
        message.what = BleBluetoothManage.SERVICE_READ_LOG;
        message.obj = getClass().getSimpleName()+str+data+str+lv+str;
        mHandler.sendMessage(message);
    }


    public void log(String str){
        Log.d("AppRunService",str);
        sendLog(str,"d");
    }
    public void log(String str,String e){
        if (e.equals("i")) {
            Log.e("AppRunService", str);
            return;
        }
        if(e.equals("e")) {
            Log.e("AppRunService", str);
        }else {
            Log.w("AppRunService", str);
        }
        sendLog(str,e);
    }

}
