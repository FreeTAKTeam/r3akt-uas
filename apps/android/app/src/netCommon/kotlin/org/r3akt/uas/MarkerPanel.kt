package org.r3akt.uas

import android.app.Activity
import android.app.AlertDialog
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.r3akt.uas.bridge.OwnMarker
import org.r3akt.uas.bridge.markerDelete
import org.r3akt.uas.bridge.markerList
import org.r3akt.uas.bridge.markerMove
import org.r3akt.uas.bridge.publishResendLastFix
import org.r3akt.uas.bridge.publishStatus
import java.util.concurrent.Executor

/**
 * The app's own operator markers (test build): move or delete them, or resend the last fix.
 * New markers are placed from the aim cross ([AimMarkerPanel]).
 * Every bridge call runs on [worker]: it may wait for the publisher.
 * The list shows the last change made here and how many are still stored; never the hub's state.
 */
class MarkerPanel(
    private val activity: Activity,
    private val worker: Executor,
) {
    private lateinit var listView: LinearLayout
    private lateinit var header: TextView

    fun show() {
        listView = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            header = TextView(activity)
            addView(header)
            addView(listView)
        }
        AlertDialog.Builder(activity)
            .setTitle("Your markers")
            .setView(ScrollView(activity).apply { addView(body) })
            .setNeutralButton("RESEND LAST FIX") { _, _ -> publishResendLastFix() }
            .setNegativeButton("CLOSE", null)
            .show()
        refresh()
    }

    private fun refresh() = worker.execute {
        val markers = runCatching { markerList() }.getOrDefault(emptyList())
        val pending = runCatching { publishStatus().markersPending.toString() }.getOrDefault("?")
        activity.runOnUiThread { showList(markers, pending) }
    }

    private fun showList(markers: List<OwnMarker>, pending: String) {
        // Stored is not sent: the outbox may still hold the latest change (UAS-PRD-010).
        header.text = "This app's markers (last change made here)\n$pending change(s) stored, waiting for the hub"
        listView.removeAllViews()
        if (markers.isEmpty()) listView.addView(TextView(activity).apply { text = "none" })
        for (m in markers) {
            listView.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(activity).apply {
                    text = "${m.name} · ${m.symbol}\n%.5f, %.5f".format(m.lat, m.lon)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(Button(activity).apply {
                    text = "MOVE HERE"
                    setOnClickListener { run("moved") { markerMove(m.objectId).name } }
                })
                addView(Button(activity).apply {
                    text = "DELETE"
                    setOnClickListener { run("deleted") { markerDelete(m.objectId); m.name } }
                })
            })
        }
    }

    /** Runs one bridge call off the UI thread and reports its outcome. */
    private fun run(done: String, call: () -> String) = worker.execute {
        val message = runCatching { "${call()} $done (stored for the hub)" }
            .getOrElse { "Not done: ${it.message?.removePrefix("reason=") ?: it}" }
        activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
        refresh()
    }
}
