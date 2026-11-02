package org.qbitx.wallet

import androidx.activity.enableEdgeToEdge

/** Test-only rendering host. No wallet access and no authentication bypass. Not in release builds. */
class AuditPreviewActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge(statusBarStyle=androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=androidx.activity.SystemBarStyle.dark(android.graphics.Color.rgb(11,14,17)))
    }
}
