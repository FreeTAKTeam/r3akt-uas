package org.r3akt.uas

import android.content.Context
import org.json.JSONObject
import org.r3akt.uas.hud.FlightAction
import org.r3akt.uas.hud.HudState
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The local session record (UAS-VID-008): one append-only JSON-lines file per cockpit session in
 * app-private storage (`files/sessions/`), read back by the SESSIONS page ([SessionRecord]).
 *
 * Why it is new storage (UAS-VID-009): no shared store holds this app's own cockpit history
 * (flights, actions and DJI's answers); it is an application session record, nothing else.
 * It holds no credentials, hub addresses or keys: only cockpit state and DJI's result texts.
 *
 * Events are written on change, never per poll; every event has a wall time (`time_ms`) and a
 * sequence number (`seq`, 0 = header), monotonic per session. Limits: [MAX_SESSIONS] files,
 * [MAX_BYTES] each, one track fix per [FIX_INTERVAL_MS] while airborne.
 * Called on the main thread; file I/O runs on one background thread.
 */
object SessionLog {
    const val MAX_SESSIONS = 50
    const val MAX_BYTES = 512L * 1024
    const val FIX_INTERVAL_MS = 5_000L

    private val io = Executors.newSingleThreadExecutor()

    private var file: File? = null
    private var variant = ""
    private var startMs = 0L
    private var seq = 0L
    private var simulated: Boolean? = null
    private var last: HudState? = null
    private var sessionId = ""
    private var lastFixNs: Long? = null
    private var bytes = 0L
    private var full = false

    /** The id of the file being written now, so the SESSIONS page does not delete it. */
    @Volatile var currentFile: String? = null
        private set

    /** The cockpit opened. The header is written with the first event, once `simulated` is known. */
    fun start(context: Context, variant: String) {
        val dir = dir(context)
        this.variant = variant
        startMs = System.currentTimeMillis()
        seq = 0
        simulated = null
        last = null
        lastFixNs = null
        bytes = 0
        full = false
        // Sortable by name: start time first.
        sessionId = UUID.randomUUID().toString()
        file = File(dir, "$startMs-$sessionId.jsonl").also { currentFile = it.name }
        io.execute {
            dir.mkdirs()
            // Keep the newest MAX_SESSIONS - 1 old records, so with this one there are at most MAX_SESSIONS.
            list(dir).drop(MAX_SESSIONS - 1).forEach { it.delete() }
        }
    }

    /** Every cockpit state, once per poll; only changes are written. */
    fun onState(state: HudState) {
        val prev = last
        last = state
        if (simulated == null) simulated = state.simulated
        // shortcut: drops what a publish build appends after " · " (its sent counter changes every poll); a source status containing " · " loses its tail.
        val status = state.status.substringBefore(" · ")
        if (prev == null || status != prev.status.substringBefore(" · ")) emit("status") { put("status", status) }
        if (prev == null || state.airborne != prev.airborne) emit("airborne") { put("airborne", state.airborne ?: JSONObject.NULL) }
        if (prev == null || state.rthActive != prev.rthActive) emit("rth") { put("active", state.rthActive) }
        if (prev == null || state.flightMode != prev.flightMode) emit("flight_mode") { put("mode", state.flightMode ?: JSONObject.NULL) }
        val now = System.nanoTime()
        val lat = state.latitudeDeg
        val lon = state.longitudeDeg
        if (state.airborne == true && lat != null && lon != null && lastFixNs.let { it == null || now - it >= FIX_INTERVAL_MS * 1_000_000 }) {
            lastFixNs = now
            emit("fix") {
                put("lat", lat)
                put("lon", lon)
                put("alt_m", state.altitudeM ?: JSONObject.NULL)
                put("alt_ref", state.altitudeReference.name)
            }
        }
    }

    /** A local flight action was sent; `error` is null when DJI accepted it. */
    fun onAction(action: FlightAction, error: String?) =
        emit("action") { put("action", action.name); put("error", error ?: JSONObject.NULL) }

    /** The cockpit closed. */
    fun end() {
        emit("end") {}
        file = null
        currentFile = null
    }

    /** All records, newest first, on the background thread; [done] gets them there too. */
    fun read(context: Context, done: (List<Pair<File, SessionRecord>>) -> Unit) = io.execute {
        done(list(dir(context)).mapNotNull { f -> runCatching { SessionRecord.parse(f.readLines()) }.getOrNull()?.let { f to it } })
    }

    /** Deletes one past record (never the one being written), on the background thread. */
    fun delete(file: File, done: () -> Unit) = io.execute {
        if (file.name != currentFile) file.delete()
        done()
    }

    private fun dir(context: Context) = File(context.filesDir, "sessions")

    private fun list(dir: File) =
        (dir.listFiles { f -> f.name.endsWith(".jsonl") } ?: emptyArray()).sortedByDescending { it.name }

    private fun emit(type: String, fields: JSONObject.() -> Unit) {
        val f = file ?: return
        if (full) return
        val lines = mutableListOf<String>()
        if (seq == 0L) lines += event("session", startMs) {
            put("format", SessionRecord.FORMAT)
            put("session_id", sessionId)
            put("variant", variant)
            put("simulated", simulated ?: JSONObject.NULL)
        }
        lines += event(type, System.currentTimeMillis(), fields)
        var text = lines.joinToString("") { "$it\n" }
        val size = text.toByteArray().size
        if (bytes + size > MAX_BYTES) {
            text = event("truncated", System.currentTimeMillis()) { put("limit_bytes", MAX_BYTES) } + "\n"
            full = true
        }
        bytes += size
        // A failed write (storage full) loses that event only; it never stops the cockpit.
        io.execute { runCatching { f.appendText(text) } }
    }

    private fun event(type: String, timeMs: Long, fields: JSONObject.() -> Unit): String =
        JSONObject().put("type", type).put("seq", seq++).put("time_ms", timeMs).apply(fields).toString()
}
