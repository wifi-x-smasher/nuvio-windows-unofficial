package com.nuvio.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIColor

private val nuvioBackgroundColor = UIColor(red = 0.008, green = 0.016, blue = 0.016, alpha = 1.0)

fun MainViewController() = ComposeUIViewController {
    App()
}.apply {
    view.backgroundColor = nuvioBackgroundColor
}
