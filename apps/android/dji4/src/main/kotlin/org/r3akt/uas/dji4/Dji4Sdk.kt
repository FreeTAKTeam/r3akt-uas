package org.r3akt.uas.dji4

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import dji.common.error.DJIError
import dji.common.error.DJISDKError
import dji.common.camera.SettingsDefinitions
import dji.common.camera.SystemState
import dji.common.flightcontroller.FlightControllerState
import dji.common.gimbal.Rotation
import dji.common.gimbal.RotationMode
import dji.common.flightcontroller.GPSSignalLevel
import dji.common.util.CommonCallbacks
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct
import dji.sdk.products.Aircraft
import dji.sdk.sdkmanager.DJISDKInitEvent
import dji.sdk.sdkmanager.DJISDKManager
import org.r3akt.uas.bridge.AircraftPose
import org.r3akt.uas.bridge.DjiFlightState
import org.r3akt.uas.bridge.normalizeDji
import kotlin.concurrent.thread

enum class Dji4Link { NO_KEY, REGISTERING, REGISTRATION_FAILED, NO_AIRCRAFT, CONNECTED }

/** Local flight commands; nothing received over a network reaches these. */
enum class Dji4Action { TAKEOFF, LAND, RTH, STOP_RTH, PHOTO, RECORD_START, RECORD_STOP, PHOTO_MODE, VIDEO_MODE, GIMBAL_DOWN, GIMBAL_LEVEL }

/** Camera shooting mode as reported; playback, download and others read as `null`. */
enum class Dji4CameraMode { PHOTO, VIDEO }

/** What the aircraft reports right now. `null` means not reported, never zero. */
data class Dji4State(
    val link: Dji4Link,
    /** DJI registration error text, verbatim. */
    val error: String?,
    val model: String?,
    /** `null` until the flight controller reports. */
    val pose: AircraftPose?,
    val flying: Boolean?,
    val motorsOn: Boolean?,
    val goingHome: Boolean,
    val flightMode: String?,
    val satellites: Int?,
    /** DJI `GPSSignalLevel` 0..5; `null` when DJI reports NONE. */
    val gpsLevel: Int?,
    /** Phone clock when this flight state arrived, epoch milliseconds. */
    val receivedAtMs: Long?,
    val flightTimeS: Long?,
    val homeDistanceM: Double?,
    val batteryPercent: Int?,
    val gimbalPitchDeg: Double?,
    /** Gimbal `attitudeInDegrees.yaw` and `yawRelativeToAircraftHeading`, as reported; their
     *  meaning per product is recorded in the ground test before any use (D-15). */
    val gimbalYawDeg: Double?,
    val gimbalYawRelativeDeg: Double?,
    val hasCamera: Boolean,
    val hasGimbal: Boolean,
    val cameraMode: Dji4CameraMode?,
    val recording: Boolean?,
    val recordingTimeS: Int?,
    /** Downlink signal quality 0..100 as reported by the air link. */
    val linkPercent: Int?,
)

/**
 * DJI SDK 4 registration, aircraft connection, live state and local flight
 * commands. Nothing may touch this object before [Dji4Loader.install] has run.
 */
object Dji4Sdk {
    @Volatile private var link = Dji4Link.NO_KEY
    @Volatile private var error: String? = null
    @Volatile private var aircraft: Aircraft? = null
    @Volatile private var flight: FlightControllerState? = null
    @Volatile private var flightAt: Long? = null
    @Volatile private var battery: Int? = null
    @Volatile private var gimbalPitch: Double? = null
    @Volatile private var gimbalYaw: Double? = null
    @Volatile private var gimbalYawRelative: Double? = null
    @Volatile private var camera: SystemState? = null
    @Volatile private var flatMode = false
    @Volatile private var linkQuality: Int? = null
    /** Whether [start] has run; before it, [Dji4Link.NO_KEY] only means "not checked yet". */
    @Volatile var started = false
        private set

