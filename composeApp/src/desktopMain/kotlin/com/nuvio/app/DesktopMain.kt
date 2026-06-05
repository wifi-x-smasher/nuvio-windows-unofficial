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
import com.nuvio.app.desktop.DesktopWindowChrome
import com.nuvio.app.core.diagnostics.AppDiagnostics
import com.nuvio.app.core.deeplink.DesktopDeepLinkBridge
import com.nuvio.app.features.player.PlayerKeyboardShortcut
import com.nuvio.app.features.player.PlayerKeyboardShortcutBridge
import java.awt.Color
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.awt.Window as AwtWindow
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.math.roundToInt
import javax.swing.JFrame
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_mark
import org.jetbrains.compose.resources.painterResource

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
                onDispose {
                    fullscreenController = null
                    awtWindow.removeWindowListener(recoveryListener)
                    awtWindow.removeWindowFocusListener(recoveryListener)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                App()

                if (!isPlayerScreenActive && !isFullscreen) {
                    DesktopWindowDragGrip(
                        onDrag = { deltaX, deltaY ->
                            moveDesktopWindowBy(awtWindow, deltaX, deltaY)
                        },
                        onDragEnd = {
                            snapDesktopWindowToNormalBounds(awtWindow)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 230.dp)
                            .zIndex(20f),
                    )
                }

                if (!isPlayerScreenActive) {
                    DesktopWindowControls(
                        isFullscreen = isFullscreen,
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
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 64.dp, height = 42.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f))
            .pointerInput(onDrag, onDragEnd) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDrag = { _, dragAmount ->
                        onDrag(dragAmount.x, dragAmount.y)
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

@Composable
private fun DesktopWindowControls(
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

private fun moveDesktopWindowBy(window: AwtWindow, deltaX: Float, deltaY: Float) {
    val dx = deltaX.roundToInt()
    val dy = deltaY.roundToInt()
    if (dx == 0 && dy == 0) return
    EventQueue.invokeLater {
        window.setLocation(window.x + dx, window.y + dy)
    }
}

private fun snapDesktopWindowToNormalBounds(window: AwtWindow) {
    EventQueue.invokeLater {
        DesktopWindowChrome.applyNormalBounds(window)
    }
}
