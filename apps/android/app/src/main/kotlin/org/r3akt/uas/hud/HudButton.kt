package org.r3akt.uas.hud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.widget.Button

/** An icon drawn into a 24 x 24 unit path, like an SVG viewBox. */
typealias Icon = Path.() -> Unit

/** A HUD control: frame, stroke icon and caption, scaled by the cockpit's design unit. */
class HudButton(
    context: Context,
    private val unit: () -> Float,
    private val font: Typeface,
    icon: Icon?,
    label: String = "",
    private val bare: Boolean = false,
    private val shutter: Boolean = false,
) : View(context) {
    enum class Tone { NORMAL, GO, CAUTION }

    var icon = icon
        set(value) { field = value; invalidate() }
    var label = label
        set(value) { field = value; invalidate() }
    var tone = Tone.NORMAL
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val matrix = Matrix()
    private val box = RectF()

    init {
        isClickable = true
        isFocusable = true
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val k = unit()
        val dim = !isEnabled
        val frameColor = when {
            dim -> Hud.DIM
            tone == Tone.CAUTION -> Hud.AMBER
            shutter -> Hud.WHITE
            else -> Hud.GREEN
        }
        val ink = when {
            dim -> Hud.DIM
            tone == Tone.GO -> Hud.INK_GO
            tone == Tone.CAUTION && !bare -> Hud.INK_CAUTION
            tone == Tone.CAUTION -> Hud.AMBER
            else -> Hud.WHITE
        }
        val w = width.toFloat()
        val h = height.toFloat()

        if (shutter) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Hud.PLATE
            canvas.drawCircle(w / 2, h / 2, w / 2 - 1.5f * k, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3 * k
            paint.color = frameColor
            canvas.drawCircle(w / 2, h / 2, w / 2 - 1.5f * k, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (dim) Hud.DIM else Hud.WHITE
            canvas.drawCircle(w / 2, h / 2, w / 2 - 9 * k, paint)
            return
        }

        if (!bare) {
            box.set(0.75f * k, 0.75f * k, w - 0.75f * k, h - 0.75f * k)
            paint.reset()
            paint.isAntiAlias = true
            paint.color = when (tone) {
                Tone.GO -> if (dim) Hud.PLATE else Hud.GREEN
                Tone.CAUTION -> if (dim) Hud.PLATE else Hud.AMBER
                Tone.NORMAL -> Hud.PLATE
            }
            canvas.drawRoundRect(box, 3 * k, 3 * k, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * k
            paint.color = frameColor
            if (dim) paint.pathEffect = DashPathEffect(floatArrayOf(4 * k, 3 * k), 0f)
            canvas.drawRoundRect(box, 3 * k, 3 * k, paint)
            paint.pathEffect = null
        }

        val iconSize = if (icon == null) 0f else (if (label.isEmpty()) 22f else 24f) * k
        val gap = if (icon == null || label.isEmpty()) 0f else 3 * k
        val textSize = 10 * k
        var top = (h - iconSize - gap - if (label.isEmpty()) 0f else textSize) / 2
        val halo = tone == Tone.NORMAL || bare || dim
        icon?.let { draw ->
            path.reset()
            path.draw()
            matrix.setScale(iconSize / 24, iconSize / 24)
            matrix.postTranslate((w - iconSize) / 2, top)
            path.transform(matrix)
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.SQUARE
            paint.strokeWidth = 2.1f * k
            paint.color = ink
            if (halo) paint.setShadowLayer(1.5f * k, 0f, 0f, Hud.HALO)
            canvas.drawPath(path, paint)
            top += iconSize + gap
        }
        if (label.isNotEmpty()) {
            paint.reset()
            paint.isAntiAlias = true
            paint.typeface = font
            paint.textSize = textSize
            paint.letterSpacing = 0.1f
            paint.textAlign = Paint.Align.CENTER
            paint.color = ink
            if (halo) paint.setShadowLayer(1.5f * k, 0f, 0f, Hud.HALO)
            canvas.drawText(label, w / 2, top + textSize * 0.8f, paint)
        }
    }
}

/** Slide-to-confirm for flight actions: a tap does nothing; dragging the knob to the far end confirms. */
class HudSlider(context: Context, private val unit: () -> Float, private val font: Typeface) : View(context) {
    var label = ""
        set(value) { field = value; contentDescription = value; invalidate() }
    var onConfirm: () -> Unit = {}

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private var slide = 0f
    private var dragging = false

    init {
        isClickable = true
        isFocusable = true
    }

    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    // TalkBack and keyboard activation; touch never gets here because onTouchEvent does not call super.
    override fun performClick(): Boolean {
        super.performClick()
        onConfirm()
        return true
    }

    @SuppressLint("ClickableViewAccessibility") // a touch must slide, never click
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> dragging = e.x <= height
            MotionEvent.ACTION_MOVE -> if (dragging) slide = ((e.x - height / 2f) / (width - height)).coerceIn(0f, 1f)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val done = dragging && e.actionMasked == MotionEvent.ACTION_UP && slide >= 0.95f
                dragging = false
                slide = 0f
                if (done) onConfirm()
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val k = unit()
        val w = width.toFloat()
        val h = height.toFloat()
        box.set(0.75f * k, 0.75f * k, w - 0.75f * k, h - 0.75f * k)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = 0x0FFFFFFF
        canvas.drawRoundRect(box, 3 * k, 3 * k, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * k
        paint.color = Hud.WHITE
        canvas.drawRoundRect(box, 3 * k, 3 * k, paint)

        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = font
        paint.textSize = 14 * k
        paint.letterSpacing = 0.16f
        paint.textAlign = Paint.Align.CENTER
        paint.color = Hud.WHITE
        paint.setShadowLayer(1.5f * k, 0f, 0f, Hud.HALO)
        canvas.drawText(label, w / 2 + 20 * k, h / 2 + 5 * k, paint)

        val pad = 4.5f * k
        val side = h - 2 * pad
        val left = pad + slide * (w - h)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Hud.GREEN
        canvas.drawRoundRect(left, pad, left + side, h - pad, 2 * k, 2 * k, paint)
        val u = 22 * k / 24
        val ox = left + (side - 24 * u) / 2
        val oy = (h - 24 * u) / 2
        path.reset()
        for (cx in listOf(5f, 12f)) {
            path.moveTo(ox + cx * u, oy + 6 * u)
            path.lineTo(ox + (cx + 6) * u, oy + 12 * u)
            path.lineTo(ox + cx * u, oy + 18 * u)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.SQUARE
        paint.strokeWidth = 2.6f * k
        paint.color = Hud.INK_GO
        canvas.drawPath(path, paint)
    }
}

/** Colours from the approved HUD style kit, and the icon set. */
object Hud {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val GREEN = 0xFF3DFF7A.toInt()
    const val GREEN_DIM = 0x8C3DFF7A.toInt()
    const val GREEN_LINE = 0x593DFF7A
    const val AMBER = 0xFFFFB020.toInt()
    const val RED = 0xFFFF4A4A.toInt()
    const val DIM = 0x73FFFFFF
    /** #040C08 at 35%: the approved backing-plate opacity. */
    const val PLATE = 0x59040C08
    const val HALO = 0xF2000000.toInt()
    const val INK_GO = 0xFF04140A.toInt()
    const val INK_CAUTION = 0xFF1A1200.toInt()

    val back: Icon = { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    val more: Icon = { addCircle(5f, 12f, 1.2f, Path.Direction.CW); addCircle(12f, 12f, 1.2f, Path.Direction.CW); addCircle(19f, 12f, 1.2f, Path.Direction.CW) }
    val close: Icon = { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val aircraft: Icon = {
        moveTo(9f, 9f); lineTo(7f, 7f); moveTo(15f, 9f); lineTo(17f, 7f)
        moveTo(9f, 15f); lineTo(7f, 17f); moveTo(15f, 15f); lineTo(17f, 17f)
        addRect(9f, 9f, 15f, 15f, Path.Direction.CW)
        for ((cx, cy) in listOf(5f to 5f, 19f to 5f, 5f to 19f, 19f to 19f)) addCircle(cx, cy, 2.6f, Path.Direction.CW)
    }
    val takeoff: Icon = { moveTo(12f, 17f); lineTo(12f, 5f); moveTo(7f, 10f); lineTo(12f, 5f); lineTo(17f, 10f); moveTo(5f, 21f); lineTo(19f, 21f) }
    val land: Icon = { moveTo(12f, 3f); lineTo(12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f); moveTo(5f, 21f); lineTo(19f, 21f) }
    val home: Icon = {
        moveTo(3f, 11f); lineTo(12f, 4f); lineTo(21f, 11f)
        moveTo(5f, 9.5f); lineTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 9.5f)
        moveTo(10f, 12f); lineTo(10f, 18f); moveTo(14f, 12f); lineTo(14f, 18f); moveTo(10f, 15f); lineTo(14f, 15f)
    }
    val photo: Icon = { addRect(3f, 7f, 21f, 20f, Path.Direction.CW); moveTo(8f, 7f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(16f, 7f); addCircle(12f, 13.5f, 3.5f, Path.Direction.CW) }
    val menu: Icon = { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f) }
    val map: Icon = {
        moveTo(3f, 6f); lineTo(9f, 4f); lineTo(15f, 6f); lineTo(21f, 4f); lineTo(21f, 18f); lineTo(15f, 20f); lineTo(9f, 18f); lineTo(3f, 20f); close()
        moveTo(9f, 4f); lineTo(9f, 18f); moveTo(15f, 6f); lineTo(15f, 20f)
    }
    val observation: Icon = {
        addCircle(12f, 12f, 7f, Path.Direction.CW)
        moveTo(12f, 2f); lineTo(12f, 7f); moveTo(12f, 17f); lineTo(12f, 22f); moveTo(2f, 12f); lineTo(7f, 12f); moveTo(17f, 12f); lineTo(22f, 12f)
    }
    val log: Icon = { for (y in listOf(6f, 12f, 18f)) { moveTo(9f, y); lineTo(21f, y); moveTo(4f, y); lineTo(5f, y) } }
}
