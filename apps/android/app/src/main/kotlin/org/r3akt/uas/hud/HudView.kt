package org.r3akt.uas.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import org.r3akt.uas.R
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
        set(value) { field = value; applyState(); invalidate() }

    var onBack: () -> Unit = {}
    var onLog: () -> Unit = {}

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

    private fun button(icon: Icon?, text: String = "", bare: Boolean = false, shutter: Boolean = false) =
        HudButton(context, { s }, label, icon, text, bare, shutter)

    private val back = button(Hud.back, bare = true).apply { contentDescription = "Back" }
    private val settings = button(Hud.more, bare = true).apply { contentDescription = "Settings, not built yet"; isEnabled = false }
    private val flight = button(Hud.aircraft, "FLIGHT")
    private val takeoffLand = button(Hud.takeoff, "TAKEOFF")
    private val rth = button(Hud.home, "RTH")
    private val cameraMode = button(Hud.photo).apply { contentDescription = "Camera mode" }
    private val shutterButton = button(null, shutter = true).apply { contentDescription = "Take photo" }
    private val menu = button(Hud.menu, "MENU")
    private val map = button(Hud.map, "MAP").apply { contentDescription = "Map, not built yet"; isEnabled = false }
    private val observations = button(Hud.observation, "OBS").apply { contentDescription = "Observations, not built yet"; isEnabled = false }
    private val log = button(Hud.log, "LOG").apply { contentDescription = "Session log" }

    init {
        setWillNotDraw(false)
        add(back, 0f, 0f, 44f, 40f, Anchor.LEFT)
        add(settings, 916f, 0f, 44f, 40f, Anchor.RIGHT)
        add(flight, 12f, 132f, 60f, 60f, Anchor.LEFT)
        add(takeoffLand, 12f, 200f, 60f, 60f, Anchor.LEFT)
        add(rth, 12f, 266f, 60f, 60f, Anchor.LEFT)
        add(cameraMode, 880f, 180f, 68f, 48f, Anchor.RIGHT)
        add(shutterButton, 880f, 238f, 68f, 68f, Anchor.RIGHT)
        add(menu, 888f, 476f, 60f, 52f, Anchor.RIGHT)
        add(map, 692f, 476f, 60f, 52f, Anchor.RIGHT)
        add(observations, 756f, 476f, 60f, 52f, Anchor.RIGHT)
        add(log, 820f, 476f, 60f, 52f, Anchor.RIGHT)

        back.setOnClickListener { onBack() }
        log.setOnClickListener { onLog() }
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
        rth.isEnabled = FlightAction.RTH in st.actions
        rth.tone = if (st.rthActive) HudButton.Tone.CAUTION else HudButton.Tone.NORMAL
        rth.label = if (st.rthActive) "STOP RTH" else "RTH"
        rth.contentDescription = (if (st.rthActive) "Stop return to home" else "Return to home") + if (rth.isEnabled) "" else ", unavailable"
        cameraMode.isEnabled = FlightAction.PHOTO in st.actions
        shutterButton.isEnabled = FlightAction.PHOTO in st.actions

        flight.icon = if (flightOpen) Hud.close else Hud.aircraft
        flight.label = if (st.rthActive) "RTH ON" else "FLIGHT"
        flight.tone = if (st.rthActive) HudButton.Tone.CAUTION else HudButton.Tone.NORMAL
        flight.contentDescription = if (flightOpen) "Close flight actions" else "Flight actions"
        takeoffLand.visibility = if (flightOpen) VISIBLE else GONE
        rth.visibility = if (flightOpen) VISIBLE else GONE

        menu.icon = if (menuOpen) Hud.close else Hud.menu
        menu.contentDescription = if (menuOpen) "Close menu" else "Menu"
        for (v in listOf(map, observations, log)) v.visibility = if (menuOpen) VISIBLE else GONE
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
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
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
        canvas.drawColor(0xFF0A0F0B.toInt())
        // Live DJI video arrives with the milestone 4 connection flow.
        write(canvas, "NO VIDEO", x(480f, Anchor.CENTER), y(186f), 13f, Hud.DIM, align = Paint.Align.CENTER, spacing = 0.2f)

        drawTopBar(canvas, st)
        drawHeading(canvas, st.headingDeg)
        drawSpeed(canvas, st)
        drawAltitude(canvas, st)
        drawGimbal(canvas, st.gimbalPitchDeg)
        drawReticle(canvas)
        drawRadar(canvas, st.headingDeg)
        drawTelemetry(canvas, st)
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
        cell("STRM", "OFF", Hud.DIM)
        cell("MESH", "OFF", Hud.DIM)
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
        } else {
            stroke(c, cx, cy - 62 * s, cx, cy, 0x8CFFFFFF.toInt(), 1.5f)
            for ((name, bearing) in listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)) {
                val a = Math.toRadians(bearing - hdg)
                write(c, name, cx + (70 * sin(a)).toFloat() * s, cy - (70 * cos(a)).toFloat() * s + 4 * s, 11f,
                    if (name == "N") Hud.WHITE else Hud.GREEN, align = Paint.Align.CENTER, spacing = 0f)
            }
        }
        path.reset()
        path.moveTo(cx, cy - 8 * s)
        path.lineTo(cx + 7 * s, cy + 8 * s)
        path.lineTo(cx, cy + 4 * s)
        path.lineTo(cx - 7 * s, cy + 8 * s)
        path.close()
        fill.color = Hud.WHITE
        c.drawPath(path, fill)
        write(c, "500 m", cx, cy + 48 * s, 10f, Hud.GREEN, monospace = true, align = Paint.Align.CENTER)
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
