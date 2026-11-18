package org.r3akt.uas

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PictureImageTest {
    @Test
    fun centredFourByThreeCrop() {
        assertArrayEquals(intArrayOf(240, 0, 1440, 1080), PictureImage.centre43(1920, 1080))
        assertArrayEquals(intArrayOf(0, 0, 1600, 1200), PictureImage.centre43(1600, 1200))
        assertArrayEquals(intArrayOf(0, 120, 1200, 900), PictureImage.centre43(1200, 1140))
    }

    @Test
    fun scaledDownNeverUp() {
        assertEquals(1440 to 1080, PictureImage.fit(1440, 1080, 1600, 1200))
        assertEquals(1600 to 1200, PictureImage.fit(4000, 3000, 1600, 1200))
        assertEquals(160 to 120, PictureImage.fit(1440, 1080, 160, 120))
    }
}
