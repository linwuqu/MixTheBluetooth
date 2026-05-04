package com.hc.mixthebluetooth.activity;


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
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.customView.UnderlineTextView;
import com.hc.mixthebluetooth.customView.dialog.SetMtu;
import com.hc.mixthebluetooth.databinding.ActivityCommunicationBinding;
import com.hc.mixthebluetooth.fragment.FragmentCustom;
import com.hc.mixthebluetooth.fragment.FragmentIonAnalysis;
import com.hc.mixthebluetooth.fragment.FragmentLog;
import com.hc.mixthebluetooth.fragment.FragmentMessage;
import com.hc.mixthebluetooth.fragment.FragmentMessageNew;
import com.hc.mixthebluetooth.fragment.FragmentSetting;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentLogItem;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.util.List;

public class CommunicationActivity extends BaseActivity<ActivityCommunicationBinding> {
    private static final String CONNECTED = "已连接";
    private static final String CONNECTING = "连接中";
    private static final String DISCONNECT = "断线了";

    private static final int PAGE_MESSAGE = 0;
    private static final int PAGE_MESSAGE_NEW = 1;
    private static final int PAGE_CUSTOM = 2;
    private static final int PAGE_ION_ANALYSIS = 3;
    private static final int PAGE_SETTING = 4;
    private static final int PAGE_LOG = 5;

    private DefaultNavigationBar mTitle;
    private UnderlineTextView mUnderlineTV;
    private ViewPagerManage viewPagerManage;

    private HoldBluetooth mHoldBluetooth;
    private List<DeviceModule> modules;
    private DeviceModule mErrorDisconnect;
    private String connectState = CONNECTING;
    private int mMTUNumber = 23;

