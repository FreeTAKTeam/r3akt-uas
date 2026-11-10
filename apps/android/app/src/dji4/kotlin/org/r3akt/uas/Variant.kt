package org.r3akt.uas

import android.app.Application
import org.r3akt.uas.dji4.Dji4Sdk
import org.r3akt.uas.hud.HudState

object Variant {
    val source: AircraftSource = object : AircraftSource {
        override val name = "DJI SDK 4"

        override fun install(app: Application) = Dji4Sdk.install(app)

        override fun describe(): String =
            "DJI SDK 4 linked (${Dji4Sdk.managerClassName}).\n" +
                "SDK registration and aircraft connection: disabled in the scaffold."

        // No aircraft connection yet (milestone 4): everything unknown, no actions offered.
        override fun cockpit(seq: UInt) = HudState(status = "NOT CONNECTED", simulated = false)
    }
}
