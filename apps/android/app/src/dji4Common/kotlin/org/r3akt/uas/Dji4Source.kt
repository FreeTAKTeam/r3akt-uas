package org.r3akt.uas

import android.app.Application
import android.content.Context
import android.view.View
import org.r3akt.uas.bridge.AltitudeReference
import org.r3akt.uas.bridge.TelemetrySample
import org.r3akt.uas.bridge.djiSample
import org.r3akt.uas.dji4.Dji4Action
import org.r3akt.uas.dji4.Dji4CameraMode
import org.r3akt.uas.dji4.Dji4Link
import org.r3akt.uas.dji4.Dji4Loader
import org.r3akt.uas.dji4.Dji4Sdk
import org.r3akt.uas.dji4.Dji4VideoView
import org.r3akt.uas.hud.CameraMode
import org.r3akt.uas.hud.FlightAction
import org.r3akt.uas.hud.HudState

/** A real aircraft through DJI SDK 4. Flight actions go straight to the SDK and nowhere else. */
object Dji4Source : AircraftSource {
    override val name = "DJI SDK 4"
    override val periodMs = 100L // DJI reports flight state at 10 Hz.

    // The loader only: touching Dji4Sdk here, before the DJI classes exist, breaks its callbacks.
    override fun install(app: Application) = Dji4Loader.install(app)

    override fun start(context: Context) = Dji4Sdk.start(context)

    override fun describe() = Dji4Sdk.describe()

    override fun videoView(context: Context): View = Dji4VideoView(context)

    override fun perform(action: FlightAction, onResult: (String?) -> Unit) = Dji4Sdk.perform(
        when (action) {
            FlightAction.TAKEOFF -> Dji4Action.TAKEOFF
            FlightAction.LAND -> Dji4Action.LAND
            FlightAction.RTH -> Dji4Action.RTH
            FlightAction.STOP_RTH -> Dji4Action.STOP_RTH
            FlightAction.PHOTO -> Dji4Action.PHOTO
            FlightAction.RECORD_START -> Dji4Action.RECORD_START
            FlightAction.RECORD_STOP -> Dji4Action.RECORD_STOP
            FlightAction.PHOTO_MODE -> Dji4Action.PHOTO_MODE
            FlightAction.VIDEO_MODE -> Dji4Action.VIDEO_MODE
            FlightAction.GIMBAL_DOWN -> Dji4Action.GIMBAL_DOWN
            FlightAction.GIMBAL_LEVEL -> Dji4Action.GIMBAL_LEVEL
        },
        onResult,
    )

    override fun cockpit(seq: UInt): HudState {
        val d = Dji4Sdk.state()
        val connected = d.link == Dji4Link.CONNECTED
        val pose = d.pose
        return HudState(
            status = when {
                d.link == Dji4Link.NO_KEY -> "NO DJI KEY"
                d.link == Dji4Link.REGISTERING -> "REGISTERING"
                d.link == Dji4Link.REGISTRATION_FAILED -> "REGISTRATION FAILED"
                !connected -> "NO AIRCRAFT"
                d.goingHome -> "RETURNING HOME"
                d.flying == true -> "IN FLIGHT"
                d.motorsOn == true -> "MOTORS ON"
                d.flying == false -> "ON GROUND"
                else -> "CONNECTED"
            },
            simulated = false,
            airborne = d.flying,
            rthActive = d.goingHome,
            flightMode = d.flightMode,
            headingDeg = pose?.headingDeg,
            groundSpeedMps = pose?.groundSpeedMps,
            verticalSpeedMps = pose?.verticalSpeedMps,
            altitudeM = pose?.altitudeM,
            altitudeReference = pose?.altitudeReference ?: AltitudeReference.UNKNOWN,
            latitudeDeg = pose?.latitudeDeg,
            longitudeDeg = pose?.longitudeDeg,
            homeDistanceM = d.homeDistanceM,
            batteryPercent = d.batteryPercent,
            gpsSatellites = d.satellites,
            gimbalPitchDeg = d.gimbalPitchDeg,
            flightTimeS = d.flightTimeS,
            video = connected,
            cameraMode = when (d.cameraMode) {
                Dji4CameraMode.PHOTO -> CameraMode.PHOTO
                Dji4CameraMode.VIDEO -> CameraMode.VIDEO
                null -> null
            },
            recording = d.recording,
            recordingTimeS = d.recordingTimeS,
            linkPercent = d.linkPercent,
            // UAS-FLT-002: an action is offered only while the aircraft reports a state that allows it.
            actions = buildSet {
                if (d.flying == false) add(FlightAction.TAKEOFF)
                if (d.flying == true) add(FlightAction.LAND)
                if (d.flying == true && !d.goingHome && d.homeDistanceM != null) add(FlightAction.RTH)
                if (d.goingHome) add(FlightAction.STOP_RTH)
                // Camera actions follow the reported mode; while it is unknown none is offered.
                if (connected && d.hasCamera && d.recording == false) when (d.cameraMode) {
                    Dji4CameraMode.PHOTO -> { add(FlightAction.PHOTO); add(FlightAction.VIDEO_MODE) }
                    Dji4CameraMode.VIDEO -> { add(FlightAction.RECORD_START); add(FlightAction.PHOTO_MODE) }
                    null -> {}
                }
                if (connected && d.recording == true) add(FlightAction.RECORD_STOP)
                if (connected && d.hasGimbal && d.gimbalPitchDeg != null) { add(FlightAction.GIMBAL_DOWN); add(FlightAction.GIMBAL_LEVEL) }
            },
        )
    }

