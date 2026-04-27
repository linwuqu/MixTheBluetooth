package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.basiclibrary.titleBasic.DefaultNavigationBar;
import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentLogItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 蓝牙感知 Fragment 基类 — 基于类型安全的 BTPackage 信道模式。
 *
 * 迁移指南（从旧模式到新模式）：
 *
 * 旧模式（需要 instanceof + arr.length 判断）：
 * <pre>{@code
 * @Override protected void updateState(String sign, Object o) {
 *     if (StaticConstants.FRAGMENT_STATE_DATA.equals(sign)) {
 *         if (o instanceof DeviceModule) { module = (DeviceModule) o; return; }
 *         Object[] arr = (Object[]) o;
 *         if (arr.length < 2) return;
 *         byte[] data = (byte[]) arr[1];
 *         processLine(decode(data));
 *     }
 * }
 * }</pre>
 *
 * 新模式（类型安全回调，无二次判断）：
 * <pre>{@code
 * @Override protected void initChannels() {
 *     register(StaticConstants.CH_BT_DATA);
 * }
 *
 * @Override protected void onBtConnected(@NonNull DeviceModule m) {
 *     module = m;
 * }
 *
 * @Override protected void onBtData(@NonNull BTPackage.BTData d) {
 *     processLine(decode(d.bytes));
 * }
 * }</pre>
 *
 * 新旧模式并存：register() 注册的信道走新回调，未注册的走 updateStateImpl()。
 *
 * @param <T> ViewBinding 类型
 */
