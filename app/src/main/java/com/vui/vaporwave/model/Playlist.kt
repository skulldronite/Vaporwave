package com.vui.vaporwave.model

import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<Long> = emptyList()
)
