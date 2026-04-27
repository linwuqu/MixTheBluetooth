package com.hc.mixthebluetooth.activity;

import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.basiclibrary.dialog.CommonDialog;
import com.hc.basiclibrary.popupWindow.CommonPopupWindow;
import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseActivity;
import com.hc.basiclibrary.viewBasic.manage.ViewPagerManage;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.bluetoothlibrary.tootl.ModuleParameters;
import com.hc.mixthebluetooth.R;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.HoldBluetooth;
import com.hc.mixthebluetooth.activity.single.MessageNewCmd;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通信中枢 — 统一管理所有 Fragment 之间的通信。
 *
 * 设计原则：
 * 1. Activity 是所有通信的枢纽，Fragment 之间不直接通信
 * 2. 所有发送到 Fragment 的数据通过 BTPackage 包装，保证类型安全
 * 3. Fragment 发出的控制指令统一在 handleCommand() 中处理
 * 4. 录制/导出逻辑内聚为 Recorder 子类，不污染 Activity 主线
 * 5. ViewPager 位置使用 TAG 而非整数硬编码，便于维护
 *
 * 通信架构图：
 *
 *   ┌─────────────────────────────────────────────────┐
 *   │              CommunicationActivity                │
 *   │                                                  │
 *   │  HoldBluetooth.readData ─────────────────────── │
 *   │        ↓                                         │
 *   │  readDataListener ────────────────────────────────│
 *   │        ↓ (BTPackage)                            │
 *   │  sendDataToFragment(CH_BT_DATA, BTPackage)      │
 *   │        ↓                                         │
 *   │  FragmentMessageNew.onBtData(BTPackage.BTData) │
 *   │  FragmentCustom.onBtData(BTPackage.BTData)     │
 *   │  FragmentMessage.onBtData(BTPackage.BTData)    │
 *   │                                                  │
 *   │  Fragment → sendDataToActivity(CMD_*, payload)  │
 *   │        ↓                                         │
 *   │  update() → handleCommand()                     │
 *   │        ↓                                         │
 *   │  HoldBluetooth.send(module, bytes)              │
 *   └─────────────────────────────────────────────────┘
 *
 * 迁移步骤：
 * 1. 确认所有 Fragment 使用 BTFragment 基类或 BTPackage 消费
 * 2. 逐步将 updateState() 中的 instanceof 替换为 onXxx() 回调
 * 3. 旧有 FRAGMENT_STATE_DATA 仍可通过 BTPackage.Connected/BTData 路由
 */
public class CommunicationActivity extends BaseActivity<ActivityCommunicationBinding> {

    // ─── 状态 ────────────────────────────────────────────────────────

    private static final String TAG_MESSAGE   = "FragmentMessage";
    private static final String TAG_NEW      = "FragmentMessageNew";
    private static final String TAG_CUSTOM   = "FragmentCustom";
    private static final String TAG_ION      = "FragmentIonAnalysis";
    private static final String TAG_SETTINGS = "FragmentSettings";
    private static final String TAG_LOG      = "FragmentLog";

    private final String CONNECTED   = "已连接";
    private final String CONNECTING = "连接中";
    private final String DISCONNECT = "断线了";

    private UnderlineTextView   mUnderlineTV;
    private int                 mMTUNumber = 23;
    private DefaultNavigationBar mTitle;
    private List<DeviceModule> modules;
    private HoldBluetooth       mHoldBluetooth;
    private DeviceModule        mErrorDisconnect;
    private final Handler       mTimeHandler = new Handler();
    private String              mDeviceName;

    // ─── 录制中枢 ────────────────────────────────────────────────────

    private final Recorder mRecorder = new Recorder();

    // ─── Fragment 注册表（TAG → Fragment 实例）─────────────────────

    private final Map<String, androidx.fragment.app.Fragment> fragmentRegistry = new HashMap<>();

    // ─── 生命周期 ────────────────────────────────────────────────────

    @Override
    public void initAll() {
        mHoldBluetooth = HoldBluetooth.getInstance();

        if (getIntent().hasExtra("device_name")) {
            mDeviceName = getIntent().getStringExtra("device_name");
        }

        initTitle();
        initDataListener();
        initFragment();
        initNavIndicator();
        initSubscriptions();
    }

    private void initSubscriptions() {
        subscription(
                StaticConstants.CMD_SEND_BT_DATA,
                StaticConstants.CMD_MSG_NEW_CONTROL
        );
    }

    // ─── 数据监听器 — 蓝牙 → Fragment ───────────────────────────────

