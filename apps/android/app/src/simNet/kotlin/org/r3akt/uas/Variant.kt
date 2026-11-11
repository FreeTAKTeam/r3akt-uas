package org.r3akt.uas

import org.r3akt.uas.bridge.camera
import org.r3akt.uas.bridge.sample

object Variant {
    /**
     * Test build only (D-4): the simulator, published. The orbit keeps its shape with wall-clock
     * time, since the hub ignores old and far-future fixes; the simulated camera pose of a sample
     * carries that sample's fix time.
     */
    val source: AircraftSource = PublishingSource(
        inner = SimulatorSource(networkReport = ""),
        storeName = "publish",
        fix = { seq ->
            val now = (System.currentTimeMillis() / 1000).toULong()
            sample(seq).copy(sampleTimeS = now, fixTimeS = now)
        },
        cameraPose = { seq, fix -> camera(seq).copy(sampleTimeMs = fix.fixTimeS * 1000u) },
    )
}
