package com.vui.vaporwave.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.vui.vaporwave.data.tagging.AudioTagWriter
import com.vui.vaporwave.data.tagging.MetadataFields
import com.vui.vaporwave.model.ExtendedTrackMetadata
import com.vui.vaporwave.ui.MetadataEditTarget
import com.vui.vaporwave.ui.MetadataSaveState
import com.vui.vaporwave.ui.components.ImageCropperDialog
import java.io.ByteArrayOutputStream

/**
 * Metadata editor for a single track, or (via [MetadataEditTarget.Album]) the shared fields of
 * every track in an album at once. Saving actually rewrites the underlying file's tags (see
 * com.vui.vaporwave.data.tagging) -- MainActivity owns the write-permission consent flow (only an
 * Activity can launch that), surfaced here purely as [saveState].
 *
 * [extended] must already be resolved (genre/recording date/album artist) before this is shown --
 * MainActivity fetches it and only composes this screen once it's ready, so these fields are
 * always prefilled with the track's *current* values rather than blank placeholders. That matters
 * because every field here is a literal overwrite, not a diff: an untouched blank field would
 * silently wipe that tag on save.
 */
@Composable
fun MetadataEditorScreen(
    target: MetadataEditTarget,
    extended: ExtendedTrackMetadata,
    saveState: MetadataSaveState,
    onSave: (MetadataFields) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAlbumTarget = target is MetadataEditTarget.Album
    val headerTitle = if (isAlbumTarget) "Edit Album" else "Edit Track"
    val initialArtworkUri = when (target) {
        is MetadataEditTarget.Track -> target.track.artworkUri
        is MetadataEditTarget.Album -> target.artworkUri
    }
    val unsupportedFormat = (target as? MetadataEditTarget.Track)
        ?.track?.formatBadge
        ?.takeIf { it in AudioTagWriter.unsupportedFormats }

    var title by remember(target) {
        mutableStateOf((target as? MetadataEditTarget.Track)?.track?.title.orEmpty())
    }
    var artist by remember(target) {
        mutableStateOf(
            when (target) {
                is MetadataEditTarget.Track -> target.track.artist
                is MetadataEditTarget.Album -> target.artist
            }
        )
    }
    var album by remember(target) {
        mutableStateOf(
            when (target) {
                is MetadataEditTarget.Track -> target.track.album
                is MetadataEditTarget.Album -> target.name
            }
        )
    }
    var albumArtist by remember(target, extended) { mutableStateOf(extended.albumArtist ?: artist) }
    var genre by remember(target, extended) { mutableStateOf(extended.genre.orEmpty()) }
    var recordingDate by remember(target, extended) { mutableStateOf(extended.recordingDate.orEmpty()) }
    var trackNumber by remember(target) {
        mutableStateOf((target as? MetadataEditTarget.Track)?.track?.trackNumber?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var discNumber by remember(target) {
        mutableStateOf((target as? MetadataEditTarget.Track)?.track?.discNumber?.takeIf { it > 0 }?.toString().orEmpty())
    }
    val encodingType = (target as? MetadataEditTarget.Track)?.track?.technicalDetails

    var pickedImageUri by remember(target) { mutableStateOf<android.net.Uri?>(null) }
    var croppedArtwork by remember(target) { mutableStateOf<Bitmap?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pickedImageUri = uri
    }

    val isSaving = saveState is MetadataSaveState.Saving
    val canSave = unsupportedFormat == null && !isSaving

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, enabled = !isSaving) { Text("Close") }
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                enabled = canSave,
                onClick = {
                    val fields = MetadataFields(
                        title = title,
                        artist = artist,
                        album = album,
                        albumArtist = albumArtist,
                        genre = genre,
                        recordingDate = recordingDate,
                        trackNumber = trackNumber.toIntOrNull() ?: 0,
                        discNumber = discNumber.toIntOrNull() ?: 0,
                        artworkJpeg = croppedArtwork?.let { toJpegBytes(it) }
                    )
                    onSave(fields)
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        }

        if (unsupportedFormat != null) {
            Text(
                text = "Editing $unsupportedFormat tags isn't supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)
            )
        }
        if (saveState is MetadataSaveState.Error) {
            Text(
                text = saveState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(64.dp)
                            )
                            val bitmap = croppedArtwork
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (initialArtworkUri != null) {
                                val context = LocalContext.current
                                AsyncImage(
                                    model = remember(initialArtworkUri) {
                                        ImageRequest.Builder(context)
                                            .data(initialArtworkUri)
                                            .size(Size(400, 400))
                                            .build()
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        // Pencil badge, bottom-right of the art -- picks a new image, which then
                        // opens the cropper below once chosen.
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(36.dp)
                        ) {
                            IconButton(onClick = { imagePickerLauncher.launch("image/*") }, enabled = !isSaving) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Change artwork",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isAlbumTarget) {
                        LabeledField(label = "Title", value = title, onValueChange = { title = it }, enabled = !isSaving)
                    }
                    LabeledField(label = "Artist", value = artist, onValueChange = { artist = it }, enabled = !isSaving)
                    LabeledField(label = "Album", value = album, onValueChange = { album = it }, enabled = !isSaving)
                    LabeledField(label = "Album Artist", value = albumArtist, onValueChange = { albumArtist = it }, enabled = !isSaving)
                    LabeledField(label = "Genre", value = genre, onValueChange = { genre = it }, enabled = !isSaving)
                    LabeledField(label = "Recording Date", value = recordingDate, onValueChange = { recordingDate = it }, enabled = !isSaving)
                    if (!isAlbumTarget) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LabeledField(
                                label = "Track Number",
                                value = trackNumber,
                                onValueChange = { trackNumber = it.filter(Char::isDigit) },
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f)
                            )
                            LabeledField(
                                label = "Disk Number",
                                value = discNumber,
                                onValueChange = { discNumber = it.filter(Char::isDigit) },
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (encodingType != null) {
                            Column {
                                Text(
                                    text = "Encoding Type",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = encodingType,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Read-only -- changing this would mean re-encoding the audio itself, not just its tags.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickedImageUri != null) {
        ImageCropperDialog(
            imageUri = pickedImageUri!!,
            onCropped = { bitmap ->
                croppedArtwork = bitmap
                pickedImageUri = null
            },
            onDismiss = { pickedImageUri = null }
        )
    }
}

private fun toJpegBytes(bitmap: Bitmap): ByteArray =
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.toByteArray()
    }

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
    )
}
