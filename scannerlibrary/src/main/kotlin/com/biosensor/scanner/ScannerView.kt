package com.biosensor.scanner

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.biosensor.scanner.api.ScannerConfig
import com.biosensor.scanner.api.ScannerController
import com.biosensor.scanner.api.ScannerError
import com.biosensor.scanner.api.ScannerErrorCode
import com.biosensor.scanner.api.ScannerListener
import com.biosensor.scanner.internal.BarcodeEngine
import com.biosensor.scanner.internal.ResultStabilizer
import com.biosensor.scanner.internal.ZxingCppBarcodeEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), ScannerController {

    private val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "scanner-analyzer").apply { isDaemon = true }
    }
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val engine: BarcodeEngine = ZxingCppBarcodeEngine()
    private val stabilizer = ResultStabilizer()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var released = false
    private var decoderFailureReported = false

    override var config: ScannerConfig = ScannerConfig()
    override var scannerListener: ScannerListener? = null

    init {
        addView(previewView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        installTapToFocus()
    }

    override fun bindToLifecycle(lifecycleOwner: LifecycleOwner) {
        check(!released) { "ScannerView has already been released" }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                bind(providerFuture.get(), lifecycleOwner)
            } catch (error: Throwable) {
                notifyError(
                    ScannerErrorCode.CAMERA_BIND_FAILED,
                    "Unable to start the camera",
                    error,
                )
            }
        }, mainExecutor)
    }

    private fun bind(provider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner) {
        cameraProvider = provider
        unbindOwnedUseCases()
        stabilizer.reset()

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    config.targetResolution,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            )
            .build()

        val previewUseCase = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        preview = previewUseCase

        val analysisUseCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setResolutionSelector(resolutionSelector)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analyzerExecutor) { image ->
                    try {
                        val currentConfig = config
                        val decoded = engine.decode(image, currentConfig).firstOrNull()
                        decoderFailureReported = false
                        val confirmed = stabilizer.accept(
                            result = decoded,
                            requiredFrames = currentConfig.confirmationFrames,
                            duplicateCooldownMillis = currentConfig.duplicateCooldownMillis,
                        )
                        if (confirmed != null) {
                            mainExecutor.execute { scannerListener?.onScanResult(confirmed) }
                        }
                    } catch (error: Throwable) {
                        if (!decoderFailureReported) {
                            decoderFailureReported = true
                            notifyError(
                                ScannerErrorCode.DECODER_FAILED,
                                "Barcode decoder failed",
                                error,
                            )
                        }
                    } finally {
                        image.close()
                    }
                }
            }
        imageAnalysis = analysisUseCase

        if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            notifyError(ScannerErrorCode.NO_CAMERA, "No rear camera is available")
            return
        }

        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            previewUseCase,
            analysisUseCase,
        )
        scannerListener?.onScannerReady(camera?.cameraInfo?.hasFlashUnit() == true)
    }

    override fun stop() {
        unbindOwnedUseCases()
        stabilizer.reset()
    }

    override fun setTorch(enabled: Boolean) {
        val activeCamera = camera
        if (activeCamera?.cameraInfo?.hasFlashUnit() != true) {
            notifyError(ScannerErrorCode.TORCH_UNAVAILABLE, "This camera has no flash")
            return
        }
        activeCamera.cameraControl.enableTorch(enabled)
    }

    override fun release() {
        if (released) return
        stop()
        released = true
        analyzerExecutor.shutdown()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun unbindOwnedUseCases() {
        val owned = listOfNotNull(preview, imageAnalysis)
        if (owned.isNotEmpty()) cameraProvider?.unbind(*owned.toTypedArray())
        imageAnalysis?.clearAnalyzer()
        preview = null
        imageAnalysis = null
        camera = null
    }

    private fun installTapToFocus() {
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                performClick()
                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                camera?.cameraControl?.startFocusAndMetering(action)
            }
            true
        }
    }

    private fun notifyError(code: ScannerErrorCode, message: String, cause: Throwable? = null) {
        mainExecutor.execute {
            scannerListener?.onScannerError(ScannerError(code, message, cause))
        }
    }
}
