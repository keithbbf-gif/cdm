package com.cosmos.cdm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = CosmosCyan,
    onPrimary = CosmosBg,
    secondary = CosmosGreen,
    onSecondary = CosmosBg,
    background = CosmosBg,
    onBackground = CosmosInk,
    surface = CosmosPanel,
    onSurface = CosmosInk,
    surfaceVariant = CosmosInput,
    onSurfaceVariant = CosmosInkDim,
    error = CosmosRed,
    onError = CosmosHead,
    outline = CosmosPanelEdge,
)

@Composable
fun CdmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = CdmTypography,
        content = content,
    )
}
