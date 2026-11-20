package org.r3akt.uas

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import org.r3akt.uas.bridge.CameraPose
import org.r3akt.uas.bridge.OwnMarker
import org.r3akt.uas.bridge.PublishState
import org.r3akt.uas.bridge.TelemetrySample
import org.r3akt.uas.bridge.markerList
import org.r3akt.uas.bridge.publishAnnounce
import org.r3akt.uas.bridge.publishOfferFix
import org.r3akt.uas.bridge.publishSetAnnounceInterval
import org.r3akt.uas.bridge.publishStart
import org.r3akt.uas.bridge.publishStatus
import org.r3akt.uas.bridge.publishStop
import org.r3akt.uas.bridge.receivedMarkers
import org.r3akt.uas.bridge.receivedReport
import org.r3akt.uas.hud.FlightAction
import org.r3akt.uas.hud.HudState
import org.r3akt.uas.hud.HudView
import java.io.File
import java.util.concurrent.Executors

/**
 * Test builds only (D-4, D-18): [inner] published to the RCH hub named by the Gradle properties
 * `r3aktHubAddress` and `r3aktHubDestination`, optionally in the RCH team `r3aktTeamUid`. Publishing
 * starts on the operator's tap in the session log and stops when the cockpit activity pauses (D-16);
 * while it runs, the same button opens the operator-marker list and a tap on the aim cross places a marker.
 * Each flavour has its own [storeName], so its identity, aircraft and markers stay its own.
 * The app reports "sent", never "accepted"; a refusal the hub sends back shows on the MESH status.
 *
 * @param fix the source's current fix, or `null` when it has none; offered on every cockpit poll,
 *   and the Rust publisher decides what is sent (UAS-VID-005).
 * @param cameraPose the camera pose for that fix, or `null` when the source cannot give a camera point.
 */
