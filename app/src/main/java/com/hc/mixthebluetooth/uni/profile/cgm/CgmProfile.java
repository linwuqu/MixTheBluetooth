package com.hc.mixthebluetooth.uni.profile.cgm;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.uni.Commands;
import com.hc.mixthebluetooth.uni.Controller;
import com.hc.mixthebluetooth.uni.Widgets;

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
                .widget(Widgets.WidgetSpec.cgmResult("cgm_result")
                        .title("CGM mmol/L")
                        .order(0)
                        .build())
                .rawLineConsumer(new CgmRawLineConsumer())
                .build();
    }

    private static final class CgmRawLineConsumer implements Controller.RawLineConsumer {
        @Override
        public void onLine(@NonNull Context context, @NonNull String line, @NonNull Controller.Gateway gateway) {
            AppApi.deviceData().consumeLine(line, result -> {
                if (result.isOk() && result.data != null) {
                    gateway.onCacheFileReady(result.data);
                }
            });
        }
    }
}
