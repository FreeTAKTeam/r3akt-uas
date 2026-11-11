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
import org.r3akt.uas.bridge.publishOfferFix
import org.r3akt.uas.bridge.publishStart
import org.r3akt.uas.bridge.publishStatus
import org.r3akt.uas.bridge.publishStop
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
 * The app reports "sent", never "accepted": it cannot read the hub's results (G-17).
 *
 * @param fix the source's current fix, or `null` when it has none; offered on every cockpit poll,
 *   and the Rust publisher decides what is sent (UAS-VID-005).
 * @param cameraPose the camera pose for that fix, or `null` when the source cannot give a camera point.
 */
class PublishingSource(
    private val inner: AircraftSource,
    private val storeName: String,
    private val fix: (seq: UInt) -> TelemetrySample?,
    private val cameraPose: ((seq: UInt, fix: TelemetrySample) -> CameraPose)?,
) : AircraftSource by inner {
    override val name = "${inner.name}, RCH publish test build"

    private lateinit var storeDir: String
    @Volatile private var publishing = false
    @Volatile private var stoppedByPause = false
    @Volatile private var startError: String? = null
    @Volatile private var lastSeq = 0u
    @Volatile private var lastFix: TelemetrySample? = null
    /** The app's own markers as last read from its store, for the radar and the map. */
    @Volatile private var ownMarkers: List<OwnMarker> = emptyList()
    // Start and stop run here, in order: stop can block for one send's path wait (POST F1).
    private val worker = Executors.newSingleThreadExecutor()

    override fun install(app: Application) {
        inner.install(app)
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
        if (publishing) "MARKERS" to { activity -> MarkerPanel(activity, worker).show() }
        else "START PUBLISH" to { _ -> startPublishing() }

    // Markers are stored by the publisher, so placing one needs publishing on.
    override fun aimAction(): (Activity, HudView) -> Unit = { activity, hud ->
        if (publishing) AimMarkerPanel(activity, hud, worker, cameraPose?.let { { pose(it) } }).show()
        else hud.alert = "NO MARKER · PUBLISHING OFF" to "Open LOG, tap START PUBLISH"
    }

    // Radar pick (D-10 (b)): the aim-cross naming flow, placed at the picked range and true bearing.
    override fun radarPick(): (Activity, Double, Double) -> Unit = { activity, rangeM, bearingDeg ->
        val hud = hudIn(activity.window.decorView)
        if (publishing && hud != null) AimMarkerPanel(activity, hud, worker, null, rangeM to bearingDeg).show()
        else Toast.makeText(activity, "NO MARKER · PUBLISHING OFF: open LOG, tap START PUBLISH", Toast.LENGTH_LONG).show()
    }

    private fun hudIn(v: View): HudView? = v as? HudView
        ?: (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { hudIn(g.getChildAt(it)) } }

    // Inner contacts (simulated ones in simNet) plus this app's markers. A marker placed from a
    // simulated aircraft carries the "SIMULATED · " note prefix (markers.rs), so it counts as simulated.
    override fun contacts(): List<Contact> = inner.contacts() + ownMarkers.map { m ->
        Contact(
            id = m.objectId, name = m.name, symbol = m.symbol, lat = m.lat, lon = m.lon,
            source = "this app",
            simulated = m.notes?.startsWith("SIMULATED") == true,
            positionTimeMs = m.updatedMs, contactTimeMs = m.updatedMs, receivedTimeMs = null,
        )
    }

    // The pose that belongs to the last offered fix, stamped with that fix's time.
    private fun pose(provider: (UInt, TelemetrySample) -> CameraPose): CameraPose? =
        lastFix?.let { provider(lastSeq, it) }

    private fun startPublishing() {
        publishing = true
        stoppedByPause = false
        worker.execute {
            startError = runCatching {
                publishStart(storeDir, BuildConfig.HUB_ADDRESS, BuildConfig.HUB_DESTINATION, BuildConfig.TEAM_UID.ifEmpty { null })
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

    override fun cockpit(seq: UInt): HudState {
        val state = inner.cockpit(seq)
        if (!publishing) return if (stoppedByPause) state.copy(status = "${state.status} · PUBLISH STOPPED") else state
        fix(seq)?.let {
            lastSeq = seq
            lastFix = it
            publishOfferFix(it)
        }
        // The store is read on the worker, like every other marker call.
        worker.execute { ownMarkers = runCatching { markerList() }.getOrDefault(ownMarkers) }
        val status = publishStatus()
        return state.copy(status = "${state.status} · ${status.state.name} ${status.fixesSent}")
    }

    override fun describe(): String = inner.describe() + "\n" + publicationReport()

    private fun publicationReport(): String {
        val s = publishStatus()
        return buildString {
            appendLine("RCH publication (test build): ${if (publishing) "on" else "off"}")
            appendLine("hub ${BuildConfig.HUB_ADDRESS.ifEmpty { "(r3aktHubAddress not set)" }}")
            appendLine("hub destination ${BuildConfig.HUB_DESTINATION.ifEmpty { "(r3aktHubDestination not set)" }}")
            appendLine("app destination hash ${s.destinationHash ?: "(starts with the first publish)"}")
            appendLine("aircraft object id ${s.objectId ?: "-"}")
            appendLine("team ${s.teamUid ?: "none (field 13 only)"}")
            appendLine("state ${s.state.name}${if (s.state == PublishState.SENT) " (handed to LXMF, not confirmed by the hub)" else ""}")
            appendLine("hub connected ${s.hubConnected}, create sent ${s.createSent}, fixes sent ${s.fixesSent}")
            appendLine("operator markers waiting for the hub: ${s.markersPending}")
            val fix = s.lastSentFix
            appendLine(
                if (fix == null) "last sent fix -"
                else "last sent fix t=${fix.fixTimeS} lat ${fix.latitudeDeg} lon ${fix.longitudeDeg}",
            )
            (startError ?: s.lastError)?.let { appendLine("error $it") }
        }.trimEnd()
    }
}
