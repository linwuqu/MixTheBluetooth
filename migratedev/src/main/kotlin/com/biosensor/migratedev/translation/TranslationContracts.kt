package com.biosensor.migratedev.translation

import kotlinx.coroutines.flow.StateFlow

/**
 * UI 与工作流之间的薄适配合约：输入侧把意图送入工作流，输出侧向 UI 暴露状态。
 *
 * `intent -> event`
 * `state -> uiState`
 */
interface Translation<Intent, UiState> {
    val uiState: StateFlow<UiState>

    fun submit(intent: Intent)
}
