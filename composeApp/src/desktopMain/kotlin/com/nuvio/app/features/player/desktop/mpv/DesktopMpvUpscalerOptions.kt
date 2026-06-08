package com.nuvio.app.features.player.desktop.mpv

import com.nuvio.app.features.experimental.VideoUpscalerPreset

internal object DesktopMpvUpscalerOptions {
    val presetCount: Int = VideoUpscalerPreset.entries.count { it != VideoUpscalerPreset.OFF }

    fun optionsFor(preset: VideoUpscalerPreset): Map<String, String> = when (preset) {
        VideoUpscalerPreset.OFF -> baseOptions()
        VideoUpscalerPreset.HIGH_QUALITY -> baseOptions() + linkedMapOf(
            "scale" to "ewa_lanczossharp",
            "cscale" to "ewa_lanczossoft",
            "dscale" to "mitchell",
            "scale-antiring" to "0.70",
            "cscale-antiring" to "0.70",
            "correct-downscaling" to "yes",
            "linear-downscaling" to "yes",
            "sigmoid-upscaling" to "yes",
        )
        VideoUpscalerPreset.SHARP_DETAIL -> baseOptions() + linkedMapOf(
            "scale" to "ewa_lanczossharp",
            "cscale" to "ewa_lanczossharp",
            "dscale" to "lanczos",
            "scale-antiring" to "0.85",
            "cscale-antiring" to "0.85",
            "correct-downscaling" to "yes",
            "linear-downscaling" to "yes",
            "sigmoid-upscaling" to "yes",
        )
        VideoUpscalerPreset.SOFT_FILM -> baseOptions() + linkedMapOf(
            "scale" to "spline36",
            "cscale" to "spline36",
            "dscale" to "mitchell",
            "scale-antiring" to "0.60",
            "cscale-antiring" to "0.60",
            "correct-downscaling" to "yes",
            "linear-downscaling" to "yes",
            "sigmoid-upscaling" to "yes",
            "deband" to "yes",
            "deband-iterations" to "1",
            "deband-threshold" to "35",
            "deband-range" to "16",
            "deband-grain" to "24",
        )
        VideoUpscalerPreset.ANIMATION -> baseOptions() + linkedMapOf(
            "scale" to "ewa_lanczossharp",
            "cscale" to "spline36",
            "dscale" to "mitchell",
            "scale-antiring" to "0.90",
            "cscale-antiring" to "0.80",
            "correct-downscaling" to "yes",
            "linear-downscaling" to "no",
            "sigmoid-upscaling" to "no",
        )
        VideoUpscalerPreset.LOW_GPU -> baseOptions() + linkedMapOf(
            "scale" to "bicubic",
            "cscale" to "bicubic",
            "dscale" to "bilinear",
        )
    }

    private fun baseOptions(): Map<String, String> = linkedMapOf(
        "scale" to "lanczos",
        "cscale" to "lanczos",
        "dscale" to "mitchell",
        "scale-antiring" to "0.0",
        "cscale-antiring" to "0.0",
        "correct-downscaling" to "no",
        "linear-downscaling" to "no",
        "sigmoid-upscaling" to "no",
        "deband" to "no",
        "deband-iterations" to "1",
        "deband-threshold" to "64",
        "deband-range" to "16",
        "deband-grain" to "48",
    )
}
