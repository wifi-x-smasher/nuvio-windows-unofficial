package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopVlcTrackSelectionTest {
    @Test
    fun resolvesUiAudioIndexAgainstSelectableVlcTracksOnly() {
        val vlcTrackIds = listOf(-1, 2, 4)

        assertEquals(2, selectableVlcTrackIdForUiIndex(vlcTrackIds, 0))
        assertEquals(4, selectableVlcTrackIdForUiIndex(vlcTrackIds, 1))
    }

    @Test
    fun ignoresOutOfRangeUiAudioIndex() {
        val vlcTrackIds = listOf(-1, 2)

        assertNull(selectableVlcTrackIdForUiIndex(vlcTrackIds, 2))
    }
}
