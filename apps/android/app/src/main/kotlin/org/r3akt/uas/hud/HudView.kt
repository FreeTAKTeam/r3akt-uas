package org.r3akt.uas.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import org.r3akt.uas.R
import org.r3akt.uas.screens.Screen
import org.r3akt.uas.bridge.AltitudeReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The cockpit HUD (UAS-FLT-001), laid out on the approved 960 x 540 design
 * and scaled to the screen. Elements on the left stay left, on the right stay
 * right and the middle stays centred, so wider phones only add space.
 */
class HudView(context: Context) : ViewGroup(context) {
    enum class Anchor { LEFT, CENTER, RIGHT }

    private class Place(val x: Float, val y: Float, val w: Float, val h: Float, val anchor: Anchor) : LayoutParams(0, 0)

    var state = HudState(status = "NOT CONNECTED", simulated = false)
        set(value) { field = value; applyState() }

    /** An action error (title, detail); stays until the operator dismisses it (UAS-FLT-005). */
    var alert: Pair<String, String>? = null
        set(value) { field = value; applyState() }

    var onBack: () -> Unit = {}
    var onLog: () -> Unit = {}
    /** Opens a page over the cockpit. */
    var onScreen: (Screen) -> Unit = {}
    var onAction: (FlightAction) -> Unit = {}

    /** Tap on the aim cross; null when this source cannot place markers. */
    var onAim: (() -> Unit)? = null
        set(value) { field = value; applyState() }

    /** The marker being named at the aim cross, as (name, colour); drawn until placed or cancelled. */
    var aimMarker: Pair<String, Int>? = null
        set(value) { field = value; invalidate() }

    /** Contacts for the small radar, set on every cockpit poll. */
    var contacts: List<org.r3akt.uas.Contact> = emptyList()
        set(value) { if (value != field) { field = value; invalidate() } }

    /** The HUD scale and the screen y of the design's line [dy], for overlays laid out on the same grid. */
    val unit get() = s
    fun screenY(dy: Float) = y(dy)

    private val label = resources.getFont(R.font.rajdhani_bold)
    private val mono = resources.getFont(R.font.b612mono_bold)

    private var s = 1f
    private var extra = 0f
    private var ox = 0f
    private var oy = 0f
    private var cutLeft = 0
    private var cutRight = 0

    private var flightOpen = false
    private var menuOpen = false
    /** The action waiting in the confirm panel. */
    private var pending: FlightAction? = null

    private fun button(icon: Icon?, text: String = "", bare: Boolean = false, shutter: Boolean = false) =
        HudButton(context, { s }, label, icon, text, bare, shutter)

    private val back = button(Hud.back, bare = true).apply { contentDescription = "Back" }
    private val settings = button(Hud.more, bare = true).apply { contentDescription = "Settings" }
    private val flight = button(Hud.aircraft, "FLIGHT")
    private val takeoffLand = button(Hud.takeoff, "TAKEOFF")
    private val rth = button(Hud.home, "RTH")
    private val cameraMode = button(Hud.photo)
    private val shutterButton = button(null, shutter = true).apply { contentDescription = "Take photo" }
    private val menu = button(Hud.menu, "MENU")
    private val map = button(Hud.map, "MAP").apply { contentDescription = "Map" }
    private val observations = button(Hud.observation, "OBS").apply { contentDescription = "Observations" }
    private val radar = button(null, "RADAR").apply { contentDescription = "Radar" }
    private val setup = button(null, "SETUP").apply { contentDescription = "Connection and setup" }
    private val stream = button(null, "STREAM")
    private val sessions = button(null, "HIST").apply { contentDescription = "Sessions and recordings" }
    // Invisible: the small radar is drawn below; this only takes the tap.
    private val radarTap = View(context).apply { contentDescription = "Open the radar"; isFocusable = true }
    private val log = button(Hud.log, "LOG").apply { contentDescription = "Session log" }
    private val slider = HudSlider(context, { s }, label)
    private val cancel = button(null, "CANCEL")
    private val dismiss = button(Hud.close, bare = true).apply { contentDescription = "Dismiss error" }
    // Invisible: the reticle is drawn below; this only takes the tap.
    // Invisible: the gimbal scale is drawn below; a tap points the camera down or back to level.
    private val gimbalTap = View(context).apply { isFocusable = true }
    private val aim = View(context).apply { contentDescription = "Place a marker at the aim cross"; isFocusable = true }

