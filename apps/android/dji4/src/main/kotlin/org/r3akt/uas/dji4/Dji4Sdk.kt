package org.r3akt.uas.dji4

import android.app.Application
import dji.sdk.sdkmanager.DJISDKManager

/**
 * DJI SDK 4 entry points. The scaffold installs the SDK's class loader but never
 * registers the app or connects to hardware (UAS-SCF-005); registration arrives
 * with the milestone 4 connection flow.
 */
object Dji4Sdk {
    /** Must run from Application.attachBaseContext before any DJI class is used. */
    fun install(app: Application) {
        // DJI SDK 4.18 ships the loader formerly published as com.secneo.sdk.Helper here.
        com.cySdkyc.clx.Helper.install(app)
    }

    /** Proves the SDK is linked without initializing or registering it. */
    val managerClassName: String = DJISDKManager::class.java.name
}
