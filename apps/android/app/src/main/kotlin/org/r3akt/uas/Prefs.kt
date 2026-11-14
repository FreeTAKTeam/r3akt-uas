package org.r3akt.uas

import android.content.Context

/**
 * Live-stream quality. Every preset but [ORIGINAL] has the phone re-encode the video at that size
 * and bitrate (more phone CPU, far less traffic); [ORIGINAL] forwards the aircraft's own H.264.
 * [LOW] is the default, to keep test traffic cheap.
 */
enum class StreamQuality(val label: String, val width: Int?, val height: Int?, val kbps: Int?) {
    LOW("LOW", 480, 360, 300),
    MEDIUM("MEDIUM", 960, 540, 800),
    HIGH("HIGH", 1280, 720, 2000),
    ORIGINAL("ORIGINAL", null, null, null);

    /** Approximate video traffic per hour of streaming, in MB; null when the aircraft sets the rate. */
    val mbPerHour: Int? get() = kbps?.let { it * 3600 / 8 / 1000 }
}

/**
 * Local operator preferences in Android private storage (no backup: allowBackup is false).
 * The video server URL may carry MediaMTX publish credentials (UAS-VID-010): it is only ever
 * shown or logged through [redact], and never leaves this app except to the video server.
 */
object Prefs {
    /** Radar ranges in metres (UAS-RAD-002); the first is the default. */
    val RADAR_RANGES_M = listOf(500, 1000, 2000, 5000)

    private fun prefs(context: Context) = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun radarRangeM(context: Context): Int =
        prefs(context).getInt("radar_range_m", RADAR_RANGES_M[0]).takeIf { it in RADAR_RANGES_M } ?: RADAR_RANGES_M[0]

    fun streamUrl(context: Context): String? = prefs(context).getString("stream_url", null)?.takeIf { validStreamUrl(it) }

    /** Saves [url], or clears it when null. */
    fun setStreamUrl(context: Context, url: String?) {
        require(url == null || validStreamUrl(url))
        prefs(context).edit().apply { if (url == null) remove("stream_url") else putString("stream_url", url) }.apply()
    }

    /** rtmp:// or rtmps:// with a host: what DJI's live stream accepts. */
    fun validStreamUrl(url: String): Boolean =
        Regex("^rtmps?://[^/?#\\s]+(/[^\\s]*)?$", RegexOption.IGNORE_CASE).matches(url)

    /** [url] with any user-info and query (where MediaMTX credentials go) removed. */
    fun redact(url: String): String {
        val scheme = url.substringBefore("://")
        val rest = url.substringAfter("://").substringBefore('?').substringBefore('#')
        val host = rest.substringBefore('/').substringAfterLast('@')
        val path = rest.substringAfter('/', "")
        val hidden = rest.substringBefore('/').contains('@') || url.contains('?')
        return "$scheme://$host" + (if (path.isEmpty()) "" else "/$path") + if (hidden) " (credentials hidden)" else ""
    }

    fun streamQuality(context: Context): StreamQuality =
        prefs(context).getString("stream_quality", null)?.let { name -> StreamQuality.entries.firstOrNull { it.name == name } }
            ?: StreamQuality.LOW

    fun setStreamQuality(context: Context, quality: StreamQuality) =
        prefs(context).edit().putString("stream_quality", quality.name).apply()

    fun setRadarRangeM(context: Context, metres: Int) {
        require(metres in RADAR_RANGES_M)
        prefs(context).edit().putInt("radar_range_m", metres).apply()
    }
}
