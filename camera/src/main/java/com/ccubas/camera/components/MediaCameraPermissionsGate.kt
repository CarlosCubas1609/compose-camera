package com.ccubas.camera.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.ccubas.camera.utils.MediaPerms

/**
 * A composable that handles the logic for requesting necessary media permissions.
 * It displays a permission request UI if permissions are not granted, and the provided
 * [content] if they are.
 *
 * @param content The composable content to display when all required permissions are granted.
 */
@Composable
fun MediaCameraPermissionsGate(
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val perms = remember { MediaPerms.required().toTypedArray() }

    var asked by rememberSaveable { mutableStateOf(false) }
    // Initialize synchronously so there's no flash when permissions are already granted
    var granted by remember { mutableStateOf(MediaPerms.isGranted(ctx)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Use isGranted() instead of checking res directly: on Android 14, partial access
        // (READ_MEDIA_VISUAL_USER_SELECTED granted) also counts as sufficient even if
        // READ_MEDIA_IMAGES is DENIED.
        granted = MediaPerms.isGranted(ctx)
        asked = true
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(perms)
    }

    // Only CAMERA and RECORD_AUDIO are checked for permanent denial.
    // Media permissions (READ_MEDIA_IMAGES, VISUAL_USER_SELECTED, etc.) must NOT be included:
    // on Android 14+ dismissing the photo picker makes shouldShowRequestPermissionRationale
    // return false — identical to permanent denial — causing a false positive here.
    val hardPerms = remember {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
    val permanentlyDenied = asked && !granted && activity != null && hardPerms.any { p ->
        ContextCompat.checkSelfPermission(ctx, p) != PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, p)
    }

    if (granted) {
        content()
    } else {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Necesitamos cámara, micrófono y acceso a fotos/videos.")
            if (!permanentlyDenied) {
                Button(onClick = { launcher.launch(perms) }) { Text("Conceder permisos") }
            } else {
                Button(onClick = {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", ctx.packageName, null))
                    ctx.startActivity(i)
                }) { Text("Abrir ajustes") }
            }
        }
    }
}

/**
 * Extension function to find the underlying [Activity] from a [Context].
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
