package org.r3akt.uas

import java.io.File

/** One file on the aircraft's card as reported; `null` = not reported. */
data class MediaItem(val name: String, val sizeBytes: Long?, val created: String?, val durationS: Float?, val video: Boolean?)

/** Media stored on the aircraft (UAS-VID-008). Callbacks may come on any thread. */
interface AircraftMedia {
    /** The card's files, or the reason they cannot be listed. */
    fun list(onResult: (List<MediaItem>?, String?) -> Unit)

    /** Copies one listed file into [dir]; [onDone] gets the file or the error, never a partial file. */
    fun download(name: String, dir: File, onProgress: (Long, Long) -> Unit, onDone: (File?, String?) -> Unit)

    /** Gives the camera back to shooting; called when the page closes. */
    fun close()
}
