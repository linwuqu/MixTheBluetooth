package com.biosensor.migratedev.ui.connection

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * 蓝牙访问门:权限请求 → 系统蓝牙开关 → 就绪,三步线性流转。
 * 属于 Route 层业务(绑 Activity/Launcher),不需要也不应该 Preview。
 */
@Composable
fun BluetoothAccessGate(
    onAccessReady: () -> Unit,
    onPermissionDenied: () -> Unit,
    onBluetoothEnableCancelled: () -> Unit
) {
    val context = LocalContext.current
    val adapter = remember {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }
    val permissions = remember { requiredBluetoothPermissions() }

    var gate by rememberSaveable {
        mutableStateOf(
            if (context.hasPermissions(permissions)) {
                GateState.AwaitingSystemEnable
            } else {
                GateState.AwaitingPermission
            }
        )
    }

    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (permissions.all { result[it] == true }) {
            gate = GateState.AwaitingSystemEnable
        } else {
            Timber.tag("Connection.UI").w("蓝牙权限被拒绝,结束任务")
            onPermissionDenied()
        }
    }
    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || adapter?.isEnabled == true) {
            gate = GateState.Ready
        } else {
            onBluetoothEnableCancelled()
        }
    }

    // adapter?.isEnabled 也作为 key:用户在系统设置/下拉栏直接开启蓝牙时,同样能推进流程
    LaunchedEffect(gate, adapter?.isEnabled) {
        when (gate) {
            // 1. 权限未授予 → 弹系统权限请求;launch 不改变 gate,不会重复弹
            GateState.AwaitingPermission -> requestPermissions.launch(permissions)

            // 2. 权限齐了 → 检查系统蓝牙:无硬件直接退出;已开则就绪;未开则弹系统开启请求
            GateState.AwaitingSystemEnable -> when {
                adapter == null -> context.findActivity()?.finishAffinity()
                adapter.isEnabled -> gate = GateState.Ready
                else -> enableBluetooth.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )
            }

            // 3. 终态:只进入一次,通知上层
            GateState.Ready -> onAccessReady()
        }
    }
}

/** 权限门状态:只有三步,没有交叉布尔。 */
private enum class GateState {
    AwaitingPermission, AwaitingSystemEnable, Ready
}

internal fun requiredBluetoothPermissions(
    sdkInt: Int = Build.VERSION.SDK_INT
): Array<String> = if (sdkInt >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun Context.hasPermissions(
    permissions: Array<String>
): Boolean = permissions.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
