package org.r3akt.uas

/**
 * Something drawn on the radar and the map. Times are epoch milliseconds; `null` is unknown
 * and shown as unknown, never as zero (no fabricated data).
 */
data class Contact(
    /** Stable identity across updates (UAS-RAD-005): the object or destination hash. */
    val id: String,
    val name: String,
    /** Canonical `r3akt-map-core` symbol id (UAS-RAD-004). */
    val symbol: String,
    val lat: Double,
    val lon: Double,
    /** Who reported it: an identity hash, "this app" or "simulator". */
    val source: String,
    /** UAS-RAD-009: simulated contacts are always identified. */
    val simulated: Boolean,
    /** When the position was measured or set. */
    val positionTimeMs: Long?,
    /** When the contact record last changed. */
    val contactTimeMs: Long?,
    /** When this app last received it; null for contacts this app created itself. */
    val receivedTimeMs: Long?,
)
