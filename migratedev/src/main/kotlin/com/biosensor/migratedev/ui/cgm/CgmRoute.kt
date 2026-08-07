package com.biosensor.migratedev.ui.cgm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biosensor.migratedev.translation.cgm.CgmIntent
import com.biosensor.migratedev.translation.cgm.CgmTranslation

/** 业务转移:收集状态 + 回调转 intent。不持有任何业务数据。 */
@Composable
fun CgmRoute(translation: CgmTranslation) {
    val state by translation.uiState.collectAsStateWithLifecycle()
    CgmScreen(
        state = state,
        onRead = { translation.submit(CgmIntent.ReadCache) },
        onSyncTime = { translation.submit(CgmIntent.SyncTime) },
        onDelete = { translation.submit(CgmIntent.DeleteCache) }
    )
}