    private boolean showLogPage;
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
        initTabs();
        initSubscription();
    }

    @Override
    protected ActivityCommunicationBinding getViewBinding() {
        return ActivityCommunicationBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logWarn("关闭CommunicationActivity...");
        if (modules != null) mHoldBluetooth.disconnect(modules.get(0));
    }


    // ----------------- Initialization -----------------
    private void initDependencies() {
        mHoldBluetooth = HoldBluetooth.getInstance();
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
        mTitle = new DefaultNavigationBar.Builder(this, findViewById(R.id.communication_name))
                .setLeftText("Biosensors System", 18)
                .setRightText(CONNECTING)
                .setRightClickListener(listener)
                .builer();

        mTitle.updateLoadingState(true);
    }

    private void initBluetoothListener() {
        HoldBluetooth.OnReadDataListener dataListener = new HoldBluetooth.OnReadDataListener() {
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
        mHoldBluetooth.setOnReadListener(dataListener);
    }

    private void initPages() {
        showLogPage = shouldShowLogPage();

        viewPagerManage = new ViewPagerManage(viewBinding.communicationFragment);
        viewPagerManage.addFragment(new FragmentMessage());
        viewPagerManage.addFragment(new FragmentMessageNew());
        viewPagerManage.addFragment(new FragmentCustom());
        viewPagerManage.addFragment(new FragmentIonAnalysis());
        viewPagerManage.addFragment(new FragmentSetting());

        if (showLogPage) {
            viewPagerManage.addFragment(new FragmentLog());
            viewBinding.tabLog.setVisibility(View.VISIBLE);
        } else {
            viewBinding.tabLog.setVisibility(View.GONE);
        }

        viewBinding.communicationFragment.setAdapter(viewPagerManage.getAdapter());
        viewPagerManage.setPositionListener(this::updateTabSelection);

        showPage(PAGE_MESSAGE_NEW);
    }

    private void initTabs() {
        bindClickListener(
                viewBinding.tabMessage,
                viewBinding.tabMessageNew,
                viewBinding.tabCustom,
                viewBinding.tabIonAnalysis,
                viewBinding.tabSetting,
                viewBinding.tabLog
        );
    }

    private void initSubscription() {
        subscription(StaticConstants.CMD_SEND_BT_DATA);
    }


    // ----------------- Fragment Commands -----------------
    @Override
    protected void update(@NonNull String sign, Object data) {
        if (sign.equals(StaticConstants.CMD_SEND_BT_DATA)) {
            onSendBtDataCommand(data);
        } else {
            logWarn("Unknown activity command: " + sign);
        }
    }

    private void onSendBtDataCommand(Object data) {
        if (!(data instanceof FragmentMessageItem)) {
            logWarn("Ignore send data command, payload is not FragmentMessageItem: " + data);
            return;
        }

        FragmentMessageItem item = (FragmentMessageItem) data;

        if (item.getModule() == null) {
            logWarn("Ignore send data command, module is null");
            return;
        }

        if (item.getByteData() == null) {
            logWarn("Ignore send data command, byteData is null");
            return;
        }

        mHoldBluetooth.sendData(item.getModule(), item.getByteData().clone());
    }

    // ----------------- Page Navigation -----------------
    @Override
    public void onClickView(View view) {
        publishFragmentHide();

        if (isCheck(viewBinding.tabMessage)) {
            showPage(PAGE_MESSAGE);
        } else if (isCheck(viewBinding.tabMessageNew)) {
            showPage(PAGE_MESSAGE_NEW);
        } else if (isCheck(viewBinding.tabCustom)) {
            showPage(PAGE_CUSTOM);
            publishFragmentUnhide();
        } else if (isCheck(viewBinding.tabIonAnalysis)) {
            showPage(PAGE_ION_ANALYSIS);
        } else if (isCheck(viewBinding.tabSetting)) {
            showPage(PAGE_SETTING);
        } else if (isCheck(viewBinding.tabLog) && showLogPage) {
            showPage(PAGE_LOG);
        }
    }

    private void showPage(int page) {
        viewBinding.communicationFragment.setCurrentItem(page);
        updateTabSelection(page);
    }

    private void updateTabSelection(int position) {
        if (mUnderlineTV != null) {
            mUnderlineTV.setState(false);
        }

        switch (position) {
            case PAGE_MESSAGE:
                mUnderlineTV = viewBinding.tabMessage.setState(true);
                break;

            case PAGE_MESSAGE_NEW:
                mUnderlineTV = viewBinding.tabMessageNew.setState(true);
                break;

            case PAGE_CUSTOM:
                mUnderlineTV = viewBinding.tabCustom.setState(true);
                break;

            case PAGE_ION_ANALYSIS:
                mUnderlineTV = viewBinding.tabIonAnalysis.setState(true);
                break;

            case PAGE_SETTING:
                mUnderlineTV = viewBinding.tabSetting.setState(true);
                break;

            case PAGE_LOG:
                if (showLogPage) {
                    mUnderlineTV = viewBinding.tabLog.setState(true);
                }
                break;
        }
    }

    private boolean shouldShowLogPage() {
        return mHoldBluetooth != null && mHoldBluetooth.isDevelopmentMode();
    }


    // ----------------- Bluetooth Callbacks -----------------
    private void onBluetoothData(byte[] data) {
        DeviceModule module = getCurrentModule();
        if (module == null) return;

        publishBtData(module, data);
    }

    private void onBluetoothReading(boolean isStart) {
        publishSpeedVisible(isStart);
    }

    private void onBluetoothConnected() {
        modules = mHoldBluetooth.getConnectedArray();

        DeviceModule module = getCurrentModule();
        if (module == null) return;

        publishBtConnected(module);

        setState(CONNECTED);
        mTitle.updateLeftText(module.getName());
        log("连接成功: " + module.getName());
    }

    private void onBluetoothDisconnected(final DeviceModule deviceModule) {
        if (mErrorDisconnect == null) {
            mErrorDisconnect = deviceModule;

            if (mHoldBluetooth != null && deviceModule != null) {
                mTimeHandler.postDelayed(() -> {
                    mHoldBluetooth.connect(deviceModule);
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
        sendDataToFragment(
                StaticConstants.CH_BT_DATA,
                new BTPackage.BTData(module, data)
        );
    }

    private void publishBtConnected(DeviceModule module) {
        sendDataToFragment(
                StaticConstants.CH_BT_DATA,
                new BTPackage.Connected(module)
        );
    }

    private void publishBtDisconnected() {
        sendDataToFragment(
                StaticConstants.CH_BT_DATA,
                BTPackage.Disconnected.INSTANCE
        );
    }

    private void publishLog(String className, String data, String level) {
        sendDataToFragment(
                StaticConstants.CH_LOG_MESSAGE,
                new FragmentLogItem(className, data, level)
        );
    }

    private void publishConnectState(String state) {
        sendDataToFragment(StaticConstants.CH_SET_CONNECT_STATE, state);
    }

    private void publishSpeedVisible(boolean visible) {
        sendDataToFragment(StaticConstants.CH_SET_SPEED_VISIBLE, visible);
    }

    private void publishVelocity(int velocity) {
        sendDataToFragment(StaticConstants.CH_VELOCITY, velocity);
    }

    private void publishSentBytes(int number) {
        sendDataToFragment(StaticConstants.CH_SENT_BYTES, number);
    }

    private void publishStopLoopSend() {
        sendDataToFragment(StaticConstants.CH_STOP_LOOP_SEND, null);
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
        if (module == null || mHoldBluetooth == null) return;

        mHoldBluetooth.tempDisconnect(module);
        setState(DISCONNECT);
    }

    private void reconnectCurrentModule() {
        DeviceModule module = getCurrentModule();

        if (module == null) {
            module = mErrorDisconnect;
        }

        if (module == null || mHoldBluetooth == null) {
            toastShort("连接失败");
            setState(DISCONNECT);
            return;
        }

        mHoldBluetooth.connect(module);
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

            if (v.getId() == R.id.pop_title_file) {
                openSendFilePage();
                return;
            }

            if (v.getId() == R.id.pop_title_mtu) {
                openMtuDialog();
            }
        };

        window.setListeners(listener, R.id.pop_title_file, R.id.pop_title_mtu);
    }

    private void openSendFilePage() {
        publishStopLoopSend();
        startActivity(SendFileActivity.class);
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

        CommonDialog.Builder builder = new CommonDialog.Builder(CommunicationActivity.this);
        builder.setView(R.layout.hint_set_mtu_vessel)
                .fullWidth()
                .loadAnimation()
                .create()
                .show();

        SetMtu setMtu = builder.getView(R.id.hint_set_mtu_vessel_view);
        setMtu.setBuilder(builder)
                .setCallback(mtu -> mHoldBluetooth.setMTU(module, mtu));
    }

    private void showPopupWindow(@NonNull CommonPopupWindow window) {
        window.getBuilder()
                .setPopupWindowsPosition(
                        CommonPopupWindow.HorizontalPosition.ALIGN_RIGHT,
                        CommonPopupWindow.VerticalPosition.BELOW
                )
                .setExcursion(this, 0, 10)
                .setAnim(R.style.pop_window_anim)
                .setShadow(this, 0.9f)
                .create()
                .show();
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
