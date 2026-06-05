package com.nuvio.app.features.player

import com.nuvio.app.features.player.desktop.mpv.MpvSubtitleStyleMode
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopMpvSubtitleStyleModeTest {
    @Test
    fun textSubtitlesUseAppControlledStyle() {
        assertEquals(
            MpvSubtitleStyleMode.AppControlled,
            MpvSubtitleStyleMode(trackCodec = "subrip", externalSubtitleActive = false),
        )
        assertEquals(
            MpvSubtitleStyleMode.AppControlled,
            MpvSubtitleStyleMode(trackCodec = "ass", externalSubtitleActive = false),
        )
    }

    @Test
    fun externalAddonSubtitlesUseAppControlledStyle() {
        assertEquals(
            MpvSubtitleStyleMode.AppControlled,
            MpvSubtitleStyleMode(trackCodec = "hdmv_pgs_subtitle", externalSubtitleActive = true),
        )
    }

    @Test
    fun bitmapSubtitlesKeepNativeRendering() {
        assertEquals(
            MpvSubtitleStyleMode.Native,
            MpvSubtitleStyleMode(trackCodec = "hdmv_pgs_subtitle", externalSubtitleActive = false),
        )
        assertEquals(
            MpvSubtitleStyleMode.Native,
            MpvSubtitleStyleMode(trackCodec = "dvd-subtitle", externalSubtitleActive = false),
        )
    }
}
