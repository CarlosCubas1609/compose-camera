package com.ccubas.camera.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Lado máximo de los bitmaps que la librería decodifica para mostrar/editar. Una foto de 50 MP a
 * resolución completa ocupa ~200 MB en ARGB_8888: Android no permite dibujarla en un Canvas
 * (límite ~100 MB, "Canvas: trying to draw too large bitmap") y suele agotar la memoria.
 * 4096 px de lado (≤ ~64 MB) conserva calidad de sobra para revisión, anotación y envío.
 */
internal const val MAX_BITMAP_DIMENSION = 4096

/** Potencia de 2 más chica que deja ambos lados dentro de [maxDimension]. */
internal fun calcularInSampleSize(width: Int, height: Int, maxDimension: Int = MAX_BITMAP_DIMENSION): Int {
    var sample = 1
    while (width / sample > maxDimension || height / sample > maxDimension) sample *= 2
    return sample
}

/** Decodifica [uri] submuestreado a [maxDimension]; null si no se puede leer. */
internal fun ContentResolver.decodeSampledBitmap(uri: Uri, maxDimension: Int = MAX_BITMAP_DIMENSION): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // Con inJustDecodeBounds decodeStream SIEMPRE devuelve null (solo llena bounds): no usarlo
    // como señal de error; solo un stream nulo lo es.
    val boundsStream = openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply {
        inSampleSize = calcularInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    return openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/** Igual que [decodeSampledBitmap] para bytes en memoria (captura de CameraX). */
internal fun decodeSampledBitmap(bytes: ByteArray, maxDimension: Int = MAX_BITMAP_DIMENSION): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calcularInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
