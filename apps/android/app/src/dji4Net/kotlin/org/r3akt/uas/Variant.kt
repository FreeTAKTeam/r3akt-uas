package org.r3akt.uas

object Variant {
    /**
     * Test build only (D-18): the DJI aircraft, published, with its own identity and store.
     * Markers go where the camera aims, with the DJI bounds of owner ruling D-15 (b).
     */
    val source: AircraftSource = PublishingSource(
        inner = Dji4Source,
        storeName = "publish-dji4",
        fix = { Dji4Source.fix() },
        cameraPose = { _, fix -> Dji4Source.cameraPose(fix) },
    )
}
