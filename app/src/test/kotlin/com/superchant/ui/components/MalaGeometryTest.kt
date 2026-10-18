package com.superchant.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MalaGeometryTest {

    @Test
    fun oneBeadPerCountWhenTheRoundFits() {
        assertEquals(27, MalaGeometry.beadsToShow(27, maxBeads = 60))
        assertEquals(54, MalaGeometry.beadsToShow(54, maxBeads = 60))
    }

    @Test
    fun largeRoundsGroupCountsEvenly() {
        assertEquals(54, MalaGeometry.beadsToShow(108, maxBeads = 60))
        assertEquals(36, MalaGeometry.beadsToShow(108, maxBeads = 40))
        assertEquals(27, MalaGeometry.beadsToShow(108, maxBeads = 30))
    }

    @Test
    fun awkwardSizesFallBackToEvenMarkers() {
        assertEquals(54, MalaGeometry.beadsToShow(997, maxBeads = 60))
        assertEquals(0, MalaGeometry.beadsToShow(108, maxBeads = 0))
    }

    @Test
    fun beadsScaleWithTheRingAndStayInsideIt() {
        val small = MalaGeometry.forRing(outerRadius = 300f, density = 2.625f, roundSize = 108)
        val large = MalaGeometry.forRing(outerRadius = 1000f, density = 2.625f, roundSize = 108)
        assertTrue(large.beadRadius > small.beadRadius)
        assertTrue(small.beadCount in 1..108 && 108 % small.beadCount == 0)
        assertTrue(large.beadOrbit + large.meruRadius <= 1000f)
        assertTrue(small.ringRadius > 0f)
    }
}
