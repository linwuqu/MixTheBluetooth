package com.hc.mixthebluetooth.ui.cgm;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.mixthebluetooth.api.AppApi;

import java.util.Date;

public final class CgmProfile {
    private CgmProfile() {
    }

    @NonNull
    public static CgmController.ProfileSpec create() {
        return CgmController.ProfileSpec.builder("cgm")
                .action(CgmController.ActionSpec.postText("sync_time", "同步时间", () -> CgmCommands.LegacyCgm.syncTime(new Date())))
                .action(CgmController.ActionSpec.postText("read_cache", "读取缓存", CgmCommands.LegacyCgm::readCache))
                .action(CgmController.ActionSpec.postText("delete_cache", "删除缓存", CgmCommands.LegacyCgm::deleteCache))
                .widget(CgmWidgets.WidgetSpec.cgmResult("cgm_result")
                        .title("CGM mmol/L")
                        .order(0)
                        .build())
                .rawLineConsumer(new CgmRawLineConsumer())
                .build();
    }

    private static final class CgmRawLineConsumer implements CgmController.RawLineConsumer {
        @Override
        public void onLine(@NonNull Context context, @NonNull String line, @NonNull CgmController.Gateway gateway) {
            AppApi.cgmWorkflow().onDeviceLine(line, gateway::onCgmWorkflowResult);
        }
    }
}
