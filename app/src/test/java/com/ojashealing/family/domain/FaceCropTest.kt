package com.ojashealing.family.domain

import androidx.compose.ui.geometry.Offset
import com.ojashealing.family.ui.screens.FaceCrop
import com.ojashealing.family.ui.screens.faceClampPan
import com.ojashealing.family.ui.screens.faceCropFor
import com.ojashealing.family.ui.screens.personAlpha
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceCropTest {
    // A 1000 x 2000 photo in a 400 x 500 frame (the opening animation's 4:5 shape)
    @Test
    fun `the frame takes the middle of the photo, in the frame's shape`() {
        assertEquals(FaceCrop(0, 375, 1000, 1250), faceCropFor(1000, 2000, 400f, 500f, 1f, Offset.Zero))
    }

    @Test
    fun `zooming in takes a smaller part, and dragging moves it the other way`() {
        assertEquals(FaceCrop(250, 688, 500, 625), faceCropFor(1000, 2000, 400f, 500f, 2f, Offset.Zero))
        // Dragging the picture 80 frame pixels right shows 100 source pixels further left (scale 0.8)
        assertEquals(FaceCrop(150, 688, 500, 625), faceCropFor(1000, 2000, 400f, 500f, 2f, Offset(80f, 0f)))
    }

    @Test
    fun `the photo can never be dragged off the frame`() {
        assertEquals(Offset(0f, 150f), faceClampPan(1000, 2000, 400f, 500f, 1f, Offset(900f, 900f)))
        assertEquals(FaceCrop(0, 0, 1000, 1250), faceCropFor(1000, 2000, 400f, 500f, 1f, Offset(0f, 9000f)))
    }

    @Test
    fun `the person stays, the background goes, and the edge between them is soft`() {
        assertEquals(255, personAlpha(1f))
        assertEquals(255, personAlpha(0.75f))
        assertEquals(0, personAlpha(0.2f))
        assertEquals(128, personAlpha(0.5f))
    }
}