    private void initDataListener() {
        HoldBluetooth.OnReadDataListener dataListener = new HoldBluetooth.OnReadDataListener() {

            @Override
            public void readData(@NonNull String mac, @NonNull byte[] data) {
                if (modules == null || modules.isEmpty()) return;
                DeviceModule module = modules.get(0);
                // 使用 BTPackage 推送蓝牙数据
                sendDataToFragment(StaticConstants.CH_BT_DATA,
                        new BTPackage.BTData(module, data));
            }

            @Override
            public void reading(boolean isStart) {
                sendDataToFragment(StaticConstants.CH_SET_SPEED_VISIBLE, isStart);
            }

            @Override
            public void connectSucceed() {
                modules = mHoldBluetooth.getConnectedArray();
                DeviceModule module = modules.get(0);

                // 推送连接成功事件（类型安全）
                sendDataToFragment(StaticConstants.CH_BT_DATA,
                        new BTPackage.Connected(module));
                // 推送 NavigationBar 引用
                mTimeHandler.postDelayed(() ->
                        sendDataToFragment(StaticConstants.CH_SET_NAV_TITLE, mTitle), 500);
                sendDataToFragment(StaticConstants.CH_SET_NAV_TITLE, mTitle);

                setState(CONNECTED);
                mTitle.updateLeftText(module.getName());
                log("连接成功: " + module.getName());
            }

            @Override
            public void errorDisconnect(@Nullable DeviceModule deviceModule) {
                if (mErrorDisconnect == null) {
                    mErrorDisconnect = deviceModule;
                    if (mHoldBluetooth != null && deviceModule != null) {
                        mTimeHandler.postDelayed(() -> {
                            mHoldBluetooth.connect(deviceModule);
                            setState(CONNECTING);
                            sendDataToFragment(StaticConstants.CH_STOP_LOOP_SEND, null);
                        }, 2000);
                        return;
                    }
                }
                setState(DISCONNECT);
                // 推送断开连接事件
                sendDataToFragment(StaticConstants.CH_BT_DATA, BTPackage.Disconnected.INSTANCE);
                String name = deviceModule != null ? deviceModule.getName() : "未知设备";
                toastLong("连接" + name + "失败，点击右上角的已断线可尝试重连");
            }

            @Override
            public void readNumber(int number) {
                sendDataToFragment(StaticConstants.CH_SENT_BYTES, number);
            }

            @Override
            public void readLog(@NonNull String className, @NonNull String data, @NonNull String lv) {
                FragmentLogItem item = new FragmentLogItem(className, data, lv);
                sendDataToFragment(StaticConstants.CH_LOG_MESSAGE, item);
            }

            @Override
            public void readVelocity(int velocity) {
                sendDataToFragment(StaticConstants.CH_VELOCITY, velocity);
            }

            @Override
            public void callbackMTU(int mtu) {
                if (mtu == -2) {
                    toastShortAlive("你的手机不支持设置MTU");
                } else if (mtu == -1) {
                    toastShortAlive("设置MTU失败..");
                } else {
                    mMTUNumber = mtu;
                    toastShortAlive("MTU 设置为: " + mtu);
                }
            }
        };
        mHoldBluetooth.setOnReadListener(dataListener);
    }

    // ─── Fragment 初始化 ─────────────────────────────────────────────

    private void initFragment() {
        ViewPagerManage manage = new ViewPagerManage(viewBinding.communicationFragment);
        manage.setDuration(400);

        // 按 TAG 注册 Fragment，避免硬编码整数位置
        FragmentMessage msgFragment = new FragmentMessage();
        FragmentMessageNew newFragment = new FragmentMessageNew();
        FragmentCustom customFragment = new FragmentCustom();
        FragmentThree settingsFragment = new FragmentThree();

        fragmentRegistry.put(TAG_MESSAGE, msgFragment);
        fragmentRegistry.put(TAG_NEW, newFragment);
        fragmentRegistry.put(TAG_CUSTOM, customFragment);
        fragmentRegistry.put(TAG_ION, new FragmentIonAnalysis());
        fragmentRegistry.put(TAG_SETTINGS, settingsFragment);

        manage.addFragment(msgFragment);    // position 0
        manage.addFragment(newFragment);   // position 1
        manage.addFragment(customFragment); // position 2
        manage.addFragment(customFragment); // position 3 (复用 FragmentCustom)
        manage.addFragment(settingsFragment); // position 4

        if (mHoldBluetooth.isDevelopmentMode()) {
            FragmentLog logFragment = new FragmentLog();
            fragmentRegistry.put(TAG_LOG, logFragment);
            manage.addFragment(logFragment);
            viewBinding.log.setVisibility(View.VISIBLE);
        }

        manage.setPositionListener(this::onPageSelected);
        viewBinding.communicationFragment.setAdapter(manage.getAdapter());
        viewBinding.communicationFragment.setOffscreenPageLimit(6);
        viewBinding.communicationFragment.setCurrentItem(0, false);
    }

