package org.r3akt.uas

import org.json.JSONObject
import java.util.Locale

/**
 * One session record read back from its JSON-lines file ([SessionLog] writes it). Plain JVM code
 * (no Android API besides `org.json`), so the unit test reads the fixture with the same reader.
 * Unknown values stay null; altitude is summarised only where it is take-off-relative.
 */
data class SessionRecord(
    val id: String,
    val variant: String,
    /** Null when the session ended before the source reported whether it was simulated. */
    val simulated: Boolean?,
    val startMs: Long,
    /** Wall time of the end event; null when the app stopped without one. */
    val endMs: Long?,
    val lastMs: Long,
    val flights: Int,
    /** Highest take-off-relative altitude among the recorded fixes; never MSL, HAE or AGL. */
    val maxTakeoffAltM: Double?,
    val actions: Int,
    val rejections: Int,
    /** True when the record hit its size limit and later events were not written. */
    val truncated: Boolean,
    val events: List<Event>,
) {
    data class Event(val seq: Long, val timeMs: Long, val text: String)

    companion object {
        const val FORMAT = "r3akt-uas-session/1"

        /** Reads one file's lines; null when the header is missing or of another format. Broken lines are skipped. */
        fun parse(lines: List<String>): SessionRecord? {
            val objects = lines.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            val head = objects.firstOrNull()?.takeIf { it.optString("type") == "session" && it.optString("format") == FORMAT }
                ?: return null
            var flights = 0
            var lastKnownAirborne: Boolean? = null
            var maxAlt: Double? = null
            var actions = 0
            var rejections = 0
            var endMs: Long? = null
            var truncated = false
            val events = objects.drop(1).map { e ->
                val text = when (e.optString("type")) {
                    "status" -> "STATUS ${e.optString("status")}"
                    "airborne" -> when (e.nullable("airborne") as Boolean?) {
                        // A link drop in flight (unknown) followed by airborne again is the same flight.
                        true -> { if (lastKnownAirborne != true) flights++; lastKnownAirborne = true; "AIRBORNE" }
                        false -> { lastKnownAirborne = false; "LANDED" }
                        null -> "AIRBORNE UNKNOWN"
                    }
                    "rth" -> if (e.optBoolean("active")) "RTH ON" else "RTH OFF"
                    "flight_mode" -> "FLIGHT MODE ${e.nullable("mode") ?: "unknown"}"
                    "fix" -> {
                        val alt = e.nullable("alt_m") as Number?
                        val ref = e.optString("alt_ref", "UNKNOWN")
                        if (alt != null && ref == "TAKEOFF_RELATIVE") maxAlt = maxOf(maxAlt ?: alt.toDouble(), alt.toDouble())
                        "FIX %.5f, %.5f · %s".format(Locale.ROOT, e.getDouble("lat"), e.getDouble("lon"), altitudeText(alt?.toDouble(), ref))
                    }
                    "action" -> {
                        actions++
                        val error = e.nullable("error") as String?
                        if (error != null) rejections++
                        "${e.optString("action").replace('_', ' ')} " + (error?.let { "REJECTED · DJI: $it" } ?: "ACCEPTED")
                    }
                    "truncated" -> { truncated = true; "RECORD FULL (${e.optLong("limit_bytes") / 1024} KiB): later events not recorded" }
                    "end" -> { endMs = e.optLong("time_ms"); "END" }
                    else -> e.optString("type").uppercase()
                }
                Event(e.optLong("seq"), e.optLong("time_ms"), text)
            }
            val startMs = head.optLong("time_ms")
            return SessionRecord(
                id = head.optString("session_id"),
                variant = head.optString("variant"),
                simulated = head.nullable("simulated") as Boolean?,
                startMs = startMs,
                endMs = endMs,
                lastMs = events.maxOfOrNull { it.timeMs } ?: startMs,
                flights = flights,
                maxTakeoffAltM = maxAlt,
                actions = actions,
                rejections = rejections,
                truncated = truncated,
                events = events,
            )
        }

        /** Altitude with its reference; take-off-relative is labelled as such and never called MSL or AGL. */
        fun altitudeText(altM: Double?, ref: String): String = when {
            altM == null -> "alt unknown"
            ref == "TAKEOFF_RELATIVE" -> "%.0f m above take-off".format(Locale.ROOT, altM)
            ref == "UNKNOWN" -> "%.0f m, reference unknown".format(Locale.ROOT, altM)
            else -> "%.0f m %s".format(Locale.ROOT, altM, ref)
        }

        private fun JSONObject.nullable(key: String): Any? = if (isNull(key)) null else get(key)
    }
}
