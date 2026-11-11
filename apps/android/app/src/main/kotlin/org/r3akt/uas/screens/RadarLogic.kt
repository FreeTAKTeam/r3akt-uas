package org.r3akt.uas.screens

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Radar arithmetic without Android, tested on the JVM against `fixtures/radar/`.
 * Range and true bearing come in from the caller (Android's `Location.distanceBetween`,
 * as for the cockpit's home distance, gap G-13); nothing here computes Earth positions.
 * Screen offsets are from the radar centre, x right and y down, in pixels.
 */
object RadarLogic {
    /** UAS-RAD-003. */
    const val LIMIT = 50

    /** A reported position older than this is drawn as aged (UAS-RAD-007). */
    const val AGED_AFTER_MS = 120_000L

    /**
     * Offset of a contact at [rangeM] and true [bearingDeg] on a radar of [radiusPx] showing
     * [maxRangeM]: heading-up with an aircraft heading (UAS-RAD-001), north-up without one.
     */
    fun project(rangeM: Double, bearingDeg: Double, headingDeg: Double?, maxRangeM: Double, radiusPx: Double): Pair<Double, Double> {
        val a = Math.toRadians(bearingDeg - (headingDeg ?: 0.0))
        val r = rangeM / maxRangeM * radiusPx
        return r * sin(a) to -r * cos(a)
    }

    /** The inverse of [project]: (range m, true bearing 0 until 360) of a screen offset. */
    fun unproject(dx: Double, dy: Double, headingDeg: Double?, maxRangeM: Double, radiusPx: Double): Pair<Double, Double> =
        hypot(dx, dy) / radiusPx * maxRangeM to normalize(Math.toDegrees(atan2(dx, -dy)) + (headingDeg ?: 0.0))

    fun normalize(deg: Double): Double = ((deg % 360) + 360) % 360

    fun inRange(rangeM: Double, maxRangeM: Double): Boolean = rangeM <= maxRangeM

    /**
     * What the radar draws: [drawn] are the indices of the nearest [limit] contacts that are
     * inside the range, nearest first; [limited] when there are more contacts than [limit];
     * [beyond] counts every contact outside the range (UAS-RAD-002, UAS-RAD-003).
     */
    class Selection(val drawn: List<Int>, val limited: Boolean, val beyond: Int)

    fun select(rangesM: List<Double>, maxRangeM: Double, limit: Int = LIMIT): Selection {
        val nearest = rangesM.indices.sortedBy { rangesM[it] }.take(limit)
        return Selection(
            drawn = nearest.filter { inRange(rangesM[it], maxRangeM) },
            limited = rangesM.size > limit,
            beyond = rangesM.count { !inRange(it, maxRangeM) },
        )
    }

    /** Indices of [points] within [touchPx] of the tap, nearest first; several mean a chooser (UAS-RAD-005). */
    fun hits(points: List<Pair<Double, Double>>, x: Double, y: Double, touchPx: Double): List<Int> {
        fun d(i: Int) = hypot(points[i].first - x, points[i].second - y)
        return points.indices.filter { d(it) <= touchPx }.sortedBy(::d)
    }

    /**
     * A reported contact is aged when its position time is unknown or older than [AGED_AFTER_MS]
     * (UAS-RAD-007). The app's own markers are set here, not reported, so they cannot be lost.
     */
    fun aged(positionTimeMs: Long?, nowMs: Long, own: Boolean): Boolean =
        !own && (positionTimeMs == null || nowMs - positionTimeMs > AGED_AFTER_MS)

    /** Age of [timeMs] at [nowMs]; unknown stays unknown, never 0 (UAS-RAD-006). */
    fun age(timeMs: Long?, nowMs: Long): String {
        if (timeMs == null) return "UNKNOWN"
        if (timeMs > nowMs) return "AHEAD OF CLOCK"
        val s = (nowMs - timeMs) / 1000
        return when {
            s < 120 -> "$s s"
            s < 7200 -> "${s / 60} min"
            else -> "${s / 3600} h"
        }
    }

    /** "125 m", "1 km", "1.25 km". */
    fun distance(m: Double): String =
        if (m < 1000) "%.0f m".format(Locale.ROOT, m)
        else "%.2f".format(Locale.ROOT, m / 1000).trimEnd('0').trimEnd('.') + " km"

    /** Three-digit true bearing, "045°"; 359.6 rounds to "000°". */
    fun bearing(deg: Double): String = "%03d°".format(Locale.ROOT, normalize(deg).roundToLong() % 360)
}
