package org.r3akt.uas

import android.app.Application
import android.content.Context
import android.view.View
import org.r3akt.uas.hud.FlightAction
import org.r3akt.uas.hud.HudState

/** The variant-specific aircraft source. Each flavor provides `Variant`. */
interface AircraftSource {
    val name: String

    /** Cockpit refresh period. */
    val periodMs: Long get() = 1000

    /** Called from Application.attachBaseContext. */
    fun install(app: Application) {}

    /** Called once from the cockpit activity; a source that connects to hardware starts here. */
    fun start(context: Context) {}

    /** A human-readable status report, shown from the cockpit's LOG button. */
    fun describe(): String

    /** Cockpit state for update `seq` (polled every [periodMs]). Unknown values stay null. */
    fun cockpit(seq: UInt): HudState

    /** Runs a local flight action now; `onResult(null)` = accepted, otherwise the error text. May call back on any thread. */
    fun perform(action: FlightAction, onResult: (String?) -> Unit) = onResult("Not available")

    /** An extra button in the session log as (label, action on the cockpit activity), or null for none. */
    fun logAction(): Pair<String, (android.app.Activity) -> Unit>? = null

    /** What a tap on the aim cross does, on the cockpit activity and its HUD; null when markers are not offered. */
    fun aimAction(): ((android.app.Activity, org.r3akt.uas.hud.HudView) -> Unit)? = null

    /** Live video surface to place behind the HUD, or null. */
    fun videoView(context: Context): View? = null
}
