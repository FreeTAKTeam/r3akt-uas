package org.r3akt.uas.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.r3akt.uas.Contact
import org.r3akt.uas.Prefs
import org.r3akt.uas.Variant
import org.r3akt.uas.hud.Hud
import org.r3akt.uas.hud.HudState
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The radar page (UAS-RAD-001 to UAS-RAD-009), centred on the aircraft: heading-up when the
 * aircraft reports a heading, north-up and labelled so when it does not; never the phone's
 * position or heading. Range and true bearing come from Android's `Location.distanceBetween`
 * (gap G-13); the arithmetic is in [RadarLogic]. In publish builds a long-press on an empty
 * spot places an operator marker there ([org.r3akt.uas.AircraftSource.radarPick]).
 */
class RadarScreen(private val activity: Activity, close: () -> Unit) : ScreenPage {
    private class Placed(val contact: Contact, val rangeM: Double, val bearingDeg: Double)

    private val frame = PageFrame(activity, Screen.RADAR.title, close)
    override val view: View = frame.root
    private val k = activity.resources.displayMetrics.density

    private var state = HudState(status = "", simulated = false)
    private var rangeM = Prefs.radarRangeM(activity)
    private var contacts: List<Contact> = emptyList()
    /** Each contact with its range and true bearing; empty without aircraft position. */
    private var placed: List<Placed> = emptyList()
    private var selection = RadarLogic.select(emptyList(), 1.0)
    /** Kept by id across updates (UAS-RAD-005). */
    private var selectedId: String? = null
    private var now = System.currentTimeMillis()

    private val radar = RadarView()
    private val status = text(12f)
    private val details = text(13f)
    private val detailsPanel = LinearLayout(activity)
    private val rangeButtons = Prefs.RADAR_RANGES_M.associateWith { m -> text(14f).apply { this.text = RadarLogic.distance(m.toDouble()) } }

