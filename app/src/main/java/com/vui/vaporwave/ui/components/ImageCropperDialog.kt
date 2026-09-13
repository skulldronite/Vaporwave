package com.vui.vaporwave.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A minimal square-crop dialog: pinch to zoom, drag to reposition, confirm to get back a square
 * Bitmap. No rotation or free aspect ratio -- album/track artwork is always square, so that's the
 * only case this needs to support.
 *
 * All internal geometry (scale, offsets) is tracked in real device pixels throughout, converting
 * to/from Dp only at the Compose layout boundary, so the on-screen transform and the final
 * pixel-region crop stay consistent with each other regardless of screen density.
 */
@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    onCropped: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        sourceBitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Drag to reposition, pinch to zoom",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                val bitmap = sourceBitmap
                val cropSizeDp = 300.dp
                val cropSizePx = with(density) { cropSizeDp.toPx() }

                // Starting scale/offset fit the bitmap to fully cover the crop square (like
                // ContentScale.Crop), centered -- from there the user's own pinch/drag adjusts on
                // top of this baseline. Hoisted to this level (not inside the crop Box below) so
                // the Crop button further down can read the current values too.
                val baseScale = remember(bitmap, cropSizePx) {
                    if (bitmap != null) max(cropSizePx / bitmap.width, cropSizePx / bitmap.height) else 1f
                }
                var scale by remember(bitmap) { mutableFloatStateOf(baseScale) }
                var offset by remember(bitmap) {
                    mutableStateOf(
                        if (bitmap != null) {
                            Offset(
                                (cropSizePx - bitmap.width * baseScale) / 2f,
                                (cropSizePx - bitmap.height * baseScale) / 2f
                            )
                        } else {
                            Offset.Zero
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .size(cropSizeDp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(
                                    with(density) { bitmap.width.toDp() },
                                    with(density) { bitmap.height.toDp() }
                                )
                                .pointerInput(bitmap) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(baseScale, baseScale * 6f)
                                        offset += pan
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    // graphicsLayer scales around the composable's own center by
                                    // default, but the crop math below (and the initial centering
                                    // above) is expressed in top-left terms -- pinning the pivot
                                    // to the top-left corner keeps both in agreement about what
                                    // "offset" means.
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    }
                }

                if (bitmap == null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Loading image...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.size(16.dp))
                    if (bitmap != null) {
                        Button(
                            onClick = { onCropped(cropBitmap(bitmap, scale, offset, cropSizePx)) }
                        ) { Text("Crop") }
                    }
                }
            }
        }
    }
}

/**
 * Maps the crop square's visible region (in the same top-left-origin, real-pixel space the
 * on-screen transform uses) back into the source bitmap's own pixel coordinates and cuts it out.
 */
private fun cropBitmap(source: Bitmap, scale: Float, offset: Offset, cropSizePx: Float): Bitmap {
    val srcX = (-offset.x / scale).roundToInt().coerceIn(0, source.width - 1)
    val srcY = (-offset.y / scale).roundToInt().coerceIn(0, source.height - 1)
    val srcSize = (cropSizePx / scale).roundToInt()
        .coerceAtMost(minOf(source.width - srcX, source.height - srcY))
        .coerceAtLeast(1)
    return Bitmap.createBitmap(source, srcX, srcY, srcSize, srcSize)
}
