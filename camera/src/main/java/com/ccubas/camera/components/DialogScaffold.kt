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

/**
 * Configures the window of a dialog to be edge-to-edge, allowing content to draw behind the system bars.
 * This is a private helper composable.
 */
@Composable
private fun ConfigureEdgeToEdgeForDialog() {
    val view = LocalView.current
    val ctx = view.context
    val activity = remember(ctx) { ctx.findActivity() }
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window

    DisposableEffect(Unit) {
        val actWindow = activity?.window

        // Configure the activity window for edge-to-edge
        actWindow?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }

        // Configure the dialog window for edge-to-edge
        dialogWindow?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }

        onDispose {
            // Cleanup if needed
        }
    }
}

/**
 * A scaffold for dialogs that sets up an edge-to-edge black background,
 * providing insets for the system bars.
 *
 * @param content The content to be displayed inside the scaffold.
 */
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