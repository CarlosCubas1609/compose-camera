package com.ccubas.camera.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ccubas.camera.utils.MediaPerms

// ===================== PERMISOS (todos) =====================
@Composable
fun MediaCameraPermissionsGate(
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }

    val perms = remember {
        MediaPerms.required().toTypedArray()
    }

    var asked by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = perms.all { p ->
            res[p] == true || ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        }
        asked = true
    }

    LaunchedEffect(Unit) {
        granted = perms.all { p -> ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED }
        if (!granted) launcher.launch(perms)
    }

    val permanentlyDenied = asked && !granted && activity != null && perms.any { p ->
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}