package com.hc.mixthebluetooth.activity;


import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

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
import com.hc.mixthebluetooth.fragment.FragmentThree;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentLogItem;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;

import java.util.List;

public class CommunicationActivity extends BaseActivity<ActivityCommunicationBinding> {

    private final String CONNECTED = "已连接", CONNECTING = "连接中", DISCONNECT = "断线了";

    private UnderlineTextView mUnderlineTV;//滑动标题暂存

    private int mMTUNumber = 23;

    private DefaultNavigationBar mTitle;

    private List<DeviceModule> modules;
    private HoldBluetooth mHoldBluetooth;

    private DeviceModule mErrorDisconnect;

    private final Handler mTimeHandler = new Handler();

    private String mDeviceName; // 添加成员变量来存储设备名称

    @Override
    public void initAll() {
        mHoldBluetooth = HoldBluetooth.getInstance();
        // 获取从 Intent 传递过来的设备名称
        if (getIntent().hasExtra("device_name")) {
            mDeviceName = getIntent().getStringExtra("device_name");
        }
        // 初始化Title
        initTitle();
        initDataListener();
        initFragment();
        mUnderlineTV = viewBinding.one.setState(true);
        bindClickListener(viewBinding.one, viewBinding.two, viewBinding.three, viewBinding.ionAnalysis, viewBinding.log);
        subscription(StaticConstants.DATA_TO_MODULE);
        //setGuide();
    }

