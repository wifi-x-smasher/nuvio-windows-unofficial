package com.nuvio.app.features.player

import com.nuvio.app.features.player.desktop.mpv.MpvSubtitleStyleMode
import com.nuvio.app.features.player.desktop.mpv.displaySubtitleTrackLabel
import com.nuvio.app.features.player.desktop.mpv.toMpvSubtitleColorString
import androidx.compose.ui.graphics.Color
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

    @Test
    fun subtitleColorUsesMpvAlphaFirstFormat() {
        assertEquals("#FFFFFFFF", Color.White.toMpvSubtitleColorString())
        assertEquals("#FFFFD700", Color(0xFFFFD700).toMpvSubtitleColorString())
        assertEquals("#FFFF0000", Color.Red.toMpvSubtitleColorString())
    }

    @Test
    fun subtitleTrackLabelsUseReadableLanguageNamesWhenTitleIsMissing() {
        assertEquals("English", displaySubtitleTrackLabel(title = "", language = "en", id = 3))
        assertEquals("Korean", displaySubtitleTrackLabel(title = "", language = "ko", id = 2))
        assertEquals("Spanish (Latin America)", displaySubtitleTrackLabel(title = "", language = "es-419", id = 4))
        assertEquals("English (SDH)", displaySubtitleTrackLabel(title = "SDH", language = "en", id = 5))
        assertEquals("English (DUB)", displaySubtitleTrackLabel(title = "English (DUB)", language = "en", id = 6))
        assertEquals("Subtitle 7", displaySubtitleTrackLabel(title = "", language = "", id = 7))
    }
}
