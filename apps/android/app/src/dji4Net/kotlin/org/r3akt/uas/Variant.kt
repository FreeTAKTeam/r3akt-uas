package org.r3akt.uas

object Variant {
    /**
     * Test build only (D-18): the DJI aircraft, published, with its own identity and store.
     * No camera point: DJI SDK 4 reports no position accuracy (D-15).
     */
    val source: AircraftSource = PublishingSource(
        inner = Dji4Source,
        storeName = "publish-dji4",
        fix = { Dji4Source.fix() },
        cameraPose = null,
    )
}
