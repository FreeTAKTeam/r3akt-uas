package org.r3akt.uas.hud

import org.r3akt.uas.bridge.AltitudeReference

/** Local flight actions; a button is enabled only when the source offers it (UAS-FLT-002). */
enum class FlightAction { TAKEOFF, LAND, RTH, STOP_RTH, PHOTO }

/**
 * What the cockpit HUD shows. `null` means unknown and is drawn as a dash,
 * never as zero (no fabricated data).
 */
data class HudState(
    val status: String,
    val simulated: Boolean,
    val airborne: Boolean? = null,
    val rthActive: Boolean = false,
    val flightMode: String? = null,
    val headingDeg: Double? = null,
    val groundSpeedMps: Double? = null,
    val verticalSpeedMps: Double? = null,
    val altitudeM: Double? = null,
    val altitudeReference: AltitudeReference = AltitudeReference.UNKNOWN,
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val homeDistanceM: Double? = null,
    val batteryPercent: Int? = null,
    val gpsSatellites: Int? = null,
    val gimbalPitchDeg: Double? = null,
    val flightTimeS: Long? = null,
    val actions: Set<FlightAction> = emptySet(),
    /** True while a live video surface should sit behind the HUD. */
    val video: Boolean = false,
)
