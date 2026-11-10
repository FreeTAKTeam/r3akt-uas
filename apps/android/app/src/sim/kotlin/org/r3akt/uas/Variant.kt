package org.r3akt.uas

import org.r3akt.uas.bridge.decodeTelemetry
import org.r3akt.uas.bridge.encodeTelemetry
import org.r3akt.uas.bridge.sample
import org.r3akt.uas.hud.HudState
import kotlin.math.roundToInt

object Variant {
    /** Simulated aircraft through the shared Telemeter codec, in-process only. */
    val source: AircraftSource = object : AircraftSource {
        override val name = "simulator"

        override fun describe(): String {
            val s = sample(0u)
            val packed = encodeTelemetry(s)
            val decoded = decodeTelemetry(packed)
            return buildString {
                appendLine("SIMULATED aircraft, sample 0")
                appendLine("lat ${s.latitudeDeg}, lon ${s.longitudeDeg}")
                appendLine("alt ${s.altitudeM} m ${s.altitudeReference}")
                appendLine("FIELD_TELEMETRY: ${packed.size} bytes (shared r3akt-map-core encoder)")
                appendLine("Shared decoder round trip:")
                appendLine(decoded.json)
                append("Network publication: disabled")
            }
        }

        // The simulated orbit has course, not heading (UAS-PRF-005), so heading stays unknown.
        // It offers no flight actions: nothing pretends to take off or capture.
        override fun cockpit(seq: UInt): HudState {
            val s = sample(seq)
            return HudState(
                status = "IN FLIGHT",
                simulated = s.simulated,
                airborne = true,
                groundSpeedMps = s.groundSpeedMps,
                altitudeM = s.altitudeM,
                altitudeReference = s.altitudeReference,
                latitudeDeg = s.latitudeDeg,
                longitudeDeg = s.longitudeDeg,
                batteryPercent = s.batteryPercent?.roundToInt(),
                flightTimeS = seq.toLong(),
            )
        }
    }
}
