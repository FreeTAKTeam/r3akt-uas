package org.r3akt.uas.screens

import android.app.Activity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.r3akt.uas.Variant
import org.r3akt.uas.hud.Hud

/**
 * Detections (not built: UAS-OBS-001) and the markers this app placed, with the same
 * operations as the LOG's MARKERS panel ([MarkerList]) in publish builds.
 */
class ObservationsScreen(activity: Activity, close: () -> Unit) : ScreenPage {
    private val frame = PageFrame(activity, Screen.OBSERVATIONS.title, close)
    override val view = frame.root

    init {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = PageFrame.dp(activity, 16)
            setPadding(pad, 0, pad, pad)
        }
        fun title(s: String) = content.addView(TextView(activity).apply {
            text = s
            typeface = frame.label
            textSize = 16f
            letterSpacing = 0.1f
            setTextColor(Hud.GREEN)
            setPadding(0, PageFrame.dp(activity, 16), 0, PageFrame.dp(activity, 4))
        })
        fun note(s: String) = content.addView(TextView(activity).apply {
            text = s
            typeface = frame.mono
            textSize = 13f
            setTextColor(Hud.AMBER)
        })
        title("DETECTIONS")
        note("Not built yet: on-device object detection is planned for milestone 5. No detections are shown.")
        title("MARKERS PLACED BY THIS APP")
        val ops = Variant.source.markerOps()
        if (ops == null) note("Publishing is off in this build: markers are placed only in publish test builds.")
        else content.addView(MarkerList(activity, ops).view)
        frame.body.addView(ScrollView(activity).apply { addView(content) })
    }
}
