package com.hc.mixthebluetooth.activity.tool.message;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.activity.single.FragmentParameter;
import com.hc.mixthebluetooth.customView.PopWindowFragment;
import com.hc.mixthebluetooth.storage.Storage;

public final class MessageOptionStore {

    public interface FragmentRuntimeAccess {
        @NonNull
        Storage storage();

        @NonNull
        FragmentParameter fragmentParameter();

        @NonNull
        Context context();
    }

    private final FragmentRuntimeAccess runtime;

    public MessageOptionStore(@NonNull FragmentRuntimeAccess runtime) {
        this.runtime = runtime;
    }

    @NonNull
    public MessageOptions load() {
        Storage storage = runtime.storage();
        String codeFormat = runtime.fragmentParameter().getCodeFormat(runtime.context());
        return new MessageOptions(
                storage.getData(PopWindowFragment.KEY_DATA),
                storage.getData(PopWindowFragment.KEY_TIME),
                storage.getData(PopWindowFragment.KEY_HEX_SEND),
                storage.getData(PopWindowFragment.KEY_HEX_READ),
                storage.getData(PopWindowFragment.KEY_CLEAR),
                storage.getData(PopWindowFragment.KEY_NEWLINE),
                codeFormat == null ? "" : codeFormat
        );
    }
}
