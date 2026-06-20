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
     * Android 14+ (API 34+): request READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, AND
     * READ_MEDIA_VISUAL_USER_SELECTED together. The system shows a 3-way dialog:
     *   "Allow all photos/videos" → full MediaStore access
     *   "Select photos/videos"   → picker-scoped partial access
     *   "Don't allow"            → denied
     * Without READ_MEDIA_IMAGES/VIDEO in the request, "Allow all" is never offered.
     * Android 13 (API 33): READ_MEDIA_IMAGES + READ_MEDIA_VIDEO for full MediaStore access.
     * Android <13: READ_EXTERNAL_STORAGE.
     */
    fun required(): List<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
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

    /** Returns true when CAMERA and RECORD_AUDIO are both granted. */
    fun isCameraGranted(ctx: Context): Boolean {
        fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return has(Manifest.permission.CAMERA) && has(Manifest.permission.RECORD_AUDIO)
    }

    /** Returns true when the app has enough media access to query the gallery. */
    fun isMediaGranted(ctx: Context): Boolean {
        fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ||
                has(Manifest.permission.READ_MEDIA_IMAGES)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                has(Manifest.permission.READ_MEDIA_IMAGES) && has(Manifest.permission.READ_MEDIA_VIDEO)
            else ->
                has(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /** Returns true when both camera and media permissions are granted. */
    fun isGranted(ctx: Context): Boolean = isCameraGranted(ctx) && isMediaGranted(ctx)

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
