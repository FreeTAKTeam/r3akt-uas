package org.r3akt.uas

import org.r3akt.uas.bridge.decodeTelemetry
import org.r3akt.uas.bridge.encodeTelemetry
import org.r3akt.uas.bridge.sample

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
    }
}
