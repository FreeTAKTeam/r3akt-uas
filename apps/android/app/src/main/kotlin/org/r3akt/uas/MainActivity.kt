package org.r3akt.uas

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/** Scaffold status screen; the cockpit, radar and map screens replace it in milestone 4. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = runCatching { Variant.source.describe() }
            .getOrElse { "Error: ${it.message}" }
        val text = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            this.text = "R3AKT UAS scaffold (${Variant.source.name})\n\n$report"
        }
        // API 35 draws edge-to-edge; keep the report clear of the system bars.
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(text) })
    }
}
