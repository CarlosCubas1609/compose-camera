package com.ccubas.camera.components

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

// ====== Window helpers (status/nav transparent + edge-to-edge) ======
@Composable
private fun ConfigureEdgeToEdgeForDialog() {
    val view = LocalView.current
    val ctx = view.context
    val activity = remember(ctx) { ctx.findActivity() }
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window

    DisposableEffect(Unit) {
        val actWindow = activity?.window

        // Activity edge-to-edge (para que el Dialog respete barras)
        actWindow?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }

        // Dialog edge-to-edge
        dialogWindow?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }

        onDispose {

        }
    }
}

@Composable
fun DialogScaffold(content: @Composable () -> Unit) {
    ConfigureEdgeToEdgeForDialog()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) { content() }
}
