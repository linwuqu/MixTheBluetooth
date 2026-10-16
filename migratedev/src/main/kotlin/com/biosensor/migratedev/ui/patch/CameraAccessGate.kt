package com.biosensor.migratedev.ui.patch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * 相机访问门:权限请求 → 就绪,两步。
 *
 * 比蓝牙那道门(权限 → 系统开关 → 就绪)少一步:相机没有"系统开关"这种全局状态。
 *
 * **被拒时与蓝牙不同**:蓝牙是流程必需,拒了就 `finishAffinity` 收工;扫码是可选项,
 * 拒了只关掉扫码子页回 CGM 页,App 不退出。
 * 属于 Route 层业务(绑 Activity/Launcher),不需要也不应该 Preview。
 */
@Composable
fun CameraAccessGate(
    onAccessReady: () -> Unit,
    onPermissionDenied: () -> Unit,
) {
    val context = LocalContext.current
    var gate by rememberSaveable {
        mutableStateOf(
            if (context.hasCameraPermission()) GateState.Ready else GateState.AwaitingPermission
        )
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            gate = GateState.Ready
        } else {
            Timber.tag(SCAN_TAG).w("相机权限被拒绝,关闭扫码子页")
            onPermissionDenied()
        }
    }

    LaunchedEffect(gate) {
        when (gate) {
            // launch 不改变 gate,不会重复弹;用户在系统弹窗里点了拒绝才走回调
            GateState.AwaitingPermission -> requestPermission.launch(Manifest.permission.CAMERA)

            // 终态:只进入一次,通知上层可以建相机了
            GateState.Ready -> onAccessReady()
        }
    }
}

/** 权限门状态:只有两步,没有交叉布尔。 */
private enum class GateState {
    AwaitingPermission, Ready
}

internal const val SCAN_TAG = "Patch.Scan"

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
