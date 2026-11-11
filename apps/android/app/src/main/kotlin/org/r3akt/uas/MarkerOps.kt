package org.r3akt.uas

import java.util.concurrent.Executor

/**
 * The app's own operator markers, offered by publish builds only ([AircraftSource.markerOps]).
 * The markers live in the shared marker store behind the native bridge; this is only the way
 * `main` code reaches them. Every call may block on the publisher: run it on [worker].
 */
interface MarkerOps {
    /** The publisher's worker; marker calls run there, in order with publish start and stop. */
    val worker: Executor

    /** True while publishing runs; markers are stored by the publisher. */
    val publishing: Boolean

    /** The app's markers in their last sent state, never the hub's state. */
    fun list(): List<Marker>

    /** How many marker changes are stored, waiting for the hub, as text ("?" when unknown). */
    fun pending(): String

    /** Moves a marker to the aircraft's current position and returns its name; throws with the reason when refused. */
    fun moveHere(id: String): String

    /** Deletes a marker; throws with the reason when refused. */
    fun delete(id: String)

    /** Sends the last aircraft fix again. */
    fun resendLastFix()

    data class Marker(val id: String, val name: String, val symbol: String, val lat: Double, val lon: Double, val notes: String?)
}