    init {
        setWillNotDraw(false)
        // First, so every other control drawn over the reticle area keeps its taps.
        add(aim, 381f, 206f, 198f, 128f, Anchor.CENTER)
        add(radarTap, 12f, 368f, 156f, 156f, Anchor.LEFT)
        add(back, 0f, 0f, 44f, 40f, Anchor.LEFT)
        add(settings, 916f, 0f, 44f, 40f, Anchor.RIGHT)
        add(flight, 12f, 132f, 60f, 60f, Anchor.LEFT)
        add(takeoffLand, 12f, 200f, 60f, 60f, Anchor.LEFT)
        add(rth, 12f, 266f, 60f, 60f, Anchor.LEFT)
        add(gimbalTap, 812f, 150f, 60f, 240f, Anchor.RIGHT)
        add(cameraMode, 880f, 180f, 68f, 48f, Anchor.RIGHT)
        add(shutterButton, 880f, 238f, 68f, 68f, Anchor.RIGHT)
        add(menu, 888f, 476f, 60f, 52f, Anchor.RIGHT)
        add(map, 692f, 476f, 60f, 52f, Anchor.RIGHT)
        add(observations, 756f, 476f, 60f, 52f, Anchor.RIGHT)
        add(log, 820f, 476f, 60f, 52f, Anchor.RIGHT)
        add(radar, 692f, 416f, 60f, 52f, Anchor.RIGHT)
        add(setup, 756f, 416f, 60f, 52f, Anchor.RIGHT)
        add(sessions, 820f, 416f, 60f, 52f, Anchor.RIGHT)
        add(stream, 888f, 416f, 60f, 52f, Anchor.RIGHT)
        add(slider, 308f, 266f, 344f, 52f, Anchor.CENTER)
        add(cancel, 562f, 330f, 90f, 44f, Anchor.CENTER)
        add(dismiss, 642f, 408f, 44f, 44f, Anchor.CENTER)

        back.setOnClickListener { onBack() }
        log.setOnClickListener { onLog() }
        settings.setOnClickListener { onScreen(Screen.SETTINGS) }
        map.setOnClickListener { onScreen(Screen.MAP) }
        observations.setOnClickListener { onScreen(Screen.OBSERVATIONS) }
        radar.setOnClickListener { onScreen(Screen.RADAR) }
        radarTap.setOnClickListener { onScreen(Screen.RADAR) }
        setup.setOnClickListener { onScreen(Screen.SETUP) }
        sessions.setOnClickListener { onScreen(Screen.SESSIONS) }
        takeoffLand.setOnClickListener { pending = if (state.airborne == true) FlightAction.LAND else FlightAction.TAKEOFF; applyState() }
        rth.setOnClickListener { if (state.rthActive) onAction(FlightAction.STOP_RTH) else { pending = FlightAction.RTH; applyState() } }
        shutterButton.setOnClickListener { shutterAction()?.let(onAction) }
        cameraMode.setOnClickListener { onAction(if (state.cameraMode == CameraMode.VIDEO) FlightAction.PHOTO_MODE else FlightAction.VIDEO_MODE) }
        gimbalTap.setOnClickListener { onAction(gimbalAction()) }
        stream.setOnClickListener { onAction(if (state.streaming) FlightAction.STREAM_STOP else FlightAction.STREAM_START) }
        slider.onConfirm = { pending?.let { pending = null; applyState(); onAction(it) } }
        cancel.setOnClickListener { pending = null; applyState() }
        dismiss.setOnClickListener { alert = null }
        aim.setOnClickListener { onAim?.invoke() }
        flight.setOnClickListener { flightOpen = !flightOpen; applyState() }
        menu.setOnClickListener { menuOpen = !menuOpen; applyState() }
        applyState()
    }

    private fun add(v: View, x: Float, y: Float, w: Float, h: Float, a: Anchor) = addView(v, Place(x, y, w, h, a))

