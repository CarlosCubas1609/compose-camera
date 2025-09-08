package com.ccubas.camera


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
import androidx.lifecycle.ViewModelProvider
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

/**
 * Represents the state of the media camera UI.
 *
 * @param mode The current camera mode (Photo or Video).
 * @param recording Whether video recording is currently active.
 * @param recSeconds The duration of the current recording in seconds.
 * @param torchOn Whether the torch (flashlight) is currently on.
 * @param flashMode The current flash mode for image capture.
 * @param thumbs The list of media thumbnails to display in the carousel.
 * @param gallery The list of media items to display in the main gallery view.
 * @param selected The list of currently selected media URIs.
 * @param previewVideo The URI of the video currently being previewed.
 * @param previewImage The URI of the image currently being previewed.
 * @param isPreviewingFromCarousel Whether the current preview originated from the carousel.
 * @param config The current configuration for the media camera.
 * @param longPressingToRecord Whether the user is currently long-pressing the shutter button to record video.
 * @param zoomRatio The current camera zoom ratio.
 */
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
 * ViewModel for the [MediaCameraScreen], handling all business logic, state management,
 * and interactions with the camera and media services.
 *
 * @param app The application context.
 */
class MediaCameraViewModel(private val app: Context) : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    /**
     * The state flow of the UI state.
     */
    val ui: StateFlow<UiState> = _ui

    private var recording: Recording? = null
    private var tickJob: Job? = null
    private val selectedSet = LinkedHashSet<Uri>()
    private val tempUris = mutableSetOf<Uri>()
    private var cancelingRecording = false

    /**
     * Sets the configuration for the media camera.
     *
     * @param config The new configuration.
     */
    fun setConfig(config: MediaCameraConfig) {
        _ui.update { it.copy(config = config) }
    }

    /**
     * Sets the camera mode (Photo or Video).
     *
     * @param m The new camera mode.
     */
    fun setMode(m: CameraMode) {
        if (_ui.value.recording) {
            cancelingRecording = true
            recording?.stop()
            recording = null
            stopTimer()
        }
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

    /**
     * Starts a timer to track video recording duration.
     */
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

    /**
     * Stops the recording timer.
     */
    private fun stopTimer() { tickJob?.cancel(); _ui.update { it.copy(recSeconds = 0) } }

    /**
     * Checks if video recording is currently in progress.
     *
     * @return True if recording, false otherwise.
     */
    fun isRecording() = recording != null

    /**
     * Loads a limited number of media thumbnails for the carousel.
     *
     * @param limit The maximum number of thumbnails to load.
     */
    fun loadThumbs(limit: Int = 12) = viewModelScope.launch(Dispatchers.IO) {
        _ui.update { it.copy(thumbs = queryMedia(limit)) }
    }

    /**
     * Loads a larger number of media items for the main gallery view.
     *
     * @param max The maximum number of media items to load.
     */
    fun loadGallery(max: Int = 300) = viewModelScope.launch(Dispatchers.IO) {
        _ui.update { it.copy(gallery = queryMedia(max)) }
    }

    /**
     * Queries the [MediaStore] for images and videos.
     *
     * @param limit The maximum number of items to query.
     * @return A list of [Thumb] objects.
     */
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

    /**
     * Toggles the flash mode for image capture.
     *
     * @param controller The camera controller.
     */
    fun toggleFlash(controller: LifecycleCameraController) {
        val next = if (_ui.value.flashMode == ImageCapture.FLASH_MODE_ON) ImageCapture.FLASH_MODE_OFF else ImageCapture.FLASH_MODE_ON
        controller.imageCaptureFlashMode = next
        _ui.update { it.copy(flashMode = next) }
    }

    /**
     * Toggles the torch (flashlight) for video recording.
     *
     * @param controller The camera controller.
     */
    fun toggleTorch(controller: LifecycleCameraController) {
        val on = !_ui.value.torchOn; controller.enableTorch(on); _ui.update{ it.copy(torchOn=on) }
    }

    /**
     * Switches between the front and back cameras.
     *
     * @param controller The camera controller.
     */
    fun switchCamera(controller: LifecycleCameraController) {
        val back = controller.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
        controller.cameraSelector = if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }

    /**
     * Toggles the selection state of a media item.
     *
     * @param uri The URI of the media item.
     */
    fun toggleSelect(uri: Uri) {
        val config = _ui.value.config
        if (uri in selectedSet) {
            selectedSet.remove(uri)
        } else if (selectedSet.size < config.maxSelection) {
            selectedSet.add(uri)
        }
        _ui.update { it.copy(selected = selectedSet.toList()) }
    }

    /**
     * Checks if more items can be selected based on the `maxSelection` config.
     *
     * @return True if more items can be selected, false otherwise.
     */
    fun canSelectMore(): Boolean {
        return selectedSet.size < _ui.value.config.maxSelection
    }

    /**
     * Initiates a preview of a media item from the carousel.
     *
     * @param uri The URI of the media item.
     * @param isVideo Whether the media item is a video.
     */
    fun previewFromCarousel(uri: Uri, isVideo: Boolean) {
        if (isVideo) {
            _ui.update { it.copy(previewVideo = uri, isPreviewingFromCarousel = true) }
        } else {
            _ui.update { it.copy(previewImage = uri, isPreviewingFromCarousel = true) }
        }
    }

    /**
     * Checks if there are any selected items.
     *
     * @return True if items are selected, false otherwise.
     */
    fun hasSelectedItems(): Boolean = selectedSet.isNotEmpty()

    /**
     * Updates the state to reflect that the user is long-pressing to record.
     *
     * @param longPressing True if the user is long-pressing, false otherwise.
     */
    fun setLongPressingToRecord(longPressing: Boolean) {
        _ui.update { it.copy(longPressingToRecord = longPressing) }
    }

    /**
     * Updates the camera zoom ratio.
     *
     * @param zoomRatio The new zoom ratio.
     * @param controller The camera controller.
     */
    fun updateZoom(zoomRatio: Float, controller: LifecycleCameraController) {
        val clampedZoom = zoomRatio.coerceIn(1f, 10f)
        controller.setZoomRatio(clampedZoom)
        _ui.update { it.copy(zoomRatio = clampedZoom) }
    }

    /**
     * Clears the current selection of media items.
     */
    fun clearSelection() {
        selectedSet.clear()
        _ui.update { it.copy(selected = emptyList()) }
    }

    /**
     * Captures a photo and saves it to a temporary file.
     *
     * @param controller The camera controller.
     * @param executor The executor to use for the capture callback.
     */
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

    /**
     * Starts recording a video to a temporary file.
     *
     * @param controller The camera controller.
     * @param executor The executor to use for the recording events.
     * @param withAudio Whether to record audio.
     */
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

    /**
     * Stops the current video recording.
     */
    fun stopRecording() {
        cancelingRecording = false
        recording?.stop()
        recording = null
    }

    /**
     * Dismisses the image preview.
     */
    fun dismissImagePreview() {
        val ui = _ui.value
        if (!ui.isPreviewingFromCarousel) {
            ui.previewImage?.let { deleteTempFile(it) }
        }
        _ui.update { it.copy(previewImage = null, isPreviewingFromCarousel = false) }
    }

    /**
     * Dismisses the video preview.
     */
    fun dismissVideoPreview() {
        val ui = _ui.value
        if (!ui.isPreviewingFromCarousel) {
            ui.previewVideo?.let { deleteTempFile(it) }
        }
        _ui.update { it.copy(previewVideo = null, isPreviewingFromCarousel = false) }
    }

    /**
     * Deletes a temporary file.
     */
    private fun deleteTempFile(uri: Uri) {
        runCatching { File(uri.path ?: return@runCatching).delete() }
        tempUris.remove(uri)
    }

    /**
     * Saves a temporary file to the MediaStore.
     *
     * @param tempFileUri The URI of the temporary file.
     * @param isVideo Whether the file is a video.
     * @return The URI of the new file in the MediaStore, or null on failure.
     */
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

    /**
     * Quickly trims a video without re-encoding.
     *
     * @param src The source video URI.
     * @param startUs The start time in microseconds.
     * @param endUs The end time in microseconds.
     * @param onDone Callback with the URI of the trimmed video, or null on failure.
     */
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

    /**
     * Processes a captured image, either saving it to the MediaStore or providing the temporary URI.
     *
     * @param tempUri The URI of the temporary image file.
     * @param onDone Callback with the final URI of the image.
     */
    fun saveImageAndSend(tempUri: Uri, onDone: (Uri?) -> Unit) {
        val config = _ui.value.config

        if (!config.saveToMediaStore) {
            // Don't save to MediaStore, use temporary/original URI.
            // The file is removed from the cleanup list so it's not deleted on close.
            // The library user is responsible for managing this file.
            tempUris.remove(tempUri)
            onDone(tempUri)
        } else {
            // Save a copy to MediaStore
            viewModelScope.launch(Dispatchers.IO) {
                val permanentUri = saveToMediaStore(tempUri, isVideo = false)
                withContext(Dispatchers.Main) {
                    onDone(permanentUri)
                }
            }
        }
    }

    /**
     * Processes a captured or selected video, trimming it if necessary, and then either
     * saving it to the MediaStore or providing the temporary URI.
     *
     * @param src The source URI of the video.
     * @param startUs The start time for trimming in microseconds.
     * @param endUs The end time for trimming in microseconds.
     * @param onDone Callback with the final URI of the video.
     */
    fun trimVideoAndSave(src: Uri, startUs: Long, endUs: Long, onDone: (Uri?) -> Unit) {
        val config = _ui.value.config
        val isFromCarousel = _ui.value.isPreviewingFromCarousel

        if (!config.saveToMediaStore) {
            if (isFromCarousel) {
                val videoDurationMs = getVideoDuration(src)
                val isFullVideo = startUs == 0L && endUs >= (videoDurationMs * 1000)

                if (isFullVideo) {
                    onDone(src)
                } else {
                    fastTrim(src, startUs, endUs) { tempUri ->
                        tempUri?.let { tempUris.remove(it) }
                        onDone(tempUri)
                    }
                }
            } else {
                fastTrim(src, startUs, endUs) { tempUri ->
                    tempUri?.let { tempUris.remove(it) }
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

    /**
     * Gets the duration of a video from its URI.
     *
     * @param uri The URI of the video.
     * @return The duration in milliseconds.
     */
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

    /**
     * Cleans up all temporary files and resets the state when the camera screen is disposed.
     */
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

/**
 * Factory for creating [MediaCameraViewModel] instances.
 *
 * @param app The application context.
 */
class MediaCameraViewModelFactory(private val app: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaCameraViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MediaCameraViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}