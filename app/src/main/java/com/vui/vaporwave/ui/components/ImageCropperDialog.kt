package com.vui.vaporwave.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 6f

/**
 * Full-screen square photo cropper for track/album artwork -- pan-and-pinch-zoom gesture
 * handling and crop math ported from Viora's circular avatar cropper (a sibling project's
 * profile-picture picker), just with a square viewport instead of a circular one, since artwork
 * here is always square rather than a round profile picture. On confirm, the square currently
 * inside the viewport is cut from [imageUri]'s bitmap at native resolution.
 */
@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    onCropped: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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
        val source = sourceBitmap
        if (source == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading image...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Dialog
        }

        val image = remember(source) { source.asImageBitmap() }
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()

        var zoom by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        // The crop window's pixel size, captured once it's laid out -- also feeds cropSquare().
        var cropPx by remember { mutableFloatStateOf(0f) }

        fun clamp() {
            if (cropPx == 0f) return
            val cover = cropPx / min(srcW, srcH)
            val shownW = srcW * cover * zoom
            val shownH = srcH * cover * zoom
            val maxX = max(0f, (shownW - cropPx) / 2f)
            val maxY = max(0f, (shownH - cropPx) / 2f)
            offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Move and scale",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
            )
            Text(
                text = "Drag to reposition · pinch to zoom",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints {
                    val density = LocalDensity.current
                    val sideDp = maxWidth.coerceAtMost(maxHeight)
                    val sidePx = with(density) { sideDp.toPx() }
                    LaunchedEffect(sidePx) {
                        cropPx = sidePx
                        clamp()
                    }

                    val cover = sidePx / min(srcW, srcH)
                    val imgWDp = with(density) { (srcW * cover).toDp() }
                    val imgHDp = with(density) { (srcH * cover).toDp() }

                    Box(
                        modifier = Modifier
                            .size(sideDp)
                            .clipToBounds()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                                    offset += pan
                                    clamp()
                                }
                            },
                    ) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            modifier = Modifier
                                // requiredSize: the image is allowed to be larger than the crop
                                // window (the Box would otherwise clamp it and the pan clamp
                                // would let empty space into the frame).
                                .requiredSize(imgWDp, imgHDp)
                                .align(Alignment.Center)
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = offset.x
                                    translationY = offset.y
                                },
                        )
                    }
                    // A plain border stands in for the circular cropper's ring. No dimmed
                    // outside-the-shape overlay is needed here (unlike the circular version, where
                    // a square bounding box extends past the visible circle) -- the crop window
                    // is itself the whole square that gets kept.
                    Box(
                        modifier = Modifier
                            .size(sideDp)
                            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp)),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Cancel") }
                Button(
                    onClick = { onCropped(cropSquare(source, cropPx, zoom, offset)) },
                    enabled = cropPx > 0f,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Use photo") }
            }
        }
    }
}

/**
 * Cut the square crop window out of [src] at native pixels -- maps the on-screen viewport back
 * through the pinch/pan transform (pivot at the viewport's own centre) into source-bitmap pixel
 * coordinates, same math as Viora's cropSquare (there, that square then bounds a circular avatar;
 * here, it's kept as-is since track/album artwork is square already).
 */
private fun cropSquare(src: Bitmap, cropPx: Float, zoom: Float, offset: Offset): Bitmap {
    if (cropPx <= 0f) return src
    val srcW = src.width.toFloat()
    val srcH = src.height.toFloat()
    val cover = cropPx / min(srcW, srcH)
    val total = zoom * cover

    // Image top-left in view px (centre-aligned box of size srcW*cover), before graphicsLayer.
    val imgLeft = (cropPx - srcW * cover) / 2f
    val imgTop = (cropPx - srcH * cover) / 2f
    val c = cropPx / 2f

    // View (0,0) mapped back through graphicsLayer (pivot = centre), then into source px.
    val preX = c + (0f - c - offset.x) / zoom
    val preY = c + (0f - c - offset.y) / zoom
    val sideSrc = cropPx / total

    val maxL = (srcW - sideSrc).coerceAtLeast(0f)
    val maxT = (srcH - sideSrc).coerceAtLeast(0f)
    val left = ((preX - imgLeft) / cover).coerceIn(0f, maxL).roundToInt()
    val top = ((preY - imgTop) / cover).coerceIn(0f, maxT).roundToInt()
    val side = sideSrc.roundToInt()
        .coerceAtMost(min(src.width - left, src.height - top))
        .coerceAtLeast(1)

    return runCatching { Bitmap.createBitmap(src, left, top, side, side) }.getOrDefault(src)
}
