package com.superchant.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MalaGeometryTest {

    @Test
    fun oneBeadPerCountWhenAll108Fit() {
        assertEquals(108, MalaGeometry.beadsToShow(maxBeads = 120))
    }

    @Test
    fun otherwiseBeadsSplitTheRoundEvenly() {
        assertEquals(54, MalaGeometry.beadsToShow(maxBeads = 60))
        assertEquals(36, MalaGeometry.beadsToShow(maxBeads = 40))
        assertEquals(27, MalaGeometry.beadsToShow(maxBeads = 30))
        assertEquals(0, MalaGeometry.beadsToShow(maxBeads = 0))
    }

    @Test
    fun beadsScaleWithTheRingAndStayInsideIt() {
        val small = MalaGeometry.forRing(outerRadius = 300f, density = 2.625f)
        val large = MalaGeometry.forRing(outerRadius = 1000f, density = 2.625f)
        assertTrue(large.beadRadius > small.beadRadius)
        assertTrue(small.beadCount in 1..108 && 108 % small.beadCount == 0)
        assertTrue(large.beadOrbit + large.meruRadius <= 1000f)
        assertTrue(small.ringRadius > 0f)
    }
}
