package com.nuvio.app.features.player.desktop

import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.features.experimental.ExperimentalFeatureSettings
import com.nuvio.app.features.experimental.WindowsInternalPlayerBackend
import com.nuvio.app.features.player.desktop.mpv.MpvDesktopPlayerBackend
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeBootstrap
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeLocator
import com.nuvio.app.features.player.desktop.nativempv.NativeMpvDesktopPlayerBackend
import com.nuvio.app.features.player.desktop.nativempv.NativeMpvRuntimeLocator

internal object DesktopPlayerBackendFactory {
    private const val BackendProperty = "nuvio.windows.player.backend"
    private const val BackendEnv = "NUVIO_WINDOWS_PLAYER_BACKEND"

    fun createWindowsBackend(): DesktopPlayerBackend {
        val selection = DesktopPlayerBackendSelection.resolve()
        DesktopRuntimeLog.info("Selected Windows player backend request=${selection.value} source=${selection.source}")
        return when (selection.backend) {
            DesktopPlayerBackendKind.None -> unavailable(
                technicalMessage = "Windows player backend disabled by configuration.",
                selection = selection,
            )
            DesktopPlayerBackendKind.NativeMpv -> createNativeMpvOrFallback(selection)
            DesktopPlayerBackendKind.Mpv,
            DesktopPlayerBackendKind.Auto
            -> createMpvOrUnavailable(selection)
        }
    }

    private fun createNativeMpvOrFallback(selection: DesktopPlayerBackendSelection): DesktopPlayerBackend {
        val runtime = NativeMpvRuntimeLocator.resolve()
        return NativeMpvDesktopPlayerBackend.create(runtime)
            .onSuccess {
                DesktopRuntimeLog.info(
                    "Selected player backend=${it.backendName} (source=${selection.source} request=${selection.value}) ${runtime.diagnostics}",
                )
            }
            .onFailure {
                DesktopRuntimeLog.error(
                    "Experimental native MPV backend unavailable; falling back to stable MediaMP. ${runtime.diagnostics}",
                    it,
                )
            }
            .getOrNull()
            ?: createMpvOrUnavailable(selection.copy(backend = DesktopPlayerBackendKind.Mpv))
    }

    private fun createMpvOrUnavailable(selection: DesktopPlayerBackendSelection): DesktopPlayerBackend =
        createMpvOrNull(selection) ?: unavailable(
            technicalMessage = "MPV backend is unavailable.",
            selection = selection,
        )

    private fun createMpvOrNull(selection: DesktopPlayerBackendSelection): DesktopPlayerBackend? {
        val runtime = MpvRuntimeLocator.resolve()
        val bootstrap = MpvRuntimeBootstrap.apply(runtime)
        if (!bootstrap.success) {
            DesktopRuntimeLog.error("MPV runtime bootstrap failed diagnostics=${bootstrap.diagnostics}", bootstrap.error)
            return null
        }
        return MpvDesktopPlayerBackend.create(runtime)
            .onSuccess {
                DesktopRuntimeLog.info("Selected player backend=${it.backendName} (source=${selection.source} request=${selection.value})")
            }
            .onFailure { DesktopRuntimeLog.error("MPV backend init failed", it) }
            .getOrNull()
    }

    private fun unavailable(
        technicalMessage: String,
        selection: DesktopPlayerBackendSelection,
    ): DesktopPlayerBackend {
        val backendName = "windows-mediamp-mpv"
        DesktopRuntimeLog.warn("Selected player backend=$backendName (source=${selection.source} request=${selection.value})")
        return UnavailableDesktopPlayerBackend(
            backendName = backendName,
            error = DesktopPlayerError.RuntimeUnavailable(
                backendName = backendName,
                technicalMessage = technicalMessage,
                suggestedAction = "Check the MPV runtime files and restart the app.",
            ),
        )
    }

    private enum class DesktopPlayerBackendKind {
        Auto,
        Mpv,
        NativeMpv,
        None,
    }

    private data class DesktopPlayerBackendSelection(
        val backend: DesktopPlayerBackendKind,
        val value: String,
        val source: String,
    ) {
        companion object {
            fun resolve(): DesktopPlayerBackendSelection {
                val property = System.getProperty(BackendProperty)?.trim()?.lowercase()
                if (!property.isNullOrBlank()) return fromValue(property, "system-property:$BackendProperty")
                val env = System.getenv(BackendEnv)?.trim()?.lowercase()
                if (!env.isNullOrBlank()) return fromValue(env, "env:$BackendEnv")
                val configured = ExperimentalFeatureSettings.windowsInternalPlayerBackend.value
                if (configured == WindowsInternalPlayerBackend.NATIVE_MPV) {
                    return fromValue(configured.id, "settings:experimental_features")
                }
                return DesktopPlayerBackendSelection(DesktopPlayerBackendKind.Auto, "auto", "default")
            }

            private fun fromValue(value: String, source: String): DesktopPlayerBackendSelection =
                DesktopPlayerBackendSelection(
                    backend = when (value) {
                        "none" -> DesktopPlayerBackendKind.None
                        "native-mpv" -> DesktopPlayerBackendKind.NativeMpv
                        "mediamp",
                        "mpv" -> DesktopPlayerBackendKind.Mpv
                        else -> DesktopPlayerBackendKind.Mpv
                    },
                    value = value,
                    source = source,
                )
        }
    }
}
