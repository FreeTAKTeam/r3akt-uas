package org.r3akt.uas

import android.app.Activity
import android.app.Application
import android.os.Bundle
import org.r3akt.uas.bridge.PublishState
import org.r3akt.uas.bridge.publishOfferFix
import org.r3akt.uas.bridge.publishStart
import org.r3akt.uas.bridge.publishStatus
import org.r3akt.uas.bridge.publishStop
import org.r3akt.uas.bridge.sample
import org.r3akt.uas.hud.HudState
import java.io.File
import java.util.concurrent.Executors

object Variant {
    val source: AircraftSource = PublishingSimulator()
}

/**
 * Test build only (D-4): the simulator, published to the RCH hub named by the Gradle
 * properties `r3aktHubAddress` and `r3aktHubDestination`, optionally in the RCH team `r3aktTeamUid`
 * (then the hub relays the aircraft to that team's REM clients). Publishing starts on the operator's
 * tap in the session log and stops whenever the cockpit leaves the foreground; while it runs,
 * the same button re-sends the last fix.
 * The app reports "sent", never "accepted": it cannot read the hub's results (G-17).
 */
class PublishingSimulator : SimulatorSource() {
    override val name = "simulator, RCH publish test build"

    private lateinit var storeDir: String
    @Volatile private var publishing = false
    @Volatile private var startError: String? = null
    // Start and stop run here, in order: stop can block for one send's path wait (POST F1).
    private val worker = Executors.newSingleThreadExecutor()

    override fun install(app: Application) {
        storeDir = File(app.noBackupFilesDir, "publish").path
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity) = stopPublishing()
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    // Leaving the cockpit stops publishing; while it runs, the button opens the operator-marker panel.
    override fun logAction(): Pair<String, (Activity) -> Unit> =
        if (publishing) "MARKERS" to { activity -> MarkerPanel(activity, worker).show() }
        else "START PUBLISH" to { _ -> startPublishing() }

    private fun startPublishing() {
        publishing = true
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
        worker.execute { publishStop() }
    }

    override fun cockpit(seq: UInt): HudState {
        val state = super.cockpit(seq)
        if (!publishing) return state
        // The orbit's shape with wall-clock time: the hub ignores old and far-future fixes.
        val now = (System.currentTimeMillis() / 1000).toULong()
        publishOfferFix(sample(seq).copy(sampleTimeS = now, fixTimeS = now))
        val status = publishStatus()
        return state.copy(status = "${state.status} · ${status.state.name} ${status.fixesSent}")
    }

    override fun publicationReport(): String {
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
