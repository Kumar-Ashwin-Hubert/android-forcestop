package dev.ashwin.forcestop.data

import androidx.compose.ui.graphics.ImageBitmap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val isSystem: Boolean,
)