    private fun applyState() {
        val st = state
        val landing = st.airborne == true
        takeoffLand.icon = if (landing) Hud.land else Hud.takeoff
        takeoffLand.label = if (landing) "LAND" else "TAKEOFF"
        takeoffLand.isEnabled = (if (landing) FlightAction.LAND else FlightAction.TAKEOFF) in st.actions
        takeoffLand.contentDescription = takeoffLand.label + if (takeoffLand.isEnabled) "" else ", unavailable"
        rth.isEnabled = (if (st.rthActive) FlightAction.STOP_RTH else FlightAction.RTH) in st.actions
        rth.tone = if (st.rthActive) HudButton.Tone.CAUTION else HudButton.Tone.NORMAL
        rth.label = if (st.rthActive) "STOP RTH" else "RTH"
        rth.contentDescription = (if (st.rthActive) "Stop return to home" else "Return to home") + if (rth.isEnabled) "" else ", unavailable"
        shutterButton.isEnabled = shutterAction() in st.actions
        shutterButton.video = st.cameraMode == CameraMode.VIDEO
        shutterButton.recording = st.recording == true
        shutterButton.contentDescription = when (shutterAction()) {
            FlightAction.RECORD_STOP -> "Stop recording"
            FlightAction.RECORD_START -> "Start recording"
            else -> "Take photo"
        } + if (shutterButton.isEnabled) "" else ", unavailable"
        val toVideo = st.cameraMode != CameraMode.VIDEO
        cameraMode.icon = if (toVideo) Hud.photo else Hud.video
        cameraMode.isEnabled = (if (toVideo) FlightAction.VIDEO_MODE else FlightAction.PHOTO_MODE) in st.actions
        cameraMode.contentDescription = (if (toVideo) "Photo mode; switch to video" else "Video mode; switch to photo") +
            if (cameraMode.isEnabled) "" else ", unavailable"
        stream.label = if (st.streaming) "STOP" else "STREAM"
        stream.tone = if (st.streaming) HudButton.Tone.CAUTION else HudButton.Tone.NORMAL
        stream.isEnabled = (if (st.streaming) FlightAction.STREAM_STOP else FlightAction.STREAM_START) in st.actions
        stream.contentDescription = (if (st.streaming) "Stop the video stream" else "Stream video to the video server") +
            if (stream.isEnabled) "" else ", unavailable"
        gimbalTap.isEnabled = gimbalAction() in st.actions
        gimbalTap.contentDescription = (if (gimbalAction() == FlightAction.GIMBAL_DOWN) "Point the gimbal down" else "Level the gimbal") +
            if (gimbalTap.isEnabled) "" else ", unavailable"

        // UAS-FLT-004: a confirm never outlives its action, so nothing fires after a disconnect.
        if (pending !in st.actions) pending = null
        val confirm = pending
        if (confirm != null) slider.label = confirmText(confirm).third
        slider.visibility = if (confirm != null) VISIBLE else GONE
        cancel.visibility = slider.visibility
        dismiss.visibility = if (alert != null) VISIBLE else GONE
        aim.visibility = if (onAim != null && confirm == null) VISIBLE else GONE

        flight.icon = if (flightOpen) Hud.close else Hud.aircraft
        flight.label = if (st.rthActive) "RTH ON" else "FLIGHT"
        flight.tone = if (st.rthActive) HudButton.Tone.CAUTION else HudButton.Tone.NORMAL
        flight.contentDescription = if (flightOpen) "Close flight actions" else "Flight actions"
        takeoffLand.visibility = if (flightOpen) VISIBLE else GONE
        rth.visibility = if (flightOpen) VISIBLE else GONE

        menu.icon = if (menuOpen) Hud.close else Hud.menu
        menu.contentDescription = if (menuOpen) "Close menu" else "Menu"
        for (v in listOf(map, observations, log, radar, setup, sessions, stream)) v.visibility = if (menuOpen) VISIBLE else GONE
        invalidate()
    }

    /** The shutter records in video mode and shoots a photo otherwise. */
    private fun shutterAction() = when {
        state.recording == true -> FlightAction.RECORD_STOP
        state.cameraMode == CameraMode.VIDEO -> FlightAction.RECORD_START
        else -> FlightAction.PHOTO
    }

    private fun gimbalAction() =
        if ((state.gimbalPitchDeg ?: 0.0) > -45.0) FlightAction.GIMBAL_DOWN else FlightAction.GIMBAL_LEVEL

