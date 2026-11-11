package org.r3akt.uas.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.r3akt.uas.hud.Hud

/**
 * Draws a canonical `r3akt-map-core` symbol id (UAS-RAD-004) for the radar and the map.
 * Shapes follow the catalog's MDI icon names (friendly rectangle, hostile rhombus, neutral
 * square, unknown clover); the affiliation colours match the marker panel.
 */
object ContactSymbol {
    fun color(symbol: String): Int = when (symbol) {
        "friendly" -> 0xFF4FA8FF.toInt()
        "hostile" -> Hud.RED
        "neutral" -> Hud.GREEN
        "unknown" -> Hud.AMBER
        else -> Hud.WHITE
    }

    private val path = Path()

    /** Draws [symbol] centred on ([x], [y]) with half-size [r] pixels. */
    fun draw(c: Canvas, symbol: String, x: Float, y: Float, r: Float, fill: Paint, line: Paint) {
        fill.color = color(symbol)
        line.color = Hud.HALO
        path.reset()
        when (symbol) {
            "friendly" -> path.addRect(x - r * 1.3f, y - r * 0.85f, x + r * 1.3f, y + r * 0.85f, Path.Direction.CW)
            "hostile" -> { path.moveTo(x, y - r * 1.25f); path.lineTo(x + r * 1.25f, y); path.lineTo(x, y + r * 1.25f); path.lineTo(x - r * 1.25f, y); path.close() }
            "neutral" -> path.addRect(x - r, y - r, x + r, y + r, Path.Direction.CW)
            "unknown" -> for ((dx, dy) in listOf(0f to -0.5f, 0.5f to 0f, 0f to 0.5f, -0.5f to 0f)) {
                path.addCircle(x + dx * r, y + dy * r, r * 0.62f, Path.Direction.CW)
            }
            else -> path.addCircle(x, y, r, Path.Direction.CW)
        }
        c.drawPath(path, fill)
        c.drawPath(path, line)
    }
}
