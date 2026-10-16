package com.biosensor.migratedev.ui.patch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.biosensor.scanner.ScannerView
import com.biosensor.scanner.api.BarcodeFormat
import com.biosensor.scanner.api.ScanResult
import com.biosensor.scanner.api.ScannerConfig
import com.biosensor.scanner.api.ScannerError
import com.biosensor.scanner.api.ScannerListener

/**
 * 扫码子页:只管相机与回调,**不碰数据库**——扫到什么交给上层解析、落库。
 *
 * 叠在 CGM 页上的一层(不是替换):CGM 页留在组合里,页签选中态不因扫一次码被重置;
 * 返回键在这层接管,所以 CGM 页那个「返回 = 退出 App」的处理器要按扫码态让位。
 *
 * 相机生命周期照库的 README:建好即 `bindToLifecycle`,销毁时先置空 listener 再 `release()`——
 * 先断开是必要的,退页面后相机最后几帧回调不该再把用户拽回扫码页。
 */
@Composable
fun PatchScanScreen(
    message: String?,
    onCodeScanned: (text: String?, rawBytes: ByteArray?) -> Unit,
    /** [reason] 非空 = 没扫成,带回 CGM 页说明;主动跳过是 null。 */
    onClose: (reason: String?) -> Unit,
) {
    var accessReady by rememberSaveable { mutableStateOf(false) }
    CameraAccessGate(
        onAccessReady = { accessReady = true },
        // 扫码是可选项:权限被拒只回 CGM 页并说明原因,不像蓝牙那样退出 App
        onPermissionDenied = { onClose(PERMISSION_DENIED) },
    )
    // 权限没过就不建 ScannerView:相机一次都不该被打开
    if (!accessReady) return

    BackHandler(onBack = { onClose(null) })

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCodeScanned = rememberUpdatedState(onCodeScanned)
    val scanner = remember { mutableStateOf<ScannerView?>(null) }
    val scannerError = remember { mutableStateOf<String?>(null) }
    val hasFlash = remember { mutableStateOf(false) }
    val torchOn = remember { mutableStateOf(false) }

    // 回调可能在界面重组后到达,所以监听器只认这几个稳定的 State 引用
    val listener = remember {
        object : ScannerListener {
            override fun onScannerReady(hasFlashUnit: Boolean) {
                hasFlash.value = hasFlashUnit
            }

            // 自动确认(画面里只有一枚)与手指点选,进的是同一条通道:
            // 上层不必知道这枚码是怎么来的,解析、落库、报错都共用一套。
            override fun onScanResult(result: ScanResult) = deliver(result)

            override fun onCodeSelected(result: ScanResult) = deliver(result)

            override fun onScannerError(error: ScannerError) {
                scannerError.value = error.message
            }

            private fun deliver(result: ScanResult) {
                scannerError.value = null
                latestOnCodeScanned.value(result.text, result.rawBytes)
            }
        }
    }

    LaunchedEffect(lifecycleOwner, scanner.value) {
        scanner.value?.bindToLifecycle(lifecycleOwner)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                ScannerView(context).apply {
                    // 只认 Data Matrix:贴片码就这一种,画面里别的二维码不该插队。
                    // maxSymbolsPerFrame + tapToSelect 一起成全"一屏多枚、点哪枚绑哪枚";
                    // 只有一枚时库自己会自动确认,不用用户动手。
                    config = ScannerConfig(
                        formats = setOf(BarcodeFormat.DATA_MATRIX),
                        maxSymbolsPerFrame = MAX_CODES_ON_SCREEN,
                        tapToSelect = true,
                    )
                    scannerListener = listener
                    scanner.value = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view ->
                view.scannerListener = null
                view.release()
            }
        )

        // 取景框固定在这儿没意义了:库会给每枚识别到的码画框,那才是"看见了几枚、看见了哪枚"。
        // 再叠一个不动的方框只会让人以为码得套进去才算数。
        Column(modifier = Modifier.align(Alignment.TopStart).padding(20.dp)) {
            Text(
                "扫描贴片",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "对准贴片上的码。画面里有多枚时,点一下要绑的那枚;点别处是对焦",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        ScanFooter(
            complaint = message ?: scannerError.value,
            hasFlash = hasFlash.value,
            torchOn = torchOn.value,
            onToggleTorch = {
                val next = !torchOn.value
                torchOn.value = next
                scanner.value?.setTorch(next)
            },
            onClose = { onClose(null) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** 底部:一行原因(有才出现)+ 闪光灯/跳过。 */
@Composable
private fun ScanFooter(
    complaint: String?,
    hasFlash: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (complaint != null) {
            Text(
                text = complaint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasFlash) {
                OutlinedButton(onClick = onToggleTorch) {
                    Text(if (torchOn) "关闭闪光灯" else "打开闪光灯")
                }
            }
            Button(onClick = onClose) { Text("跳过") }
        }
    }
}

private const val PERMISSION_DENIED = "没有相机权限,无法扫码"

/** 一屏最多认几枚。六枚贴片样例同屏也够用,再多解码就白烧帧率了。 */
private const val MAX_CODES_ON_SCREEN = 6
