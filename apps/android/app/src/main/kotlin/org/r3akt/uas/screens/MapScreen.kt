package org.r3akt.uas.screens

import android.app.Activity

/** Stub; replaced by the page's implementation. */
class MapScreen(activity: Activity, close: () -> Unit) : ScreenPage {
    override val view = notBuilt(activity, Screen.MAP.title, close)
}
