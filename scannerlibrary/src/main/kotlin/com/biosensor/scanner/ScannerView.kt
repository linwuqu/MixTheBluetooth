package com.biosensor.scanner

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import com.biosensor.scanner.api.ScanResult
import com.biosensor.scanner.internal.BarcodeEngine
import com.biosensor.scanner.internal.BoxPx
import com.biosensor.scanner.internal.CoordinateMapper
import com.biosensor.scanner.internal.MultiCodeTracker
import com.biosensor.scanner.internal.ResultKey
import com.biosensor.scanner.internal.ResultStabilizer
import com.biosensor.scanner.internal.ScanBoxOverlayView
import com.biosensor.scanner.internal.SizePx
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
    private val overlayView = ScanBoxOverlayView(context)
    private val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "scanner-analyzer").apply { isDaemon = true }
    }
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val engine: BarcodeEngine = ZxingCppBarcodeEngine()
    private val stabilizer = ResultStabilizer()
    private val multiCodeTracker = MultiCodeTracker()

    /** 当前屏上的框,主线程独占。点选时按它做命中判定。 */
    private var boxesOnScreen: List<Pair<ResultKey, BoxPx>> = emptyList()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private var released = false
    private var decoderFailureReported = false

    override var config: ScannerConfig = ScannerConfig()
        set(value) {
            field = value
            if (!value.tapToSelect) clearBoxes()
        }
    override var scannerListener: ScannerListener? = null

    init {
        addView(previewView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(overlayView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        installTouchHandling()
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
        multiCodeTracker.reset()
        clearBoxes()

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
                        val decoded = engine.decode(image, currentConfig)
                        decoderFailureReported = false
                        if (currentConfig.tapToSelect) {
                            analyzeForTapToSelect(image, decoded, currentConfig)
                        } else {
                            val confirmed = stabilizer.accept(
                                result = decoded.firstOrNull(),
                                requiredFrames = currentConfig.confirmationFrames,
                                duplicateCooldownMillis = currentConfig.duplicateCooldownMillis,
                            )
                            if (confirmed != null) {
                                mainExecutor.execute { scannerListener?.onScanResult(confirmed) }
                            }
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

    /**
     * 点选模式的每帧处理:攒够帧数的码上框,孤单的一枚自动确认,其余的等手指。
     *
     * 跑在分析线程上,只把"算好的一帧快照"丢给主线程。投递是单线程 FIFO,所以主线程上
     * 晚到的帧不会盖掉早到的——顺序天然是对的。
     */
    private fun analyzeForTapToSelect(
        image: ImageProxy,
        decoded: List<ScanResult>,
        currentConfig: ScannerConfig,
    ) {
        val crop = image.cropRect
        val frameSize = CoordinateMapper.uprightSize(
            crop.width(),
            crop.height(),
            image.imageInfo.rotationDegrees,
        )
        val outcome = multiCodeTracker.accept(
            frame = decoded,
            confirmationFrames = currentConfig.confirmationFrames,
            duplicateCooldownMillis = currentConfig.duplicateCooldownMillis,
        )
        val autoConfirmed = outcome.autoConfirmed
        if (autoConfirmed != null) {
            mainExecutor.execute { scannerListener?.onScanResult(autoConfirmed) }
        }
        val boxes = outcome.boxes.map { box ->
            box.key to CoordinateMapper.boundingBox(box.result.bounds)
        }
        mainExecutor.execute { renderBoxes(boxes, frameSize) }
    }

    /** 分析缓冲坐标 → 预览缓冲坐标 → 视图坐标。两跳,不能并成一跳(见 [CoordinateMapper])。 */
    private fun renderBoxes(boxes: List<Pair<ResultKey, BoxPx>>, frameSize: SizePx) {
        if (released) return
        val viewSize = SizePx(width, height)
        if (viewSize.width <= 0 || viewSize.height <= 0) return
        val previewSize = previewSizeOr(frameSize)
        boxesOnScreen = boxes.map { (key, box) ->
            key to CoordinateMapper.mapToView(
                CoordinateMapper.mapToView(box, frameSize, previewSize),
                previewSize,
                viewSize,
            )
        }
        overlayView.setBoxes(boxesOnScreen.map { it.second })
    }

    /**
     * 预览流的扶正尺寸。拿不到(还没绑上)就退回分析流尺寸——此时画面还没有预览可对,
     * 差一帧也无所谓,绑定完成的下一帧就正了。
     */
    private fun previewSizeOr(fallback: SizePx): SizePx {
        val info = preview?.resolutionInfo ?: return fallback
        return CoordinateMapper.uprightSize(
            info.resolution.width,
            info.resolution.height,
            info.rotationDegrees,
        )
    }

    private fun clearBoxes() {
        mainExecutor.execute {
            boxesOnScreen = emptyList()
            overlayView.setBoxes(emptyList())
        }
    }

    override fun stop() {
        unbindOwnedUseCases()
        stabilizer.reset()
        multiCodeTracker.reset()
        clearBoxes()
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

    private fun installTouchHandling() {
        // 覆盖层在预览之上,触摸全落到这里:先看点没点到框,没点中才当对焦。
        overlayView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                performClick()
                if (!selectBoxAt(event.x, event.y)) focusAt(event.x, event.y)
            }
            true
        }
    }

    private fun selectBoxAt(x: Float, y: Float): Boolean {
        val slop = 12f * resources.displayMetrics.density
        // 后画的在上层,命中判定从后往前找,跟肉眼看到的一致。
        val hit = boxesOnScreen.lastOrNull { (_, box) -> box.contains(x, y, slop) } ?: return false
        val result = multiCodeTracker.select(hit.first) ?: return false
        scannerListener?.onCodeSelected(result)
        return true
    }

    private fun focusAt(x: Float, y: Float) {
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    private fun notifyError(code: ScannerErrorCode, message: String, cause: Throwable? = null) {
        mainExecutor.execute {
            scannerListener?.onScannerError(ScannerError(code, message, cause))
        }
    }
}
