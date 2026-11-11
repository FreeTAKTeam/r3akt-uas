package org.r3akt.uas.screens

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.r3akt.uas.R
import org.r3akt.uas.hud.Hud
import org.r3akt.uas.hud.HudState

/** The pages opened over the cockpit (PLAN.md section 3, "Flight application"). */
enum class Screen(val title: String) {
    SETUP("CONNECTION / SETUP"),
    MAP("MAP"),
    RADAR("RADAR"),
    OBSERVATIONS("OBSERVATIONS"),
    SESSIONS("SESSIONS / RECORDINGS"),
    SETTINGS("SETTINGS"),
}

/**
 * One full-screen page. The cockpit keeps polling its source underneath and passes each
 * new state to [update]; flight interaction never depends on a page (UAS-FLT-003).
 */
interface ScreenPage {
    val view: View
    fun update(state: HudState) {}
    fun close() {}
}

fun openScreen(screen: Screen, activity: Activity, close: () -> Unit): ScreenPage = when (screen) {
    Screen.SETUP -> SetupScreen(activity, close)
    Screen.MAP -> MapScreen(activity, close)
    Screen.RADAR -> RadarScreen(activity, close)
    Screen.OBSERVATIONS -> ObservationsScreen(activity, close)
    Screen.SESSIONS -> SessionsScreen(activity, close)
    Screen.SETTINGS -> SettingsScreen(activity, close)
}

/** The common page frame: an opaque dark page, a title bar with BACK, and a [body] below it. */
class PageFrame(activity: Activity, title: String, close: () -> Unit) {
    val label: Typeface = activity.resources.getFont(R.font.rajdhani_bold)
    val mono: Typeface = activity.resources.getFont(R.font.b612mono_bold)
    val body = FrameLayout(activity)
    val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BACKGROUND)
        isClickable = true // the cockpit underneath gets no taps
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4))
            addView(TextView(activity).apply {
                text = "‹ BACK"
                typeface = label
                textSize = 16f
                setTextColor(Hud.GREEN)
                setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 16), dp(activity, 8))
                contentDescription = "Back to the cockpit"
                setOnClickListener { close() }
            })
            addView(TextView(activity).apply {
                text = title
                typeface = label
                textSize = 18f
                letterSpacing = 0.1f
                setTextColor(Hud.WHITE)
            })
        }
        addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }

    companion object {
        const val BACKGROUND = 0xFF0A0F0B.toInt()
        fun dp(activity: Activity, v: Int) = (v * activity.resources.displayMetrics.density).toInt()
    }
}

/** Placeholder until the page is built; says so instead of pretending. */
internal fun notBuilt(activity: Activity, title: String, close: () -> Unit): View =
    PageFrame(activity, title, close).apply {
        body.addView(TextView(activity).apply {
            text = "Not built yet."
            typeface = mono
            setTextColor(Hud.AMBER)
            gravity = Gravity.CENTER
        })
    }.root
