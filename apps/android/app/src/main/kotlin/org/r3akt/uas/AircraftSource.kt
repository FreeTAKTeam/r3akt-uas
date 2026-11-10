package org.r3akt.uas

import android.app.Application
import org.r3akt.uas.hud.HudState

/** The variant-specific aircraft source. Each flavor provides `Variant`. */
interface AircraftSource {
    val name: String

    /** Called from Application.attachBaseContext. */
    fun install(app: Application) {}

    /** A human-readable status report, shown from the cockpit's LOG button. */
    fun describe(): String

    /** Cockpit state for update `seq` (polled at 1 Hz). Unknown values stay null. */
    fun cockpit(seq: UInt): HudState
}
