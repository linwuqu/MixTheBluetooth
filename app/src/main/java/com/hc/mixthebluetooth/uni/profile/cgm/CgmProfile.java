package com.hc.mixthebluetooth.uni.profile.cgm;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.uni.Commands;
import com.hc.mixthebluetooth.uni.Controller;

import java.io.File;
import java.util.Date;

public final class CgmProfile {
    private CgmProfile() {
    }

    @NonNull
    public static Controller.ProfileSpec create() {
        return Controller.ProfileSpec.builder("cgm")
                .action(Controller.ActionSpec.postText("sync_time", "同步时间", () -> Commands.LegacyCgm.syncTime(new Date())))
                .action(Controller.ActionSpec.postText("read_cache", "读取缓存", Commands.LegacyCgm::readCache))
                .action(Controller.ActionSpec.postText("delete_cache", "删除缓存", Commands.LegacyCgm::deleteCache))
                .rawLineConsumer(new CgmRawLineConsumer())
                .build();
    }

    private static final class CgmRawLineConsumer implements Controller.RawLineConsumer {
        private CgmPlaybackRecorder recorder;

        @Override
        public void onLine(@NonNull Context context, @NonNull String line, @NonNull Controller.Gateway gateway) {
            if (recorder == null) {
                recorder = new CgmPlaybackRecorder(context);
            }
            CgmPlaybackRecorder.Result result = recorder.onLine(line);
            File file = result.file();
            if (result.isCompleted() && file != null) {
                gateway.onCacheFileReady(file);
            }
        }
    }
}