class PublishingSource(
    private val inner: AircraftSource,
    private val storeName: String,
    private val fix: (seq: UInt) -> TelemetrySample?,
    private val cameraPose: ((seq: UInt, fix: TelemetrySample) -> CameraPose?)?,
) : AircraftSource by inner {
    override val name = "${inner.name}, RCH publish test build"

    private lateinit var storeDir: String
    @Volatile private var publishing = false
    @Volatile private var stoppedByPause = false
    @Volatile private var startError: String? = null
    @Volatile private var lastSeq = 0u
    @Volatile private var lastFix: TelemetrySample? = null
    /** The app's own markers as last read from its store, for the radar and the map. */
    @Volatile private var markerCache: List<OwnMarker> = emptyList()
    // Start and stop run here, in order: stop can block for one send's path wait (POST F1).
    private val worker = Executors.newSingleThreadExecutor()

    private var app: Application? = null
    /** The announce interval last handed to the publisher, so a SETTINGS change applies while running. */
    @Volatile private var announceS = 0L

    override fun install(app: Application) {
        inner.install(app)
        this.app = app
        storeDir = File(app.noBackupFilesDir, storeName).path
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            // The cockpit only: DJI's USB accessory activity also pauses it on a controller reconnect.
            override fun onActivityPaused(activity: Activity) {
                if (activity is MainActivity) stopPublishing()
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun logAction(): Pair<String, (Activity) -> Unit> =
        if (publishing) "MARKERS" to { activity -> MarkerPanel(activity, ownMarkers).show() }
        else "START PUBLISH" to { _ -> startPublishing() }

    private val ownMarkers = BridgeMarkerOps(worker) { publishing }

    override fun markerOps(): MarkerOps = ownMarkers

    // Markers are stored by the publisher, so placing one needs publishing on.
    override fun aimAction(): (Activity, HudView) -> Unit = { activity, hud ->
        if (publishing) AimMarkerPanel(activity, hud, worker, cameraPose?.let { { pose(it) } }).show()
        else hud.alert = "NO MARKER · PUBLISHING OFF" to "Open MESH, tap START PUBLISH"
    }

    // Radar pick (D-10 (b)): the aim-cross naming flow, placed at the picked range and true bearing.
    override fun radarPick(): (Activity, Double, Double) -> Unit = { activity, rangeM, bearingDeg ->
        val hud = hudIn(activity.window.decorView)
        if (publishing && hud != null) AimMarkerPanel(activity, hud, worker, null, rangeM to bearingDeg).show()
        else Toast.makeText(activity, "NO MARKER · PUBLISHING OFF: open MESH, tap START PUBLISH", Toast.LENGTH_LONG).show()
    }

    private fun hudIn(v: View): HudView? = v as? HudView
        ?: (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { hudIn(g.getChildAt(it)) } }

    // Inner contacts (simulated ones in simNet), this app's markers and the team markers received
    // from the hub (UAS-PRD-013). A marker placed from a simulated aircraft carries the
    // "SIMULATED · " note prefix (markers.rs), so it counts as simulated.
    override fun contacts(): List<Contact> {
        val own = markerCache
        val received = receivedMarkers().filter { r -> own.none { it.objectId == r.marker.objectId } }
        return inner.contacts() + own.map { m ->
            Contact(
                id = m.objectId, name = m.name, symbol = m.symbol, lat = m.lat, lon = m.lon,
                source = "this app",
                simulated = m.notes?.startsWith("SIMULATED") == true,
                positionTimeMs = m.updatedMs, contactTimeMs = m.updatedMs, receivedTimeMs = null,
            )
        } + received.map { r ->
            val m = r.marker
            Contact(
                id = m.objectId, name = m.name, symbol = m.symbol, lat = m.lat, lon = m.lon,
                source = r.source,
                simulated = m.notes?.startsWith("SIMULATED") == true,
                positionTimeMs = m.updatedMs, contactTimeMs = m.updatedMs, receivedTimeMs = r.receivedMs,
            )
        }
    }

    // The pose that belongs to the last offered fix, stamped with that fix's time.
    private fun pose(provider: (UInt, TelemetrySample) -> CameraPose?): CameraPose? =
        lastFix?.let { provider(lastSeq, it) }

    private fun startPublishing() {
        publishing = true
        stoppedByPause = false
        worker.execute {
            startError = runCatching {
                val h = hub()
                publishStart(storeDir, h.address.orEmpty(), h.destination.orEmpty(), h.teamUid)
            }.exceptionOrNull()?.let { it.message ?: it.toString() }
            if (startError != null) publishing = false
        }
    }

    private fun stopPublishing() {
        if (!publishing) return
        publishing = false
        stoppedByPause = true
        worker.execute { publishStop() }
    }

    override fun perform(action: FlightAction, onResult: (String?) -> Unit) =
        if (action != FlightAction.ANNOUNCE) inner.perform(action, onResult)
        else if (!publishing) onResult("Start publishing first: MESH → START PUBLISH")
        else { publishAnnounce(); onResult(null) }

    override fun cockpit(seq: UInt): HudState {
        val state = inner.cockpit(seq)
        if (!publishing) return if (stoppedByPause) state.copy(status = "${state.status} · PUBLISH STOPPED") else state
        app?.let(Prefs::announceIntervalS)?.takeIf { it != announceS }?.let {
            announceS = it
            runCatching { publishSetAnnounceInterval(it.toULong()) }
        }
        fix(seq)?.let {
            lastSeq = seq
            lastFix = it
            publishOfferFix(it)
        }
        // The store is read on the worker, like every other marker call.
        worker.execute { markerCache = runCatching { markerList() }.getOrDefault(markerCache) }
        val status = publishStatus()
        // MESH is on while the Reticulum interface to the hub reports its stream connected.
        return state.copy(
            status = "${state.status} · ${status.state.name} ${status.fixesSent}",
            mesh = status.hubConnected,
            actions = state.actions + FlightAction.ANNOUNCE,
        )
    }

    override fun describe(): String = inner.describe() + "\n" + publicationReport()

    private fun publicationReport(): String {
        val s = publishStatus()
        return buildString {
            appendLine("RCH publication (test build): ${if (publishing) "on" else "off"}")
            val h = hub()
            appendLine("hub ${h.address ?: "(not set)"}")
            appendLine("hub destination ${h.destination ?: "(not set)"}")
            appendLine("app destination hash ${s.destinationHash ?: "(starts with the first publish)"}")
            appendLine("aircraft object id ${s.objectId ?: "-"}")
            appendLine("team ${s.teamUid ?: "none (field 13 only)"}")
            appendLine("state ${s.state.name}${if (s.state == PublishState.SENT) " (handed to LXMF, not confirmed by the hub)" else ""}")
            appendLine("hub connected ${s.hubConnected}, create sent ${s.createSent}, fixes sent ${s.fixesSent}")
            appendLine("operator markers waiting for the hub: ${s.markersPending}")
            appendLine(receivedReport())
            appendLine("last announce ${s.lastAnnounceS?.let { "${System.currentTimeMillis() / 1000 - it} s ago" } ?: "-"}, every ${announceS.takeIf { it > 0 } ?: "-"} s")
            val fix = s.lastSentFix
            appendLine(
                if (fix == null) "last sent fix -"
                else "last sent fix t=${fix.fixTimeS} lat ${fix.latitudeDeg} lon ${fix.longitudeDeg}",
            )
            (startError ?: s.lastError)?.let { appendLine("error $it") }
        }.trimEnd()
    }

    override fun setupItems(): List<SetupItem> {
        val s = publishStatus()
        val error = startError ?: s.lastError
        val h = hub()
        val missing = listOfNotNull("hub address".takeIf { h.address == null }, "hub destination".takeIf { h.destination == null })
        return inner.setupItems() + listOf(
            if (missing.isEmpty()) SetupItem("Hub", SetupState.OK, "${h.address}, destination ${h.destination}")
            else SetupItem("Hub", SetupState.PROBLEM, "Not set: ${missing.joinToString()}; enter them on SETTINGS → HUB"),
            SetupItem(
                "Hub publishing",
                when {
                    error != null || s.state == PublishState.FAILED -> SetupState.PROBLEM
                    publishing && s.state == PublishState.SENT -> SetupState.OK
                    else -> SetupState.WAITING
                },
                when {
                    publishing -> "${s.state.name}, hub connected ${s.hubConnected}, fixes sent ${s.fixesSent}" +
                        if (s.state == PublishState.SENT) " (handed to LXMF, not confirmed by the hub)" else ""
                    stoppedByPause -> "Stopped when the cockpit paused; MESH → START PUBLISH"
                    else -> "Off; MESH → START PUBLISH"
                } + (error?.let { "\nError: $it" } ?: ""),
            ),
        )
    }

    // Saved on SETTINGS → HUB; otherwise the build's defaults, which only debug builds carry.
    private fun hub(): Prefs.Hub {
        val saved = app?.let(Prefs::hub) ?: Prefs.Hub(null, null, null)
        return Prefs.Hub(
            saved.address ?: BuildConfig.HUB_ADDRESS.ifEmpty { null },
            saved.destination ?: BuildConfig.HUB_DESTINATION.ifEmpty { null },
            saved.teamUid ?: BuildConfig.TEAM_UID.ifEmpty { null },
        )
    }

    override fun configItems() = inner.configItems() + hub().let { h ->
        listOf(
            "Hub address" to (h.address ?: "not set"),
            "Hub destination" to (h.destination ?: "not set"),
            "Team UID" to (h.teamUid ?: "none"),
        )
    } + listOf(
        "App identity (destination hash)" to (publishStatus().destinationHash ?: "created on the first publish"),
    )
}
