package org.r3akt.uas.screens

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.r3akt.uas.SessionLog
import org.r3akt.uas.SessionRecord
import org.r3akt.uas.hud.Hud
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Past cockpit sessions from the local session record (UAS-VID-008), newest first; a tap opens
 * one session's events. Video is recorded on the aircraft's own card; media download is not
 * built, and this page says so instead of listing anything.
 */
class SessionsScreen(private val activity: Activity, close: () -> Unit) : ScreenPage {
    private val frame = PageFrame(activity, Screen.SESSIONS.title, close)
    private val content = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val pad = PageFrame.dp(activity, 16)
        setPadding(pad, 0, pad, pad)
    }
    private val scroll = ScrollView(activity).apply { addView(content) }
    override val view = frame.root.also { frame.body.addView(scroll) }

    init { showList() }

    private fun showList() {
        content.removeAllViews()
        content.addView(title("SESSIONS"))
        content.addView(text("Reading…", Hud.DIM))
        SessionLog.read(activity) { records -> activity.runOnUiThread { showSessions(records) } }
    }

    private fun showSessions(records: List<Pair<File, SessionRecord>>) {
        content.removeAllViews()
        content.addView(title("SESSIONS"))
        content.addView(text("Kept on this device only: the newest ${SessionLog.MAX_SESSIONS} sessions, " +
            "${SessionLog.MAX_BYTES / 1024} KiB each, track fix every ${SessionLog.FIX_INTERVAL_MS / 1000} s in flight.", Hud.DIM))
        if (records.isEmpty()) content.addView(text("No sessions recorded.", Hud.WHITE))
        for ((file, r) in records) {
            val current = file.name == SessionLog.currentFile
            content.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, PageFrame.dp(activity, 8), 0, PageFrame.dp(activity, 8))
                addView(text(summary(r, current), Hud.WHITE).apply {
                    contentDescription = "Open session events"
                    setOnClickListener { showEvents(r) }
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (!current) addView(Button(activity).apply {
                    text = "DELETE"
                    setOnClickListener { confirmDelete(file, r) }
                })
            })
        }
        content.addView(title("RECORDINGS"))
        // UAS-VID-008, recordings part: nothing is listed until it exists.
        content.addView(text("Video records on the aircraft's own card (shutter in video mode); start and stop " +
            "appear in the session events. Not built yet: listing or downloading media from the aircraft. " +
            "No recordings are kept by this app.", Hud.AMBER))
    }

    private fun summary(r: SessionRecord, current: Boolean): String {
        val sim = when (r.simulated) { true -> " · SIMULATED"; false -> ""; null -> " · simulated unknown" }
        val duration = when {
            current -> "in progress"
            r.endMs != null -> duration(r.endMs - r.startMs)
            else -> "≥ ${duration(r.lastMs - r.startMs)} (no end record)"
        }
        val alt = r.maxTakeoffAltM?.let { "%.0f m".format(it) } ?: "unknown"
        return "${time(r.startMs)} · $duration\n${r.variant}$sim\n" +
            "flights ${r.flights} · max alt above take-off $alt · actions ${r.actions}, rejected ${r.rejections}" +
            if (r.truncated) "\nrecord full: later events not recorded" else ""
    }

    private fun showEvents(r: SessionRecord) {
        content.removeAllViews()
        content.addView(TextView(activity).apply {
            text = "‹ SESSIONS"
            typeface = frame.label
            textSize = 16f
            setTextColor(Hud.GREEN)
            setPadding(0, PageFrame.dp(activity, 8), 0, PageFrame.dp(activity, 8))
            setOnClickListener { showList() }
        })
        content.addView(text(summary(r, false) + "\nsession ${r.id}", Hud.WHITE))
        content.addView(text(r.events.joinToString("\n") { "+${duration(it.timeMs - r.startMs).padEnd(8)} #${it.seq}  ${it.text}" }, Hud.WHITE)
            .apply { setTextIsSelectable(true) })
        scroll.scrollTo(0, 0)
    }

    private fun confirmDelete(file: File, r: SessionRecord) {
        AlertDialog.Builder(activity)
            .setMessage("Delete the session of ${time(r.startMs)}? It cannot be recovered.")
            .setPositiveButton("DELETE") { _, _ -> SessionLog.delete(file) { activity.runOnUiThread { showList() } } }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun title(s: String) = TextView(activity).apply {
        text = s
        typeface = frame.label
        textSize = 16f
        letterSpacing = 0.1f
        setTextColor(Hud.GREEN)
        setPadding(0, PageFrame.dp(activity, 16), 0, PageFrame.dp(activity, 4))
    }

    private fun text(s: String, color: Int) = TextView(activity).apply {
        text = s
        typeface = frame.mono
        textSize = 13f
        setTextColor(color)
    }

    private fun time(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(ms))

    private fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%dh%02dm%02ds".format(s / 3600, s / 60 % 60, s % 60) else "%dm%02ds".format(s / 60, s % 60)
    }
}
