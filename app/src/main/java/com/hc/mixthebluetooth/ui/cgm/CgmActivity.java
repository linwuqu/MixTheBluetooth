package com.hc.mixthebluetooth.ui.cgm;


import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.app.hubert.guide.NewbieGuide;
import com.app.hubert.guide.model.GuidePage;
import com.app.hubert.guide.model.RelativeGuide;
import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.basiclibrary.popupWindow.CommonPopupWindow;
import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.basiclibrary.viewBasic.manage.ViewPagerManage;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.driver.implementation.bluetooth.AndroidBluetoothController;
import com.hc.mixthebluetooth.ui.shared.dialog.SetMtu;
import com.hc.mixthebluetooth.databinding.ActivityCommunicationBinding;

import java.util.List;
import android.util.Log;

public class CgmActivity extends BaseActivity<ActivityCommunicationBinding> {
    private static final String CONNECTED = "已连接";
    private static final String CONNECTING = "连接中";
    private static final String DISCONNECT = "断线了";

    private DefaultNavigationBar mTitle;
    private ViewPagerManage viewPagerManage;

    private AndroidBluetoothController mAndroidBluetoothController;
    private List<DeviceModule> modules;
    private DeviceModule mErrorDisconnect;
    private String connectState = CONNECTING;
    private int mMTUNumber = 23;

    private String mDeviceName;

    private final Handler mTimeHandler = new Handler();

    // ----------------- Lifecycle -----------------
    @Override
    public void initAll() {
        initDependencies();
        readIntentArgs();
        initTitle();
        initBluetoothListener();
        initPages();
        initSubscription();
    }

