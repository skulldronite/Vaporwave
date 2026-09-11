package com.vui.vaporwave.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vui.vaporwave.model.AudioTrack
import com.vui.vaporwave.theme.SearchCardSurface
import com.vui.vaporwave.theme.Typography
import com.vui.vaporwave.theme.VaporwaveLightColorScheme
import com.vui.vaporwave.ui.components.TrackItem

/**
 * Full-screen search "card": a light surface that always reads as light regardless of which app
 * theme is active, deliberately -- it's meant to look like a distinct, lifted panel rather than
 * a continuation of the current background. Wrapped in its own light [MaterialTheme] override so
 * every child composable's text/icon colors (including [TrackItem], reused unmodified) come out
 * legible against it without touching any of them individually.
 *
 * Rendered as a top-level overlay (a sibling of the Scaffold, not nested inside its content
 * slot), so it owns its own status bar and IME insets rather than inheriting Scaffold's
 * innerPadding -- avoiding the double-reservation bugs that came from mixing the two last time.
 */
@Composable
fun SearchScreen(
    query: String,
    results: List<AudioTrack>,
    currentTrack: AudioTrack?,
    onQueryChange: (String) -> Unit,
    onTrackClick: (AudioTrack) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Opening the card should put the cursor straight in the field -- the user tapped search
    // because they intend to type immediately.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val cardColorScheme = remember {
        VaporwaveLightColorScheme.copy(
            background = SearchCardSurface,
            surface = SearchCardSurface,
            surfaceContainer = SearchCardSurface,
            surfaceContainerHigh = SearchCardSurface
        )
    }

    MaterialTheme(colorScheme = cardColorScheme, typography = Typography) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            // Only the top corners round off, since this covers the full screen and its bottom
            // edge sits flush with the device's own bottom edge -- rounding there would just cut
            // into content for no visual reason.
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Search songs, artists...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { keyboardController?.hide() }
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear query")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                }

                when {
                    query.isBlank() -> SearchPrompt("Search your library by song, artist, album or format")

                    results.isEmpty() -> SearchPrompt("No tracks match \"$query\"")

                    else -> {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp, start = 8.dp, end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(items = results, key = { it.id }) { track ->
                                TrackItem(
                                    track = track,
                                    isPlayingThisTrack = currentTrack?.id == track.id,
                                    onClick = {
                                        keyboardController?.hide()
                                        onTrackClick(track)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPrompt(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(48.dp))
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
