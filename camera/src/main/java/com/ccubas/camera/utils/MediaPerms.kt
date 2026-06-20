package com.ccubas.camera.utils
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Utility object for handling media-related permissions.
 */
object MediaPerms {
    /**
     * Permissions to request based on API level.
     *
     * Android 14+: READ_MEDIA_IMAGES/VIDEO are declared maxSdkVersion=33 in the manifest so
     * they must NOT be requested on API 34+ (system auto-denies undeclared permissions and
     * shouldShowRequestPermissionRationale returns false, triggering a false permanentlyDenied).
     * Only READ_MEDIA_VISUAL_USER_SELECTED is needed on API 34+.
     * Android 13: READ_MEDIA_IMAGES + VIDEO for direct MediaStore access.
     * Android <13: READ_EXTERNAL_STORAGE.
     */
    fun required(): List<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            else ->
                listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
        }
    }

    /**
     * Whether the app has enough media access to show the camera and gallery.
     *
     * Android 14+: accepts either full access (READ_MEDIA_IMAGES granted) or partial access
     * (READ_MEDIA_VISUAL_USER_SELECTED granted) — both let the custom gallery work.
     */
    fun isGranted(ctx: Context): Boolean {
        fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        val camera = has(Manifest.permission.CAMERA)
        val audio = has(Manifest.permission.RECORD_AUDIO)
        val media = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ||
                has(Manifest.permission.READ_MEDIA_IMAGES)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                has(Manifest.permission.READ_MEDIA_IMAGES) && has(Manifest.permission.READ_MEDIA_VIDEO)
            else ->
                has(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return camera && audio && media
    }

    /**
     * Opens the application's settings screen to allow the user to manage permissions manually.
     */
    fun openAppSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", ctx.packageName, null)
        }
        ctx.startActivity(i)
    }
}
