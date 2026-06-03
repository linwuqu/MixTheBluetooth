package com.hc.mixthebluetooth.api.cgm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hc.mixthebluetooth.api.ApiCallback;
import com.hc.mixthebluetooth.api.CallResult;

public interface CgmWorkflow {
    @NonNull
    Update onReadCacheSent();

    @NonNull
    Update onDeleteCacheSent();

    @NonNull
    Update onDeviceText(@NonNull String text, @NonNull ApiCallback<CallResult<CgmResult>> callback);

    void reset();

    final class Update {
        @Nullable
        public final String message;
        @Nullable
        public final String commandText;
        public final boolean uploadStarted;
        public final boolean deleteConfirmed;
        public final boolean error;

        private Update(@Nullable String message,
                       @Nullable String commandText,
                       boolean uploadStarted,
                       boolean deleteConfirmed,
                       boolean error) {
            this.message = message;
            this.commandText = commandText;
            this.uploadStarted = uploadStarted;
            this.deleteConfirmed = deleteConfirmed;
            this.error = error;
        }

        @NonNull
        public static Update message(@NonNull String message) {
            return new Update(message, null, false, false, false);
        }

        @NonNull
        public static Update command(@NonNull String message, @NonNull String commandText) {
            return new Update(message, commandText, false, false, false);
        }

        @NonNull
        public static Update uploadStarted(@NonNull String message) {
            return new Update(message, null, true, false, false);
        }

        @NonNull
        public static Update deleteConfirmed(@NonNull String message) {
            return new Update(message, null, false, true, false);
        }

        @NonNull
        public static Update error(@NonNull String message) {
            return new Update(message, null, false, false, true);
        }
    }
}
