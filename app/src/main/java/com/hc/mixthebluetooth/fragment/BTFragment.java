package com.hc.mixthebluetooth.fragment;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.hc.basiclibrary.viewBasic.BaseFragment;
import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.BTPackage;
import com.hc.mixthebluetooth.activity.single.StaticConstants;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentLogItem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Base class for Bluetooth-aware fragments.
 * Subclasses declare the channels they care about in initChannels().
 * BTFragment subscribes to those channels and routes known payloads
 * into typed callback methods.
 */
public abstract class BTFragment<T extends ViewBinding> extends BaseFragment<T> {

    private final Set<String> registeredChannels = new HashSet<>();

    /**
     * Subclass declares what it wants to receive.
     */
    protected abstract void initChannels();

    /**
     * Subclass writes normal init logic here.
     * <p>
     * This replaces BaseFragment.initAll() for BTFragment children.
     */
    protected abstract void initAllImpl(View view, Context context);

    protected void register(String... channels) {
        registeredChannels.addAll(Arrays.asList(channels));
    }

    @Override
    protected void initAll(View view, Context context) {
        initChannels();

        if (!registeredChannels.isEmpty()) {
            subscription(registeredChannels.toArray(new String[0]));
        }

        initAllImpl(view, context);
    }

    @Override
    protected void updateState(String sign, Object data) {
        if (!registeredChannels.contains(sign)) {
            updateStateImpl(sign, data);
            return;
        }

        switch (sign) {
            case StaticConstants.CH_BT_DATA:
                routeBtData(data);
                break;

            case StaticConstants.CH_REC_STATE:
                if (data instanceof Boolean) {
                    onRecStateChanged((Boolean) data);
                }
                break;

            case StaticConstants.CH_REC_EXPORT_RESULT:
                onRecExportResult(data != null ? data.toString() : null);
                break;

            case StaticConstants.CH_LOG_MESSAGE:
                if (data instanceof FragmentLogItem) {
                    onLogMessage((FragmentLogItem) data);
                }
                break;

            default:
                updateStateImpl(sign, data);
                break;
        }
    }

    private void routeBtData(Object data) {
        if (data instanceof BTPackage.BTData) {
            onBtData((BTPackage.BTData) data);
            return;
        }

        if (data instanceof BTPackage.Connected) {
            onBtConnected(((BTPackage.Connected) data).module);
            return;
        }

        if (data instanceof BTPackage.Disconnected) {
            onBtDisconnected();
            return;
        }

        // Compatibility path: old fragments can still handle DeviceModule/Object[].
        updateStateImpl(StaticConstants.CH_BT_DATA, data);
    }

    protected void onBtConnected(DeviceModule module) {
    }

    protected void onBtData(BTPackage.BTData data) {
    }

    protected void onBtDisconnected() {
    }

    protected void onRecStateChanged(boolean recording) {
    }

    protected void onRecExportResult(@Nullable String path) {
    }

    protected void onLogMessage(FragmentLogItem item) {
    }

    protected void onConnectStateChanged(String state) {
    }

    protected void onFragmentVisibilityChanged(boolean visible) {
    }

    /**
     * Compatibility hook for old-style updateState logic.
     * New fragments usually do not need to override this.
     */
    protected void updateStateImpl(String sign, Object data) {
    }
}
