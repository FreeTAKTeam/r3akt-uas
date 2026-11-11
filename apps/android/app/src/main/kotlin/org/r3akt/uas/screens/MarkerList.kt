package org.r3akt.uas.screens

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.r3akt.uas.MarkerOps
import org.r3akt.uas.hud.Hud

/**
 * The app's own operator markers with MOVE HERE, DELETE and RESEND LAST FIX: one list for the
 * LOG's MARKERS panel and the OBSERVATIONS page. Every [MarkerOps] call runs on its worker.
 * It shows the last change made here and how many are still stored; never the hub's state.
 */
class MarkerList(private val activity: Activity, private val ops: MarkerOps) {
    private val header = TextView(activity).apply { setTextColor(Hud.WHITE) }
    private val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    val view: View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(header)
        addView(rows)
        addView(Button(activity).apply {
            text = "RESEND LAST FIX"
            setOnClickListener { ops.worker.execute { ops.resendLastFix() } }
        })
    }

    init { refresh() }

    fun refresh() = ops.worker.execute {
        val markers = runCatching { ops.list() }.getOrDefault(emptyList())
        val pending = runCatching { ops.pending() }.getOrDefault("?")
        val publishing = ops.publishing
        activity.runOnUiThread { show(markers, pending, publishing) }
    }

    private fun show(markers: List<MarkerOps.Marker>, pending: String, publishing: Boolean) {
        // Stored is not sent: the outbox may still hold the latest change (UAS-PRD-010).
        header.text = (if (publishing) "" else "Publishing is off: open LOG, tap START PUBLISH.\n") +
            "This app's markers (last change made here)\n$pending change(s) stored, waiting for the hub"
        rows.removeAllViews()
        if (markers.isEmpty()) rows.addView(TextView(activity).apply { text = "none"; setTextColor(Hud.DIM) })
        for (m in markers) {
            rows.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(View(activity).apply {
                    setBackgroundColor(ContactSymbol.color(m.symbol))
                    contentDescription = m.symbol
                }, LinearLayout.LayoutParams(24, 24).apply { marginEnd = 16 })
                addView(TextView(activity).apply {
                    setTextColor(Hud.WHITE)
                    text = "${m.name} · ${m.symbol}\n%.5f, %.5f".format(m.lat, m.lon) + (m.notes?.let { "\n$it" } ?: "")
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Button(activity).apply {
                    text = "MOVE HERE"
                    setOnClickListener { run("moved") { ops.moveHere(m.id) } }
                })
                addView(Button(activity).apply {
                    text = "DELETE"
                    setOnClickListener {
                        AlertDialog.Builder(activity)
                            .setMessage("Delete ${m.name}? The deletion is sent to the hub.")
                            .setPositiveButton("DELETE") { _, _ -> run("deleted") { ops.delete(m.id); m.name } }
                            .setNegativeButton("CANCEL", null)
                            .show()
                    }
                })
            })
        }
    }

    /** Runs one marker call on the worker and reports its outcome. */
    private fun run(done: String, call: () -> String) = ops.worker.execute {
        val message = runCatching { "${call()} $done (stored for the hub)" }
            .getOrElse { "Not done: ${it.message?.removePrefix("reason=") ?: it}" }
        activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
        refresh()
    }
}
