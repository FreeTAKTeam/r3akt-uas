package org.r3akt.uas

import android.app.Application
import android.content.Context
import android.view.View
import org.r3akt.uas.bridge.AltitudeReference
import org.r3akt.uas.dji4.Dji4Action
import org.r3akt.uas.dji4.Dji4Link
import org.r3akt.uas.dji4.Dji4Loader
import org.r3akt.uas.dji4.Dji4Sdk
import org.r3akt.uas.dji4.Dji4VideoView
import org.r3akt.uas.hud.FlightAction
import org.r3akt.uas.hud.HudState

object Variant {
    /** A real aircraft through DJI SDK 4. Flight actions go straight to the SDK and nowhere else. */
    val source: AircraftSource = object : AircraftSource {
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
                // UAS-FLT-002: an action is offered only while the aircraft reports a state that allows it.
                actions = buildSet {
                    if (d.flying == false) add(FlightAction.TAKEOFF)
                    if (d.flying == true) add(FlightAction.LAND)
                    if (d.flying == true && !d.goingHome && d.homeDistanceM != null) add(FlightAction.RTH)
                    if (d.goingHome) add(FlightAction.STOP_RTH)
                    if (connected && d.hasCamera) add(FlightAction.PHOTO)
                },
            )
        }
    }
}
