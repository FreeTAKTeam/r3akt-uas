package org.r3akt.uas

import android.app.Application

/** The variant-specific aircraft source. Each flavor provides `Variant`. */
interface AircraftSource {
    val name: String

    /** Called from Application.attachBaseContext. */
    fun install(app: Application) {}

    /** A human-readable status report for the scaffold screen. */
    fun describe(): String
}
