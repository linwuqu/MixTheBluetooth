package com.hc.mixthebluetooth.uni;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Commands {
    private Commands() {
    }

    public enum Capability {
        SYNC_TIME,
        START_MEASURE,
        READ_CACHE,
        DELETE_CACHE,
        SET_PARAMS,
        STOP_MEASURE
    }

    public interface Encoder {
        @NonNull
        String encode(@NonNull Capability capability);
    }

    public static final class LegacyCgm {
        private LegacyCgm() {
        }

        @NonNull
        public static String syncTime(@NonNull Date date) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy,MM,dd,HH,mm,ss", Locale.getDefault());
            return "TIME," + sdf.format(date) + "\n\r";
        }

        @NonNull
        public static String readCache() {
            return "ALL\n\r";
        }

        @NonNull
        public static String deleteCache() {
            return "DELETE\n\r";
        }
    }
}
