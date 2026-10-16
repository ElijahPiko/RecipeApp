package com.recipe.app.ui.recipe.crop

import kotlin.math.max
import kotlin.math.min

/**
 * Pure math for a fixed square (500x500) crop.
 *
 * The source image is first scaled with [coverScale] so it entirely fills the square viewport
 * (with overflow cropped), then zoomed by `scale` (>= 1) and panned by `offset` (in viewport px),
 * both around the centre of the viewport. Given those inputs we can compute the exact square
 * region of the source bitmap that is visible, so it can be extracted and resized to 500x500.
 *
 * Kept free of Android types so it can be unit-tested on the JVM.
 */
object ImageCropMath {

    data class CropRect(val left: Float, val top: Float, val width: Float, val height: Float)

    /** Uniform scale required so [source] covers the viewport (no letterboxing). */
    fun coverScale(srcWidth: Int, srcHeight: Int, viewportPx: Int): Float {
        val safeW = srcWidth.coerceAtLeast(1)
        val safeH = srcHeight.coerceAtLeast(1)
        val safeV = viewportPx.coerceAtLeast(1).toFloat()
        return max(safeV / safeW, safeV / safeH)
    }

    /** Clamps the pan offset so the image always covers the viewport (no gaps appear). */
    fun clampOffset(
        srcWidth: Int,
        srcHeight: Int,
        viewportPx: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Pair<Float, Float> {
        val c = viewportPx / 2f
        val cover = coverScale(srcWidth, srcHeight, viewportPx)
        val drawW = srcWidth * cover
        val drawH = srcHeight * cover
        val drawX = (viewportPx - drawW) / 2f
        val drawY = (viewportPx - drawH) / 2f

        // Top-left of the image on screen before the pan offset (zoom is around the centre).
        val baseX = c + scale * (drawX - c)
        val baseY = c + scale * (drawY - c)
        val minX = viewportPx - drawW * scale - baseX
        val maxX = -baseX
        val minY = viewportPx - drawH * scale - baseY
        val maxY = -baseY

        val clampedX = offsetX.coerceIn(min(minX, maxX), max(minX, maxX))
        val clampedY = offsetY.coerceIn(min(minY, maxY), max(minY, maxY))
        return clampedX to clampedY
    }

    /** The square source region (in source pixels) currently visible in the viewport. */
    fun cropRect(
        srcWidth: Int,
        srcHeight: Int,
        viewportPx: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): CropRect {
        val safeScale = scale.coerceAtLeast(1f)
        val c = viewportPx / 2f
        val cover = coverScale(srcWidth, srcHeight, viewportPx)
        val drawW = srcWidth * cover
        val drawH = srcHeight * cover
        val drawX = (viewportPx - drawW) / 2f
        val drawY = (viewportPx - drawH) / 2f

        // Inverse of screen = c + scale*(pre - c) + offset, taken at the viewport's top-left corner.
        val preX0 = c - (c + offsetX) / safeScale
        val preY0 = c - (c + offsetY) / safeScale
        val left = (preX0 - drawX) / cover
        val top = (preY0 - drawY) / cover
        val size = viewportPx / (cover * safeScale)
        return CropRect(left, top, size, size)
    }
}