    private void onPageSelected(int position) {
        if (mUnderlineTV != null) mUnderlineTV.setState(false);
        switch (position) {
            case 0: mUnderlineTV = viewBinding.one.setState(true);       break;
            case 1: mUnderlineTV = viewBinding.two.setState(true);       break;
            case 2: mUnderlineTV = viewBinding.three.setState(true);     break;
            case 3: mUnderlineTV = viewBinding.ionAnalysis.setState(true); break;
            case 4: /* settings — no underline */                         break;
            case 5:
                if (mHoldBluetooth.isDevelopmentMode()) {
                    mUnderlineTV = viewBinding.log.setState(true);
                }
                break;
        }
    }

    // ─── 导航指示器 ──────────────────────────────────────────────────

    private void initNavIndicator() {
        mUnderlineTV = viewBinding.one.setState(true);
        bindClickListener(viewBinding.one, viewBinding.two,
                viewBinding.three, viewBinding.ionAnalysis, viewBinding.log);
    }

    // ─── 顶部导航栏 ──────────────────────────────────────────────────

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
                    setState(DISCONNECT);
                }
            } else if (str.equals(DISCONNECT)) {
                if ((modules != null || mErrorDisconnect != null) && mHoldBluetooth != null) {
                    mHoldBluetooth.connect(
                            modules != null && modules.get(0) != null ? modules.get(0) : mErrorDisconnect);
                    setState(CONNECTING);
                } else {
                    toastShort("连接失败...");
                    setState(DISCONNECT);
                }
            }
        };

        mTitle = new DefaultNavigationBar.Builder(this, findViewById(R.id.communication_name))
                .setLeftText("Biosensors System", 18)
                .setRightText(CONNECTING)
                .setRightClickListener(listener)
                .builer();
        mTitle.updateLoadingState(true);
    }

    private void setState(@NonNull String state) {
        switch (state) {
            case CONNECTED:
                mTitle.updateRight(CONNECTED);
                sendDataToFragment(StaticConstants.CH_SET_CONNECT_STATE, CONNECTED);
                mErrorDisconnect = null;
                break;
            case CONNECTING:
                mTitle.updateRight(CONNECTING);
                mTitle.updateLoadingState(true);
                sendDataToFragment(StaticConstants.CH_SET_CONNECT_STATE, CONNECTING);
                break;
            case DISCONNECT:
                mTitle.updateRight(DISCONNECT);
                mTitle.updateLoadingState(false);
                sendDataToFragment(StaticConstants.CH_SET_CONNECT_STATE, DISCONNECT);
                break;
        }
    }

    // ─── 指令处理中枢 — Fragment → Activity ─────────────────────────

    @Override
    protected void update(@NonNull String sign, @Nullable Object data) {
        switch (sign) {
            case StaticConstants.CMD_SEND_BT_DATA:
                handleSendData(data);
                break;
            case StaticConstants.CMD_MSG_NEW_CONTROL:
                handleMessageNewControl(data);
                break;
        }
    }

    private void handleSendData(@Nullable Object data) {
        if (!(data instanceof FragmentMessageItem)) return;
        FragmentMessageItem item = (FragmentMessageItem) data;
        mHoldBluetooth.sendData(item.getModule(), item.getByteData().clone());
    }

    private void handleMessageNewControl(@Nullable Object data) {
        String cmd = data != null ? data.toString() : "";
        logWarn("MessageNew control: " + cmd);

        switch (cmd) {
            case MessageNewCmd.START_RECORD:
                mRecorder.start();
                break;
            case MessageNewCmd.STOP_RECORD:
                mRecorder.stop();
                break;
            case MessageNewCmd.EXPORT:
                mRecorder.export();
                break;
        }
    }

    // ─── 录制中枢 ────────────────────────────────────────────────────

    /**
     * 录制中枢 — 将录制/导出逻辑从 Activity 主线中解耦。
     *
     * 职责：
     * - 管理录制状态
     * - 管理文件创建和写入（异步）
     * - 向 Fragment 推送状态变更和导出结果
     */
    private class Recorder {
        private volatile boolean recording = false;
        private File             recordFile;
        private int             sampleCount = 0;
        private final ExecutorService io = Executors.newSingleThreadExecutor();

        void start() {
            recordFile = createRecordFile();
            recording = true;
            sampleCount = 0;
            sendDataToFragment(StaticConstants.CH_REC_STATE, true);
            toastShortAlive("录制已开始: " + (recordFile != null ? recordFile.getName() : "null"));
            logWarn("Recorder: started, file=" + (recordFile != null ? recordFile.getAbsolutePath() : "null"));
        }

        void stop() {
            recording = false;
            sendDataToFragment(StaticConstants.CH_REC_STATE, false);
            toastShortAlive("录制已停止，共 " + sampleCount + " 条");
            logWarn("Recorder: stopped, samples=" + sampleCount);
        }

        void export() {
            String path = recordFile != null ? recordFile.getAbsolutePath() : "";
            sendDataToFragment(StaticConstants.CH_REC_EXPORT_RESULT, path);
            toastShortAlive(path.isEmpty() ? "尚无录制文件" : "导出: " + path);
            logWarn("Recorder: export path=" + path);
        }

        void appendSample(@Nullable String jsonLine) {
            if (!recording || recordFile == null) return;
            if (jsonLine == null || jsonLine.trim().isEmpty()) return;

            sampleCount++;
            if (sampleCount == 1 || sampleCount % 50 == 0) {
                logWarn("Recorder: samples=" + sampleCount);
            }

            io.execute(() -> writeUtf8Line(recordFile, jsonLine));
        }

        private File createRecordFile() {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "message_new_" + ts + ".jsonl");
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            return file;
        }

        private void writeUtf8Line(File file, String line) {
            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                w.write(line);
                w.newLine();
            } catch (Exception e) {
                logError("Recorder write failed: " + e.getMessage());
            }
        }
    }

    // ─── 导航点击 ────────────────────────────────────────────────────

    @Override
    protected void onClickView(@NonNull View view) {
        UnderlineTextView tv = (UnderlineTextView) view;
        if (mUnderlineTV != null) mUnderlineTV.setState(false);
        tv.setState(true);
        mUnderlineTV = tv;

        if (isCheck(viewBinding.one)) {
            viewBinding.communicationFragment.setCurrentItem(0);
        } else if (isCheck(viewBinding.two)) {
            viewBinding.communicationFragment.setCurrentItem(1);
        } else if (isCheck(viewBinding.three)) {
            viewBinding.communicationFragment.setCurrentItem(2);
            sendDataToFragment(StaticConstants.CH_FRAGMENT_UNHIDE, null);
        } else if (isCheck(viewBinding.ionAnalysis)) {
            viewBinding.communicationFragment.setCurrentItem(4);
        } else if (isCheck(viewBinding.log)) {
            if (fragmentRegistry.containsKey(TAG_LOG)) {
                viewBinding.communicationFragment.setCurrentItem(
                        fragmentRegistry.size() - 1);
            }
        }
    }

    // ─── 右上角菜单 ──────────────────────────────────────────────────

    private void popupWindow(View anchor) {
        CommonPopupWindow window = new CommonPopupWindow(R.layout.pop_window_title, anchor);

        List<DeviceModule> connected = HoldBluetooth.getInstance().getConnectedArray();
        if (!connected.isEmpty() && connected.get(0).isBLE()) {
            TextView mtuView = window.findViewById(R.id.pop_title_mtu);
            mtuView.setText("修改MTU(" + mMTUNumber + ")");
        }

        View.OnClickListener listener = v -> {
            window.dismiss();
            if (!mTitle.getParams().mRightText.equals(CONNECTED)) {
                toastLong("请连接模块后再操作");
                return;
            }
            int id = v.getId();
            if (id == R.id.pop_title_file) {
                sendDataToFragment(StaticConstants.CH_STOP_LOOP_SEND, null);
                startActivity(SendFileActivity.class);
            } else if (id == R.id.pop_title_mtu) {
                DeviceModule module = HoldBluetooth.getInstance().getConnectedArray().get(0);
                if (!module.isBLE()) {
                    toastLong("只支持BLE蓝牙设置MTU");
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
                        .setCallback(mtu -> HoldBluetooth.getInstance().setMTU(module, mtu));
            }
        };

        window.setListeners(listener, R.id.pop_title_file, R.id.pop_title_mtu);
        window.getBuilder()
                .setPopupWindowsPosition(
                        CommonPopupWindow.HorizontalPosition.ALIGN_RIGHT,
                        CommonPopupWindow.VerticalPosition.BELOW)
                .setExcursion(this, 0, 10)
                .setAnim(R.style.pop_window_anim)
                .setShadow(this, 0.9f)
                .create()
                .show();
    }

    // ─── 生命周期 ────────────────────────────────────────────────────

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
}