    override fun setupItems(): List<SetupItem> {
        val d = Dji4Sdk.state()
        val registered = d.link == Dji4Link.NO_AIRCRAFT || d.link == Dji4Link.CONNECTED
        val connected = d.link == Dji4Link.CONNECTED
        val needsRegistration = SetupItem("", SetupState.WAITING, "Needs DJI registration first")
        return listOf(
            when {
                !Dji4Sdk.started -> SetupItem("DJI SDK key", SetupState.WAITING, "Not checked yet: the SDK starts after the INTERNET permission answer")
                d.link == Dji4Link.NO_KEY -> SetupItem("DJI SDK key", SetupState.PROBLEM, "No djiApiKey in this build; the SDK is not registered and nothing connects")
                else -> SetupItem("DJI SDK key", SetupState.OK, "Present in this build (not shown)")
            },
            when (d.link) {
                Dji4Link.NO_KEY -> SetupItem("DJI registration", SetupState.WAITING, "Not attempted without a key")
                Dji4Link.REGISTERING -> SetupItem("DJI registration", SetupState.WAITING, "Registering with DJI…")
                Dji4Link.REGISTRATION_FAILED -> SetupItem("DJI registration", SetupState.PROBLEM, "DJI: ${d.error ?: "no error reported"}")
                else -> SetupItem("DJI registration", SetupState.OK, "Registered, DJI SDK ${Dji4Sdk.sdkVersion() ?: "version not reported"}")
            },
            when (Dji4Sdk.remoteControllerConnected()) {
                true -> SetupItem("Remote controller", SetupState.OK, "Connected (reported by the SDK)")
                false -> SetupItem("Remote controller", SetupState.WAITING, "Not connected (reported by the SDK)")
                null -> if (registered) SetupItem("Remote controller", SetupState.UNKNOWN, "Not reported: no DJI product on USB")
                else needsRegistration.copy(item = "Remote controller")
            },
            when {
                connected -> SetupItem("Aircraft", SetupState.OK, "Connected: ${d.model ?: "model not reported"}")
                registered -> SetupItem("Aircraft", SetupState.WAITING, "No aircraft connected")
                else -> needsRegistration.copy(item = "Aircraft")
            },
            Dji4Sdk.firmware()?.let { SetupItem("Firmware", SetupState.OK, it) }
                ?: SetupItem("Firmware", SetupState.UNKNOWN, if (connected) "Not reported by the SDK" else "No aircraft connected"),
            // The decoder's health is in the LOG report; reading it here would reset its rate counters.
            if (connected) SetupItem("Video", SetupState.UNKNOWN, "Primary feed opened behind the HUD; feed rate and decoder state: LOG")
            else SetupItem("Video", SetupState.WAITING, "No aircraft connected, no video"),
        )
    }

    override fun configItems() = listOf(
        "DJI SDK key" to when {
            !Dji4Sdk.started -> "not checked yet"
            Dji4Sdk.state().link == Dji4Link.NO_KEY -> "not configured"
            else -> "configured (not shown)"
        },
    )

    /** The current DJI state as a publishable fix, or `null`: the rules are Rust's `dji_sample`. */
    fun fix(): TelemetrySample? {
        val d = Dji4Sdk.state()
        val pose = d.pose ?: return null
        val at = d.receivedAtMs ?: return null
        return djiSample(
            pose,
            d.gpsLevel?.toUByte(),
            d.batteryPercent?.toDouble(),
            at.toULong(),
            System.currentTimeMillis().toULong(),
        )
    }
}
