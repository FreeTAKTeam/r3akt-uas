package org.r3akt.uas.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.r3akt.uas.Contact
import org.r3akt.uas.Variant
import org.r3akt.uas.hud.Hud
import org.r3akt.uas.hud.HudState
import kotlin.math.hypot

/** This session's flight track, fed by the cockpit's poll so it is complete while the map is closed. */
internal val sessionTrack = Track()

/** A north-up local map of the aircraft, its track and the contacts. No basemap in this pass. */
class MapScreen(activity: Activity, close: () -> Unit) : ScreenPage {
    private val frame = PageFrame(activity, Screen.MAP.title, close)
    private val map = MapView(activity, frame.mono)
    override val view = frame.root

    init {
        val dp = { v: Int -> PageFrame.dp(activity, v) }
        frame.body.addView(map)
        val buttons = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            for ((text, describe, action) in listOf(
                Triple("+", "Zoom in", { map.zoom(0.5) }),
                Triple("−", "Zoom out", { map.zoom(2.0) }),
                Triple("CENTRE", "Centre on the aircraft and follow it", { map.follow() }),
            )) addView(TextView(activity).apply {
                this.text = text
                typeface = frame.label
                textSize = 18f
                setTextColor(Hud.WHITE)
                gravity = Gravity.CENTER
                minWidth = dp(56); minHeight = dp(48)
                setPadding(dp(10), 0, dp(10), 0)
                background = GradientDrawable().apply { setColor(Hud.PLATE); setStroke(dp(1), Hud.GREEN) }
                contentDescription = describe
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }
        frame.body.addView(buttons, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
            .apply { marginEnd = dp(12) })
        frame.body.addView(map.details, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = dp(12) })
    }

    override fun update(state: HudState) = map.update(state)
}

private class MapView(context: Context, private val mono: Typeface) : View(context) {
    private val d = resources.displayMetrics.density
    private var state: HudState? = null
    private var contacts: List<Contact> = emptyList()
    private var contactsFailed = false
    private var centre: LatLon? = null
    private var following = true
    private var metresPerPixel = 1.0
    private var selected: String? = null

    /** Details of the tapped contact. */
    val details = TextView(context).apply {
        typeface = mono
        textSize = 13f
        setTextColor(Hud.WHITE)
        setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        background = GradientDrawable().apply { setColor(0xD9040C08.toInt()); setStroke(d.toInt(), Hud.GREEN) }
        visibility = GONE
        setOnClickListener { selected = null; showDetails(); invalidate() }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; setShadowLayer(2 * d, 0f, 0f, Hud.HALO) }
    private val path = Path()

    fun update(st: HudState) {
        state = st
        val r = runCatching { Variant.source.contacts() }
        contacts = r.getOrDefault(emptyList())
        contactsFailed = r.isFailure
        if (following) aircraft()?.let { centre = it }
        // Never the phone's location: without the aircraft, start on a contact or show nothing.
        if (centre == null) centre = contacts.firstOrNull()?.let { LatLon(it.lat, it.lon) }
        contentDescription = "Map. " + (if (aircraft() == null) "No aircraft position. " else "") +
            "Contacts: " + contacts.joinToString(", ") { it.name }.ifEmpty { "none" }
        showDetails()
        invalidate()
    }

    fun zoom(factor: Double) {
        metresPerPixel = (metresPerPixel * factor).coerceIn(0.05, 2000.0)
        invalidate()
    }

    fun follow() {
        following = true
        aircraft()?.let { centre = it }
        invalidate()
    }

