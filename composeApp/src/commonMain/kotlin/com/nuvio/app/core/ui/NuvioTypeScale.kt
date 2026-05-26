package com.nuvio.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class NuvioTypeScale(
    val labelXs: TextStyle,
    val labelSm: TextStyle,
    val bodySm: TextStyle,
    val bodyMd: TextStyle,
    val bodyLg: TextStyle,
    val titleSm: TextStyle,
    val titleMd: TextStyle,
    val titleLg: TextStyle,
    val displaySm: TextStyle,
    val displayMd: TextStyle,
)

internal val LocalNuvioTypeScale = staticCompositionLocalOf {
    NuvioTypeScale(
        labelXs = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
        labelSm = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
        bodySm = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
        bodyMd = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        bodyLg = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        titleSm = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
        titleMd = TextStyle(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        titleLg = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        displaySm = TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold),
        displayMd = TextStyle(fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold),
    )
}

val MaterialTheme.nuvioTypeScale: NuvioTypeScale
    @Composable
    get() = LocalNuvioTypeScale.current
