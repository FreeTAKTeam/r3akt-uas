package org.r3akt.uas.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapModelTest {
    @Test
    fun lostPositionIsAGapNeverALeadingOrDoubleOne() {
        val t = Track()
        t.add(null, null)
        t.add(1.0, 2.0)
        t.add(1.0, null)
        t.add(null, null)
        t.gap()
        t.add(1.1, 2.1)
        assertEquals(listOf(LatLon(1.0, 2.0), null, LatLon(1.1, 2.1)), t.points)
    }

    @Test
    fun repeatedPositionIsKeptOnce() {
        val t = Track()
        repeat(5) { t.add(1.0, 2.0) }
        assertEquals(1, t.points.size)
    }

    @Test
    fun trackIsBoundedAndDropsOldestFirst() {
        val t = Track(max = 3)
        for (i in 1..5) t.add(i.toDouble(), 0.0)
        assertEquals(listOf(3.0, 4.0, 5.0), t.points.map { it!!.lat })
        // A gap that becomes the oldest entry is dropped with what it separated.
        t.gap(); t.add(6.0, 0.0); t.add(7.0, 0.0)
        assertEquals(listOf(LatLon(6.0, 0.0), LatLon(7.0, 0.0)), t.points)
        assertNull(Track(max = 2).apply { gap() }.points.firstOrNull())
    }

    @Test
    fun projectionRoundTrip() {
        for (centre in listOf(LatLon(45.46, 9.19), LatLon(-33.9, 151.2), LatLon(60.0, 179.99))) {
            val p = LocalProjection(centre, 2.5)
            val q = LatLon(centre.lat + 0.004, centre.lon + 0.02)
            val back = p.at(p.x(q), p.y(q))
            assertEquals(q.lat, back.lat, 1e-9)
            assertEquals(LocalProjection(q, 1.0).x(back), 0.0, 1e-6) // across the antimeridian too
            assertEquals(0.0, p.x(centre), 1e-12)
        }
    }

    @Test
    fun northIsUpAndEastIsRight() {
        val p = LocalProjection(LatLon(10.0, 10.0), 1.0)
        assertTrue(p.y(LatLon(10.001, 10.0)) < 0)
        assertTrue(p.x(LatLon(10.0, 10.001)) > 0)
        // One degree of latitude is about 111 km.
        assertEquals(111_319.5, -p.y(LatLon(11.0, 10.0)), 1.0)
    }

    @Test
    fun scaleBarIsOneTwoFive() {
        assertEquals(100.0, scaleBarMetres(1.0, 150.0), 0.0)
        assertEquals(200.0, scaleBarMetres(1.0, 250.0), 0.0)
        assertEquals(500.0, scaleBarMetres(2.0, 300.0), 0.0)
        assertEquals(1000.0, scaleBarMetres(10.0, 100.0), 0.0)
    }
}