public abstract class BTFragment<T extends ViewBinding>
        extends BaseFragment<T> {

    // ─── 可重写的类型安全回调 ─────────────────────────────────────

    /** 蓝牙连接成功回调 */
    protected void onBtConnected(@NonNull DeviceModule module) {}

    /** 蓝牙数据帧回调 */
    protected void onBtData(@NonNull BTPackage.BTData data) {}

    /** 蓝牙连接断开回调 */
    protected void onBtDisconnected() {}

    /** 实时速度回调 */
    protected void onVelocity(int bytesPerSecond) {}

    /** 日志消息回调 */
    protected void onLog(@NonNull String message) {}

    /** 录制状态变更回调 */
    protected void onRecStateChanged(boolean recording) {}

    /** 导出结果回调 */
    protected void onRecExportResult(@Nullable String path) {}

    /** 连接状态变更回调（给 FragmentThree） */
    protected void onConnectStateChanged(@NonNull String state) {}

    /** Fragment 隐藏/显示回调 */
    protected void onFragmentVisibilityChanged(boolean visible) {}

    /** NavigationBar 引用回调 */
    protected void onNavTitleReceived(@NonNull DefaultNavigationBar title) {}

    /** 已发送字节数回调 */
    protected void onSentBytesChanged(int bytes) {}

    /** 停止循环发送回调 */
    protected void onStopLoopSend() {}

    /** 速度显示/隐藏回调 */
    protected void onSpeedVisibleChanged(boolean visible) {}

    /** 换行设置变更回调（FragmentCustom 子 Fragment） */
    protected void onNewlineChanged(boolean newline) {}

    // ─── 信道注册 ─────────────────────────────────────────────────

    /**
     * 子类在此方法中注册需要的信道。
     * 在 initAll() 之前调用。
     *
     * 示例：
     * <pre>{@code
     * @Override
     * protected void initChannels() {
     *     register(StaticConstants.CH_BT_DATA,
     *               StaticConstants.CH_REC_STATE,
     *               StaticConstants.CH_SET_NAV_TITLE);
     * }
     * }</pre>
     *
     * 注册后，自动分派到对应的 onXxx() 回调。
     * 未注册的信道走旧模式（updateStateImpl）。
     */
    protected void register(@NonNull String... channels) {
        for (String ch : channels) {
            registeredChannels.add(ch);
        }
    }

    // ─── 生命周期 ─────────────────────────────────────────────────

    /**
     * 子类实现原有的 initAll 逻辑。
     * initAllImpl 在 initChannels() 之后被调用。
     */
    protected abstract void initAllImpl(@NonNull View view, @NonNull Context context);

    private final Set<String> registeredChannels = new HashSet<>();
    private boolean subscribed = false;

    @Override
    protected final void initAll(@NonNull View view, @NonNull Context context) {
        initChannels();
        if (!subscribed) {
            subscribed = true;
            subscription(
                    StaticConstants.CH_BT_DATA,
                    StaticConstants.CH_REC_STATE,
                    StaticConstants.CH_REC_EXPORT_RESULT,
                    StaticConstants.CH_SET_CONNECT_STATE,
                    StaticConstants.CH_SET_NAV_TITLE,
                    StaticConstants.CH_SENT_BYTES,
                    StaticConstants.CH_STOP_LOOP_SEND,
                    StaticConstants.CH_SET_SPEED_VISIBLE,
                    StaticConstants.CH_LOG_MESSAGE,
                    StaticConstants.CH_FRAGMENT_HIDE,
                    StaticConstants.CH_FRAGMENT_UNHIDE,
                    StaticConstants.EV_CUSTOM_NEWLINE
            );
        }
        initAllImpl(view, context);
    }

    // ─── 类型安全路由 ──────────────────────────────────────────────

    @Override
    protected final void updateState(@NonNull String sign, @Nullable Object data) {
        if (!registeredChannels.contains(sign)) {
            updateStateImpl(sign, data);
            return;
        }

        switch (sign) {
            case StaticConstants.CH_BT_DATA:
                routeBT(sign, data);
                break;
            case StaticConstants.CH_REC_STATE:
                if (data instanceof Boolean) {
                    onRecStateChanged((Boolean) data);
                }
                break;
            case StaticConstants.CH_REC_EXPORT_RESULT:
                onRecExportResult(data != null ? data.toString() : null);
                break;
            case StaticConstants.CH_SET_CONNECT_STATE:
                if (data != null) onConnectStateChanged(data.toString());
                break;
            case StaticConstants.CH_SET_NAV_TITLE:
                if (data instanceof DefaultNavigationBar) {
                    onNavTitleReceived((DefaultNavigationBar) data);
                }
                break;
            case StaticConstants.CH_SENT_BYTES:
                if (data instanceof Number) {
                    onSentBytesChanged(((Number) data).intValue());
                }
                break;
            case StaticConstants.CH_STOP_LOOP_SEND:
                onStopLoopSend();
                break;
            case StaticConstants.CH_SET_SPEED_VISIBLE:
                if (data instanceof Boolean) {
                    onSpeedVisibleChanged((Boolean) data);
                }
                break;
            case StaticConstants.CH_LOG_MESSAGE:
                if (data instanceof FragmentLogItem) {
                    onLog(((FragmentLogItem) data).getData());
                } else if (data != null) {
                    onLog(data.toString());
                }
                break;
            case StaticConstants.CH_FRAGMENT_HIDE:
                onFragmentVisibilityChanged(false);
                break;
            case StaticConstants.CH_FRAGMENT_UNHIDE:
                onFragmentVisibilityChanged(true);
                break;
            case StaticConstants.EV_CUSTOM_NEWLINE:
                if (data instanceof Boolean) {
                    onNewlineChanged((Boolean) data);
                }
                break;
            default:
                updateStateImpl(sign, data);
                break;
        }
    }

    private void routeBT(@NonNull String sign, @Nullable Object data) {
        if (data instanceof BTPackage.BTData) {
            onBtData((BTPackage.BTData) data);
        } else if (data instanceof BTPackage.Connected) {
            onBtConnected(((BTPackage.Connected) data).module);
        } else if (data instanceof BTPackage.Disconnected) {
            onBtDisconnected();
        } else if (data instanceof BTPackage.Velocity) {
            onVelocity(((BTPackage.Velocity) data).bytesPerSecond);
        } else if (data instanceof BTPackage.Log) {
            onLog(((BTPackage.Log) data).message);
        } else {
            // 兼容旧模式：Object[]
            updateStateImpl(sign, data);
        }
    }

    /**
     * 旧模式兜底实现（处理未注册信道或 Object[] 兼容）。
     * 子类重写此方法以保持对旧信道的兼容。
     */
    protected void updateStateImpl(@NonNull String sign, @Nullable Object data) {
        // 默认空实现。子类重写以处理旧模式兼容。
    }
}