    @Override
    protected ActivityCommunicationBinding getViewBinding() {
        return ActivityCommunicationBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void update(String sign, Object data) {
        if (sign.equals(StaticConstants.DATA_TO_MODULE)) {
            FragmentMessageItem item = (FragmentMessageItem) data;
            mHoldBluetooth.sendData(item.getModule(), item.getByteData().clone());
        }
    }

    //判断点击到哪个View
    public void onClickView(View view) {
        //把这个按钮，触发点击事件，并存下到mUnderlineTV中，等下次触发另外按钮时，再复位所保存的按钮
        UnderlineTextView underlineTextView = (UnderlineTextView) view;
        if (mUnderlineTV != null) mUnderlineTV.setState(false);
        underlineTextView.setState(true);
        mUnderlineTV = underlineTextView;
        sendDataToFragment(StaticConstants.FRAGMENT_THREE_HIDE, null);

        if (isCheck(viewBinding.one)) viewBinding.communicationFragment.setCurrentItem(0);
        if (isCheck(viewBinding.two)) viewBinding.communicationFragment.setCurrentItem(1);
        if (isCheck(viewBinding.three)) {
            viewBinding.communicationFragment.setCurrentItem(2);
            sendDataToFragment(StaticConstants.FRAGMENT_UNHIDDEN, null);//设置该页面非隐藏
        }
        if (isCheck(viewBinding.ionAnalysis)) viewBinding.communicationFragment.setCurrentItem(4);
        if (isCheck(viewBinding.log)) viewBinding.communicationFragment.setCurrentItem(5);
    }

    private void initFragment() {
        ViewPagerManage manage = new ViewPagerManage(viewBinding.communicationFragment);

        manage.addFragment(new FragmentMessage()); //第二个fragment（原主界面）
        manage.addFragment(new FragmentMessageNew());
        manage.addFragment(new FragmentCustom());  //第三个fragment
        manage.addFragment(new FragmentCustom());  //第三个fragment
        manage.addFragment(new FragmentThree()); //第四个fragment

        if (mHoldBluetooth.isDevelopmentMode()) {
            manage.addFragment(new FragmentLog());
            viewBinding.log.setVisibility(View.VISIBLE);
        }

        mTimeHandler.postDelayed(() -> sendDataToFragment(StaticConstants.FRAGMENT_STATE_SEND_SEND_TITLE, mTitle), 500);
        sendDataToFragment(StaticConstants.FRAGMENT_STATE_SEND_SEND_TITLE, mTitle);//将头部触底给fragment

        manage.setDuration(400);//控制ViewPager速度，400ms
        manage.setPositionListener(position -> {
            if (mUnderlineTV != null) mUnderlineTV.setState(false);
            switch (position) {
                case 0:
                    mUnderlineTV = viewBinding.one.setState(true);
                    break;
                case 1:
                    mUnderlineTV = viewBinding.two.setState(true);
                    break;
                case 2:
                    mUnderlineTV = viewBinding.three.setState(true);
                    break;
                case 3:
                    mUnderlineTV = viewBinding.ionAnalysis.setState(true);
                    break;
                case 4:
                    break;
                case 5:
                    if (mHoldBluetooth.isDevelopmentMode())
                        mUnderlineTV = viewBinding.log.setState(true);
                    break;
            }
        });
        viewBinding.communicationFragment.setAdapter(manage.getAdapter());
        viewBinding.communicationFragment.setOffscreenPageLimit(6);
        // 默认显示第一个页面（离子浓度分析）
        viewBinding.communicationFragment.setCurrentItem(0, false);
    }

    //初始化蓝牙数据的监听
    private void initDataListener() {
        HoldBluetooth.OnReadDataListener dataListener = new HoldBluetooth.OnReadDataListener() {
            @Override
            public void readData(String mac, byte[] data) {//读取发往模块的数据
                if (modules != null && modules.size() > 0) {
                    sendDataToFragment(StaticConstants.FRAGMENT_STATE_DATA, new Object[]{modules.get(0), data});
                }
            }

            @Override
            public void reading(boolean isStart) {
                //单独发给fragmentMessage的，2021-10-22
                if (isStart) {
                    sendDataToFragment(StaticConstants.FRAGMENT_STATE_1, null);
                } else {
                    sendDataToFragment(StaticConstants.FRAGMENT_STATE_2, null);
                }
            }

            @Override
            public void connectSucceed() {
                modules = mHoldBluetooth.getConnectedArray();
                sendDataToFragment(StaticConstants.FRAGMENT_STATE_DATA, modules.get(0));
                setState(CONNECTED);//设置连接状态
                mTitle.updateLeftText(modules.get(0).getName());
                log("连接成功: " + modules.get(0).getName());
            }

            @Override
            public void errorDisconnect(final DeviceModule deviceModule) {//蓝牙异常断开
                if (mErrorDisconnect == null) {//判断是否已经重复连接
                    mErrorDisconnect = deviceModule;
                    if (mHoldBluetooth != null && deviceModule != null) {
                        mTimeHandler.postDelayed(() -> {
                            mHoldBluetooth.connect(deviceModule);
                            setState(CONNECTING);//设置正在连接状态
                            sendDataToFragment(StaticConstants.FRAGMENT_STATE_STOP_LOOP_SEND, null);
                        }, 2000);
                        return;
                    }
                }
                setState(DISCONNECT);//设置断开状态
                if (deviceModule != null) {
                    toastLong("连接" + deviceModule.getName() + "失败，点击右上角的已断线可尝试重连");
                } else {
                    toastLong("连接模块失败，请返回上一个页面重连");
                }
            }

            @Override
            public void readNumber(int number) {
                //把发送的数据更新到发送文件的activity 与 Fragment上
                sendDataToFragment(StaticConstants.FRAGMENT_STATE_NUMBER, number);
            }

            @Override
            public void readLog(String className, String data, String lv) {
                //拿到日志
                sendDataToFragment(StaticConstants.FRAGMENT_STATE_LOG_MESSAGE, new FragmentLogItem(className, data, lv));
            }

            @Override
            public void readVelocity(int velocity) {
                sendDataToFragment(StaticConstants.FRAGMENT_STATE_SERVICE_VELOCITY, velocity);
            }

            @Override
            public void callbackMTU(int mtu) {
                if (mtu == -2) {
                    toastShortAlive("你的手机不支持设置MTU");
                    return;
                }

                if (mtu == -1) {
                    toastShortAlive("设置MTU失败..");
                    return;
                }
                mMTUNumber = mtu;
                toastShortAlive("MTU 设置为: " + mtu);
            }
        };
        mHoldBluetooth.setOnReadListener(dataListener);
    }

    private void initTitle() {
        View.OnClickListener listener = v -> {
            if (v.getId() == R.id.right_more) {
                popupWindow(v);
                return;
            }
            String str = ((TextView) v).getText().toString();
            if (str.equals(CONNECTED)) {
                if (modules != null && mHoldBluetooth != null) {
                    mHoldBluetooth.tempDisconnect(modules.get(0));
                    setState(DISCONNECT);//设置断线状态
                }
            } else if (str.equals(DISCONNECT)) {
                if ((modules != null || mErrorDisconnect != null) && mHoldBluetooth != null) {
                    mHoldBluetooth.connect(modules != null && modules.get(0) != null ? modules.get(0) : mErrorDisconnect);
                    log("开启连接动画..");
                    setState(CONNECTING);//设置正在连接状态
                } else {
                    toastShort("连接失败...");
                    setState(DISCONNECT);//设置断线状态
                }
            }
        };
        mTitle = new DefaultNavigationBar
                .Builder(this, findViewById(R.id.communication_name))
                .setLeftText("Biosensors System", 18)
                .setRightText(CONNECTING)
                .setRightClickListener(listener)
                .builer();
        mTitle.updateLoadingState(true);

    }

    /*
     * 设置连接状态
     * */
    private void setState(String state) {
        switch (state) {
            case CONNECTED://连接成功
                mTitle.updateRight(CONNECTED);
                sendDataToFragment(StaticConstants.FRAGMENT_STATE_CONNECT_STATE, CONNECTED);
                mErrorDisconnect = null;
                break;

            case CONNECTING://连接中
                mTitle.updateRight(CONNECTING);
                mTitle.updateLoadingState(true);
                sendDataToFragment(StaticConstants.FRAGMENT_STATE_CONNECT_STATE, CONNECTING);
                break;

            case DISCONNECT://连接断开
                mTitle.updateRight(DISCONNECT);
                sendDataToFragment(StaticConstants.FRAGMENT_STATE_CONNECT_STATE, DISCONNECT);
                break;
        }
    }

    /**
     * 设置引导界面
     */
    private void setGuide() {
        NewbieGuide.with(this)
                .setLabel("guide1")
                .anchor(getWindow().getDecorView())
                .addGuidePage(GuidePage.newInstance()
                        .addHighLight(mTitle.getView(R.id.right_more),
                                new RelativeGuide(R.layout.guide_page_main, Gravity.START))
                        .setOnLayoutInflatedListener((view, controller) -> {
                            String data = "设置MTU，发送文件在这👉";
                            TextView textView = view.findViewById(R.id.guide_page_text);
                            if (textView != null) textView.setText(data);
                        }))
                .show();
    }

    private void popupWindow(View view) {

        final CommonPopupWindow window = new CommonPopupWindow(R.layout.pop_window_title, view);

        if (HoldBluetooth.getInstance().getConnectedArray().size() > 0 &&
                HoldBluetooth.getInstance().getConnectedArray().get(0).isBLE()) {
            String data = "修改MTU(" + mMTUNumber + ")";
            TextView mtu = window.findViewById(R.id.pop_title_mtu);
            mtu.setText(data);
        }

        View.OnClickListener listener = v -> {
            window.dismiss();
            if (!mTitle.getParams().mRightText.equals(CONNECTED)) {
                toastLong("请连接模块后再操作");
                return;
            }

            if (v.getId() == R.id.pop_title_file) {
                sendDataToFragment(StaticConstants.FRAGMENT_STATE_STOP_LOOP_SEND, null);
                startActivity(SendFileActivity.class);
            }

            if (v.getId() == R.id.pop_title_mtu) {
                final DeviceModule deviceModule = HoldBluetooth.getInstance().getConnectedArray().get(0);
                if (!deviceModule.isBLE()) {
                    toastLong("只支持BLE蓝牙设置MTU");
                    return;
                }
                CommonDialog.Builder collectBuilder = new CommonDialog.Builder(CommunicationActivity.this);
                collectBuilder.setView(R.layout.hint_set_mtu_vessel).fullWidth().loadAnimation().create().show();
                SetMtu setMtu = collectBuilder.getView(R.id.hint_set_mtu_vessel_view);
                setMtu.setBuilder(collectBuilder).setCallback(mtu -> HoldBluetooth.getInstance().setMTU(deviceModule, mtu));
            }
        };

        window.setListeners(listener, R.id.pop_title_file, R.id.pop_title_mtu);//设置点击事件

        window.getBuilder().setPopupWindowsPosition(
                        CommonPopupWindow.HorizontalPosition.ALIGN_RIGHT,
                        CommonPopupWindow.VerticalPosition.BELOW)
                .setExcursion(this, 0, 10)
                .setAnim(R.style.pop_window_anim)
                .setShadow(this, 0.9f)
                .create().show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logWarn("关闭CommunicationActivity...");
        if (modules != null) mHoldBluetooth.disconnect(modules.get(0));
    }
}