    @Override
    protected ActivityCommunicationBinding getViewBinding() {
        return ActivityCommunicationBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onDestroy() {
        Log.d("CgmActivity", ">>> onDestroy called");
        super.onDestroy();
        logWarn("关闭CgmActivity...");
        if (mAndroidBluetoothController == null) return;

        DeviceModule module = getCurrentModule();
        mAndroidBluetoothController.setOnReadListener(null);

        if (module != null) {
            mAndroidBluetoothController.disconnect(module);
        }
    }


    @Override
    protected void onPause() {
        Log.d("CgmActivity", ">>> onPause called");
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.d("CgmActivity", ">>> onResume called");
        super.onResume();
    }


    // ----------------- Initialization -----------------
    private void initDependencies() {
        mAndroidBluetoothController = AndroidBluetoothController.getInstance();
    }

    private void readIntentArgs() {
        if (getIntent().hasExtra("device_name")) {
            mDeviceName = getIntent().getStringExtra("device_name");
        }
    }

    private void initTitle() {
        View.OnClickListener listener = v -> {
            if (v.getId() == R.id.right_more) {
                popupWindow(v);
                return;
            }
            handleConnectStateClick();
        };
        mTitle = new DefaultNavigationBar.Builder(this, findViewById(R.id.communication_name)).setLeftText("Biosensors System", 18).setRightText(CONNECTING).setRightClickListener(listener).builer();

        mTitle.updateLoadingState(true);
    }

    private void initBluetoothListener() {
        AndroidBluetoothController.OnReadDataListener dataListener = new AndroidBluetoothController.OnReadDataListener() {
            @Override
            public void readData(String mac, byte[] data) {
                onBluetoothData(data);
            }

            @Override
            public void reading(boolean isStart) {
                onBluetoothReading(isStart);
            }

            @Override
            public void connectSucceed() {
                onBluetoothConnected();
            }

            @Override
            public void errorDisconnect(DeviceModule deviceModule) {
                onBluetoothDisconnected(deviceModule);
            }

            @Override
            public void readNumber(int number) {
                onBluetoothReadNumber(number);
            }

            @Override
            public void readLog(String className, String data, String lv) {
                onBluetoothLog(className, data, lv);
            }

            @Override
            public void readVelocity(int velocity) {
                onBluetoothVelocity(velocity);
            }

            @Override
            public void callbackMTU(int mtu) {
                onBluetoothMtuChanged(mtu);
            }
        };
        mAndroidBluetoothController.setOnReadListener(dataListener);
        syncConnectedModule("initBluetoothListener");
    }

    private void initPages() {
        viewPagerManage = new ViewPagerManage(viewBinding.communicationFragment);
        viewPagerManage.addFragment(new CgmFragment());
        viewBinding.communicationFragment.setAdapter(viewPagerManage.getAdapter());
    }

    private void initSubscription() {
        subscription(StaticConstants.CMD_SEND_BT_DATA, StaticConstants.CMD_BT_POST);
    }


    // ----------------- Fragment Commands -----------------
    @Override
    protected void update(@NonNull String sign, Object data) {
        if (sign.equals(StaticConstants.CMD_SEND_BT_DATA)) {
            onSendBtDataCommand(data);
        } else if (sign.equals(StaticConstants.CMD_BT_POST)) {
            onBtPostCommand(data);
        } else {
            logWarn("Unknown activity command: " + sign);
        }
    }

    private void onSendBtDataCommand(Object data) {
        if (!(data instanceof MessageItem)) {
            logWarn("Ignore send data command, payload is not MessageItem: " + data);
            return;
        }

        MessageItem item = (MessageItem) data;

        if (item.getModule() == null) {
            logWarn("Ignore send data command, module is null");
            return;
        }

        if (item.getByteData() == null) {
            logWarn("Ignore send data command, byteData is null");
            return;
        }

        mAndroidBluetoothController.sendData(item.getModule(), item.getByteData().clone());
    }

    private void onBtPostCommand(Object data) {
        if (!(data instanceof CgmBluetoothEvent.BTPost)) {
            logWarn("Ignore BT post command, payload is not BTPost: " + data);
            return;
        }

        CgmBluetoothEvent.BTPost post = (CgmBluetoothEvent.BTPost) data;
        mAndroidBluetoothController.sendData(post.module, post.bytes.clone());
    }


    // ----------------- Bluetooth Callbacks -----------------
    private void onBluetoothData(byte[] data) {
        DeviceModule module = getCurrentModule();
        if (module == null) {
            logWarn("收到蓝牙数据但当前连接设备为空，忽略本次数据: bytes=" + (data == null ? 0 : data.length));
            return;
        }

        publishBtData(module, data);
    }

    private void onBluetoothReading(boolean isStart) {
        publishSpeedVisible(isStart);
    }

    private void onBluetoothConnected() {
        modules = mAndroidBluetoothController.getConnectedArray();

        DeviceModule module = getCurrentModule();
        if (module == null) return;

        applyConnectedModule(module, "callback");
    }

    private void syncConnectedModule(@NonNull String reason) {
        if (mAndroidBluetoothController == null) return;

        modules = mAndroidBluetoothController.getConnectedArray();
        DeviceModule module = getCurrentModule();
        log("同步蓝牙连接状态[" + reason + "]: count=" + (modules == null ? 0 : modules.size())
                + ", current=" + (module == null ? "null" : module.getName()));

        if (module == null || isConnected()) return;

        applyConnectedModule(module, reason);
    }

    private void applyConnectedModule(@NonNull DeviceModule module, @NonNull String source) {
        publishBtConnected(module);

        setState(CONNECTED);
        mTitle.updateLeftText(module.getName());
        log("连接成功[" + source + "]: " + module.getName());
    }

    private void onBluetoothDisconnected(final DeviceModule deviceModule) {
        if (mErrorDisconnect == null) {
            mErrorDisconnect = deviceModule;

            if (mAndroidBluetoothController != null && deviceModule != null) {
                mTimeHandler.postDelayed(() -> {
                    mAndroidBluetoothController.connect(deviceModule);
                    setState(CONNECTING);
                    publishStopLoopSend();
                }, 2000);
                return;
            }
        }

        setState(DISCONNECT);
        publishBtDisconnected();

        if (deviceModule != null) {
            toastLong("连接 " + deviceModule.getName() + " 失败，点右上角断开状态可尝试重连");
        } else {
            toastLong("连接模块失败，请返回上一页重连");
        }
    }

    private void onBluetoothReadNumber(int number) {
        publishSentBytes(number);
    }

    private void onBluetoothLog(String className, String data, String lv) {
        publishLog(className, data, lv);
    }

    private void onBluetoothVelocity(int velocity) {
        publishVelocity(velocity);
    }

    private void onBluetoothMtuChanged(int mtu) {
        if (mtu == -2) {
            toastShortAlive("你的手机不支持设置 MTU");
            return;
        }

        if (mtu == -1) {
            toastShortAlive("设置 MTU 失败");
            return;
        }

        mMTUNumber = mtu;
        toastShortAlive("MTU 设置为 " + mtu);
    }


    // ----------------- Fragment Events -----------------
    private void publishBtData(DeviceModule module, byte[] data) {
        sendDataToFragment(StaticConstants.CH_BT_DATA, new CgmBluetoothEvent.BTData(module, data));
        sendDataToFragment(StaticConstants.CH_BT_EVENT, new CgmBluetoothEvent.BTData(module, data));
    }

    private void publishBtConnected(DeviceModule module) {
        sendDataToFragment(StaticConstants.CH_BT_DATA, new CgmBluetoothEvent.Connected(module));
        sendDataToFragment(StaticConstants.CH_BT_EVENT, new CgmBluetoothEvent.Connected(module));
    }

    private void publishBtDisconnected() {
        sendDataToFragment(StaticConstants.CH_BT_DATA, CgmBluetoothEvent.Disconnected.INSTANCE);
        sendDataToFragment(StaticConstants.CH_BT_EVENT, CgmBluetoothEvent.Disconnected.INSTANCE);
    }

    private void publishLog(String className, String data, String level) {
        log("BT log[" + level + "] " + className + ": " + data);
    }

    private void publishConnectState(String state) {
        sendDataToFragment(StaticConstants.CH_SET_CONNECT_STATE, state);
        sendDataToFragment(StaticConstants.CH_BT_EVENT, new CgmBluetoothEvent.ConnectState(state));
    }

    private void publishSpeedVisible(boolean visible) {
        sendDataToFragment(StaticConstants.CH_SET_SPEED_VISIBLE, visible);
    }

    private void publishVelocity(int velocity) {
        sendDataToFragment(StaticConstants.CH_VELOCITY, velocity);
    }

    private void publishSentBytes(int number) {
        sendDataToFragment(StaticConstants.CH_SENT_BYTES, number);
        sendDataToFragment(StaticConstants.CH_BT_EVENT, new CgmBluetoothEvent.SentBytes(number));
    }

    private void publishStopLoopSend() {
        sendDataToFragment(StaticConstants.CH_STOP_LOOP_SEND, null);
        sendDataToFragment(StaticConstants.CH_BT_EVENT, CgmBluetoothEvent.StopLoopSend.INSTANCE);
    }

    private void publishFragmentHide() {
        sendDataToFragment(StaticConstants.CH_FRAGMENT_HIDE, null);
    }

    private void publishFragmentUnhide() {
        sendDataToFragment(StaticConstants.CH_FRAGMENT_UNHIDE, null);
    }


    // ----------------- Connection State -----------------
    private void setState(String state) {
        connectState = state;
        mTitle.updateRight(state);

        if (CONNECTED.equals(state)) {
            mTitle.updateLoadingState(false);
            sendConnectStateToFragments(CONNECTED);
            mErrorDisconnect = null;
            return;
        }
        if (CONNECTING.equals(state)) {
            mTitle.updateLoadingState(true);
            sendConnectStateToFragments(CONNECTING);
            return;
        }

        if (DISCONNECT.equals(state)) {
            mTitle.updateLoadingState(false);
            sendConnectStateToFragments(DISCONNECT);
        }
    }

    private void sendConnectStateToFragments(String state) {
        publishConnectState(state);
    }

    private void handleConnectStateClick() {
        if (isConnected()) {
            disconnectCurrentModule();
            return;
        }

        if (isDisconnected()) {
            reconnectCurrentModule();
        }
    }

    private void disconnectCurrentModule() {
        DeviceModule module = getCurrentModule();
        if (module == null || mAndroidBluetoothController == null) return;

        mAndroidBluetoothController.tempDisconnect(module);
        setState(DISCONNECT);
    }

    private void reconnectCurrentModule() {
        DeviceModule module = getCurrentModule();

        if (module == null) {
            module = mErrorDisconnect;
        }

        if (module == null || mAndroidBluetoothController == null) {
            toastShort("连接失败");
            setState(DISCONNECT);
            return;
        }

        mAndroidBluetoothController.connect(module);
        log("开启连接动画");
        setState(CONNECTING);
    }

    private boolean isConnected() {
        return CONNECTED.equals(connectState);
    }

    private boolean isDisconnected() {
        return DISCONNECT.equals(connectState);
    }

    private boolean isConnecting() {
        return CONNECTING.equals(connectState);
    }

    @Nullable
    private DeviceModule getCurrentModule() {
        if (modules == null || modules.isEmpty()) return null;
        return modules.get(0);
    }


    // ----------------- Title Menu -----------------
    private void popupWindow(View anchor) {
        CommonPopupWindow window = new CommonPopupWindow(R.layout.pop_window_title, anchor);

        updateMtuMenuText(window);
        bindPopupActions(window);
        showPopupWindow(window);
    }

    private void updateMtuMenuText(CommonPopupWindow window) {
        DeviceModule module = getCurrentModule();
        if (module == null || !module.isBLE()) return;

        TextView mtu = window.findViewById(R.id.pop_title_mtu);
        if (mtu != null) {
            mtu.setText("修改 MTU(" + mMTUNumber + ")");
        }
    }

    private void bindPopupActions(CommonPopupWindow window) {
        View.OnClickListener listener = v -> {
            window.dismiss();

            if (!isConnected()) {
                toastLong("请连接模块后再操作");
                return;
            }

            if (v.getId() == R.id.pop_title_mtu) {
                openMtuDialog();
            }
        };

        window.setListeners(listener, R.id.pop_title_mtu);
    }

    private void openMtuDialog() {
        DeviceModule module = getCurrentModule();

        if (module == null) {
            toastLong("请连接模块后再操作");
            return;
        }

        if (!module.isBLE()) {
            toastLong("只支持 BLE 蓝牙设置 MTU");
            return;
        }

        CommonDialog.Builder builder = new CommonDialog.Builder(CgmActivity.this);
        builder.setView(R.layout.hint_set_mtu_vessel).fullWidth().loadAnimation().create().show();

        SetMtu setMtu = builder.getView(R.id.hint_set_mtu_vessel_view);
        setMtu.setBuilder(builder).setCallback(mtu -> mAndroidBluetoothController.setMTU(module, mtu));
    }

    private void showPopupWindow(@NonNull CommonPopupWindow window) {
        window.getBuilder().setPopupWindowsPosition(CommonPopupWindow.HorizontalPosition.ALIGN_RIGHT, CommonPopupWindow.VerticalPosition.BELOW).setExcursion(this, 0, 10).setAnim(R.style.pop_window_anim).setShadow(this, 0.9f).create().show();
    }


    // ----------------- Guide -----------------
    private void setGuide() {
        NewbieGuide.with(this).setLabel("guide1").anchor(getWindow().getDecorView()).addGuidePage(GuidePage.newInstance().addHighLight(mTitle.getView(R.id.right_more), new RelativeGuide(R.layout.guide_page_main, Gravity.START)).setOnLayoutInflatedListener((view, controller) -> {
            String data = "设置MTU，发送文件在这👉";
            TextView textView = view.findViewById(R.id.guide_page_text);
            if (textView != null) textView.setText(data);
        })).show();
    }

}
