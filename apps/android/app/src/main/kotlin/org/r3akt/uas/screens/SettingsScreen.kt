package org.r3akt.uas.screens

import android.app.Activity
import android.graphics.Typeface
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.r3akt.uas.BuildConfig
import org.r3akt.uas.Prefs
import org.r3akt.uas.StreamQuality
import org.r3akt.uas.Variant
import org.r3akt.uas.hud.Hud
import org.r3akt.uas.hud.HudState

/**
 * Local preferences (Android private storage) and the read-only build configuration.
 * Hub settings and the DJI key are compiled in from Gradle properties; the key is never shown. The video server URL
 * may hold MediaMTX credentials: it is stored privately and shown only redacted.
 */
class SettingsScreen(private val activity: Activity, close: () -> Unit) : ScreenPage {
    private val frame = PageFrame(activity, Screen.SETTINGS.title, close)
    override val view = frame.root
    private val ranges = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val config = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val streamNow = TextView(activity)
    private val streamInput = EditText(activity)
    private val qualities = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val qualityNote = TextView(activity)
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
                addView(heading("VIDEO SERVER"))
                addView(streamNow)
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(streamInput.apply {
                        hint = "rtmp://host:1935/uas/<aircraft>?user=…&pass=…"
                        typeface = frame.mono
                        textSize = 13f
                        setTextColor(Hud.WHITE)
                        setHintTextColor(Hud.DIM)
                        isSingleLine = true
                        // Never offered to the keyboard's learning or autofill: it may hold a password.
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                        contentDescription = "Video server RTMP URL"
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 7f))
                    addView(action("SAVE") {
                        val url = streamInput.text.toString().trim()
                        if (Prefs.validStreamUrl(url)) {
                            Prefs.setStreamUrl(activity, url)
                            streamInput.text.clear()
                        } else {
                            Toast.makeText(activity, "Not an rtmp:// or rtmps:// URL", Toast.LENGTH_LONG).show()
                        }
                        showStream()
                    }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.5f))
                    addView(action("CLEAR") { Prefs.setStreamUrl(activity, null); showStream() },
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.5f))
                })
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, PageFrame.dp(activity, 6), 0, 0)
                    addView(text("Stream quality", frame.label, 15f, Hud.WHITE), LinearLayout.LayoutParams(0, WRAP_CONTENT, 3f))
                    addView(qualities, LinearLayout.LayoutParams(0, WRAP_CONTENT, 7f))
                })
                addView(qualityNote)
                addView(text(
                    "MediaMTX RTMP publish URL (packaging/mediamtx). LOW, MEDIUM and HIGH re-encode on the phone " +
                        "at that size and bitrate; ORIGINAL forwards the aircraft's own H.264 at its own rate. " +
                        "Viewers get WebRTC, HLS or RTSP from MediaMTX. Kept in this app's private " +
                        "storage, shown and logged without credentials. Streaming starts only from MENU → STREAM.",
                    frame.mono, 12f, Hud.DIM,
                ))
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
        showStream()
        showQualities()
    }

    private fun showQualities() {
        val current = Prefs.streamQuality(activity)
        qualities.removeAllViews()
        StreamQuality.entries.forEach { q ->
            val selected = q == current
            qualities.addView(text(q.label, frame.mono, 13f, if (selected) Hud.INK_GO else Hud.WHITE).apply {
                val p = PageFrame.dp(activity, 10)
                setPadding(p, p, p, p)
                if (selected) setBackgroundColor(Hud.GREEN)
                contentDescription = "Stream quality ${q.label}" + if (selected) ", selected" else ""
                setOnClickListener {
                    Prefs.setStreamQuality(activity, q)
                    showQualities()
                }
            })
        }
        qualityNote.text = if (current.kbps != null) {
            "${current.width}x${current.height}, ${current.kbps} kbps: about ${current.mbPerHour} MB per hour"
        } else {
            "Aircraft's own stream: its bitrate, often several Mbps (GBs per hour)"
        }
        qualityNote.typeface = frame.mono
        qualityNote.textSize = 12f
        qualityNote.setTextColor(if (current == StreamQuality.ORIGINAL) Hud.AMBER else Hud.WHITE)
        qualityNote.setPadding(0, PageFrame.dp(activity, 4), 0, PageFrame.dp(activity, 6))
    }

    private fun showStream() {
        val url = Prefs.streamUrl(activity)
        streamNow.text = url?.let(Prefs::redact) ?: "Not set: streaming is unavailable"
        streamNow.typeface = frame.mono
        streamNow.textSize = 13f
        streamNow.setTextColor(if (url == null) Hud.AMBER else Hud.WHITE)
        streamNow.setPadding(0, 0, 0, PageFrame.dp(activity, 6))
    }

    private fun action(label: String, onClick: () -> Unit) = text(label, frame.label, 14f, Hud.GREEN).apply {
        gravity = Gravity.CENTER
        val p = PageFrame.dp(activity, 10)
        setPadding(p, p, p, p)
        setOnClickListener { onClick() }
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
