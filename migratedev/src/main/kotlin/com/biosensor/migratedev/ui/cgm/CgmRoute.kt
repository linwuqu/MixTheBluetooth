package com.biosensor.migratedev.ui.cgm

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biosensor.migratedev.translation.cgm.CgmIntent
import com.biosensor.migratedev.translation.cgm.CgmTranslation
import com.biosensor.migratedev.translation.patch.PatchIntent
import com.biosensor.migratedev.translation.patch.PatchTranslation
import com.biosensor.migratedev.ui.patch.PatchScanScreen

/**
 * 业务转移:收集状态 + 回调转 intent。不持有任何业务数据。
 *
 * 同时拿两个 translation:CGM 与贴片是两个独立子域,这里只做并联读取——
 * 绑定列表作为参数传给 [CgmScreen],`CgmUiState` 一行不用改(架构 13 §D3/§4.5)。
 */
@Composable
fun CgmRoute(cgm: CgmTranslation, patch: PatchTranslation) {
    val state by cgm.uiState.collectAsStateWithLifecycle()
    val patchState by patch.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 这台设备一枚贴片都没绑过 → 进来就把扫码子页打开(架构 13 §D5)。
    // 开过一次 shouldScan 就变 false,用户跳过不会再被弹回来。
    LaunchedEffect(patchState.shouldScan) {
        if (patchState.shouldScan) patch.submit(PatchIntent.OpenScanner)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CgmScreen(
            state = state,
            bindings = patchState.bindings,
            patchMessage = patchState.message,
            onScanPatch = { patch.submit(PatchIntent.OpenScanner) },
            onRead = { cgm.submit(CgmIntent.ReadCache) },
            onForceStopRead = { cgm.submit(CgmIntent.ForceStopRead) },
            onUpload = { cgm.submit(CgmIntent.UploadDataset) },
            onSyncTime = { cgm.submit(CgmIntent.SyncTime) },
            onDelete = { cgm.submit(CgmIntent.DeleteCache) }
        )
        // 叠在 CGM 页上而不是替换它:CGM 页留在组合里,扫一次码不会把页签选中态清掉
        if (patchState.scanning) {
            PatchScanScreen(
                message = patchState.message,
                onCodeScanned = { text, rawBytes ->
                    patch.submit(PatchIntent.CodeScanned(text, rawBytes))
                },
                onClose = { reason -> patch.submit(PatchIntent.CloseScanner(reason)) }
            )
        }
    }

    // Cgm 页是流程终点:返回键 = 退出整个应用(连接页已移出导航栈,不可返回死页,见故障分析 12 §3.5)。
    // 扫码子页开着时让位给它——它自己接返回键,按一下关的是子页,不是 App。
    BackHandler(enabled = !patchState.scanning) {
        context.findActivity()?.finish()
    }
}

/** 从组合上下文链找回宿主 Activity(组合上下文常被 ContextThemeWrapper 包裹,需逐层解包)。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
