package com.vui.vaporwave.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlayStat(
    val trackId: Long,
    val playCount: Int,
    val lastPlayedAt: Long
)
