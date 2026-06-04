package com.nuvio.app.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWindowChromeTest {
    @Test
    fun convertsRgbToWindowsColorRefByteOrder() {
        assertEquals(
            0x00120a0b,
            DesktopWindowChrome.rgbToColorRef(red = 11, green = 10, blue = 18),
        )
        assertEquals(
            0x00f8f5f5,
            DesktopWindowChrome.rgbToColorRef(red = 245, green = 245, blue = 248),
        )
    }
}
