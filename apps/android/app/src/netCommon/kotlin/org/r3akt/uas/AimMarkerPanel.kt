package org.r3akt.uas

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.r3akt.uas.bridge.Affiliation
import org.r3akt.uas.bridge.CameraPose
import org.r3akt.uas.bridge.markerCreate
import org.r3akt.uas.bridge.markerCreateAtCamera
import org.r3akt.uas.bridge.markerList
import org.r3akt.uas.hud.Hud
import org.r3akt.uas.hud.HudButton
import org.r3akt.uas.hud.HudView
import java.util.concurrent.Executor

/**
 * Names and places an operator marker after a tap on the aim cross (owner direction, 11 November 2026):
 * a panel on the HUD's right with folded NAME and AFFILIATION rows that open on a tap, and PLACE MARKER.
 * With a camera pose the marker goes where the camera centre meets the ground, read when PLACE is
 * pressed; without one it goes at the aircraft position (D-15 (a)) and the panel says so.
 * Names follow the FreeTAKUAS Alpha–Zulu counter (D-11). Bridge calls run on [worker].
 */
class AimMarkerPanel(
    private val activity: Activity,
    private val hud: HudView,
    private val worker: Executor,
    private val cameraPose: (() -> CameraPose?)?,
) {
    private val s = hud.unit
    private val label = activity.resources.getFont(R.font.rajdhani_bold)
    private val mono = activity.resources.getFont(R.font.b612mono_bold)
    private val root = LinearLayout(activity)
    private val nameValue = text("", 15f, Hud.WHITE, mono)
    private val affValue = text("", 15f, Hud.WHITE, mono)
    private val affDot = View(activity)
    private val nameField = EditText(activity)
    private val nameFold = LinearLayout(activity)
    private val affFold = LinearLayout(activity)
    private val chips = LinearLayout(activity)
    private val segments = mutableMapOf<Affiliation, TextView>()
    private val problem = text("", 12f, Hud.AMBER, label)
    private val place = HudButton(activity, { s }, label, null, "PLACE MARKER").apply { tone = HudButton.Tone.GO }

    fun show() {
        // One panel at a time; a panel left on a destroyed cockpit does not count.
        if (current?.activity?.isDestroyed == false) return
        current = this
        val pad = (12 * s).toInt()
        root.apply {
            orientation = LinearLayout.VERTICAL
            background = frame(Hud.GREEN, 0xFF0A0F0B.toInt())
            setPadding(pad, pad / 2, pad, pad)
            isClickable = true // the panel takes its own taps; none fall through to the HUD
        }

        val close = HudButton(activity, { s }, label, Hud.close, bare = true).apply {
            contentDescription = "Cancel the marker"
            setOnClickListener { close() }
        }
        root.addView(LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("NEW MARKER", 14f, Hud.GREEN, label), LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(close, LinearLayout.LayoutParams(px(40f), px(40f)))
        })
        val where = if (cameraPose != null) "AT THE AIM CROSS, READ WHEN PLACED"
        else "AT THE AIRCRAFT POSITION · NO CAMERA POINT FROM THIS SOURCE"
        root.addView(text(where, 11f, Hud.DIM, mono).apply { setPadding(0, 0, 0, pad / 2) })

        root.addView(row("NAME", nameValue, null) { toggle(nameFold) }, rowParams())
        nameField.apply {
            isSingleLine = true
            typeface = mono
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 15 * s)
            setTextColor(Hud.GREEN)
            background = frame(Hud.GREEN, 0xFF0A0F0B.toInt())
            setPadding(pad / 2, 0, pad / 2, 0)
            contentDescription = "Marker name"
            // Landscape: keep the cockpit visible instead of Android's full-screen text editor.
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            setOnEditorActionListener { _, _, _ -> setName(nameField.text.toString().trim()); fold(); true }
        }
        nameFold.apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(nameField, LinearLayout.LayoutParams(MATCH, px(34f)).apply { topMargin = pad / 2 })
            addView(chips, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = pad / 2 })
        }
        root.addView(nameFold)

        root.addView(row("AFFILIATION", affValue, affDot) { toggle(affFold) }, rowParams())
        affFold.apply {
            visibility = View.GONE
            for (a in Affiliation.entries) {
                val seg = text(a.name, 12f, Hud.DIM, label).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { setAffiliation(a); fold() }
                }
                segments[a] = seg
                addView(seg, LinearLayout.LayoutParams(0, px(34f), 1f).apply { marginEnd = pad / 3; topMargin = pad / 2 })
            }
        }
        root.addView(affFold)

        root.addView(problem, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = pad / 2 })
        root.addView(View(activity), LinearLayout.LayoutParams(MATCH, 0, 1f))
        place.contentDescription = "Place marker"
        place.setOnClickListener { placeMarker() }
        root.addView(place, LinearLayout.LayoutParams(MATCH, px(48f)))

        setAffiliation(lastAffiliation)
        setName("")
        worker.execute {
            val next = nextName(lastBase)
            activity.runOnUiThread { if (nameField.text.isEmpty()) setName(next) }
        }

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        content.addView(root, FrameLayout.LayoutParams(px(340f), MATCH, Gravity.END).apply { topMargin = hud.screenY(44f).toInt() })
    }

    private fun placeMarker() {
        val name = nameField.text.toString().trim()
        if (name.isEmpty()) {
            problem.text = "ENTER A NAME"
            open(nameFold)
            return
        }
        setName(name)
        val affiliation = lastAffiliation
        problem.text = ""
        place.isEnabled = false
        worker.execute {
            val result = runCatching {
                val pose = cameraPose
                if (pose == null) markerCreate(name, affiliation).name
                else markerCreateAtCamera(name, affiliation, pose() ?: error("aircraft position unknown")).name
            }
            activity.runOnUiThread {
                place.isEnabled = true
                result.onSuccess {
                    close()
                    // Stored is not sent: the outbox may still hold it (UAS-PRD-010).
                    Toast.makeText(activity, "$it placed (stored for the hub)", Toast.LENGTH_LONG).show()
                }.onFailure {
                    problem.text = "NOT PLACED: ${it.message?.removePrefix("reason=") ?: it}"
                }
            }
        }
    }

    private fun close() {
        keyboard(false)
        (root.parent as? ViewGroup)?.removeView(root)
        hud.aimMarker = null
        current = null
    }

    private fun setName(name: String) {
        if (nameField.text.toString() != name) nameField.setText(name)
        nameValue.text = name.ifEmpty { "—" }
        names.firstOrNull { name.startsWith("$it-") }?.let { lastBase = it }
        hud.aimMarker = name.ifEmpty { "—" } to color(lastAffiliation)
        showChips()
    }

    private fun setAffiliation(a: Affiliation) {
        lastAffiliation = a
        affValue.text = a.name
        affDot.background = GradientDrawable().apply { setColor(color(a)) }
        for ((k, seg) in segments) {
            val on = k == a
            seg.background = frame(if (on) color(k) else Hud.GREEN_LINE, if (on) color(k) else 0)
            seg.setTextColor(if (on) Hud.INK_GO else Hud.WHITE)
        }
        hud.aimMarker = nameValue.text.toString() to color(a)
    }

    // Quick picks: the next four Alpha–Zulu names after the current one, each with its counter.
    private fun showChips() {
        chips.removeAllViews()
        val from = names.indexOf(lastBase)
        for (i in 1..4) {
            val base = names[(from + i) % names.size]
            chips.addView(text(base.uppercase(), 12f, Hud.GREEN, label).apply {
                background = frame(Hud.GREEN_LINE, 0)
                gravity = Gravity.CENTER
                setOnClickListener {
                    worker.execute {
                        val next = nextName(base)
                        activity.runOnUiThread { setName(next); fold() }
                    }
                }
            }, LinearLayout.LayoutParams(0, px(30f), 1f).apply { marginEnd = px(4f) })
        }
    }

    private fun toggle(f: LinearLayout) = if (f.visibility == View.VISIBLE) fold() else open(f)

    private fun open(f: LinearLayout) {
        fold()
        f.visibility = View.VISIBLE
        if (f === nameFold) { nameField.requestFocus(); nameField.selectAll(); keyboard(true) }
    }

    private fun fold() {
        nameFold.visibility = View.GONE
        affFold.visibility = View.GONE
        keyboard(false)
    }

    private fun keyboard(show: Boolean) {
        val imm = activity.getSystemService(InputMethodManager::class.java)
        if (show) imm.showSoftInput(nameField, 0) else imm.hideSoftInputFromWindow(nameField.windowToken, 0)
    }

    private fun row(title: String, value: TextView, dot: View?, onTap: () -> Unit) = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = frame(Hud.GREEN_LINE, 0)
        setPadding(px(10f), 0, px(10f), 0)
        contentDescription = "$title, tap to change"
        setOnClickListener { onTap() }
        addView(text(title, 11f, Hud.GREEN, label), LinearLayout.LayoutParams(px(92f), WRAP))
        addView(value, LinearLayout.LayoutParams(0, WRAP, 1f))
        dot?.let { addView(it, LinearLayout.LayoutParams(px(10f), px(10f)).apply { marginEnd = px(8f) }) }
        addView(text("▾", 14f, Hud.GREEN, label))
    }

    private fun rowParams() = LinearLayout.LayoutParams(MATCH, px(38f)).apply { topMargin = px(6f) }

    private fun text(str: String, size: Float, color: Int, face: Typeface) = TextView(activity).apply {
        text = str
        typeface = face
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s)
        setTextColor(color)
        letterSpacing = if (face == mono) 0f else 0.1f
        includeFontPadding = false
    }

    private fun frame(stroke: Int, fill: Int) = GradientDrawable().apply {
        setColor(fill)
        setStroke(maxOf(1, (1.5f * s).toInt()), stroke)
        cornerRadius = 3 * s
    }

    private fun px(v: Float) = (v * s).toInt()

    // Alpha-1, Alpha-2, ...: the FreeTAKUAS counter. Runs on the worker (it reads the store).
    private fun nextName(base: String) = "$base-${markerList().count { it.name.startsWith("$base-") } + 1}"

    private fun color(a: Affiliation) = when (a) {
        Affiliation.FRIENDLY -> 0xFF4FA8FF.toInt()
        Affiliation.HOSTILE -> Hud.RED
        Affiliation.NEUTRAL -> Hud.GREEN
        Affiliation.UNKNOWN -> Hud.AMBER
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val names = listOf(
            "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliett",
            "Kilo", "Lima", "Mike", "November", "Oscar", "Papa", "Quebec", "Romeo", "Sierra", "Tango",
            "Uniform", "Victor", "Whiskey", "X-ray", "Yankee", "Zulu",
        )
        // Kept for the next marker in this session: repeated hostiles need no re-pick.
        var lastAffiliation = Affiliation.HOSTILE
        var lastBase = "Alpha"
        var current: AimMarkerPanel? = null
    }
}
