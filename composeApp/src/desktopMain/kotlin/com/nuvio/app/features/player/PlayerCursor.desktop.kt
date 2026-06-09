package com.nuvio.app.features.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

private val blankPointerIcon: PointerIcon by lazy {
    val transparentImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
    val cursor = Toolkit.getDefaultToolkit()
        .createCustomCursor(transparentImage, Point(0, 0), "nuvioBlankCursor")
    PointerIcon(cursor)
}

actual fun Modifier.playerHiddenCursor(hidden: Boolean): Modifier =
    if (hidden) {
        pointerHoverIcon(blankPointerIcon, overrideDescendants = true)
    } else {
        this
    }
