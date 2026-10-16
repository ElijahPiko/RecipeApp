package com.recipe.app.ui.recipe.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A fixed square (500x500) crop dialog. The source image fills the square viewport
 * (cover) and can be panned/zoomed; on confirm the visible square is cropped to 500x500.
 */
@Composable
fun CropImageDialog(
    source: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var viewportPx by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(onDismissRequest = onCancel) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                Text("裁剪图片 (500×500)", style = MaterialTheme.typography.titleMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(top = 12.dp)
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .onSizeChanged { viewportPx = it.width }
                        .pointerInput(source) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 8f)
                                val (cx, cy) = ImageCropMath.clampOffset(
                                    source.width,
                                    source.height,
                                    viewportPx,
                                    newScale,
                                    offset.x + pan.x,
                                    offset.y + pan.y
                                )
                                scale = newScale
                                offset = Offset(cx, cy)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (viewportPx > 0 && source.width > 0 && source.height > 0) {
                        Image(
                            bitmap = source.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    }
                    CropOverlay(Modifier.fillMaxSize())
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(
                        onClick = {
                            val rect = ImageCropMath.cropRect(
                                source.width, source.height, viewportPx, scale, offset.x, offset.y
                            )
                            onConfirm(ImageCrop.crop(source, rect))
                        },
                        enabled = viewportPx > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("确认") }
                }
            }
        }
    }
}

@Composable
private fun CropOverlay(modifier: Modifier) {
    val line = Color.White.copy(alpha = 0.8f)
    Box(modifier.border(1.dp, Color.White)) {
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp).background(line)
        )
        Box(
            Modifier.align(Alignment.Center).fillMaxHeight().width(1.dp).background(line)
        )
    }
}
