package com.superchant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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

    @Test
    fun meruBeadClearsItsNeighboursLikeTheBeadsClearEachOther() {
        for ((outer, beads) in listOf(1000f to 108, 410f to 54, 198f to 36)) {
            val g = MalaGeometry.forRing(outerRadius = outer, density = 2.625f)
            assertEquals("bead count at outer=$outer", beads, g.beadCount)
            fun position(angle: Double) = Pair(g.beadOrbit * cos(angle), g.beadOrbit * sin(angle))
            fun distance(a: Double, b: Double): Double {
                val (ax, ay) = position(a)
                val (bx, by) = position(b)
                return hypot(ax - bx, ay - by)
            }
            val meru = -PI / 2
            val beadGap = distance(g.beadAngle(0), g.beadAngle(1)) - 2 * g.beadRadius
            val firstGap = distance(meru, g.beadAngle(0)) - g.meruRadius - g.beadRadius
            val lastGap = distance(meru, g.beadAngle(g.beadCount - 1)) - g.meruRadius - g.beadRadius
            assertTrue("beads overlap at outer=$outer", beadGap > 0)
            assertEquals("first bead gap at outer=$outer", beadGap, firstGap, beadGap * 0.05)
            assertEquals("last bead gap at outer=$outer", beadGap, lastGap, beadGap * 0.05)
        }
    }
}
