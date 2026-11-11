package org.r3akt.uas.screens

import android.app.Activity
import android.graphics.Typeface
import android.os.SystemClock
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.r3akt.uas.BuildConfig
import org.r3akt.uas.Prefs
import org.r3akt.uas.Variant
import org.r3akt.uas.hud.Hud
import org.r3akt.uas.hud.HudState

/**
 * Local preferences (Android private storage) and the read-only build configuration.
 * Hub settings and the DJI key are compiled in from Gradle properties; no credential is shown or stored here.
 */
class SettingsScreen(private val activity: Activity, close: () -> Unit) : ScreenPage {
    private val frame = PageFrame(activity, Screen.SETTINGS.title, close)
    override val view = frame.root
    private val ranges = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val config = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var shown: List<Pair<String, String>>? = null
    private var checkedAt = 0L

    init {
        val p = PageFrame.dp(activity, 16)
        frame.body.addView(ScrollView(activity).apply {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(p, 0, p, p)
                addView(heading("LOCAL PREFERENCES"))
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(text("Radar default range", frame.label, 15f, Hud.WHITE), LinearLayout.LayoutParams(0, WRAP_CONTENT, 3f))
                    addView(ranges, LinearLayout.LayoutParams(0, WRAP_CONTENT, 7f))
                })
                addView(heading("BUILD CONFIGURATION (READ-ONLY)"))
                addView(config)
                addView(text(
                    "Hub address, hub destination, team UID and the DJI app key are set when the app is built, " +
                        "as Gradle properties outside the repository (packaging/android/README.md). " +
                        "They cannot be changed here, and the DJI key is never shown.",
                    frame.mono, 12f, Hud.DIM,
                ).apply { setPadding(0, p, 0, 0) })
            })
        })
        showRanges()
    }

    override fun update(state: HudState) {
        // The identity hash appears with the first publish; once a second is enough to pick it up.
        val now = SystemClock.elapsedRealtime()
        if (now - checkedAt < 1000) return
        checkedAt = now
        val items = listOf(
            "Variant" to "${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE} (${Variant.source.name})" +
                if (state.simulated) ", SIMULATED" else "",
            "App version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        ) + runCatching { Variant.source.configItems() }.getOrElse { listOf("Source" to "Error: ${it.message ?: it}") }
        if (items == shown) return
        shown = items
        config.removeAllViews()
        items.forEach { (name, value) ->
            config.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                val p = PageFrame.dp(activity, 6)
                setPadding(0, p, 0, p)
                addView(text(name, frame.label, 15f, Hud.WHITE), LinearLayout.LayoutParams(0, WRAP_CONTENT, 3f))
                addView(text(value, frame.mono, 13f, Hud.WHITE).apply { setTextIsSelectable(true) },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 7f))
            })
        }
    }

    private fun showRanges() {
        val current = Prefs.radarRangeM(activity)
        ranges.removeAllViews()
        Prefs.RADAR_RANGES_M.forEach { metres ->
            val selected = metres == current
            ranges.addView(text(if (metres < 1000) "$metres m" else "${metres / 1000} km", frame.mono, 14f,
                if (selected) Hud.INK_GO else Hud.WHITE).apply {
                val p = PageFrame.dp(activity, 12)
                setPadding(p, p, p, p)
                if (selected) setBackgroundColor(Hud.GREEN)
                contentDescription = "Radar default range $text" + if (selected) ", selected" else ""
                setOnClickListener {
                    Prefs.setRadarRangeM(activity, metres)
                    showRanges()
                }
            })
        }
    }

    private fun heading(value: String) = text(value, frame.label, 16f, Hud.GREEN).apply {
        letterSpacing = 0.1f
        setPadding(0, PageFrame.dp(activity, 12), 0, PageFrame.dp(activity, 4))
    }

    private fun text(value: String, font: Typeface, size: Float, color: Int) = TextView(activity).apply {
        text = value
        typeface = font
        textSize = size
        setTextColor(color)
        setPadding(0, 0, PageFrame.dp(activity, 8), 0)
    }
}
