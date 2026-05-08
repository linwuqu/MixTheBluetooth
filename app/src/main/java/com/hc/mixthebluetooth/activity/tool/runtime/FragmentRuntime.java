package com.hc.mixthebluetooth.activity.tool.runtime;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.activity.tool.message.MessageOptionStore;
import com.hc.mixthebluetooth.recyclerData.itemHolder.FragmentMessageItem;
import com.hc.mixthebluetooth.storage.Storage;

public final class FragmentRuntime implements MessageOptionStore.FragmentRuntimeAccess {

    public interface CommandSink {
        void sendBtData(@NonNull FragmentMessageItem item);
    }

    public interface Notifier {
        void toast(@NonNull String message);
    }

    public interface Logger {
        void log(@NonNull String message);
    }

    private final Context context;
    private final Storage storage;
    private final FragmentParameter fragmentParameter;
    private final CommandSink commandSink;
    private final Notifier notifier;
    private final Logger logger;

    private DeviceModule module;
    private boolean connected;

    public FragmentRuntime(
            @NonNull Context context,
            @NonNull Storage storage,
            @NonNull FragmentParameter fragmentParameter,
            @NonNull CommandSink commandSink,
            @NonNull Notifier notifier,
            @NonNull Logger logger
    ) {
        this.context = context;
        this.storage = storage;
        this.fragmentParameter = fragmentParameter;
        this.commandSink = commandSink;
        this.notifier = notifier;
        this.logger = logger;
    }

    @NonNull
    @Override
    public Context context() {
        return context;
    }

    @NonNull
    @Override
    public Storage storage() {
        return storage;
    }

    @NonNull
    @Override
    public FragmentParameter fragmentParameter() {
        return fragmentParameter;
    }

    @Nullable
    public DeviceModule module() {
        return module;
    }

    public void setModule(@Nullable DeviceModule module) {
        this.module = module;
    }

    public boolean connected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public void sendBtData(@NonNull FragmentMessageItem item) {
        commandSink.sendBtData(item);
    }

    public void toast(@NonNull String message) {
        notifier.toast(message);
    }

    public void log(@NonNull String message) {
        logger.log(message);
    }
}
