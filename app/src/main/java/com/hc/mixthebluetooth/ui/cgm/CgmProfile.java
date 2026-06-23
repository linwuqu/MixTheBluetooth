package com.hc.mixthebluetooth.ui.cgm;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hc.bluetoothlibrary.DeviceModule;
import com.hc.mixthebluetooth.api.AppApi;
import com.hc.mixthebluetooth.api.cgm.CgmWorkflow;
import com.hc.mixthebluetooth.driver.implementation.log.ApiTraceLogger;

import java.util.Date;

public final class CgmProfile {
    private static final String OWNER = "CgmProfile";
    private static final String API_BT_SEND = "BT_SEND";

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
                .widget(CgmWidgets.WidgetSpec.pointList("cgm_point_list")
                        .title("血糖数据明细")
                        .order(1)
                        .build())
                .rawLineConsumer(new CgmRawLineConsumer())
                .build();
    }

    private static final class CgmRawLineConsumer implements CgmController.RawLineConsumer {
        @Override
        public void onLine(@NonNull Context context,
                           @NonNull DeviceModule module,
                           @NonNull String text,
                           @NonNull CgmController.Gateway gateway) {
            CgmWorkflow.Update update = AppApi.cgmWorkflow().onDeviceText(text, result -> {
                gateway.onCgmWorkflowResult(result);
                if (result.isOk()) {
                    // 读取缓存成功并上传成功后，自动清设备缓存
                    postWorkflowText(gateway, module, "delete_cache", CgmCommands.LegacyCgm.deleteCache());
                    gateway.onCgmWorkflowUpdate(AppApi.cgmWorkflow().onDeleteCacheSent());
                }
            });
            if (update.commandText != null) {
                // 缺失重传
                postWorkflowText(gateway, module, "read_cache_retry", update.commandText);
            }
            gateway.onCgmWorkflowUpdate(update);
        }

        @Override
        public void onActionPosted(@NonNull Context context,
                                   @NonNull DeviceModule module,
                                   @NonNull CgmController.ActionSpec action,
                                   @NonNull String payload,
                                   @NonNull CgmController.Gateway gateway) {
            if ("read_cache".equals(action.id)) {
                gateway.onCgmWorkflowUpdate(AppApi.cgmWorkflow().onReadCacheSent());
            } else if ("delete_cache".equals(action.id)) {
                gateway.onCgmWorkflowUpdate(AppApi.cgmWorkflow().onDeleteCacheSent());
            }
        }

        private void postWorkflowText(@NonNull CgmController.Gateway gateway,
                                      @NonNull DeviceModule module,
                                      @NonNull String id,
                                      @NonNull String payload) {
            ApiTraceLogger.text(OWNER, API_BT_SEND, "command",
                    "id=" + id + "\npayload=" + payload);
            gateway.postText(module, payload);
        }
    }
}
