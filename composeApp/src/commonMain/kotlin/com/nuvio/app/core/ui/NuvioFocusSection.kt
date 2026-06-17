package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.nuvio.app.isDesktop

/**
 * Keyboard navigation model for the desktop/TV-style experience:
 *
 * - **Arrow keys** move focus *within* a section (handled by the per-item focusable + directional
 *   traversal already wired into rows/grids).
 * - **Tab / Shift+Tab** move focus *between* whole sections (sidebar -> content -> top bar ...),
 *   one section per press, wrapping around the ends.
 *
 * Each section registers a [FocusRequester] under an explicit [order]. The navigator tracks which
 * section currently holds focus so Tab knows where to go next. Sections are marked with
 * [nuvioFocusSection]; the container that wraps all of them installs [nuvioSectionTabNavigation].
 */
@Stable
internal class NuvioSectionNavigator {
    private val requesters = mutableStateMapOf<Int, FocusRequester>()

    /** Order of the section that currently has focus, or null before anything is focused. */
    internal var focusedOrder by mutableStateOf<Int?>(null)
        private set

    fun requesterFor(order: Int): FocusRequester = requesters.getOrPut(order) { FocusRequester() }

    fun onSectionFocused(order: Int) {
        focusedOrder = order
    }

    /** Moves focus to the section [delta] steps away (wrapping). Returns true if handled. */
    fun moveBy(delta: Int): Boolean {
        val ordered = requesters.keys.sorted()
        if (ordered.isEmpty()) return false
        val current = focusedOrder ?: ordered.first()
        val currentIndex = ordered.indexOf(current).let { if (it < 0) 0 else it }
        val size = ordered.size
        val nextIndex = ((currentIndex + delta) % size + size) % size
        val target = requesters[ordered[nextIndex]] ?: return false
        return runCatching {
            target.requestFocus()
            true
        }.getOrDefault(false)
    }

    /** Requests focus into the section registered under [order]. Safe to call before layout. */
    fun focusSection(order: Int): Boolean =
        runCatching {
            requesters[order]?.requestFocus()
            requesters.containsKey(order)
        }.getOrDefault(false)
}

@Composable
internal fun rememberNuvioSectionNavigator(): NuvioSectionNavigator = remember { NuvioSectionNavigator() }

/**
 * Marks a region as a Tab-navigable section. Registers a [FocusRequester] for Tab targeting and
 * reports when it gains focus so the navigator can track the active section.
 */
@Composable
internal fun Modifier.nuvioFocusSection(
    navigator: NuvioSectionNavigator,
    order: Int,
    enabled: Boolean = true,
): Modifier {
    if (!isDesktop || !enabled) return this
    val requester = navigator.requesterFor(order)
    return this
        .onFocusChanged { state -> if (state.hasFocus) navigator.onSectionFocused(order) }
        .focusRequester(requester)
}

/**
 * Installs Tab / Shift+Tab section switching. Apply on a container that wraps every section so the
 * key event is seen before children consume it.
 */
@Composable
internal fun Modifier.nuvioSectionTabNavigation(
    navigator: NuvioSectionNavigator,
    enabled: Boolean = true,
): Modifier {
    if (!isDesktop || !enabled) return this
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != Key.Tab) {
            false
        } else {
            navigator.moveBy(if (event.isShiftPressed) -1 else 1)
        }
    }
}

/**
 * Requests focus into [order] once the section is laid out. Use to give a freshly opened screen an
 * initial focus target so arrow keys work immediately without a mouse click. Re-runs when [key]
 * changes (e.g. switching tabs).
 */
@Composable
internal fun NuvioSectionNavigator.RequestInitialSectionFocus(order: Int, key: Any? = Unit) {
    if (!isDesktop) return
    val navigator = this
    LaunchedEffect(key) {
        // Retry across a few frames: the focus node attaches after composition, so the first
        // request can land before the section exists. Stop as soon as the section has focus.
        repeat(6) {
            if (navigator.focusedOrder == order) return@LaunchedEffect
            navigator.focusSection(order)
            withFrameNanos { }
        }
    }
}
