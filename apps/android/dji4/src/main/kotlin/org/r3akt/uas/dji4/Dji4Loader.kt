package org.r3akt.uas.dji4

import android.app.Application

/**
 * Installs the DJI SDK 4 class loader. Kept apart from [Dji4Sdk] on purpose:
 * a class that mentions DJI types must not be initialized before this has run,
 * or Android marks its DJI callback classes as broken for the whole process
 * and every later flight command fails with NoClassDefFoundError.
 */
object Dji4Loader {
    /** Must run from Application.attachBaseContext, before [Dji4Sdk] or [Dji4VideoView] is touched. */
    fun install(app: Application) {
        // DJI SDK 4.18 ships the loader formerly published as com.secneo.sdk.Helper here.
        com.cySdkyc.clx.Helper.install(app)
    }
}
