package org.r3akt.uas

import android.content.Context

/** Local operator preferences (Android private storage; no credentials here). */
object Prefs {
    /** Radar ranges in metres (UAS-RAD-002); the first is the default. */
    val RADAR_RANGES_M = listOf(500, 1000, 2000, 5000)

    private fun prefs(context: Context) = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun radarRangeM(context: Context): Int =
        prefs(context).getInt("radar_range_m", RADAR_RANGES_M[0]).takeIf { it in RADAR_RANGES_M } ?: RADAR_RANGES_M[0]

    fun setRadarRangeM(context: Context, metres: Int) {
        require(metres in RADAR_RANGES_M)
        prefs(context).edit().putInt("radar_range_m", metres).apply()
    }
}
