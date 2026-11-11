package org.r3akt.uas

import android.app.Activity
import android.app.AlertDialog
import android.widget.ScrollView
import org.r3akt.uas.bridge.markerDelete
import org.r3akt.uas.bridge.markerList
import org.r3akt.uas.bridge.markerMove
import org.r3akt.uas.bridge.publishResendLastFix
import org.r3akt.uas.bridge.publishStatus
import org.r3akt.uas.screens.MarkerList
import java.util.concurrent.Executor

/**
 * The app's own operator markers (test build) through the native bridge; the same operations
 * back the LOG's MARKERS panel and the OBSERVATIONS page. New markers are placed from the aim
 * cross ([AimMarkerPanel]). Every bridge call runs on [worker]: it may wait for the publisher.
 */
class BridgeMarkerOps(override val worker: Executor, private val isPublishing: () -> Boolean) : MarkerOps {
    override val publishing get() = isPublishing()
    override fun list() = markerList().map { MarkerOps.Marker(it.objectId, it.name, it.symbol, it.lat, it.lon, it.notes) }
    override fun pending() = publishStatus().markersPending.toString()
    override fun moveHere(id: String) = markerMove(id).name
    override fun delete(id: String) = markerDelete(id)
    override fun resendLastFix() = publishResendLastFix()
}

/** The LOG's MARKERS panel: the shared [MarkerList] in a dialog. */
class MarkerPanel(private val activity: Activity, private val ops: MarkerOps) {
    fun show() {
        val list = MarkerList(activity, ops)
        list.view.setPadding(32, 16, 32, 16)
        AlertDialog.Builder(activity)
            .setTitle("Your markers")
            .setView(ScrollView(activity).apply { addView(list.view) })
            .setNegativeButton("CLOSE", null)
            .show()
    }
}
