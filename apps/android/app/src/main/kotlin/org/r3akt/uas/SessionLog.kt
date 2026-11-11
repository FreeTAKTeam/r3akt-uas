package org.r3akt.uas

import android.content.Context
import org.r3akt.uas.hud.FlightAction
import org.r3akt.uas.hud.HudState

/** The local session record (UAS-VID-008). Stub: the sessions page implements it. */
object SessionLog {
    /** The cockpit opened. */
    fun start(context: Context, variant: String) {}

    /** Every cockpit state, once per poll. */
    fun onState(state: HudState) {}

    /** A local flight action was sent; `error` is null when DJI accepted it. */
    fun onAction(action: FlightAction, error: String?) {}

    /** The cockpit closed. */
    fun end() {}
}
