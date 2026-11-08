package com.ojashealing.family.ui

import androidx.compose.ui.unit.dp
import com.ojashealing.family.ui.components.fitSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ResponsiveImageTest {

    @Test
    fun landscapeFillsTheWidth() {
        assertEquals(360.dp to 202.5.dp, fitSize(16f / 9f, 360.dp, 480.dp))
    }

    @Test
    fun squareFillsTheWidth() {
        assertEquals(360.dp to 360.dp, fitSize(1f, 360.dp, 480.dp))
    }

    @Test
    fun tallPhotoGetsNarrowerInsteadOfBeingCut() {
        // A 9:16 phone screenshot: full height of 480, so 270 wide, nothing cropped
        assertEquals(270.dp to 480.dp, fitSize(9f / 16f, 360.dp, 480.dp))
    }

    @Test
    fun unknownShapeFallsBackToFourByThree() {
        assertEquals(360.dp to 270.dp, fitSize(Float.NaN, 360.dp, 480.dp))
    }
}
