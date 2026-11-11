package org.r3akt.uas.screens

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A WGS84 position in degrees. */
data class LatLon(val lat: Double, val lon: Double)

/**
 * The flight track: at most [max] entries, oldest first. A `null` entry is a gap (position
 * lost or not observed); the map never draws a line across it.
 */
class Track(private val max: Int = 3600) {
    private val items = ArrayDeque<LatLon?>()
    val points: List<LatLon?> get() = items

    fun add(lat: Double?, lon: Double?) {
        if (lat == null || lon == null) return gap()
        val p = LatLon(lat, lon)
        if (items.lastOrNull() == p) return // a hovering aircraft adds nothing
        items.addLast(p)
        trim()
    }

    fun gap() {
        if (items.lastOrNull() != null) items.addLast(null)
        trim()
    }

    private fun trim() {
        while (items.size > max) items.removeFirst()
        while (items.isNotEmpty() && items.first() == null) items.removeFirst()
    }
}

/**
 * Display-only local equirectangular projection around [centre]: x east, y down, in pixels
 * of [metresPerPixel]. Numbers shown to the operator never come from here.
 */
class LocalProjection(val centre: LatLon, val metresPerPixel: Double) {
    private val k = cos(Math.toRadians(centre.lat)).coerceAtLeast(1e-6)

    fun x(p: LatLon): Double = wrap(p.lon - centre.lon) * k * M_PER_DEG / metresPerPixel
    fun y(p: LatLon): Double = -(p.lat - centre.lat) * M_PER_DEG / metresPerPixel

    /** The position at pixel offset ([x], [y]) from the centre. */
    fun at(x: Double, y: Double) = LatLon(
        (centre.lat - y * metresPerPixel / M_PER_DEG).coerceIn(-89.0, 89.0),
        wrap(centre.lon + x * metresPerPixel / (k * M_PER_DEG)),
    )

    private companion object {
        /** WGS84 semi-major axis, metres per degree of arc. */
        const val M_PER_DEG = 6378137.0 * PI / 180

        /** Longitude difference into [-180, 180). */
        fun wrap(d: Double) = ((d + 180) % 360 + 360) % 360 - 180
    }
}

/** The longest 1-2-5 length in metres that fits in [maxPx] pixels. */
fun scaleBarMetres(metresPerPixel: Double, maxPx: Double): Double {
    val max = metresPerPixel * maxPx
    val m = 10.0.pow(floor(log10(max)))
    return when {
        5 * m <= max -> 5 * m
        2 * m <= max -> 2 * m
        else -> m
    }
}
