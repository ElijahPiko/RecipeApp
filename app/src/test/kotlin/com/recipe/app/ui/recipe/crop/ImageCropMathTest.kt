package com.recipe.app.ui.recipe.crop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCropMathTest {

    private val viewport = 500

    @Test
    fun `cover scale fills the viewport`() {
        // Tall image: width defines the cover scale.
        assertEquals(0.5f, ImageCropMath.coverScale(1000, 2000, viewport), 0.0001f)
        // Wide image: height defines the cover scale.
        assertEquals(0.5f, ImageCropMath.coverScale(2000, 1000, viewport), 0.0001f)
        // Smaller than viewport -> scale up to cover.
        assertTrue(ImageCropMath.coverScale(200, 100, viewport) > 1f)
    }

    @Test
    fun `at scale 1 a square image cannot pan`() {
        val (ox, oy) = ImageCropMath.clampOffset(1000, 1000, viewport, 1f, 999f, -999f)
        assertEquals(0f, ox, 0.0001f)
        assertEquals(0f, oy, 0.0001f)
    }

    @Test
    fun `tall image allows vertical pan only at scale 1`() {
        val (ox, oy) = ImageCropMath.clampOffset(1000, 2000, viewport, 1f, 500f, 500f)
        assertEquals(0f, ox, 0.0001f)
        assertTrue(oy >= 0f)
        assertTrue(oy <= 250f) // (drawH - viewport)/2 = (1000-500)/2
    }

    @Test
    fun `crop rect at scale 1 shows full source for a square image`() {
        val r = ImageCropMath.cropRect(1000, 1000, viewport, 1f, 0f, 0f)
        assertEquals(0f, r.left, 0.0001f)
        assertEquals(0f, r.top, 0.0001f)
        assertEquals(1000f, r.width, 0.0001f)
        assertEquals(1000f, r.height, 0.0001f)
    }

    @Test
    fun `crop rect for a wide image is centred horizontally`() {
        val r = ImageCropMath.cropRect(2000, 1000, viewport, 1f, 0f, 0f)
        assertEquals(500f, r.left, 0.0001f)
        assertEquals(0f, r.top, 0.0001f)
        assertEquals(1000f, r.width, 0.0001f)
        assertEquals(1000f, r.height, 0.0001f)
    }

    @Test
    fun `zooming in crops the centre square`() {
        val r = ImageCropMath.cropRect(1000, 1000, viewport, 2f, 0f, 0f)
        assertEquals(250f, r.left, 0.0001f)
        assertEquals(250f, r.top, 0.0001f)
        assertEquals(500f, r.width, 0.0001f)
        assertEquals(500f, r.height, 0.0001f)
    }

    @Test
    fun `crop rect stays within source bounds after a large offset`() {
        val (ox, oy) = ImageCropMath.clampOffset(1000, 2000, viewport, 1.5f, 5000f, 5000f)
        val r = ImageCropMath.cropRect(1000, 2000, viewport, 1.5f, ox, oy)
        assertTrue(r.left >= 0f)
        assertTrue(r.top >= 0f)
        assertTrue(r.width <= 1000f + 0.001f)
        assertTrue(r.top + r.height <= 2000f + 0.001f)
    }
}
