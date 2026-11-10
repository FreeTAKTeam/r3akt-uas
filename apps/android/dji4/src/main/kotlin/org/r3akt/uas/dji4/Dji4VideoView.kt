package org.r3akt.uas.dji4

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager

/** Live preview of the aircraft's primary video feed. Create it only while an aircraft is connected. */
class Dji4VideoView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    @Volatile private var codec: DJICodecManager? = null
    private val listener = VideoFeeder.VideoDataListener { data, size -> codec?.sendDataToDecoder(data, size) }

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
        codec = DJICodecManager(context, surface, width, height)
        VideoFeeder.getInstance()?.primaryVideoFeed?.addVideoDataListener(listener)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        VideoFeeder.getInstance()?.primaryVideoFeed?.removeVideoDataListener(listener)
        codec?.cleanSurface()
        codec = null
        return false
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
