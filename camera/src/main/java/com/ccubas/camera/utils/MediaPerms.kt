package com.ccubas.camera.utils
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Utility object for handling media-related permissions.
 */
object MediaPerms {
    /**
     * Determines the list of required permissions based on the Android API level.
     *
     * @return A list of permission strings required for the camera and media access.
     */
    fun required(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * Opens the application's settings screen to allow the user to manage permissions manually.
     *
     * @param ctx The context used to start the settings activity.
     */
    fun openAppSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
        }
        ctx.startActivity(i)
    }
}