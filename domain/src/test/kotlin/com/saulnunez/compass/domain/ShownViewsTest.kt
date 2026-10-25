package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ShownViewsTest {

    @Test
    fun `Edit mode shows the views chosen for Practice mode, always with the Tablature it edits`() {
        assertEquals(ShownViews.TABLATURE, ShownViews.TABLATURE.inEditMode())
        assertEquals(ShownViews.BOTH, ShownViews.BOTH.inEditMode())
        // Notes are written on the Tablature, so Standard notation alone gains the Tablature beside it.
        assertEquals(ShownViews.BOTH, ShownViews.STANDARD_NOTATION.inEditMode())
    }
}
