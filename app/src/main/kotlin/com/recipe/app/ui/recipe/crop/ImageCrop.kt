package com.recipe.app.ui.recipe.crop

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes a picked image (downsampled so it stays memory-friendly) and produces a fixed
 * 500x500 crop using the square region computed by [ImageCropMath].
 */
object ImageCrop {
    const val OUTPUT_SIZE = 500
    private const val MAX_DECODE_DIM = 2048

    fun decode(resolver: ContentResolver, uri: Uri, maxDim: Int = MAX_DECODE_DIM): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) {
                null
            } else {
                var sample = 1
                while (max(w, h) / sample > maxDim) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Extracts [rect] (already clamped to source bounds) and resizes it to a [outSize] square. */
    fun crop(source: Bitmap, rect: ImageCropMath.CropRect, outSize: Int = OUTPUT_SIZE): Bitmap {
        val sw = source.width
        val sh = source.height
        val left = rect.left.roundToInt().coerceIn(0, sw - 1)
        val top = rect.top.roundToInt().coerceIn(0, sh - 1)
        val side = rect.width.roundToInt().coerceIn(1, min(sw - left, sh - top))
        val region = Bitmap.createBitmap(source, left, top, side, side)
        val out = Bitmap.createScaledBitmap(region, outSize, outSize, true)
        if (region != out) region.recycle()
        return out
    }

    fun toJpegBytes(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }
}
