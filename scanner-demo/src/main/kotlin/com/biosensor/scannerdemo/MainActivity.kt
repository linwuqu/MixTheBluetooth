package com.biosensor.scannerdemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.biosensor.scanner.ScannerView
import com.biosensor.scanner.api.ScanResult
import com.biosensor.scanner.api.ScannerConfig
import com.biosensor.scanner.api.ScannerError
import com.biosensor.scanner.api.ScannerListener
import com.biosensor.scanner.api.ScannerProfile

class MainActivity : ComponentActivity(), ScannerListener {
    private lateinit var scannerView: ScannerView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var torchButton: Button
    private var torchEnabled = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startScanner()
        } else {
            statusText.text = "相机权限被拒绝，无法扫码"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        scannerView.scannerListener = this
        scannerView.config = ScannerConfig.forProfile(ScannerProfile.COMMON)
    }

    override fun onStart() {
        super.onStart()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScanner()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStop() {
        scannerView.stop()
        torchEnabled = false
        updateTorchButton()
        super.onStop()
    }

    override fun onDestroy() {
        scannerView.release()
        super.onDestroy()
    }

    override fun onScannerReady(hasFlash: Boolean) {
        statusText.text = "扫描中 · 支持 QR / Data Matrix / Code 128 / EAN"
        torchButton.isEnabled = hasFlash
        torchButton.alpha = if (hasFlash) 1f else 0.45f
    }

    override fun onScanResult(result: ScanResult) {
        val displayValue = result.text ?: result.rawBytes?.joinToString("") { "%02X".format(it) } ?: "<空内容>"
        resultText.text = buildString {
            appendLine("识别成功：${result.format}")
            appendLine("耗时：${result.decodeTimeMillis} ms")
            append("内容：$displayValue")
        }
        statusText.text = "已识别；继续对准可扫描下一个码"
    }

    override fun onScannerError(error: ScannerError) {
        statusText.text = "${error.code}: ${error.message}"
    }

    private fun startScanner() {
        statusText.text = "正在启动相机…"
        scannerView.bindToLifecycle(this)
    }

    private fun buildContentView(): View {
        scannerView = ScannerView(this)
        statusText = label(
            text = "等待相机权限…",
            textSize = 14f,
            background = 0xCC101418.toInt(),
        )
        resultText = label(
            text = "请将二维码或条形码放入框内\n点击画面可以对焦",
            textSize = 16f,
            background = 0xDD101418.toInt(),
        )
        torchButton = Button(this).apply {
            text = "打开闪光灯"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xAA253038.toInt())
            setOnClickListener {
                torchEnabled = !torchEnabled
                scannerView.setTorch(torchEnabled)
                updateTorchButton()
            }
        }

        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(scannerView, matchParent())
            addView(ScanOverlayView(this@MainActivity), matchParent())

            addView(
                statusText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP,
                ).apply { setMargins(dp(16), dp(20), dp(16), 0) },
            )

            val bottomPanel = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(16), dp(12), dp(16), dp(20))
                addView(
                    resultText,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    torchButton,
                    LinearLayout.LayoutParams(dp(180), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(12)
                    },
                )
            }
            addView(
                bottomPanel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ),
            )
        }
    }

    private fun label(text: String, textSize: Float, background: Int) = TextView(this).apply {
        this.text = text
        this.textSize = textSize
        setTextColor(Color.WHITE)
        setBackgroundColor(background)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun updateTorchButton() {
        torchButton.text = if (torchEnabled) "关闭闪光灯" else "打开闪光灯"
    }

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
