package com.nuvio.app.features.player.desktop.nativempv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.nuvio.app.desktop.DesktopRuntimeLog
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Canvas
import java.awt.event.HierarchyEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

@Composable
internal fun NativeMpvVideoSurface(
    modifier: Modifier,
    onWindowIdAvailable: (ULong) -> Unit,
) {
    Box(modifier = modifier.background(Color.Black)) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                JPanel(java.awt.BorderLayout()).apply {
                    background = java.awt.Color.BLACK
                    isOpaque = true
                    val canvas = Canvas().apply {
                        background = java.awt.Color.BLACK
                        ignoreRepaint = true
                    }
                    add(canvas, java.awt.BorderLayout.CENTER)
                    canvas.addHierarchyListener { event ->
                        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return@addHierarchyListener
                        if (!canvas.isShowing) return@addHierarchyListener
                        SwingUtilities.invokeLater {
                            runCatching {
                                Pointer.nativeValue(Native.getComponentPointer(canvas)).toULong()
                            }.onSuccess { hwnd ->
                                DesktopRuntimeLog.info("native-mpv surface hwnd=$hwnd")
                                onWindowIdAvailable(hwnd)
                            }.onFailure { error ->
                                DesktopRuntimeLog.error("native-mpv surface hwnd unavailable", error)
                            }
                        }
                    }
                }
            },
        )
    }
}
