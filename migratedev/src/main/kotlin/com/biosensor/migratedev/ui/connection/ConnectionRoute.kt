package com.biosensor.migratedev.ui.connection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biosensor.migratedev.translation.connection.ConnectionIntent
import com.biosensor.migratedev.translation.connection.ConnectionPhase
import com.biosensor.migratedev.translation.connection.ConnectionTranslation

@Composable
fun ConnectionRoute(
    translation: ConnectionTranslation, onLogoutReady: suspend () -> Unit
) {
    val state by translation.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()

    if (state.phase == ConnectionPhase.AwaitingBluetoothAccess) {
        BluetoothAccessGate(onAccessReady = {
            translation.submit(
                ConnectionIntent.BluetoothAccessGranted
            )
        }, onPermissionDenied = {
            activity?.finishAffinity()
        }, onBluetoothEnableCancelled = {
            // 保持 Awaiting；不扫描，也不二次弹系统请求。
        })
    }

    DisposableEffect(lifecycleOwner, translation) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> translation.submit(
                    ConnectionIntent.BecameVisible
                )

                Lifecycle.Event.ON_STOP -> translation.submit(
                    ConnectionIntent.BecameHidden
                )

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == ConnectionPhase.LogoutReady) {
            onLogoutReady()
        }
    }

    ConnectionScreen(state = state, onRefresh = {
        translation.submit(ConnectionIntent.Refresh)
    }, onDeviceSelected = {
        translation.submit(
            ConnectionIntent.SelectDevice(it)
        )
    }, onLogout = {
        translation.submit(ConnectionIntent.Logout)
    })
}
