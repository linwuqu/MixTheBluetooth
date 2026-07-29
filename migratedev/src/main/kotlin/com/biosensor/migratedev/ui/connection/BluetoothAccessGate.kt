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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import timber.log.Timber

@Composable
fun BluetoothAccessGate(
    onAccessReady: () -> Unit,
    onPermissionDenied: () -> Unit,
    onBluetoothEnableCancelled: () -> Unit
) {
    val context = LocalContext.current
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    val permissions = requiredBluetoothPermissions()
    var permissionRequestStarted by rememberSaveable { mutableStateOf(false) }
    var enableRequestStarted by rememberSaveable { mutableStateOf(false) }
    var accessReported by rememberSaveable { mutableStateOf(false) }
    var permissionsGranted by rememberSaveable {
        mutableStateOf(context.hasPermissions(permissions))
    }

    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || adapter?.isEnabled == true) {
            if (!accessReported) {
                accessReported = true
                onAccessReady()
            }
        } else {
            onBluetoothEnableCancelled()
        }
    }
    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (permissions.all { result[it] == true }) {
            permissionsGranted = true
        } else {
            Timber.tag("Connection.UI").w(
                "Bluetooth permission denied; closing task"
            )
            onPermissionDenied()
        }
    }

    LaunchedEffect(permissionsGranted, adapter?.isEnabled) {
        when {
            !permissionsGranted && !permissionRequestStarted -> {
                permissionRequestStarted = true
                requestPermissions.launch(permissions)
            }

            permissionsGranted && adapter == null -> {
                context.findActivity()?.finishAffinity()
            }

            permissionsGranted && adapter?.isEnabled == true && !accessReported -> {
                accessReported = true
                onAccessReady()
            }

            permissionsGranted && !enableRequestStarted -> {
                enableRequestStarted = true
                enableBluetooth.launch(
                    Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE
                    )
                )
            }
        }
    }
}

internal fun requiredBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
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
