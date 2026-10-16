package com.biosensor.scanner.internal

import com.biosensor.scanner.api.ScanBounds

/**
 * 像素尺寸。故意不用 `android.util.Size`:这一层是纯算术,要能在 JVM 单测里跑。
 */
internal data class SizePx(val width: Int, val height: Int)

/**
 * 轴对齐矩形。坐标系含义由调用处决定(画面缓冲 / 预览缓冲 / 视图),三个都出现过。
 */
internal data class BoxPx(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** [slop] 是手指的宽容量:点在框边上差几个像素也该算点中。 */
    fun contains(x: Float, y: Float, slop: Float = 0f): Boolean =
        x >= left - slop && x <= right + slop && y >= top - slop && y <= bottom + slop
}

/**
 * 把解码结果里的码框,一步步算到屏幕上那个框。
 *
 * 三段坐标,一步都不能省:
 * 1. **分析缓冲**(引擎解出码的地方)→ 2. **预览缓冲**(用户实际看到的画面)→ 3. **视图**。
 * 1 到 2 通常只差一个缩放,但**不能假设它等于 1**:`Preview` 与分析流各挑各的分辨率,
 * 只是恰好用了同一个 `ResolutionSelector` 才一般相同。所以 2 由 `Preview.getResolutionInfo()` 实测,
 * 而不是拿 1 顶上。
 *
 * 2 到 3 是 [androidx.camera.view.PreviewView.ScaleType.FILL_CENTER]:等比放大到铺满,
 * 长出来的那一边居中裁掉。`PreviewView` 的 COMPATIBLE 模式就是这么摆预览的,框得跟着它走。
 */
internal object CoordinateMapper {

    /**
     * 旋转 90°/270° 时,扶正后的宽高要对调。
     *
     * 这里是**整套映射里唯一的假设**:zxing-cpp 收到的 `rotationDegrees` 由它自己用来扶正,
     * 于是吐回来的 `position` 四角也落在扶正后的坐标系里——所以映射全程不用再转一次,
     * 只要把宽高对调。真机上要先核对框与码是否贴合;若整体错位,先怀疑这一条。
     */
    fun uprightSize(width: Int, height: Int, rotationDegrees: Int): SizePx {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        return if (normalized == 90 || normalized == 270) {
            SizePx(height, width)
        } else {
            SizePx(width, height)
        }
    }

    /** 码可能是斜着摆的,框取四角的最小外接矩形。 */
    fun boundingBox(bounds: ScanBounds): BoxPx {
        val xs = listOf(
            bounds.topLeft.x, bounds.topRight.x, bounds.bottomRight.x, bounds.bottomLeft.x,
        )
        val ys = listOf(
            bounds.topLeft.y, bounds.topRight.y, bounds.bottomRight.y, bounds.bottomLeft.y,
        )
        return BoxPx(
            left = xs.min().toFloat(),
            top = ys.min().toFloat(),
            right = xs.max().toFloat(),
            bottom = ys.max().toFloat(),
        )
    }

    /** [source] 与 [view] 比例相同则纯缩放,不同则按 FILL_CENTER 居中裁切。 */
    fun mapToView(rect: BoxPx, source: SizePx, view: SizePx): BoxPx {
        if (source.width <= 0 || source.height <= 0 || view.width <= 0 || view.height <= 0) {
            return BoxPx(0f, 0f, 0f, 0f)
        }
        val scale = maxOf(
            view.width.toFloat() / source.width,
            view.height.toFloat() / source.height,
        )
        val dx = (view.width - source.width * scale) / 2f
        val dy = (view.height - source.height * scale) / 2f
        return BoxPx(
            left = rect.left * scale + dx,
            top = rect.top * scale + dy,
            right = rect.right * scale + dx,
            bottom = rect.bottom * scale + dy,
        )
    }
}
