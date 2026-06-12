package com.nuvio.app.features.downloads

import java.awt.Color
import java.awt.Graphics2D
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

internal actual object DownloadsLiveStatusPlatform {
    private val notifiedTerminalIds = mutableSetOf<String>()
    private var trayIcon: TrayIcon? = null

    actual fun onItemsChanged(items: List<DownloadItem>) {
        if (!SystemTray.isSupported()) return

        val currentIds = items.mapTo(mutableSetOf()) { it.id }
        notifiedTerminalIds.retainAll(currentIds)

        items.forEach { item ->
            when (item.status) {
                DownloadStatus.Completed -> notifyTerminalState(
                    item = item,
                    message = "Download completed",
                    messageType = TrayIcon.MessageType.INFO,
                )

                DownloadStatus.Failed -> notifyTerminalState(
                    item = item,
                    message = item.errorMessage?.takeIf { it.isNotBlank() } ?: "Download failed",
                    messageType = TrayIcon.MessageType.ERROR,
                )

                DownloadStatus.Queued,
                DownloadStatus.Downloading,
                DownloadStatus.Paused,
                -> notifiedTerminalIds.remove(item.id)
            }
        }
    }

    private fun notifyTerminalState(
        item: DownloadItem,
        message: String,
        messageType: TrayIcon.MessageType,
    ) {
        if (!notifiedTerminalIds.add(item.id)) return
        val icon = trayIcon ?: createTrayIcon().also { trayIcon = it } ?: return
        runCatching {
            icon.displayMessage("Nuvio", "${item.title}\n$message", messageType)
        }
    }

    private fun createTrayIcon(): TrayIcon? =
        runCatching {
            val icon = TrayIcon(createNotificationImage(), "Nuvio").apply {
                isImageAutoSize = true
            }
            SystemTray.getSystemTray().add(icon)
            icon
        }.getOrNull()

    private fun createNotificationImage(): BufferedImage {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.paintNuvioGlyph()
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun Graphics2D.paintNuvioGlyph() {
        color = Color(18, 18, 24)
        fillRect(0, 0, 16, 16)
        color = Color(139, 92, 246)
        fillRoundRect(3, 2, 10, 12, 3, 3)
        color = Color(0, 210, 255)
        fillPolygon(intArrayOf(6, 6, 11), intArrayOf(5, 11, 8), 3)
    }
}
