package org.r3akt.uas.hud

import org.r3akt.uas.bridge.AltitudeReference

/** Local flight actions; a button is enabled only when the source offers it (UAS-FLT-002). */
enum class FlightAction {
    TAKEOFF, LAND, RTH, STOP_RTH, PHOTO,
    RECORD_START, RECORD_STOP, PHOTO_MODE, VIDEO_MODE,
    /** Gimbal pitch to -90° (straight down) or 0° (level); finer pitch stays on the controller's wheel. */
    GIMBAL_DOWN, GIMBAL_LEVEL,
    /** Live video to the configured video server (UAS-VID-001); started only by the operator. */
    STREAM_START, STREAM_STOP,
}

/** The camera's shooting mode as reported; anything else (playback, download) is unknown. */
enum class CameraMode { PHOTO, VIDEO }

/**
 * What the cockpit HUD shows. `null` means unknown and is drawn as a dash,
 * never as zero (no fabricated data).
 */
data class HudState(
    val status: String,
    val simulated: Boolean,
    val airborne: Boolean? = null,
    val rthActive: Boolean = false,
    val flightMode: String? = null,
    val headingDeg: Double? = null,
    val groundSpeedMps: Double? = null,
    val verticalSpeedMps: Double? = null,
    val altitudeM: Double? = null,
    val altitudeReference: AltitudeReference = AltitudeReference.UNKNOWN,
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val homeDistanceM: Double? = null,
    val batteryPercent: Int? = null,
    val gpsSatellites: Int? = null,
    val gimbalPitchDeg: Double? = null,
    val flightTimeS: Long? = null,
    val cameraMode: CameraMode? = null,
    val recording: Boolean? = null,
    val recordingTimeS: Int? = null,
    /** Aircraft-to-controller downlink quality as reported, 0..100. */
    val linkPercent: Int? = null,
    /** Reticulum link to the hub: null = no hub link running, true = established, false = configured but down. */
    val mesh: Boolean? = null,
    /** Live video to the video server: true while the stream runs. */
    val streaming: Boolean = false,
    val actions: Set<FlightAction> = emptySet(),
    /** True while a live video surface should sit behind the HUD. */
    val video: Boolean = false,
)