    private fun confirmText(action: FlightAction) = when (action) {
        FlightAction.LAND -> Triple("CONFIRM LANDING",
            "Aircraft descends and lands at its current position. Check that the area below is clear.", "SLIDE TO LAND")
        FlightAction.RTH -> Triple("CONFIRM RETURN TO HOME",
            "Aircraft flies back to the recorded home point and lands there.", "SLIDE TO RETURN")
        else -> Triple("CONFIRM TAKEOFF",
            "Aircraft climbs to 1.2 m above the take-off point and hovers. Check that the area is clear.", "SLIDE TO TAKE OFF")
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        // Keep controls clear of a camera cutout; the backgrounds still run edge to edge.
        if (Build.VERSION.SDK_INT >= 28) {
            val cut = insets.displayCutout
            cutLeft = cut?.safeInsetLeft ?: 0
            cutRight = cut?.safeInsetRight ?: 0
            requestLayout()
        }
        return insets
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        val h = getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
        setMeasuredDimension(w, h)
        val usable = (w - cutLeft - cutRight).toFloat()
        s = min(usable / 960f, h / 540f)
        extra = usable - 960f * s
        ox = cutLeft.toFloat()
        oy = (h - 540f * s) / 2
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val p = c.layoutParams as Place
            c.measure(MeasureSpec.makeMeasureSpec((p.w * s).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((p.h * s).toInt(), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val p = c.layoutParams as Place
            val left = x(p.x, p.anchor).toInt()
            val top = y(p.y).toInt()
            c.layout(left, top, left + c.measuredWidth, top + c.measuredHeight)
        }
    }

    override fun checkLayoutParams(p: LayoutParams?) = p is Place

    private fun x(dx: Float, a: Anchor) = ox + dx * s + when (a) {
        Anchor.LEFT -> 0f
        Anchor.CENTER -> extra / 2
        Anchor.RIGHT -> extra
    }

    private fun y(dy: Float) = oy + dy * s

    // ---- drawing -------------------------------------------------------

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private fun plate(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int = Hud.PLATE) {
        fill.color = color
        c.drawRect(l, t, r, b, fill)
    }

    private fun stroke(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float = 2f) {
        line.color = color
        line.strokeWidth = width * s
        line.setShadowLayer(1.5f * s, 0f, 0f, Hud.HALO)
        c.drawLine(x1, y1, x2, y2, line)
    }

    /** Draws text at a baseline; returns its width. */
    private fun write(
        c: Canvas, str: String, x: Float, y: Float, size: Float, color: Int,
        monospace: Boolean = false, align: Paint.Align = Paint.Align.LEFT, spacing: Float = if (monospace) 0f else 0.1f,
    ): Float {
        text.typeface = if (monospace) mono else label
        text.textSize = size * s
        text.color = color
        text.textAlign = align
        text.letterSpacing = spacing
        text.setShadowLayer(2f * s, 0f, 0f, Hud.HALO)
        c.drawText(str, x, y, text)
        return text.measureText(str)
    }

    private fun box(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int = Hud.GREEN) {
        plate(c, l, t, r, b)
        line.color = color
        line.strokeWidth = 1.5f * s
        line.setShadowLayer(1.5f * s, 0f, 0f, Hud.HALO)
        c.drawRect(l, t, r, b, line)
    }

    private fun fmt(v: Double?, digits: Int = 1) = v?.let { "%.${digits}f".format(it) } ?: "—"

    override fun onDraw(canvas: Canvas) {
        val st = state
        // With live video the canvas stays clear so the video view behind shows through.
        if (!st.video) {
            canvas.drawColor(0xFF0A0F0B.toInt())
            // The confirm panel covers this spot.
            if (pending == null) write(canvas, "NO VIDEO", x(480f, Anchor.CENTER), y(186f), 13f, Hud.DIM, align = Paint.Align.CENTER, spacing = 0.2f)
        }

        drawTopBar(canvas, st)
        drawHeading(canvas, st.headingDeg)
        drawSpeed(canvas, st)
        drawAltitude(canvas, st)
        drawGimbal(canvas, st.gimbalPitchDeg)
        if (st.recording == true) {
            val t = st.recordingTimeS?.let { "%d:%02d".format(it / 60, it % 60) } ?: "—"
            write(canvas, "REC $t", x(914f, Anchor.RIGHT), y(326f), 12f, Hud.RED, monospace = true, align = Paint.Align.CENTER)
        }
        val confirm = pending
        if (confirm == null) drawReticle(canvas) else drawConfirm(canvas, st, confirm)
        drawRadar(canvas, st.headingDeg)
        drawTelemetry(canvas, st)
        alert?.let { drawAlert(canvas, it.first, it.second) }
    }

    private fun drawConfirm(c: Canvas, st: HudState, action: FlightAction) {
        val l = x(290f, Anchor.CENTER)
        val (title, body) = confirmText(action)
        box(c, l, y(166f), l + 380 * s, y(390f))
        write(c, title, l + 18 * s, y(194f), 13f, Hud.GREEN, spacing = 0.14f)
        // Never claims a home point that is not known.
        val home = st.homeDistanceM != null
        write(c, if (home) "HOME RECORDED" else "NO HOME POINT", l + 362 * s, y(194f), 12f,
            if (home) Hud.WHITE else Hud.AMBER, monospace = true, align = Paint.Align.RIGHT)
        text.typeface = label; text.textSize = 14 * s; text.color = Hud.WHITE
        text.textAlign = Paint.Align.LEFT; text.letterSpacing = 0f
        c.save()
        c.translate(l + 18 * s, y(204f))
        StaticLayout.Builder.obtain(body, 0, body.length, text, (344 * s).toInt()).setMaxLines(3).build().draw(c)
        c.restore()
    }

    private fun drawAlert(c: Canvas, title: String, detail: String) {
        val l = x(270f, Anchor.CENTER)
        val t = y(404f)
        plate(c, l, t, l + 420 * s, t + 52 * s, 0x59280606)
        line.color = Hud.RED; line.strokeWidth = 2f * s
        c.drawRect(l, t, l + 420 * s, t + 52 * s, line)
        val u = 22 * s / 24
        val ix = l + 12 * s
        val iy = t + 15 * s
        path.reset()
        path.moveTo(ix + 12 * u, iy + 3 * u); path.lineTo(ix + 22 * u, iy + 21 * u); path.lineTo(ix + 2 * u, iy + 21 * u); path.close()
        path.moveTo(ix + 12 * u, iy + 10 * u); path.lineTo(ix + 12 * u, iy + 15 * u)
        path.moveTo(ix + 12 * u, iy + 18 * u); path.lineTo(ix + 12 * u, iy + 18.5f * u)
        c.drawPath(path, line)
        write(c, title, l + 44 * s, t + 22 * s, 13f, Hud.RED)
        text.typeface = mono; text.textSize = 12 * s; text.letterSpacing = 0f
        val shown = TextUtils.ellipsize(detail, text, 318 * s, TextUtils.TruncateAt.END).toString()
        write(c, shown, l + 44 * s, t + 40 * s, 12f, Hud.WHITE, monospace = true)
    }

    private fun drawTopBar(c: Canvas, st: HudState) {
        plate(c, 0f, y(0f), width.toFloat(), y(40f))
        stroke(c, 0f, y(40f), width.toFloat(), y(40f), Hud.GREEN_LINE, 1f)
        val base = y(26f)

        // Left: flight status, simulator badge, flight mode.
        var lx = x(56f, Anchor.LEFT)
        val statusColor = if (st.rthActive) Hud.AMBER else Hud.GREEN
        text.typeface = label; text.textSize = 13 * s; text.letterSpacing = 0.1f
        val sw = text.measureText(st.status)
        rect.set(lx, y(9f), lx + sw + 16 * s, y(31f))
        line.color = statusColor; line.strokeWidth = 1.5f * s
        c.drawRect(rect, line)
        write(c, st.status, lx + 8 * s, base, 13f, statusColor)
        lx = rect.right + 10 * s
        if (st.simulated) {
            // UAS-RAD-009: simulator output is always marked, and the badge cannot be hidden.
            text.textSize = 13 * s
            val bw = text.measureText("SIMULATED")
            rect.set(lx, y(9f), lx + bw + 16 * s, y(31f))
            fill.color = Hud.AMBER
            c.drawRect(rect, fill)
            write(c, "SIMULATED", lx + 8 * s, base, 13f, Hud.INK_CAUTION)
            lx = rect.right + 10 * s
        }
        st.flightMode?.let { write(c, it, lx, base, 14f, Hud.WHITE, monospace = true) }

        // Right: status cells, laid out right to left from the settings button.
        var rx = x(916f, Anchor.RIGHT)
        fun cell(name: String, value: String, color: Int = Hud.WHITE) {
            text.typeface = mono; text.textSize = 14 * s; text.letterSpacing = 0f
            val vw = text.measureText(value)
            text.typeface = label; text.textSize = 11 * s; text.letterSpacing = 0.1f
            val nw = text.measureText(name)
            val left = rx - 20 * s - nw - 6 * s - vw
            stroke(c, rx, y(0f), rx, y(40f), Hud.GREEN_LINE, 1f)
            write(c, name, left + 10 * s, base, 11f, Hud.GREEN)
            write(c, value, left + 10 * s + nw + 6 * s, base, 14f, color, monospace = true)
            rx = left
        }
        val battery = st.batteryPercent
        cell("BAT", battery?.let { "$it%" } ?: "—", if (battery != null && battery <= 30) Hud.AMBER else Hud.WHITE)
        cell("LINK", st.linkPercent?.let { "$it%" } ?: "—", if (st.linkPercent != null && st.linkPercent <= 30) Hud.AMBER else Hud.WHITE)
        if (st.streaming) cell("STRM", "LIVE", Hud.GREEN) else cell("STRM", "OFF", Hud.DIM)
        when (st.mesh) {
            true -> cell("MESH", "ON", Hud.GREEN)
            false -> cell("MESH", "DOWN", Hud.AMBER)
            null -> cell("MESH", "OFF", Hud.DIM)
        }
        cell("GPS", st.gpsSatellites?.toString() ?: "—")

        // Battery bar under the top bar.
        plate(c, 0f, y(40f), width.toFloat(), y(44f), 0x4D000000)
        if (battery != null) {
            fill.color = if (battery <= 30) Hud.AMBER else Hud.GREEN
            c.drawRect(0f, y(40f), width * battery / 100f, y(44f), fill)
        }
    }

    private fun drawHeading(c: Canvas, hdg: Double?) {
        val l = x(300f, Anchor.CENTER)
        val cx = x(480f, Anchor.CENTER)
        plate(c, l, y(60f), l + 360 * s, y(94f))
        if (hdg != null) {
            c.save()
            c.clipRect(l, y(58f), l + 360 * s, y(94f))
            stroke(c, l, y(60f), l + 360 * s, y(60f), Hud.WHITE)
            var d = ceil((hdg - 45) / 5).toInt() * 5
            while (d <= hdg + 45) {
                val tx = cx + ((d - hdg) * 4).toFloat() * s
                val n = ((d % 360) + 360) % 360
                val major = n % 10 == 0
                stroke(c, tx, y(61f), tx, y(if (major) 73f else 67f), Hud.WHITE)
                if (major && abs(d - hdg) >= 10) {
                    val name = when (n) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; else -> "%03d".format(n) }
                    write(c, name, tx, y(88f), 12f, Hud.GREEN, monospace = true, align = Paint.Align.CENTER)
                }
                d += 5
            }
            c.restore()
            stroke(c, cx, y(56f), cx, y(70f), Hud.GREEN)
        }
        write(c, "HDG", x(412f, Anchor.CENTER), y(116f), 11f, Hud.GREEN)
        box(c, x(446f, Anchor.CENTER), y(98f), x(514f, Anchor.CENTER), y(124f))
        write(c, hdg?.let { "%03d°".format(((it.toInt() % 360) + 360) % 360) } ?: "—", cx, y(117f), 17f, Hud.WHITE, monospace = true, align = Paint.Align.CENTER)
    }

    private fun drawSpeed(c: Canvas, st: HudState) {
        val l = x(150f, Anchor.LEFT)
        val r = l + 70 * s
        val mid = y(270f)
        write(c, "SPD", l, y(142f), 11f, Hud.GREEN)
        write(c, "m/s", l + 30 * s, y(142f), 11f, Hud.WHITE)
        plate(c, l, y(150f), r, y(390f))
        val v = st.groundSpeedMps
        if (v != null) {
            c.save()
            c.clipRect(l, y(150f), r, y(390f))
            stroke(c, r - s, y(150f), r - s, y(390f), Hud.WHITE)
            for (t in ceil(v - 10).toInt()..(v + 10).toInt()) {
                if (t < 0) continue
                val ty = mid - ((t - v) * 12).toFloat() * s
                val major = t % 2 == 0
                stroke(c, r - (if (major) 14 else 8) * s, ty, r - 2 * s, ty, Hud.WHITE)
                if (major && abs(t - v) > 1.4) write(c, "$t", l + 50 * s, ty + 4 * s, 12f, Hud.GREEN, monospace = true, align = Paint.Align.RIGHT)
            }
            c.restore()
        }
        box(c, l + 2 * s, mid - 15 * s, l + 54 * s, mid + 15 * s)
        write(c, fmt(v), l + 28 * s, mid + 6 * s, 17f, Hud.WHITE, monospace = true, align = Paint.Align.CENTER)
        val vs = st.verticalSpeedMps?.let { (if (it >= 0) "+" else "") + "%.1f m/s".format(it) } ?: "—"
        // Starts right of the radar ring.
        val w = write(c, "V.S", l + 26 * s, y(412f), 11f, Hud.GREEN)
        write(c, vs, l + 26 * s + w + 6 * s, y(412f), 15f, Hud.WHITE, monospace = true)
    }

    private fun drawAltitude(c: Canvas, st: HudState) {
        val l = x(724f, Anchor.RIGHT)
        val mid = y(270f)
        // UAS-PRF-004: the altitude always carries its reference; take-off-relative is never MSL/AGL.
        val ref = when (st.altitudeReference) {
            AltitudeReference.TAKEOFF_RELATIVE -> "TKOF REL"
            AltitudeReference.MSL -> "MSL"
            AltitudeReference.HAE -> "HAE"
            AltitudeReference.AGL -> "AGL"
            AltitudeReference.UNKNOWN -> "REF ?"
        }
        var lx = l + write(c, "ALT", l, y(142f), 11f, Hud.GREEN) + 6 * s
        lx += write(c, "m", lx, y(142f), 11f, Hud.WHITE) + 6 * s
        text.typeface = label; text.textSize = 9 * s; text.letterSpacing = 0.08f
        val tw = text.measureText(ref)
        line.color = Hud.GREEN; line.strokeWidth = 1f * s
        c.drawRect(lx, y(132f), lx + tw + 8 * s, y(145f), line)
        write(c, ref, lx + 4 * s, y(142f), 9f, Hud.GREEN, spacing = 0.08f)

        plate(c, l, y(150f), l + 76 * s, y(390f))
        val a = st.altitudeM
        if (a != null) {
            c.save()
            c.clipRect(l, y(150f), l + 76 * s, y(390f))
            stroke(c, l + s, y(150f), l + s, y(390f), Hud.WHITE)
            var t = ceil((a - 25) / 5).toInt() * 5
            while (t <= a + 25) {
                val ty = mid - ((t - a) * 4.8).toFloat() * s
                val major = t % 10 == 0
                stroke(c, l + 2 * s, ty, l + (if (major) 14 else 8) * s, ty, Hud.WHITE)
                if (major && abs(t - a) > 4) write(c, "$t", l + 20 * s, ty + 4 * s, 12f, Hud.GREEN, monospace = true)
                t += 5
            }
            c.restore()
        }
        box(c, l + 14 * s, mid - 15 * s, l + 74 * s, mid + 15 * s)
        write(c, fmt(a), l + 44 * s, mid + 6 * s, 17f, Hud.WHITE, monospace = true, align = Paint.Align.CENTER)
    }

    private fun drawGimbal(c: Canvas, pitch: Double?) {
        val gx = x(837f, Anchor.RIGHT)
        write(c, "GMBL", x(818f, Anchor.RIGHT), y(146f), 10f, Hud.GREEN)
        stroke(c, gx, y(160f), gx, y(380f), Hud.GREEN)
        for ((deg, ty) in listOf(0 to 160f, -30 to 233f, -60 to 307f, -90 to 380f)) {
            stroke(c, gx - 5 * s, y(ty), gx + 5 * s, y(ty), Hud.GREEN)
            write(c, "$deg", gx - 8 * s, y(ty + 4f), 10f, Hud.GREEN, monospace = true, align = Paint.Align.RIGHT)
        }
        if (pitch != null) {
            val py = y(160f + (-pitch.toFloat() / 90f) * 220f)
            path.reset()
            path.moveTo(gx + 3 * s, py)
            path.lineTo(gx + 11 * s, py - 6 * s)
            path.lineTo(gx + 11 * s, py + 6 * s)
            path.close()
            fill.color = Hud.WHITE
            c.drawPath(path, fill)
            write(c, "%.0f°".format(pitch), gx + 13 * s, py + 4 * s, 12f, Hud.WHITE, monospace = true)
        }
    }

    private fun drawReticle(c: Canvas) {
        val cx = x(480f, Anchor.CENTER)
        val cy = y(270f)
        stroke(c, cx - 44 * s, cy, cx - 12 * s, cy, Hud.WHITE)
        stroke(c, cx + 12 * s, cy, cx + 44 * s, cy, Hud.WHITE)
        stroke(c, cx, cy - 30 * s, cx, cy - 14 * s, Hud.WHITE)
        stroke(c, cx, cy + 14 * s, cx, cy + 30 * s, Hud.WHITE)
        fill.color = Hud.GREEN
        c.drawCircle(cx, cy, 3 * s, fill)
        for ((sx, sy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) {
            val bx = cx + sx * 99 * s
            val by = cy + sy * 64 * s
            stroke(c, bx, by, bx - sx * 20 * s, by, Hud.GREEN)
            stroke(c, bx, by, bx, by - sy * 20 * s, Hud.GREEN)
        }
        aimMarker?.let { (name, color) ->
            // The pending marker: a diamond on the cross, its name below, both until placed.
            path.reset()
            path.moveTo(cx, cy - 9 * s); path.lineTo(cx + 9 * s, cy); path.lineTo(cx, cy + 9 * s); path.lineTo(cx - 9 * s, cy); path.close()
            fill.color = color
            c.drawPath(path, fill)
            line.color = Hud.WHITE; line.strokeWidth = 2f * s
            c.drawPath(path, line)
            write(c, "$name ?", cx, cy + 50 * s, 12f, Hud.AMBER, align = Paint.Align.CENTER)
        }
    }

    private fun drawRadar(c: Canvas, hdg: Double?) {
        val cx = x(90f, Anchor.LEFT)
        val cy = y(446f)
        fill.color = Hud.PLATE
        c.drawCircle(cx, cy, 78 * s, fill)
        line.strokeWidth = 1.5f * s; line.color = Hud.GREEN
        c.drawCircle(cx, cy, 77 * s, line)
        line.strokeWidth = 1f * s; line.color = Hud.GREEN_DIM
        c.drawCircle(cx, cy, 62 * s, line)
        c.drawCircle(cx, cy, 31 * s, line)
        if (hdg == null) {
            // UAS-RAD-008: without aircraft heading the radar says so; phone heading is never used.
            write(c, "NO HEADING", cx, cy - 18 * s, 11f, Hud.AMBER, align = Paint.Align.CENTER)
            // Contacts are then drawn north-up, and the aircraft as a dot with no direction.
            write(c, "N", cx, cy - 66 * s, 11f, Hud.WHITE, align = Paint.Align.CENTER, spacing = 0f)
            fill.color = Hud.WHITE
            c.drawCircle(cx, cy, 5 * s, fill)
        } else {
            stroke(c, cx, cy - 62 * s, cx, cy, 0x8CFFFFFF.toInt(), 1.5f)
            for ((name, bearing) in listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)) {
                val a = Math.toRadians(bearing - hdg)
                write(c, name, cx + (70 * sin(a)).toFloat() * s, cy - (70 * cos(a)).toFloat() * s + 4 * s, 11f,
                    if (name == "N") Hud.WHITE else Hud.GREEN, align = Paint.Align.CENTER, spacing = 0f)
            }
            path.reset()
            path.moveTo(cx, cy - 8 * s)
            path.lineTo(cx + 7 * s, cy + 8 * s)
            path.lineTo(cx, cy + 4 * s)
            path.lineTo(cx - 7 * s, cy + 8 * s)
            path.close()
            fill.color = Hud.WHITE
            c.drawPath(path, fill)
        }
        val maxM = org.r3akt.uas.Prefs.radarRangeM(context).toDouble()
        write(c, org.r3akt.uas.screens.RadarLogic.distance(maxM), cx, cy + 48 * s, 10f, Hud.GREEN, monospace = true, align = Paint.Align.CENTER)
        // Contacts inside the range, north-up without heading; none without aircraft position (UAS-RAD-008).
        val lat = state.latitudeDeg
        val lon = state.longitudeDeg
        if (lat == null || lon == null) {
            write(c, "NO POSITION", cx, cy + 24 * s, 11f, Hud.AMBER, align = Paint.Align.CENTER)
            return
        }
        val polar = contacts.map { k ->
            FloatArray(2).also { android.location.Location.distanceBetween(lat, lon, k.lat, k.lon, it) }
        }
        val sel = org.r3akt.uas.screens.RadarLogic.select(polar.map { it[0].toDouble() }, maxM)
        line.strokeWidth = 1f * s
        for (i in sel.drawn) {
            val (dx, dy) = org.r3akt.uas.screens.RadarLogic.project(polar[i][0].toDouble(), polar[i][1].toDouble(), hdg, maxM, 62.0 * s)
            org.r3akt.uas.screens.ContactSymbol.draw(c, contacts[i].symbol, cx + dx.toFloat(), cy + dy.toFloat(), 3.5f * s, fill, line)
        }
    }

    private fun drawTelemetry(c: Canvas, st: HudState) {
        val l = x(186f, Anchor.LEFT)
        val t = y(476f)
        plate(c, l, t, l + 500 * s, t + 52 * s)
        line.color = Hud.GREEN_LINE; line.strokeWidth = 1f * s
        c.drawRect(l, t, l + 500 * s, t + 52 * s, line)
        val lab = t + 20 * s
        val v = t + 43 * s
        write(c, "DIST HOME", l + 14 * s, lab, 11f, Hud.GREEN)
        val dw = write(c, st.homeDistanceM?.let { "%.0f".format(it) } ?: "—", l + 14 * s, v, 19f, Hud.WHITE, monospace = true)
        if (st.homeDistanceM != null) write(c, " m", l + 14 * s + dw, v, 12f, Hud.WHITE)
        stroke(c, l + 112 * s, t, l + 112 * s, t + 52 * s, Hud.GREEN_LINE, 1f)
        write(c, "POS WGS84", l + 126 * s, lab, 11f, Hud.GREEN)
        val lat = st.latitudeDeg
        val lon = st.longitudeDeg
        val pos = if (lat != null && lon != null) {
            "%.5f %s  %.5f %s".format(abs(lat), if (lat >= 0) "N" else "S", abs(lon), if (lon >= 0) "E" else "W")
        } else "—"
        write(c, pos, l + 126 * s, v, 15f, Hud.WHITE, monospace = true)
        stroke(c, l + 404 * s, t, l + 404 * s, t + 52 * s, Hud.GREEN_LINE, 1f)
        write(c, "FLT TIME", l + 418 * s, lab, 11f, Hud.GREEN)
        val time = st.flightTimeS?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "—"
        write(c, time, l + 418 * s, v, 19f, Hud.WHITE, monospace = true)
    }
}
