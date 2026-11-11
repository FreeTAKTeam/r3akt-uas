package org.r3akt.uas.dji4

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.TextureView
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager
import java.util.concurrent.atomic.AtomicLong

/** Live preview of the aircraft's primary video feed. Create it only while an aircraft is connected. */
class Dji4VideoView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    @Volatile private var codec: DJICodecManager? = null
    private val listener = VideoFeeder.VideoDataListener { data, size ->
        bytes.addAndGet(size.toLong())
        packets.incrementAndGet()
        codec?.sendDataToDecoder(data, size)
    }

    init {
        surfaceTextureListener = this
    }

    // shortcut: letterboxed to 16:9, so a 4:3 photo-mode feed is stretched; size from the codec's reported video size once tested on an aircraft.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (w * 9 > h * 16) setMeasuredDimension(h * 16 / 9, h) else setMeasuredDimension(w, w * 9 / 16)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        // One decoder at a time, process-wide: a decoder that is not destroyed stays registered
        // inside the SDK and keeps decoding, so every extra one adds to the phone's load.
        release()
        val created = DJICodecManager(context, surface, width, height)
        codec = created
        current = created
        VideoFeeder.getInstance()?.primaryVideoFeed?.addVideoDataListener(listener)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        VideoFeeder.getInstance()?.primaryVideoFeed?.removeVideoDataListener(listener)
        val old = codec
        codec = null
        if (old != null) {
            if (current === old) current = null
            old.cleanSurface()
            // cleanSurface alone leaves the decoder and its SDK registration alive (DJI SDK 4.18).
            old.destroyCodec()
        }
        return true
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    companion object {
        @Volatile private var current: DJICodecManager? = null
        private val bytes = AtomicLong()
        private val packets = AtomicLong()
        private var lastAt = SystemClock.elapsedRealtime()
        private var lastBytes = 0L
        private var lastPackets = 0L

        /** Destroys a decoder left behind by an earlier view. */
        private fun release() {
            current?.let {
                current = null
                it.cleanSurface()
                it.destroyCodec()
            }
        }

        /** Video feed and decoder state since the previous report, for the LOG dialog. */
        @Synchronized
        fun report(): String {
            val now = SystemClock.elapsedRealtime()
            val seconds = ((now - lastAt) / 1000.0).coerceAtLeast(0.001)
            val b = bytes.get()
            val p = packets.get()
            val kbps = (b - lastBytes) * 8 / 1000.0 / seconds
            val pps = (p - lastPackets) / seconds
            lastAt = now
            lastBytes = b
            lastPackets = p
            val c = current ?: return "Video: no decoder, feed %.0f kbit/s".format(kbps)
            val size = runCatching { "${c.videoWidth}x${c.videoHeight}" }.getOrDefault("-")
            val ok = runCatching { c.isDecoderOK }.getOrNull()
            return "Video: feed %.0f kbit/s, %.0f packets/s, decoder %s, %s".format(
                kbps, pps, when (ok) { true -> "ok"; false -> "NOT OK"; null -> "-" }, size,
            )
        }
    }
}