    init {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12f), px(8f), px(8f), px(8f))
            addView(text(12f).apply { text = "RANGE"; typeface = frame.label; setTextColor(Hud.GREEN) })
            for ((m, b) in rangeButtons) {
                b.gravity = Gravity.CENTER
                b.contentDescription = "Range ${b.text}"
                b.setOnClickListener { setRange(m) }
                addView(b, LinearLayout.LayoutParams(MATCH_PARENT, px(36f)).apply { topMargin = px(6f) })
            }
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(14f) })
        }
        detailsPanel.apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = box(Hud.GREEN, PageFrame.BACKGROUND)
            setPadding(px(12f), px(4f), px(12f), px(12f))
            addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(14f).apply { text = "CONTACT"; typeface = frame.label; setTextColor(Hud.GREEN) },
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(text(16f).apply {
                    text = "✕"
                    setPadding(px(12f), px(8f), px(4f), px(8f))
                    contentDescription = "Close contact details"
                    setOnClickListener { select(null) }
                })
            })
            addView(ScrollView(activity).apply { addView(details) })
        }
        frame.body.addView(LinearLayout(activity).apply {
            addView(ScrollView(activity).apply { addView(column) }, LinearLayout.LayoutParams(px(160f), MATCH_PARENT))
            addView(radar, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            addView(detailsPanel, LinearLayout.LayoutParams(px(270f), MATCH_PARENT))
        }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        styleRange()
    }

    override fun update(state: HudState) {
        this.state = state
        now = System.currentTimeMillis()
        contacts = runCatching { Variant.source.contacts() }.getOrDefault(emptyList())
        val lat = state.latitudeDeg
        val lon = state.longitudeDeg
        // UAS-RAD-008: without the aircraft's own position nothing is placed.
        placed = if (lat == null || lon == null) emptyList() else contacts.map { c ->
            val r = FloatArray(2)
            Location.distanceBetween(lat, lon, c.lat, c.lon, r)
            Placed(c, r[0].toDouble(), RadarLogic.normalize(r[1].toDouble()))
        }
        refresh()
    }

    private fun refresh() {
        selection = RadarLogic.select(placed.map { it.rangeM }, rangeM.toDouble())
        showStatus()
        showDetails()
        radar.invalidate()
    }

    private fun setRange(m: Int) {
        rangeM = m
        Prefs.setRadarRangeM(activity, m)
        styleRange()
        refresh()
    }

    private fun styleRange() = rangeButtons.forEach { (m, b) ->
        val on = m == rangeM
        b.background = box(if (on) Hud.GREEN else Hud.GREEN_LINE, if (on) Hud.GREEN else 0)
        b.setTextColor(if (on) Hud.INK_GO else Hud.WHITE)
        b.isSelected = on
    }

    private fun select(id: String?) {
        selectedId = id
        showDetails()
        radar.invalidate()
    }

    private fun showStatus() {
        val lines = mutableListOf<Pair<String, Int>>()
        // UAS-RAD-009: simulated aircraft and contacts are always identified.
        if (state.simulated) lines += "SIMULATED AIRCRAFT" to Hud.AMBER
        contacts.count { it.simulated }.takeIf { it > 0 }?.let { lines += "$it SIMULATED CONTACTS" to Hud.AMBER }
        if (state.latitudeDeg == null || state.longitudeDeg == null) lines += "NO AIRCRAFT POSITION" to Hud.AMBER
        lines += state.headingDeg?.let { "HEADING-UP · HDG ${RadarLogic.bearing(it)}" to Hud.GREEN }
            ?: ("NORTH-UP · NO HEADING" to Hud.AMBER)
        if (placed.isNotEmpty()) {
            if (selection.limited) lines += "LIMITED · ${RadarLogic.LIMIT} OF ${placed.size}" to Hud.AMBER
            lines += "${selection.drawn.size} SHOWN" to Hud.WHITE
            if (selection.beyond > 0) lines += "${selection.beyond} BEYOND ${RadarLogic.distance(rangeM.toDouble())}" to Hud.DIM
            val aged = selection.drawn.count { aged(placed[it].contact) }
            if (aged > 0) lines += "$aged AGED (DIMMED)" to Hud.DIM
        } else if (contacts.isEmpty()) lines += "NO CONTACTS" to Hud.DIM
        if (Variant.source.radarPick() != null) lines += "LONG-PRESS AN EMPTY SPOT TO PLACE A MARKER" to Hud.DIM
        status.text = spans(lines)
        radar.contentDescription = "Radar, ${lines.joinToString(", ") { it.first.lowercase() }}"
    }

    // UAS-RAD-006: distance, true bearing, source and the three ages; unknown is never 0.
    private fun showDetails() {
        val id = selectedId
        detailsPanel.visibility = if (id == null) View.GONE else View.VISIBLE
        if (id == null) return
        val c = contacts.firstOrNull { it.id == id }
        if (c == null) {
            details.text = spans(listOf("NO LONGER REPORTED" to Hud.AMBER, "id $id" to Hud.DIM))
            return
        }
        val p = placed.firstOrNull { it.contact.id == id }
        val lines = mutableListOf(c.name to Hud.WHITE)
        if (c.simulated) lines += "SIMULATED" to Hud.AMBER
        if (aged(c)) lines += "AGED · POSITION NOT CURRENT" to Hud.AMBER
        lines += "symbol    ${c.symbol}" to ContactSymbol.color(c.symbol)
        if (p == null) {
            lines += "distance  UNKNOWN" to Hud.AMBER
            lines += "bearing   UNKNOWN (NO AIRCRAFT POSITION)" to Hud.AMBER
        } else {
            lines += "distance  ${"%.0f".format(Locale.ROOT, p.rangeM)} m" + (if (p.rangeM > rangeM) " (OUTSIDE RINGS)" else "") to Hud.WHITE
            lines += "bearing   ${RadarLogic.bearing(p.bearingDeg)} TRUE" to Hud.WHITE
        }
        lines += "source    ${c.source}" to Hud.WHITE
        lines += "position age   ${RadarLogic.age(c.positionTimeMs, now)}" to Hud.WHITE
        lines += "contact age    ${RadarLogic.age(c.contactTimeMs, now)}" to Hud.WHITE
        lines += "reception age  ${RadarLogic.age(c.receivedTimeMs, now)}" to Hud.WHITE
        details.text = spans(lines)
    }

    private fun aged(c: Contact) = RadarLogic.aged(c.positionTimeMs, now, own = c.source == "this app")

    private fun tap(found: List<Placed>) {
        when (found.size) {
            0 -> {}
            1 -> select(found[0].contact.id)
            // Overlapping contacts: the operator picks one (UAS-RAD-005).
            else -> AlertDialog.Builder(activity)
                .setTitle("${found.size} CONTACTS HERE")
                .setItems(found.map { p ->
                    "${p.contact.name} · ${p.contact.symbol} · ${"%.0f".format(Locale.ROOT, p.rangeM)} m" +
                        (if (p.contact.simulated) " · SIMULATED" else "") + (if (aged(p.contact)) " · AGED" else "")
                }.toTypedArray()) { _, i -> select(found[i].contact.id) }
                .setNegativeButton("CANCEL", null)
                .show()
        }
    }

    private fun pick(rangeM: Double, bearingDeg: Double) {
        val action = Variant.source.radarPick() ?: return
        if (state.latitudeDeg == null || state.longitudeDeg == null) {
            Toast.makeText(activity, "NO MARKER · NO AIRCRAFT POSITION", Toast.LENGTH_LONG).show()
            return
        }
        action(activity, rangeM, bearingDeg)
    }

    private inner class RadarView : View(activity) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = frame.mono }
        private val dash = DashPathEffect(floatArrayOf(3 * k, 3 * k), 0f)
        private val path = Path()
        private var cx = 0f
        private var cy = 0f
        private var radius = 1f
        /** What was drawn last, with screen offsets, for taps. */
        private var shown: List<Pair<Placed, Pair<Double, Double>>> = emptyList()

        private val gestures = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { tap(under(e)); return true }
            override fun onLongPress(e: MotionEvent) {
                val found = under(e)
                if (found.isNotEmpty()) return tap(found)
                val (r, b) = RadarLogic.unproject((e.x - cx).toDouble(), (e.y - cy).toDouble(), state.headingDeg, rangeM.toDouble(), radius.toDouble())
                if (r > 0 && r <= rangeM) pick(r, b)
            }
        })

        private fun under(e: MotionEvent): List<Placed> =
            RadarLogic.hits(shown.map { it.second }, (e.x - cx).toDouble(), (e.y - cy).toDouble(), 22.0 * k).map { shown[it].first }

        @SuppressLint("ClickableViewAccessibility") // taps on contacts; the page's buttons stay accessible
        override fun onTouchEvent(e: MotionEvent) = gestures.onTouchEvent(e)

        private fun write(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
            ink.textSize = size * k
            ink.color = color
            ink.textAlign = align
            c.drawText(s, x, y, ink)
        }

        override fun onDraw(c: Canvas) {
            cx = width / 2f
            cy = height / 2f
            radius = min(width, height) / 2f - 26 * k
            if (radius <= 0) return
            val hdg = state.headingDeg
            val north = hdg ?: 0.0

            // Linear distance rings with labels (UAS-RAD-002).
            line.pathEffect = null
            line.strokeWidth = 1 * k
            for (i in 1..4) {
                val r = radius * i / 4
                line.color = if (i == 4) Hud.GREEN else Hud.GREEN_LINE
                c.drawCircle(cx, cy, r, line)
                write(c, RadarLogic.distance(rangeM * i / 4.0), cx + r * 0.707f + 2 * k, cy + r * 0.707f + 12 * k, 10f, Hud.GREEN_DIM, Paint.Align.LEFT)
            }
            // Compass rose, turned by the aircraft heading; north-up without one (UAS-RAD-001, UAS-RAD-008).
            for (deg in 0 until 360 step 10) {
                val a = Math.toRadians(deg - north)
                val inner = radius - if (deg % 30 == 0) 8 * k else 4 * k
                line.color = Hud.GREEN_DIM
                c.drawLine(cx + inner * sin(a).toFloat(), cy - inner * cos(a).toFloat(),
                    cx + radius * sin(a).toFloat(), cy - radius * cos(a).toFloat(), line)
            }
            for ((name, deg) in listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)) {
                val a = Math.toRadians(deg - north)
                val r = radius + 14 * k
                write(c, name, cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat() + 5 * k, 14f, if (name == "N") Hud.WHITE else Hud.GREEN)
            }

            // The aircraft: a fixed pointer with heading, a dot with no direction without one.
            fill.color = Hud.WHITE
            if (hdg != null) {
                line.color = 0x59FFFFFF
                c.drawLine(cx, cy, cx, cy - radius, line)
                path.reset()
                path.moveTo(cx, cy - 11 * k); path.lineTo(cx + 9 * k, cy + 10 * k); path.lineTo(cx, cy + 5 * k)
                path.lineTo(cx - 9 * k, cy + 10 * k); path.close()
                c.drawPath(path, fill)
            } else {
                c.drawCircle(cx, cy, 6 * k, fill)
                write(c, "NORTH-UP · NO HEADING", cx, cy + 24 * k, 12f, Hud.AMBER)
            }
            if (state.latitudeDeg == null || state.longitudeDeg == null) {
                write(c, "NO AIRCRAFT POSITION", cx, cy - 18 * k, 14f, Hud.AMBER)
                shown = emptyList()
                return
            }

            // Contacts, nearest drawn last so they stay on top.
            shown = selection.drawn.map { i ->
                val p = placed[i]
                p to RadarLogic.project(p.rangeM, p.bearingDeg, hdg, rangeM.toDouble(), radius.toDouble())
            }
            val r = 6 * k
            for ((p, xy) in shown.asReversed()) {
                val x = cx + xy.first.toFloat()
                val y = cy + xy.second.toFloat()
                line.strokeWidth = 1 * k
                val old = aged(p.contact)
                // UAS-RAD-007: aged contacts stay visible, dimmed and ringed with a dashed line.
                if (old) c.saveLayerAlpha(x - 3 * r, y - 3 * r, x + 3 * r, y + 3 * r, 90)
                ContactSymbol.draw(c, p.contact.symbol, x, y, r, fill, line)
                if (old) {
                    c.restore()
                    line.color = Hud.AMBER
                    line.pathEffect = dash
                    c.drawCircle(x, y, r * 1.9f, line)
                    line.pathEffect = null
                }
                if (p.contact.id == selectedId) {
                    line.color = Hud.WHITE
                    line.strokeWidth = 2 * k
                    c.drawCircle(x, y, r * 2.3f, line)
                    write(c, p.contact.name, x + r * 2.6f, y + 4 * k, 12f, Hud.WHITE, Paint.Align.LEFT)
                }
            }
        }
    }

    private fun spans(lines: List<Pair<String, Int>>) = SpannableStringBuilder().apply {
        for ((i, l) in lines.withIndex()) {
            if (i > 0) append("\n")
            val start = length
            append(l.first)
            setSpan(ForegroundColorSpan(l.second), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun text(size: Float) = TextView(activity).apply {
        typeface = frame.mono
        textSize = size
        setTextColor(Hud.WHITE)
    }

    private fun box(stroke: Int, fill: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(maxOf(1, (1.5f * k).toInt()), stroke)
        cornerRadius = 3 * k
    }

    private fun px(v: Float) = (v * k).toInt()
}