    private fun aircraft(): LatLon? {
        val st = state ?: return null
        return LatLon(st.latitudeDeg ?: return null, st.longitudeDeg ?: return null)
    }

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(g: ScaleGestureDetector): Boolean { zoom(1.0 / g.scaleFactor); return true }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val c = centre ?: return false
            following = false
            centre = LocalProjection(c, metresPerPixel).at(dx.toDouble(), dy.toDouble())
            invalidate()
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean { select(e.x, e.y); return true }
    })

    @SuppressLint("ClickableViewAccessibility") // TalkBack gets the contact list from contentDescription
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        if (!scaler.isInProgress) gestures.onTouchEvent(e)
        return true
    }

    private fun select(x: Float, y: Float) {
        val p = LocalProjection(centre ?: return, metresPerPixel)
        selected = contacts.map { it to hypot(width / 2 + p.x(LatLon(it.lat, it.lon)) - x, height / 2 + p.y(LatLon(it.lat, it.lon)) - y) }
            .filter { it.second < 28 * d }.minByOrNull { it.second }?.first?.id
        showDetails()
        invalidate()
    }

    private fun showDetails() {
        val k = contacts.firstOrNull { it.id == selected }
        if (k == null) { selected = null; details.visibility = GONE; return }
        val a = aircraft()
        val range = if (a == null) "DIST —  BRG —  (no aircraft position)" else {
            val r = FloatArray(2)
            Location.distanceBetween(a.lat, a.lon, k.lat, k.lon, r)
            "DIST %.0f m  BRG %03.0f°  from aircraft".format(r[0], (r[1] + 360) % 360)
        }
        val now = System.currentTimeMillis()
        val age = { t: Long? -> if (t == null) "unknown" else ((now - t) / 1000).let { if (it < 120) "$it s" else if (it < 7200) "${it / 60} min" else "${it / 3600} h" } }
        details.text = "${k.name}${if (k.simulated) "  [SIMULATED]" else ""}\n" +
            "SOURCE ${k.source}\n$range\n" +
            "AGE position ${age(k.positionTimeMs)} · contact ${age(k.contactTimeMs)} · received ${age(k.receivedTimeMs)}"
        details.visibility = VISIBLE
    }

    private fun write(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        text.textSize = size * d; text.color = color; text.textAlign = align
        c.drawText(s, x, y, text)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val st = state
        val ctr = centre
        if (ctr != null) {
            val p = LocalProjection(ctr, metresPerPixel)
            val sx = { q: LatLon -> (w / 2 + p.x(q)).toFloat() }
            val sy = { q: LatLon -> (h / 2 + p.y(q)).toFloat() }
            path.reset()
            var pen = false
            for (q in sessionTrack.points) {
                if (q == null) { pen = false; continue }
                if (pen) path.lineTo(sx(q), sy(q)) else path.moveTo(sx(q), sy(q))
                pen = true
            }
            line.color = Hud.GREEN_DIM; line.strokeWidth = 2 * d
            c.drawPath(path, line)
            line.strokeWidth = 1.5f * d
            for (k in contacts) {
                val q = LatLon(k.lat, k.lon)
                ContactSymbol.draw(c, k.symbol, sx(q), sy(q), 8 * d, fill, line)
                if (k.id == selected) { line.color = Hud.WHITE; c.drawCircle(sx(q), sy(q), 17 * d, line) }
                write(c, k.name + if (k.simulated) " (SIM)" else "", sx(q), sy(q) + 22 * d, 11f, Hud.WHITE, Paint.Align.CENTER)
            }
            aircraft()?.let { a -> drawAircraft(c, sx(a), sy(a), st?.headingDeg) }
        }

        // Overlays: north arrow, status, scale bar, simulated badge.
        val m = 16 * d
        path.reset()
        path.moveTo(m + 10 * d, m); path.lineTo(m + 18 * d, m + 22 * d); path.lineTo(m + 10 * d, m + 17 * d)
        path.lineTo(m + 2 * d, m + 22 * d); path.close()
        fill.color = Hud.WHITE
        c.drawPath(path, fill)
        write(c, "N", m + 10 * d, m + 38 * d, 13f, Hud.WHITE, Paint.Align.CENTER)

        val notes = buildList {
            if (st != null && aircraft() == null) add("NO AIRCRAFT POSITION")
            if (aircraft() != null && st?.headingDeg == null) add("AIRCRAFT HEADING UNKNOWN")
            if (contactsFailed) add("CONTACTS UNAVAILABLE")
        }
        notes.forEachIndexed { i, s -> write(c, s, w / 2, m + 14 * d + i * 18 * d, 14f, Hud.AMBER, Paint.Align.CENTER) }
        if (st?.simulated == true || contacts.any { it.simulated }) {
            write(c, "SIMULATED", w - m, m + 14 * d, 14f, Hud.AMBER, Paint.Align.RIGHT)
        }

        val metres = scaleBarMetres(metresPerPixel, (w / 4).toDouble())
        val px = (metres / metresPerPixel).toFloat()
        val by = h - m
        line.color = Hud.WHITE; line.strokeWidth = 2 * d
        path.reset()
        path.moveTo(m, by - 6 * d); path.lineTo(m, by); path.lineTo(m + px, by); path.lineTo(m + px, by - 6 * d)
        c.drawPath(path, line)
        write(c, if (metres >= 1000) "%.0f km".format(metres / 1000) else "%.0f m".format(metres), m, by - 10 * d, 12f, Hud.WHITE)
        write(c, "NO BASEMAP · NORTH UP", w - m, h - m, 10f, Hud.DIM, Paint.Align.RIGHT)
    }

    private fun drawAircraft(c: Canvas, x: Float, y: Float, heading: Double?) {
        fill.color = Hud.WHITE
        line.color = Hud.HALO; line.strokeWidth = 1.5f * d
        if (heading == null) {
            // Without heading the aircraft has no direction (UAS-RAD-008: phone heading is never used).
            c.drawCircle(x, y, 7 * d, fill)
            c.drawCircle(x, y, 7 * d, line)
            write(c, "NO HDG", x, y - 13 * d, 10f, Hud.AMBER, Paint.Align.CENTER)
            return
        }
        c.save()
        c.rotate(heading.toFloat(), x, y)
        path.reset()
        path.moveTo(x, y - 12 * d); path.lineTo(x + 9 * d, y + 10 * d); path.lineTo(x, y + 5 * d)
        path.lineTo(x - 9 * d, y + 10 * d); path.close()
        c.drawPath(path, fill)
        c.drawPath(path, line)
        c.restore()
    }
}
