package org.r3akt.uas

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * The two transmitted tiers of a picture marker (UAS-OBS-005, issue #19): both are 4:3 crops
 * centred on the aim cross (the frame centre), never upscaled. AI-box crops are not used yet.
 */
object PictureImage {
    const val THUMB_W = 160
    const val THUMB_H = 120
    const val REDUCED_W = 1600
    const val REDUCED_H = 1200
    // shortcut: provisional 2 KB target and quality floor from issue #19; set from the constrained-link measurements (acceptance 7).
    const val THUMB_TARGET_BYTES = 2048
    private const val THUMB_MIN_QUALITY = 20
    private const val REDUCED_QUALITY = 75

    /** The largest centred 4:3 rectangle in a [w] × [h] frame, as (left, top, width, height). */
    fun centre43(w: Int, h: Int): IntArray {
        val cw = minOf(w, h * 4 / 3)
        val ch = cw * 3 / 4
        return intArrayOf((w - cw) / 2, (h - ch) / 2, cw, ch)
    }

    /** The output size for a crop of [cw] × [ch] bounded by [maxW] × [maxH]: scaled down, never up. */
    fun fit(cw: Int, ch: Int, maxW: Int, maxH: Int): Pair<Int, Int> =
        if (cw <= maxW && ch <= maxH) cw to ch else maxW to maxH

    /** 160 × 120 JPEG, quality stepped down towards the byte target (the last try is kept). */
    fun thumbnail(frame: Bitmap): ByteArray {
        val img = crop(frame, THUMB_W, THUMB_H)
        var q = 60
        var out = jpeg(img, q)
        while (out.size > THUMB_TARGET_BYTES && q > THUMB_MIN_QUALITY) {
            q -= 10
            out = jpeg(img, q)
        }
        img.recycle()
        return out
    }

    /** At most 1600 × 1200 JPEG for the hub only. */
    fun reduced(frame: Bitmap): ByteArray {
        val img = crop(frame, REDUCED_W, REDUCED_H)
        return jpeg(img, REDUCED_QUALITY).also { if (img !== frame) img.recycle() }
    }

    private fun crop(frame: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val (x, y, cw, ch) = centre43(frame.width, frame.height)
        val (w, h) = fit(cw, ch, maxW, maxH)
        val cut = Bitmap.createBitmap(frame, x, y, cw, ch)
        if (cw == w && ch == h) return cut
        return Bitmap.createScaledBitmap(cut, w, h, true).also { if (it !== cut) cut.recycle() }
    }

    private fun jpeg(img: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { img.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
