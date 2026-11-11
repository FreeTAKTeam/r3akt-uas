package org.r3akt.uas.screens

import android.app.Activity

/** Stub; replaced by the page's implementation. */
class RadarScreen(activity: Activity, close: () -> Unit) : ScreenPage {
    override val view = notBuilt(activity, Screen.RADAR.title, close)
}
