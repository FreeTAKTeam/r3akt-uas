package org.r3akt.uas

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import org.r3akt.uas.hud.HudState
import org.r3akt.uas.hud.HudView

/** The flight cockpit: the HUD skin over the aircraft source and its live video. */
class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var hud: HudView
    private var video: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var seq = 0u
    /** The last rejected action in full; the HUD strip shortens long DJI texts. */
    private var lastRejection: String? = null

    private val tick = object : Runnable {
        override fun run() {
            val state = runCatching { Variant.source.cockpit(seq++) }
                .getOrElse { HudState(status = "SOURCE ERROR", simulated = false) }
            hud.state = state
            showVideo(state.video)
            handler.postDelayed(this, Variant.source.periodMs)
        }
    }

    private fun showVideo(on: Boolean) {
        if (on && video == null) {
            video = Variant.source.videoView(this)?.also {
                root.addView(it, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER))
            }
        } else if (!on && video != null) {
            root.removeView(video)
            video = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hud = HudView(this)
        hud.onBack = { finish() }
        hud.onLog = ::showReport
        // Sent once, on the tap; a rejection is shown and never queued or retried (UAS-FLT-004, UAS-FLT-005).
        hud.onAction = { action ->
            Variant.source.perform(action) { error ->
                runOnUiThread {
                    if (error != null && !isDestroyed) {
                        val title = "${action.name.replace('_', ' ')} REJECTED · NOT RETRIED"
                        lastRejection = "$title\nDJI: $error"
                        hud.alert = title to "DJI: $error"
                    }
                }
            }
        }
        root = FrameLayout(this).apply { addView(hud) }
        setContentView(root)
        Variant.source.start(this)
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    /** The scaffold status report (shared codec round trip), kept as the session log for now. */
    private fun showReport() {
        val report = runCatching { Variant.source.describe() }.getOrElse { "Error: ${it.message}" }
        val text = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            this.text = "R3AKT UAS (${Variant.source.name})\n\n$report" + (lastRejection?.let { "\n\n$it" } ?: "")
        }
        AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
