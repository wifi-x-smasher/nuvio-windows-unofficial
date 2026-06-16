package com.nuvio.app.features.details.components

import kotlin.test.Test
import kotlin.test.assertEquals

class TrailerPlayerPopupLayoutTest {
    @Test
    fun playerSurfaceUsesParentBounds() {
        assertEquals(TrailerPopupPlayerSurfaceBounds.MatchParent, trailerPopupPlayerSurfaceBounds())
    }
}
