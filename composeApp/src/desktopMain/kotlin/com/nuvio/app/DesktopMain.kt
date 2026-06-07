package com.nuvio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.zIndex
import com.nuvio.app.desktop.DesktopPlayerRegistry
import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.desktop.DesktopSystemTray
import com.nuvio.app.desktop.DesktopWindowChrome
import com.nuvio.app.desktop.DiscordRichPresence
import com.nuvio.app.core.diagnostics.AppDiagnostics
import com.nuvio.app.core.deeplink.DesktopDeepLinkBridge
import com.nuvio.app.features.commandpalette.CommandPaletteController
import com.nuvio.app.features.player.PlayerKeyboardShortcut
import com.nuvio.app.features.player.PlayerKeyboardShortcutBridge
import java.awt.Color
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window as AwtWindow
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_mark
import org.jetbrains.compose.resources.painterResource

private const val DesktopDragMinVisiblePx = 120

fun main(args: Array<String>) {
    System.setProperty("compose.interop.blending", "true")
    System.setProperty("compose.swing.render.on.graphics", "true")
    System.setProperty("compose.layers.type", "COMPONENT")
    val renderApi = configureDesktopRenderer()
    AppDiagnostics.install()
    DesktopRuntimeLog.initialize()
    DesktopRuntimeLog.info("desktop.renderer configured skiko.renderApi=$renderApi")
    DesktopRuntimeLog.installGlobalExceptionHandlers()
    if (DesktopDeepLinkBridge.forwardToPrimaryInstanceIfNeeded(args)) {
        return
    }
    DesktopDeepLinkBridge.install(args)
    DiscordRichPresence.start()

    application {
        val windowState = rememberWindowState(
            width = 1280.dp,
            height = 720.dp,
            placement = WindowPlacement.Floating,
        )
        var fullscreenController by remember { mutableStateOf<DesktopFullscreenController?>(null) }
        var isFullscreen by remember { mutableStateOf(false) }
        var isPlayerScreenActive by remember { mutableStateOf(PlayerKeyboardShortcutBridge.isActive) }
        val currentController by rememberUpdatedState(fullscreenController)

        DisposableEffect(Unit) {
            val removePlayerActiveObserver = PlayerKeyboardShortcutBridge.observeActiveState { active ->
                isPlayerScreenActive = active
            }
            val dispatcher = java.awt.KeyEventDispatcher { event ->
                if (event.id != KeyEvent.KEY_PRESSED) {
                    false
                } else when {
                    isDesktopFullscreenShortcut(event.keyCode, event.isAltDown) -> {
                        currentController?.toggle()
                        true
                    }
                    event.isControlDown && !event.isAltDown && !event.isMetaDown &&
                        (event.keyCode == KeyEvent.VK_K || event.keyCode == KeyEvent.VK_P) -> {
                        CommandPaletteController.toggle()
                        true
                    }
                    !event.isAltDown && !event.isControlDown && !event.isMetaDown -> {
                        val handledByPlayer = desktopPlayerKeyboardShortcutFor(event.keyCode)
                            ?.let(PlayerKeyboardShortcutBridge::dispatch) ?: false
                        when {
                            handledByPlayer -> true
                            event.keyCode == KeyEvent.VK_ESCAPE &&
                                shouldExitFullscreenOnEscape(
                                    mode = if (currentController?.isFullscreen == true) {
                                        DesktopWindowMode.Fullscreen
                                    } else {
                                        DesktopWindowMode.Normal
                                    },
                                    handledByPlayer = false,
                                ) -> {
                                currentController?.requestExitFullscreen()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
            val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            keyboardFocusManager.addKeyEventDispatcher(dispatcher)
            onDispose {
                keyboardFocusManager.removeKeyEventDispatcher(dispatcher)
                removePlayerActiveObserver()
            }
        }

        Window(
            onCloseRequest = {
                DesktopPlayerRegistry.closeAll("windowClose")
                DesktopPlayerRegistry.awaitAllCloses(timeoutMs = 1500)
                exitApplication()
            },
            title = "Nuvio",
            state = windowState,
            undecorated = true,
            icon = painterResource(Res.drawable.app_logo_mark),
        ) {
            val awtWindow = window
            DisposableEffect(awtWindow) {
                DesktopWindowChrome.applyNuvioChrome(awtWindow)

                DesktopSystemTray.install(awtWindow) {
                    DesktopPlayerRegistry.closeAll("trayQuit")
                    DesktopPlayerRegistry.awaitAllCloses(timeoutMs = 1500)
                    exitApplication()
                }

                val controller = DesktopFullscreenController(awtWindow) { fullscreen ->
                    isFullscreen = fullscreen
                }
                fullscreenController = controller
                controller.applyInitial()

                val recoveryListener = object : WindowAdapter() {
                    private fun recover(event: WindowEvent) {
                        if (isDesktopWindowRecoveryEvent(event.id)) {
                            DesktopWindowChrome.applyNuvioChrome(awtWindow)
                            repaintDesktopWindow(awtWindow)
                        }
                    }

                    override fun windowActivated(event: WindowEvent) = recover(event)
                    override fun windowDeiconified(event: WindowEvent) = recover(event)
                    override fun windowGainedFocus(event: WindowEvent) = recover(event)
                }

                awtWindow.addWindowListener(recoveryListener)
                awtWindow.addWindowFocusListener(recoveryListener)

                val dragBoundsGuard = object : ComponentAdapter() {
                    override fun componentMoved(event: ComponentEvent) {
                        clampDesktopWindowToVirtualDesktopNow(awtWindow)
                    }
                }
                awtWindow.addComponentListener(dragBoundsGuard)

                onDispose {
                    fullscreenController = null
                    awtWindow.removeWindowListener(recoveryListener)
                    awtWindow.removeWindowFocusListener(recoveryListener)
                    awtWindow.removeComponentListener(dragBoundsGuard)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                App()

                if (!isPlayerScreenActive && !isFullscreen) {
                    DesktopWindowDragGrip(
                        window = awtWindow,
                        onDragStart = {
                            logDesktopWindowDrag("start", awtWindow)
                        },
                        onDragEnd = {
                            snapDesktopWindowToNormalBounds(awtWindow)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 290.dp)
                            .zIndex(20f),
                    )
                }

                if (!isPlayerScreenActive) {
                    DesktopWindowControls(
                        isFullscreen = isFullscreen,
                        onCommandPalette = { CommandPaletteController.toggle() },
                        onToggleFullscreen = { currentController?.toggle() },
                        onMinimize = {
                            (awtWindow as? JFrame)?.extendedState = JFrame.ICONIFIED
                        },
                        onClose = {
                            DesktopPlayerRegistry.closeAll("windowClose")
                            DesktopPlayerRegistry.awaitAllCloses(timeoutMs = 1500)
                            exitApplication()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 78.dp)
                            .zIndex(20f),
                    )
                }
            }
        }
    }
}

internal fun configureDesktopRenderer(): String {
    val requested = System.getProperty("nuvio.renderApi")
        ?: System.getenv("NUVIO_RENDER_API")
        ?: System.getProperty("skiko.renderApi")
        ?: "OPENGL"
    val normalized = requested.trim().uppercase()
    val safeValue = when (normalized) {
        "OPENGL",
        "SOFTWARE" -> normalized
        else -> "OPENGL"
    }
    System.setProperty("skiko.renderApi", safeValue)
    return safeValue
}

@Composable
private fun DesktopWindowDragGrip(
    window: AwtWindow,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragAnchorMouse by remember { mutableStateOf<Point?>(null) }
    var dragAnchorWindow by remember { mutableStateOf<Point?>(null) }

    Box(
        modifier = modifier
            .size(width = 64.dp, height = 42.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f))
            .pointerInput(window, onDragStart, onDragEnd) {
                detectDragGestures(
                    onDragStart = {
                        dragAnchorMouse = currentScreenPointerLocation()
                        dragAnchorWindow = window.location
                        onDragStart()
                    },
                    onDragCancel = {
                        dragAnchorMouse = null
                        dragAnchorWindow = null
                        onDragEnd()
                    },
                    onDragEnd = {
                        dragAnchorMouse = null
                        dragAnchorWindow = null
                        onDragEnd()
                    },
                    onDrag = { _, _ ->
                        val startMouse = dragAnchorMouse ?: return@detectDragGestures
                        val startWindow = dragAnchorWindow ?: return@detectDragGestures
                        val currentMouse = currentScreenPointerLocation() ?: return@detectDragGestures
                        val targetBounds = clampedDesktopWindowBounds(
                            windowBounds = Rectangle(
                                startWindow.x + currentMouse.x - startMouse.x,
                                startWindow.y + currentMouse.y - startMouse.y,
                                window.width,
                                window.height,
                            ),
                            virtualDesktopBounds = desktopVirtualBounds(),
                        )
                        setDesktopWindowLocation(window, targetBounds.x, targetBounds.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.DragIndicator,
            contentDescription = "Drag window",
            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun currentScreenPointerLocation(): Point? =
    runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()

private fun setDesktopWindowLocation(window: AwtWindow, x: Int, y: Int) {
    if (EventQueue.isDispatchThread()) {
        window.setLocation(x, y)
    } else {
        EventQueue.invokeLater {
            window.setLocation(x, y)
        }
    }
}

@Composable
private fun DesktopWindowControls(
    isFullscreen: Boolean,
    onCommandPalette: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!isFullscreen) {
            DesktopControlButton(
                icon = Icons.Rounded.Search,
                contentDescription = "Command palette (Ctrl+K)",
                onClick = onCommandPalette,
            )
        }
        DesktopControlButton(
            icon = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            contentDescription = "Toggle fullscreen (F11)",
            onClick = onToggleFullscreen,
        )
        if (!isFullscreen) {
            DesktopControlButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Minimize",
                onClick = onMinimize,
            )
            DesktopControlButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Close",
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun DesktopControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(22.dp),
        )
    }
}

internal enum class DesktopWindowMode { Normal, Fullscreen }

internal fun nextWindowMode(current: DesktopWindowMode): DesktopWindowMode =
    if (current == DesktopWindowMode.Fullscreen) DesktopWindowMode.Normal
    else DesktopWindowMode.Fullscreen

/** Esc leaves fullscreen only while browsing AND only if the player did not consume it. */
internal fun shouldExitFullscreenOnEscape(
    mode: DesktopWindowMode,
    handledByPlayer: Boolean,
): Boolean = mode == DesktopWindowMode.Fullscreen && !handledByPlayer

/** Swallows key auto-repeat and double F11/Alt+Enter/click events within [windowMs]. */
internal fun shouldDebounceToggle(
    nowNanos: Long,
    lastToggleNanos: Long,
    windowMs: Long = 250,
): Boolean = lastToggleNanos != 0L && (nowNanos - lastToggleNanos) < windowMs * 1_000_000L

/**
 * Owns the runtime fullscreen transitions for the desktop window. All native work is
 * marshalled onto the AWT event-dispatch thread and guarded against re-entrancy and key
 * auto-repeat so a held F11 (or rapid clicks) cannot flicker the window. Fullscreen is a
 * borderless resize to the current monitor's bounds — no exclusive mode (which minimizes on
 * focus loss) and no peer/decoration recreation (which crashed the MPV-embedded window).
 */
internal class DesktopFullscreenController(
    private val window: AwtWindow,
    private val onModeChanged: (Boolean) -> Unit,
) {
    @Volatile
    private var mode = DesktopWindowMode.Normal
    private val transitioning = java.util.concurrent.atomic.AtomicBoolean(false)
    private var lastToggleNanos = 0L

    val isFullscreen: Boolean
        get() = mode == DesktopWindowMode.Fullscreen

    fun applyInitial() {
        EventQueue.invokeLater {
            DesktopWindowChrome.applyNormalBounds(window)
            onModeChanged(false)
        }
    }

    fun toggle() {
        val now = System.nanoTime()
        if (shouldDebounceToggle(now, lastToggleNanos)) return
        if (!transitioning.compareAndSet(false, true)) return
        lastToggleNanos = now
        val target = nextWindowMode(mode)
        EventQueue.invokeLater {
            try {
                applyMode(target)
            } finally {
                transitioning.set(false)
            }
        }
    }

    fun requestExitFullscreen() {
        if (mode != DesktopWindowMode.Fullscreen) return
        if (!transitioning.compareAndSet(false, true)) return
        lastToggleNanos = System.nanoTime()
        EventQueue.invokeLater {
            try {
                applyMode(DesktopWindowMode.Normal)
            } finally {
                transitioning.set(false)
            }
        }
    }

    private fun applyMode(target: DesktopWindowMode) {
        if (target == DesktopWindowMode.Fullscreen) {
            DesktopWindowChrome.applyFullscreenBounds(window)
        } else {
            DesktopWindowChrome.applyNormalBounds(window)
        }
        mode = target
        onModeChanged(target == DesktopWindowMode.Fullscreen)
    }
}

internal fun isDesktopFullscreenShortcut(keyCode: Int, isAltDown: Boolean): Boolean =
    keyCode == KeyEvent.VK_F11 || (isAltDown && keyCode == KeyEvent.VK_ENTER)

internal fun desktopPlayerKeyboardShortcutFor(keyCode: Int): PlayerKeyboardShortcut? = when (keyCode) {
    KeyEvent.VK_SPACE,
    KeyEvent.VK_ENTER,
    KeyEvent.VK_K -> PlayerKeyboardShortcut.TogglePlayback
    KeyEvent.VK_LEFT,
    KeyEvent.VK_J -> PlayerKeyboardShortcut.SeekBackward
    KeyEvent.VK_RIGHT,
    KeyEvent.VK_L -> PlayerKeyboardShortcut.SeekForward
    KeyEvent.VK_UP -> PlayerKeyboardShortcut.VolumeUp
    KeyEvent.VK_DOWN -> PlayerKeyboardShortcut.VolumeDown
    KeyEvent.VK_M -> PlayerKeyboardShortcut.ToggleMute
    KeyEvent.VK_ESCAPE,
    KeyEvent.VK_BACK_SPACE -> PlayerKeyboardShortcut.CloseOrBack
    else -> null
}

internal fun isDesktopWindowRecoveryEvent(eventId: Int): Boolean =
    eventId == WindowEvent.WINDOW_ACTIVATED ||
        eventId == WindowEvent.WINDOW_DEICONIFIED ||
        eventId == WindowEvent.WINDOW_GAINED_FOCUS

private fun repaintDesktopWindow(window: AwtWindow) {
    EventQueue.invokeLater {
        window.background = Color.BLACK
        (window as? JFrame)?.contentPane?.let { pane ->
            pane.background = Color.BLACK
            pane.invalidate()
            pane.validate()
            pane.repaint()
        }
        window.invalidate()
        window.validate()
        window.repaint()
    }
}

private fun snapDesktopWindowToNormalBounds(window: AwtWindow) {
    EventQueue.invokeLater {
        logDesktopWindowDragNow("end-before-snap", window)
        DesktopWindowChrome.applyNormalBounds(window)
        clampDesktopWindowToVirtualDesktopNow(window)
        logDesktopWindowDragNow("end-after-snap", window)
    }
}

internal fun clampedDesktopWindowBounds(
    windowBounds: Rectangle,
    virtualDesktopBounds: Rectangle,
    minVisiblePx: Int = DesktopDragMinVisiblePx,
): Rectangle {
    val minVisibleX = minOf(minVisiblePx, windowBounds.width, virtualDesktopBounds.width).coerceAtLeast(1)
    val minVisibleY = minOf(minVisiblePx, windowBounds.height, virtualDesktopBounds.height).coerceAtLeast(1)
    val minX = virtualDesktopBounds.x - windowBounds.width + minVisibleX
    val maxX = virtualDesktopBounds.x + virtualDesktopBounds.width - minVisibleX
    val minY = virtualDesktopBounds.y - windowBounds.height + minVisibleY
    val maxY = virtualDesktopBounds.y + virtualDesktopBounds.height - minVisibleY
    return Rectangle(
        boundedCoordinate(windowBounds.x, minX, maxX, virtualDesktopBounds.x),
        boundedCoordinate(windowBounds.y, minY, maxY, virtualDesktopBounds.y),
        windowBounds.width,
        windowBounds.height,
    )
}

private fun boundedCoordinate(value: Int, min: Int, max: Int, fallback: Int): Int =
    if (min <= max) value.coerceIn(min, max) else fallback

private fun clampDesktopWindowToVirtualDesktopNow(window: AwtWindow) {
    val current = window.bounds
    val virtualDesktop = desktopVirtualBounds()
    val clamped = clampedDesktopWindowBounds(current, virtualDesktop)
    if (current.x == clamped.x && current.y == clamped.y) return
    DesktopRuntimeLog.warn(
        "desktop.window.drag.clamp from=${current.toDesktopLogString()} " +
            "to=${clamped.toDesktopLogString()} virtual=${virtualDesktop.toDesktopLogString()}",
    )
    window.setLocation(clamped.x, clamped.y)
}

private fun desktopVirtualBounds(): Rectangle {
    val configurations = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .screenDevices
        .map { it.defaultConfiguration.bounds }
    return configurations
        .drop(1)
        .fold(configurations.firstOrNull() ?: Rectangle(0, 0, 1, 1)) { union, bounds ->
            union.union(bounds)
        }
}

private fun logDesktopWindowDrag(phase: String, window: AwtWindow) {
    EventQueue.invokeLater {
        logDesktopWindowDragNow(phase, window)
    }
}

private fun logDesktopWindowDragNow(phase: String, window: AwtWindow) {
    val gc = window.graphicsConfiguration
    val monitorBounds = gc?.bounds
    val transform = gc?.defaultTransform
    val screenCount = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size
    DesktopRuntimeLog.info(
        "desktop.window.drag.$phase bounds=${window.bounds.toDesktopLogString()} " +
            "monitor=${monitorBounds?.toDesktopLogString() ?: "unknown"} " +
            "scale=${transform?.scaleX ?: "unknown"}x${transform?.scaleY ?: "unknown"} " +
            "screens=$screenCount",
    )
}

private fun Rectangle.toDesktopLogString(): String =
    "${x}x${y}+${width}x$height"
