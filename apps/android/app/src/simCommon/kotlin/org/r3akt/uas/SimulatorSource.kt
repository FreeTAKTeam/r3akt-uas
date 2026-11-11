package org.r3akt.uas

import org.r3akt.uas.bridge.camera
import org.r3akt.uas.bridge.decodeTelemetry
import org.r3akt.uas.bridge.encodeTelemetry
import org.r3akt.uas.bridge.sample
import org.r3akt.uas.hud.HudState
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Simulated aircraft through the shared Telemeter codec; shared by the sim and simNet variants. */
/** @param networkReport the report's last line; empty when a publishing wrapper adds its own. */
open class SimulatorSource(private val networkReport: String = "Network publication: disabled") : AircraftSource {
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
            append(networkReport)
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
            // Simulated gimbal, aimed at the orbit centre (shared with the camera point).
            gimbalPitchDeg = camera(seq).elevationDeg,
        )
    }

    override fun setupItems() = listOf(
        SetupItem("Aircraft", SetupState.OK, "SIMULATED in-process orbit; no hardware"),
        SetupItem("DJI SDK", SetupState.NOT_IN_BUILD, "The simulator build has no DJI SDK, key or controller"),
        SetupItem("Video", SetupState.NOT_IN_BUILD, "The simulator has no video"),
    )

    override fun configItems() = listOf("DJI SDK key" to "not in this build (simulator)")
    // Simulated contacts (UAS-RAD-009) at fixed coordinates around the orbit centre (45.0, 7.0):
    // a spiral in degrees from about 130 m to 6.4 km, so 13 lie beyond 5 km and 62 exceed the
    // 50-contact limit, plus two placed about 10 m from others to need the overlap chooser.
    // Each poll reports them anew; every 15th from the 8th has a position 10 minutes old
    // and the 12th none, so aged and unknown states show.
    private val simPositions: List<Pair<Double, Double>> = (0 until 60).map { i ->
        val a = Math.toRadians(i * 137.5)
        val r = 0.0012 + 0.00095 * i
        45.0 + r * cos(a) to 7.0 + r * sin(a) * 1.41421
    }.let { it + (it[0].first + 0.00009 to it[0].second) + (it[1].first to it[1].second + 0.00012) }
    override fun contacts(): List<Contact> {
        val now = System.currentTimeMillis()
        return simPositions.mapIndexed { i, (lat, lon) ->
            val position = when {
                i == 11 -> null
                i % 15 == 7 -> now - 600_000
                else -> now
            }
            Contact(
                id = "sim-contact-%02d".format(i + 1),
                name = "SIM %02d".format(i + 1),
                symbol = listOf("friendly", "hostile", "neutral", "unknown")[i % 4],
                lat = lat, lon = lon,
                source = "simulator",
                simulated = true,
                positionTimeMs = position,
                contactTimeMs = position ?: now,
                receivedTimeMs = now,
            )
        }
    }
}
