package com.ccubas.composecamera

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.ccubas.composecamera.models.CameraMode
import com.ccubas.composecamera.models.MediaCameraConfig
import com.ccubas.composecamera.models.MediaType
import com.ccubas.composecamera.models.Thumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.math.max

data class UiState(
    val mode: CameraMode = CameraMode.Photo,
    val recording: Boolean = false,
    val recSeconds: Int = 0,
    val torchOn: Boolean = false,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val thumbs: List<Thumb> = emptyList(),
    val gallery: List<Thumb> = emptyList(),
    val selected: List<Uri> = emptyList(),
    val previewVideo: Uri? = null,
    val previewImage: Uri? = null,
    val isPreviewingFromCarousel: Boolean = false,
    val config: MediaCameraConfig = MediaCameraConfig(),
    val longPressingToRecord: Boolean = false,
    val zoomRatio: Float = 1f
)

/**
 * ViewModel for the MediaCamera component
 */
class MediaCameraViewModel(private val app: Context) : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var recording: Recording? = null
    private var tickJob: Job? = null
    private val selectedSet = LinkedHashSet<Uri>()
    private val tempUris = mutableSetOf<Uri>()
    private var cancelingRecording = false

    fun setConfig(config: MediaCameraConfig) {
        _ui.update { it.copy(config = config) }
    }

    fun setMode(m: CameraMode) { 
        // Detener grabación si está activa
        if (_ui.value.recording) {
            cancelingRecording = true
            recording?.stop()
            recording = null
            stopTimer()
        }
        // Limpiar previews y resetear todo como si nunca hubiera empezado
        _ui.value.previewVideo?.let { deleteTempFile(it) }
        _ui.value.previewImage?.let { deleteTempFile(it) }
        setLongPressingToRecord(false)
        _ui.update { it.copy(
            mode = m, 
            zoomRatio = 1f,
            recording = false,
            recSeconds = 0,
            previewVideo = null,
            previewImage = null,
            isPreviewingFromCarousel = false
        ) }
    }

    private fun startTimer() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            var s = 0
            while (true) {
                delay(1000); s++
                _ui.update { it.copy(recSeconds = s) }
            }
        }
    }
    private fun stopTimer() { 
        tickJob?.cancel()
        _ui.update { it.copy(recSeconds = 0) } 
    }
    fun isRecording() = recording != null

    fun loadThumbs(limit: Int = 12) = viewModelScope.launch(Dispatchers.IO) {
        _ui.update { it.copy(thumbs = queryMedia(limit)) }
    }
    fun loadGallery(max: Int = 300) = viewModelScope.launch(Dispatchers.IO) {
        _ui.update { it.copy(gallery = queryMedia(max)) }
    }

    private fun queryMedia(limit: Int): List<Thumb> {
        val cr = app.contentResolver
        val config = _ui.value.config
        
        fun q(uri: Uri, idCol: String, dateCol: String, isVideo: Boolean) = buildList<Pair<Long, Thumb>> {
            cr.query(uri, arrayOf(idCol, dateCol), null, null, "$dateCol DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(idCol)
                val iDt = c.getColumnIndexOrThrow(dateCol)
                var i = 0
                while (c.moveToNext() && i++ < limit) {
                    val id = c.getLong(iId); val dt = c.getLong(iDt)
                    val u = ContentUris.withAppendedId(uri, id)
                    add(dt to Thumb(u, isVideo))
                }
            }
        }
        
        val results = buildList<Pair<Long, Thumb>> {
            when (config.mediaType) {
                MediaType.PHOTO_ONLY -> {
                    addAll(q(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, false))
                }
                MediaType.VIDEO_ONLY -> {
                    addAll(q(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED, true))
                }
                MediaType.BOTH -> {
                    addAll(q(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, false))
                    addAll(q(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED, true))
                }
            }
        }
        
        return results.sortedByDescending { it.first }.map { it.second }.take(limit)
    }

    fun toggleFlash(controller: LifecycleCameraController) {
        val next = if (_ui.value.flashMode == ImageCapture.FLASH_MODE_ON) ImageCapture.FLASH_MODE_OFF else ImageCapture.FLASH_MODE_ON
        controller.imageCaptureFlashMode = next
        _ui.update { it.copy(flashMode = next) }
    }
    fun toggleTorch(controller: LifecycleCameraController) {
        val on = !_ui.value.torchOn; controller.enableTorch(on); _ui.update{ it.copy(torchOn=on) }
    }
    fun switchCamera(controller: LifecycleCameraController) {
        val back = controller.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
        controller.cameraSelector = if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }

    fun toggleSelect(uri: Uri) {
        val config = _ui.value.config
        if (uri in selectedSet) {
            selectedSet.remove(uri)
        } else if (selectedSet.size < config.maxSelection) {
            selectedSet.add(uri)
        }
        _ui.update { it.copy(selected = selectedSet.toList()) }
    }
    
    fun canSelectMore(): Boolean {
        return selectedSet.size < _ui.value.config.maxSelection
    }

    fun clearSelection() {
        selectedSet.clear()
        _ui.update { it.copy(selected = emptyList()) }
    }

    fun previewFromCarousel(uri: Uri, isVideo: Boolean) {
        if (isVideo) {
            _ui.update { it.copy(previewVideo = uri, isPreviewingFromCarousel = true) }
        } else {
            _ui.update { it.copy(previewImage = uri, isPreviewingFromCarousel = true) }
        }
    }

    fun hasSelectedItems(): Boolean = selectedSet.isNotEmpty()
    
    fun setLongPressingToRecord(longPressing: Boolean) {
        _ui.update { it.copy(longPressingToRecord = longPressing) }
    }
    
    fun updateZoom(zoomRatio: Float, controller: LifecycleCameraController) {
        val clampedZoom = zoomRatio.coerceIn(1f, 10f)
        controller.setZoomRatio(clampedZoom)
        _ui.update { it.copy(zoomRatio = clampedZoom) }
    }

    fun capturePhoto(controller: LifecycleCameraController, executor: Executor) {
        val tempFile = File(app.cacheDir, "PHOTO_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        controller.takePicture(opts, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("MediaCamera", "Photo capture failed: ${exc.message}", exc)
            }
            override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                val uri = res.savedUri ?: Uri.fromFile(tempFile)
                tempUris.add(uri)
                _ui.update { it.copy(previewImage = uri) }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun startRecording(controller: LifecycleCameraController, executor: Executor, withAudio: Boolean) {
        if (recording != null) return
        val tempFile = File(app.cacheDir, "VIDEO_${System.currentTimeMillis()}.mp4")
        val out = FileOutputOptions.Builder(tempFile).build()
        val tempUri = Uri.fromFile(tempFile)
        tempUris.add(tempUri)

        recording = controller.startRecording(out, AudioConfig.create(withAudio), executor) { e ->
            when (e) {
                is VideoRecordEvent.Start -> {
                    _ui.update { it.copy(recording = true) }; startTimer()
                }
                is VideoRecordEvent.Finalize -> {
                    stopTimer()
                    if (e.hasError()) {
                        Log.e("MediaCamera", "Video capture error: ${e.error}")
                        deleteTempFile(tempUri)
                    } else if (!cancelingRecording) {
                        _ui.update { it.copy(previewVideo = tempUri) }
                    } else {
                        deleteTempFile(tempUri)
                    }
                    _ui.update { it.copy(recording = false) }
                    recording = null
                    cancelingRecording = false
                }
            }
        }
    }
    
    fun stopRecording() { 
        cancelingRecording = false
        recording?.stop()
        recording = null 
    }

    fun dismissImagePreview() {
        val ui = _ui.value
        if (!ui.isPreviewingFromCarousel) {
            ui.previewImage?.let { deleteTempFile(it) }
        }
        _ui.update { it.copy(previewImage = null, isPreviewingFromCarousel = false) }
    }
    
    fun dismissVideoPreview() {
        val ui = _ui.value
        if (!ui.isPreviewingFromCarousel) {
            ui.previewVideo?.let { deleteTempFile(it) }
        }
        _ui.update { it.copy(previewVideo = null, isPreviewingFromCarousel = false) }
    }

    private fun deleteTempFile(uri: Uri) {
        runCatching { File(uri.path ?: return@runCatching).delete() }
        tempUris.remove(uri)
    }

    suspend fun saveToMediaStore(tempFileUri: Uri, isVideo: Boolean): Uri? = withContext(Dispatchers.IO) {
        val resolver = app.contentResolver
        val mediaStoreUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val extension = if (isVideo) "mp4" else "jpg"
        val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val prefix = if (isVideo) "VID" else "IMG"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${prefix}_${System.currentTimeMillis()}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        var newUri: Uri? = null
        try {
            newUri = resolver.insert(mediaStoreUri, values) ?: return@withContext null
            resolver.openOutputStream(newUri)?.use { outputStream ->
                resolver.openInputStream(tempFileUri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                } ?: return@withContext null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(newUri, values, null, null)
            }

            deleteTempFile(tempFileUri)
            return@withContext newUri
        } catch (e: Exception) {
            newUri?.let { resolver.delete(it, null, null) }
            Log.e("MediaCamera", "Failed to save to MediaStore", e)
            return@withContext null
        }
    }

    @OptIn(UnstableApi::class)
    @SuppressLint("WrongConstant")
    fun fastTrim(src: Uri, startUs: Long, endUs: Long, onDone: (Uri?) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            if (endUs <= startUs) { 
                withContext(Dispatchers.Main) { onDone(null) }
                return@launch 
            }

            var extractor: MediaExtractor? = null
            var muxer: MediaMuxer? = null
            var pfd: ParcelFileDescriptor? = null
            val out = File(app.cacheDir, "trim_${System.currentTimeMillis()}.mp4")
            var result: Uri? = null

            try {
                extractor = MediaExtractor()
                pfd = app.contentResolver.openFileDescriptor(src, "r") ?: run { 
                    withContext(Dispatchers.Main) { onDone(null) }
                    return@launch 
                }
                extractor.setDataSource(pfd.fileDescriptor)

                val indexMap = HashMap<Int, Int>()
                var rotation = 0
                var maxInput = 256 * 1024
                val tracks = extractor.trackCount

                for (i in 0 until tracks) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                        extractor.selectTrack(i)
                        if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxInput = max(maxInput, fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                        }
                        if (mime.startsWith("video/") && fmt.containsKey("rotation-degrees")) {
                            rotation = fmt.getInteger("rotation-degrees")
                        }
                    }
                }

                muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                for (i in 0 until tracks) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                        indexMap[i] = muxer.addTrack(fmt)
                    }
                }
                if (rotation != 0) muxer.setOrientationHint(rotation)
                muxer.start()

                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                val buffer = ByteBuffer.allocateDirect(maxInput)
                val info = MediaCodec.BufferInfo()

                while (true) {
                    val track = extractor.sampleTrackIndex
                    if (track < 0) break

                    info.offset = 0
                    info.size = extractor.readSampleData(buffer, 0)
                    if (info.size <= 0) break

                    val pts = extractor.sampleTime
                    if (pts < 0) break
                    if (pts < startUs) { extractor.advance(); continue }
                    if (pts > endUs) break

                    info.presentationTimeUs = pts
                    info.flags = extractor.sampleFlags

                    val dst = indexMap[track]
                    if (dst == null) {
                        extractor.advance()
                        continue
                    }

                    muxer.writeSampleData(dst, buffer, info)
                    extractor.advance()
                }

                muxer.stop()
                result = Uri.fromFile(out).also { tempUris.add(it) }
            } catch (e: Exception) {
                Log.e("MediaCamera", "fastTrim error: ${e.message}", e)
                runCatching { out.delete() }
            } finally {
                runCatching { muxer?.release() }
                runCatching { extractor?.release() }
                runCatching { pfd?.close() }
                withContext(Dispatchers.Main) { onDone(result) }
            }
        }

    fun saveImageAndSend(tempUri: Uri, onDone: (Uri?) -> Unit) {
        val config = _ui.value.config
        val isFromCarousel = _ui.value.isPreviewingFromCarousel
        
        if (isFromCarousel && !config.saveToMediaStore) {
            onDone(tempUri)
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val permanentUri = saveToMediaStore(tempUri, isVideo = false)
                withContext(Dispatchers.Main) {
                    onDone(permanentUri)
                }
            }
        }
    }
    
    fun trimVideoAndSave(src: Uri, startUs: Long, endUs: Long, onDone: (Uri?) -> Unit) {
        val config = _ui.value.config
        val isFromCarousel = _ui.value.isPreviewingFromCarousel
        
        if (isFromCarousel && !config.saveToMediaStore) {
            val videoDurationMs = getVideoDuration(src)
            val isFullVideo = startUs == 0L && endUs >= (videoDurationMs * 1000)
            
            if (isFullVideo) {
                onDone(src)
            } else {
                fastTrim(src, startUs, endUs) { tempUri ->
                    onDone(tempUri)
                }
            }
        } else {
            fastTrim(src, startUs, endUs) { tempUri ->
                if (tempUri != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val permanentUri = saveToMediaStore(tempUri, isVideo = true)
                        withContext(Dispatchers.Main) {
                            onDone(permanentUri)
                        }
                    }
                } else {
                    onDone(null)
                }
            }
        }
    }
    
    private fun getVideoDuration(uri: Uri): Long {
        return try {
            val extractor = MediaExtractor()
            val pfd = app.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                extractor.setDataSource(pfd.fileDescriptor)
                val duration = extractor.cachedDuration / 1000
                pfd.close()
                extractor.release()
                duration
            } else 0L
        } catch (e: Exception) {
            Log.e("MediaCamera", "Error getting video duration", e)
            0L
        }
    }

    fun cleanupOnDispose() {
        runCatching { stopRecording() }
        selectedSet.clear()
        _ui.update { it.copy(
            previewImage = null,
            previewVideo = null,
            selected = emptyList()
        ) }
        viewModelScope.launch(Dispatchers.IO) {
            tempUris.forEach { uri ->
                runCatching { File(uri.path ?: return@forEach).delete() }
            }
            tempUris.clear()
        }
    }
}