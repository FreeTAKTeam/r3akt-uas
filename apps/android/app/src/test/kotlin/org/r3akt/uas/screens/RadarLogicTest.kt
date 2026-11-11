package org.r3akt.uas.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Runs the case files in `fixtures/radar/` (path from the `radar.fixtures` system property). */
class RadarLogicTest {
    private fun lines(name: String): List<String> =
        File(System.getProperty("radar.fixtures"), name).readLines()
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
            .also { check(it.isNotEmpty()) { "no cases in $name" } }

    private fun cols(line: String) = line.split(Regex("\\s+"))

    @Test fun projection() = lines("projection.txt").forEach { line ->
        val (range, bearing, heading, max, ex, ey) = cols(line)
        val hdg = heading.toDoubleOrNull()
        val (x, y) = RadarLogic.project(range.toDouble(), bearing.toDouble(), hdg, max.toDouble(), 1.0)
        assertEquals(line, ex.toDouble(), x, 1e-6)
        assertEquals(line, ey.toDouble(), y, 1e-6)
        // The radar pick inverts the projection.
        val (r, b) = RadarLogic.unproject(x, y, hdg, max.toDouble(), 1.0)
        assertEquals(line, range.toDouble(), r, 1e-6)
        assertEquals(line, 0.0, (RadarLogic.normalize(b - bearing.toDouble() + 180) - 180), 1e-6)
    }

    @Test fun range() = lines("range.txt").forEach { line ->
        val (range, max, expected) = cols(line)
        assertEquals(line, expected == "in", RadarLogic.inRange(range.toDouble(), max.toDouble()))
    }

    @Test fun nearest() = lines("nearest.txt").forEach { line ->
        val (head, list) = line.split(":").map { it.trim() }
        val (limit, max, limited, beyond, drawn, nearest) = cols(head)
        val ranges = list.split(',').filter { it.isNotBlank() }.map { it.trim().toDouble() }
        val sel = RadarLogic.select(ranges, max.toDouble(), limit.toInt())
        assertEquals(line, limited.toBoolean(), sel.limited)
        assertEquals(line, beyond.toInt(), sel.beyond)
        assertEquals(line, drawn.toInt(), sel.drawn.size)
        assertEquals(line, nearest.toIntOrNull(), sel.drawn.firstOrNull())
        assertEquals(line, sel.drawn.sortedBy { ranges[it] }, sel.drawn)
    }

    @Test fun hits() = lines("hit.txt").forEach { line ->
        val (head, list) = line.split(":").map { it.trim() }
        val (x, y, touch, expected) = cols(head)
        val points = cols(list).map { p -> p.split(',').let { it[0].toDouble() to it[1].toDouble() } }
        val want = if (expected == "-") emptyList() else expected.split(',').map(String::toInt)
        assertEquals(line, want, RadarLogic.hits(points, x.toDouble(), y.toDouble(), touch.toDouble()))
    }

    @Test fun aged() = lines("aged.txt").forEach { line ->
        val (age, who, expected) = cols(line)
        val now = 1_800_000_000_000L
        val time = age.toLongOrNull()?.let { now - it * 1000 }
        assertEquals(line, expected == "aged", RadarLogic.aged(time, now, own = who == "own"))
    }

    @Test fun format() = lines("format.txt").forEach { line ->
        val c = cols(line)
        val expected = c.drop(2).joinToString(" ")
        val now = 1_800_000_000_000L
        val got = when (c[0]) {
            "age" -> RadarLogic.age(c[1].toLongOrNull()?.let { now - it }, now)
            "distance" -> RadarLogic.distance(c[1].toDouble())
            "bearing" -> RadarLogic.bearing(c[1].toDouble())
            else -> error("unknown kind in: $line")
        }
        assertEquals(line, expected, got)
    }

    private operator fun <T> List<T>.component6() = this[5]
}
