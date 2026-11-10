package org.r3akt.uas

import android.app.Application
import android.content.Context

class UasApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Variant.source.install(this)
    }
}
