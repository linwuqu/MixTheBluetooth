package com.biosensor.migratedev.ui.cgm

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biosensor.migratedev.translation.cgm.CgmIntent
import com.biosensor.migratedev.translation.cgm.CgmTranslation

/** 业务转移:收集状态 + 回调转 intent。不持有任何业务数据。 */
@Composable
fun CgmRoute(translation: CgmTranslation) {
    val state by translation.uiState.collectAsStateWithLifecycle()
    // Cgm 页是流程终点:返回键 = 退出整个应用(连接页已移出导航栈,不可返回死页,见故障分析 12 §3.5)
    val context = LocalContext.current
    BackHandler {
        context.findActivity()?.finish()
    }
    CgmScreen(
        state = state,
        onRead = { translation.submit(CgmIntent.ReadCache) },
        onSyncTime = { translation.submit(CgmIntent.SyncTime) },
        onDelete = { translation.submit(CgmIntent.DeleteCache) }
    )
}

/** 从组合上下文链找回宿主 Activity(组合上下文常被 ContextThemeWrapper 包裹,需逐层解包)。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
