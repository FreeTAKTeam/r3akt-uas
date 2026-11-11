package org.r3akt.uas.screens

import android.app.Activity
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.r3akt.uas.BuildConfig
import org.r3akt.uas.SetupItem
import org.r3akt.uas.SetupState
import org.r3akt.uas.Variant
import org.r3akt.uas.hud.Hud
import org.r3akt.uas.hud.HudState

/**
 * The connection/setup checklist: the build, its Android permissions, and what the source
 * reports about DJI, the aircraft, video and hub publishing. Nothing here is inferred.
 */
class SetupScreen(private val activity: Activity, close: () -> Unit) : ScreenPage {
    private val frame = PageFrame(activity, Screen.SETUP.title, close)
    override val view = frame.root
    private val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    @Suppress("DEPRECATION") // getPackageInfo(String, Int): the Long-flags overload needs API 33.
    private val declared = activity.packageManager
        .getPackageInfo(activity.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
    /** The declared permissions Android grants at runtime (dangerous); the others are install-time or special. */
    @Suppress("DEPRECATION") // protectionLevel: getProtection() needs API 28.
    private val runtime = declared.filter {
        runCatching {
            (activity.packageManager.getPermissionInfo(it, 0).protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) ==
                PermissionInfo.PROTECTION_DANGEROUS
        }.getOrDefault(false)
    }.toSet()
    private var missing = emptyList<String>()
    private var shown: List<SetupItem>? = null
    private var checkedAt = 0L

    private val request = TextView(activity).apply {
        text = "REQUEST MISSING RUNTIME PERMISSIONS"
        typeface = frame.label
        textSize = 16f
        setTextColor(Hud.GREEN)
        val p = PageFrame.dp(activity, 12)
        setPadding(p, p, p, p)
        setOnClickListener { activity.requestPermissions(missing.filter { it in runtime }.toTypedArray(), PERMISSION_REQUEST) }
    }

    init {
        val p = PageFrame.dp(activity, 16)
        frame.body.addView(ScrollView(activity).apply {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(p, 0, p, p)
                addView(rows)
                addView(request)
            })
        })
    }

    override fun update(state: HudState) {
        // The DJI source polls at 10 Hz; the checklist needs no more than once a second.
        val now = SystemClock.elapsedRealtime()
        if (now - checkedAt < 1000) return
        checkedAt = now
        missing = declared.filter { activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        val items = items(state)
        request.visibility = if (missing.any { it in runtime }) View.VISIBLE else View.GONE
        if (items == shown) return
        shown = items
        rows.removeAllViews()
        items.forEach { rows.addView(row(it)) }
    }

    private fun items(state: HudState): List<SetupItem> = buildList {
        add(SetupItem("Build", SetupState.OK,
            "${BuildConfig.FLAVOR} ${BuildConfig.BUILD_TYPE} ${BuildConfig.VERSION_NAME}, source ${Variant.source.name}" +
                if (state.simulated) ", SIMULATED" else ", not simulated"))
        add(SetupItem("Phone", SetupState.OK,
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
        if (declared.isEmpty()) add(SetupItem("Permissions", SetupState.OK, "None declared: this build uses no network or hardware"))
        // Whether DJI needs a permission the phone does not grant is unknown (acceptance.md), so that is not a PROBLEM.
        declared.forEach {
            add(when {
                it !in missing -> SetupItem(it.substringAfterLast('.'), SetupState.OK, "Granted ($it)")
                it in runtime -> SetupItem(it.substringAfterLast('.'), SetupState.PROBLEM, "Not granted; request below ($it)")
                else -> SetupItem(it.substringAfterLast('.'), SetupState.UNKNOWN,
                    "Not granted; install-time or special, not requestable here; whether it is needed is unknown ($it)")
            })
        }
        addAll(runCatching { Variant.source.setupItems() }
            .getOrElse { listOf(SetupItem("Source", SetupState.PROBLEM, "Error: ${it.message ?: it}")) })
        // Only the -Pr3aktPublish=true flavours (simNet, dji4Net) carry the publisher.
        if (!BuildConfig.FLAVOR.endsWith("Net")) {
            add(SetupItem("Hub publishing", SetupState.NOT_IN_BUILD, "This build does not publish (test builds: -Pr3aktPublish=true)"))
        }
    }

    private fun row(item: SetupItem) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        val p = PageFrame.dp(activity, 6)
        setPadding(0, p, 0, p)
        addView(cell(item.item, frame.label, 15f, Hud.WHITE), LinearLayout.LayoutParams(0, WRAP_CONTENT, 3f))
        addView(cell(item.state.label, frame.mono, 13f, color(item.state)), LinearLayout.LayoutParams(0, WRAP_CONTENT, 2f))
        addView(cell(item.text, frame.mono, 13f, Hud.WHITE), LinearLayout.LayoutParams(0, WRAP_CONTENT, 7f))
    }

    private fun cell(value: String, font: android.graphics.Typeface, size: Float, color: Int) = TextView(activity).apply {
        text = value
        typeface = font
        textSize = size
        setTextColor(color)
        setPadding(0, 0, PageFrame.dp(activity, 8), 0)
    }

    private fun color(state: SetupState) = when (state) {
        SetupState.OK -> Hud.GREEN
        SetupState.WAITING -> Hud.AMBER
        SetupState.PROBLEM -> Hud.RED
        SetupState.UNKNOWN -> Hud.WHITE
        SetupState.NOT_IN_BUILD -> Hud.DIM
    }

    private companion object {
        // MainActivity's own INTERNET request uses 1; this answer needs no handling, the next update shows it.
        const val PERMISSION_REQUEST = 2
    }
}
