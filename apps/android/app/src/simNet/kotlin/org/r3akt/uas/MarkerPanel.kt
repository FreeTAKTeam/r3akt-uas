package org.r3akt.uas

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.r3akt.uas.bridge.Affiliation
import org.r3akt.uas.bridge.CameraPose
import org.r3akt.uas.bridge.OwnMarker
import org.r3akt.uas.bridge.markerCreate
import org.r3akt.uas.bridge.markerCreateAtCamera
import org.r3akt.uas.bridge.markerDelete
import org.r3akt.uas.bridge.markerList
import org.r3akt.uas.bridge.markerMove
import org.r3akt.uas.bridge.publishResendLastFix
import org.r3akt.uas.bridge.publishStatus
import java.util.concurrent.Executor

/**
 * Operator markers (test build): place a marker at the aircraft's position or where the
 * camera centre meets the ground (shared ground point, refused when it is not valid), then
 * move or delete the app's own markers. The pickers follow the FreeTAKUAS GeoObject popup
 * (affiliation and an Alpha–Zulu name with a counter; FreeTAKUAS 704f4aa, MIT).
 * Every bridge call runs on [worker]: it may wait for the publisher.
 * The list shows the last change made here and how many are still stored; never the hub's state.
 */
class MarkerPanel(
    private val activity: Activity,
    private val worker: Executor,
    /** The camera pose now, read on the tap so it matches the aircraft fix. */
    private val cameraPose: () -> CameraPose,
) {
    private val affiliations = Affiliation.entries.toTypedArray()
    private val names = arrayOf(
        "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliett",
        "Kilo", "Lima", "Mike", "November", "Oscar", "Papa", "Quebec", "Romeo", "Sierra", "Tango",
        "Uniform", "Victor", "Whiskey", "X-ray", "Yankee", "Zulu",
    )
    private lateinit var listView: LinearLayout
    private lateinit var header: TextView
    private var dialog: AlertDialog? = null

    fun show() {
        val affiliation = picker(affiliations.map { it.name.lowercase() }.toTypedArray())
        val name = picker(names)
        val pickers = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(column("Affiliation", affiliation))
            addView(column("Name", name))
        }
        listView = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val markers = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            header = TextView(activity)
            addView(header)
            addView(ScrollView(activity).apply { addView(listView) })
        }
        // Side by side: the cockpit is landscape, so a stacked list would fall off the dialog.
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 16)
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(pickers)
                addView(Button(activity).apply {
                    text = "PLACE AT CAMERA"
                    setOnClickListener {
                        val pose = cameraPose()
                        val base = names[name.value]
                        val chosen = affiliations[affiliation.value]
                        run("placed") {
                            val count = markerList().count { it.name.startsWith("$base-") } + 1
                            markerCreateAtCamera("$base-$count", chosen, pose).name
                        }
                    }
                })
            })
            addView(markers, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle("Operator markers")
            .setView(body)
            .setPositiveButton("PLACE AT AIRCRAFT", null)
            .setNeutralButton("RESEND LAST FIX") { _, _ -> publishResendLastFix() }
            .setNegativeButton("CLOSE", null)
            .show()
        // Keep the panel open after placing, like the FreeTAKUAS popup's Send.
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val base = names[name.value]
            val chosen = affiliations[affiliation.value]
            run("placed") {
                val count = markerList().count { it.name.startsWith("$base-") } + 1
                markerCreate("$base-$count", chosen).name
            }
        }
        refresh()
    }

    private fun picker(values: Array<String>) = NumberPicker(activity).apply {
        minValue = 0
        maxValue = values.size - 1
        displayedValues = values
        wrapSelectorWheel = false
    }

    private fun column(title: String, content: View) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24, 0, 24, 0)
        addView(TextView(activity).apply { text = title })
        addView(content)
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
            .getOrElse { "Not done: ${it.message ?: it}" }
        activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_LONG).show() }
        refresh()
    }
}