    /** Registers the app with DJI and starts listening for an aircraft. Does nothing after the first call. */
    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        @Suppress("DEPRECATION") // Bundle.get: the key may be compiled as a non-string value.
        val key = app.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
            .metaData?.get("com.dji.sdk.API_KEY")?.toString().orEmpty()
        // UAS-SCF-005: without a key the SDK manager is never touched, so nothing connects.
        if (key.isEmpty()) return
        link = Dji4Link.REGISTERING
        thread(name = "dji-register") {
            runCatching { DJISDKManager.getInstance().registerApp(app, Callback) }
                .onFailure { fail(it.message ?: it.toString()) }
        }
    }

    private fun fail(reason: String) {
        error = reason
        link = Dji4Link.REGISTRATION_FAILED
    }

    private object Callback : DJISDKManager.SDKManagerCallback {
        override fun onRegister(result: DJIError?) {
            if (result == DJISDKError.REGISTRATION_SUCCESS) {
                link = Dji4Link.NO_AIRCRAFT
                DJISDKManager.getInstance().startConnectionToProduct()
            } else {
                fail(result?.description ?: "no error reported")
            }
        }

        override fun onProductConnect(product: BaseProduct?) = attach(product)
        override fun onProductChanged(product: BaseProduct?) = attach(product)
        override fun onProductDisconnect() = detach()

        override fun onComponentChange(key: BaseProduct.ComponentKey?, old: BaseComponent?, new: BaseComponent?) {
            new?.setComponentListener { attach(DJISDKManager.getInstance().product) }
            attach(DJISDKManager.getInstance().product)
        }

        override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {}
        override fun onDatabaseDownloadProgress(current: Long, total: Long) {}
    }

    private fun attach(product: BaseProduct?) {
        val connected = (product as? Aircraft)?.takeIf { it.isConnected } ?: return detach()
        aircraft = connected
        connected.flightController?.setStateCallback {
            flight = it
            flightAt = System.currentTimeMillis()
        }
        connected.battery?.setStateCallback { battery = it.chargeRemainingInPercent }
        connected.gimbal?.setStateCallback { state ->
            val attitude = state.attitudeInDegrees
            gimbalPitch = attitude?.pitch
                ?.takeUnless { it == dji.common.gimbal.Attitude.NO_ROTATION }?.toDouble()
            gimbalYaw = attitude?.yaw
                ?.takeUnless { it == dji.common.gimbal.Attitude.NO_ROTATION }?.toDouble()
            gimbalYawRelative = state.yawRelativeToAircraftHeading.toDouble().takeIf { it.isFinite() }
        }
        connected.camera?.let { cam ->
            flatMode = runCatching { cam.isFlatCameraModeSupported }.getOrDefault(false)
            cam.setSystemStateCallback { camera = it }
        }
        connected.airLink?.setDownlinkSignalQualityCallback { linkQuality = it.takeIf { q -> q in 0..100 } }
        link = Dji4Link.CONNECTED
    }

    /** Forgets every reported value, so the cockpit shows unknown rather than stale data. */
    private fun detach() {
        aircraft = null
        flight = null
        flightAt = null
        battery = null
        gimbalPitch = null
        gimbalYaw = null
        gimbalYawRelative = null
        camera = null
        linkQuality = null
        if (link == Dji4Link.CONNECTED) link = Dji4Link.NO_AIRCRAFT
    }

    fun state(): Dji4State {
        val link = link
        // A callback that lands after a disconnect must not bring old values back.
        val live = link == Dji4Link.CONNECTED
        val product = aircraft.takeIf { live }
        val fc = flight.takeIf { live }
        val cam = camera.takeIf { live }
        // All DJI value normalization lives in the Rust integration (UAS-ARC-005).
        val pose = fc?.let {
            val at = it.aircraftLocation
            normalizeDji(
                DjiFlightState(
                    latitudeDeg = at?.latitude ?: Double.NaN,
                    longitudeDeg = at?.longitude ?: Double.NaN,
                    altitudeM = at?.altitude?.toDouble() ?: Double.NaN,
                    yawDeg = it.attitude?.yaw ?: Double.NaN,
                    velocityNorthMps = it.velocityX.toDouble(),
                    velocityEastMps = it.velocityY.toDouble(),
                    velocityDownMps = it.velocityZ.toDouble(),
                )
            )
        }
        return Dji4State(
            link = link,
            error = error,
            model = product?.model?.displayName,
            pose = pose,
            flying = fc?.isFlying,
            motorsOn = fc?.areMotorsOn(),
            goingHome = fc?.isGoingHome == true,
            flightMode = fc?.flightModeString,
            satellites = fc?.satelliteCount,
            gpsLevel = fc?.gpsSignalLevel?.takeIf { it != GPSSignalLevel.NONE }?.value(),
            receivedAtMs = flightAt.takeIf { fc != null },
            flightTimeS = fc?.flightTimeInSeconds?.toLong(),
            homeDistanceM = homeDistance(fc, pose),
            batteryPercent = battery.takeIf { live },
            gimbalPitchDeg = gimbalPitch.takeIf { live },
            gimbalYawDeg = gimbalYaw.takeIf { live },
            gimbalYawRelativeDeg = gimbalYawRelative.takeIf { live },
            hasCamera = product?.camera != null,
            hasGimbal = product?.gimbal != null,
            cameraMode = cam?.let(::cameraMode),
            recording = cam?.isRecording,
            recordingTimeS = cam?.takeIf { it.isRecording }?.currentVideoRecordingTimeInSeconds,
            linkPercent = linkQuality.takeIf { live },
        )
    }

    private fun cameraMode(st: SystemState): Dji4CameraMode? =
        if (flatMode) st.flatMode?.name?.let {
            when {
                it.startsWith("PHOTO_") -> Dji4CameraMode.PHOTO
                it.startsWith("VIDEO_") || it == "SLOW_MOTION" -> Dji4CameraMode.VIDEO
                else -> null
            }
        } else when (st.mode) {
            SettingsDefinitions.CameraMode.SHOOT_PHOTO -> Dji4CameraMode.PHOTO
            SettingsDefinitions.CameraMode.RECORD_VIDEO -> Dji4CameraMode.VIDEO
            else -> null
        }

    private fun homeDistance(fc: FlightControllerState?, pose: AircraftPose?): Double? {
        val home = fc?.homeLocation?.takeIf { fc.isHomeLocationSet && it.isValid } ?: return null
        val lat = pose?.latitudeDeg ?: return null
        val lon = pose.longitudeDeg ?: return null
        val metres = FloatArray(1)
        Location.distanceBetween(lat, lon, home.latitude, home.longitude, metres)
        return metres[0].toDouble()
    }

    /**
     * Sends one local flight command, straight from an operator tap. [onResult]
     * gets `null` when DJI accepts it, otherwise DJI's error text verbatim
     * (UAS-FLT-005), possibly on another thread.
     */
    fun perform(action: Dji4Action, onResult: (String?) -> Unit) {
        val product = aircraft.takeIf { link == Dji4Link.CONNECTED }
        // An exception from the SDK is reported like a rejection; it must not take the cockpit down in flight.
        runCatching {
            val fc = product?.flightController
            val cam = product?.camera
            val done = CommonCallbacks.CompletionCallback<DJIError> { onResult(it?.description) }
            val sent = when (action) {
                Dji4Action.TAKEOFF -> fc?.startTakeoff(done)
                Dji4Action.LAND -> fc?.startLanding(done)
                Dji4Action.RTH -> fc?.startGoHome(done)
                Dji4Action.STOP_RTH -> fc?.cancelGoHome(done)
                Dji4Action.PHOTO -> cam?.startShootPhoto(done)
                Dji4Action.RECORD_START -> cam?.startRecordVideo(done)
                Dji4Action.RECORD_STOP -> cam?.stopRecordVideo(done)
                Dji4Action.PHOTO_MODE -> cam?.let {
                    if (flatMode) it.setFlatMode(SettingsDefinitions.FlatCameraMode.PHOTO_SINGLE, done)
                    else it.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, done)
                }
                Dji4Action.VIDEO_MODE -> cam?.let {
                    if (flatMode) it.setFlatMode(SettingsDefinitions.FlatCameraMode.VIDEO_NORMAL, done)
                    else it.setMode(SettingsDefinitions.CameraMode.RECORD_VIDEO, done)
                }
                Dji4Action.GIMBAL_DOWN -> product?.gimbal?.rotate(pitchTo(-90f), done)
                Dji4Action.GIMBAL_LEVEL -> product?.gimbal?.rotate(pitchTo(0f), done)
            }
            // UAS-FLT-004: a command with nowhere to go is refused now, never kept for a later connection.
            if (sent == null) onResult("No aircraft connected")
        }.onFailure { onResult(it.message ?: it.toString()) }
    }

    private fun pitchTo(deg: Float) = Rotation.Builder().mode(RotationMode.ABSOLUTE_ANGLE).pitch(deg).time(1.0).build()

    // Read-only details for the setup page, as the SDK reports them; `null` = not reported.
    // The SDK manager is touched only once registered (UAS-SCF-005), and a getter that throws reads as unknown.
    private val registered get() = link == Dji4Link.NO_AIRCRAFT || link == Dji4Link.CONNECTED

    fun sdkVersion(): String? = if (registered) runCatching { DJISDKManager.getInstance().sdkVersion }.getOrNull() else null

    /** The remote controller link as reported, or `null` when no product or controller is reported. */
    fun remoteControllerConnected(): Boolean? = if (!registered) null else
        runCatching { (DJISDKManager.getInstance().product as? Aircraft)?.remoteController?.isConnected }.getOrNull()

    /** The connected aircraft's firmware package version, or `null`. */
    fun firmware(): String? = aircraft.takeIf { link == Dji4Link.CONNECTED }
        ?.let { runCatching { it.firmwarePackageVersion }.getOrNull() }?.takeIf { it.isNotBlank() }

    /** Status for the cockpit's LOG dialog. */
    fun describe(): String {
        val s = state()
        return buildString {
            append("DJI SDK 4: ")
            append(
                when (s.link) {
                    Dji4Link.NO_KEY -> "no app key in this build (djiApiKey Gradle property). The SDK is not registered and nothing connects."
                    Dji4Link.REGISTERING -> "registering with DJI…"
                    Dji4Link.REGISTRATION_FAILED -> "registration failed."
                    Dji4Link.NO_AIRCRAFT -> "registered, waiting for an aircraft."
                    Dji4Link.CONNECTED -> "connected to ${s.model ?: "an aircraft"}."
                }
            )
            s.error?.let { append("\nDJI: $it") }
            s.flightMode?.let { append("\nFlight mode: $it") }
            // Recorded in the ground test (DJI plan task 4); shown as reported.
            if (s.link == Dji4Link.CONNECTED) {
                append("\nCamera ${s.cameraMode ?: "mode not reported"}${if (flatMode) " (flat modes)" else ""}, recording ${s.recording ?: "-"}, link ${s.linkPercent ?: "-"}%")
                append("\nGPS level ${s.gpsLevel ?: "-"}, satellites ${s.satellites ?: "-"}")
                append("\nGimbal yaw ${s.gimbalYawDeg ?: "-"}, relative to heading ${s.gimbalYawRelativeDeg ?: "-"}")
                append("\n").append(Dji4VideoView.report())
            }
        }
    }
}
