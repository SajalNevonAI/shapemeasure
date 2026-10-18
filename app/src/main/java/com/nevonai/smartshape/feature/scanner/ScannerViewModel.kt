package com.nevonai.smartshape.feature.scanner

import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.exifinterface.media.ExifInterface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nevonai.smartshape.cv.FrameQuality
import com.nevonai.smartshape.cv.ShapeDetector
import com.nevonai.smartshape.data.AppSettings
import com.nevonai.smartshape.data.SettingsRepository
import com.nevonai.smartshape.model.DetectedShape
import com.nevonai.smartshape.model.FocusCorrection
import com.nevonai.smartshape.model.Point2
import com.nevonai.smartshape.model.primary
import com.nevonai.smartshape.util.BitmapIO
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

data class LiveFrame(
    val shapes: List<DetectedShape> = emptyList(),
    /** Upright analysis frame size the shape coordinates refer to. */
    val width: Int = 0,
    val height: Int = 0,
    val quality: FrameQuality? = null,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _live = MutableStateFlow(LiveFrame())
    val live: StateFlow<LiveFrame> = _live.asStateFlow()

    /**
     * Lens-to-subject distance from autofocus (mm), updated with every preview frame. Null when the
     * lens reports infinity or the camera can't tell.
     */
    private val _focusMm = MutableStateFlow<Double?>(null)
    val focusMm: StateFlow<Double?> = _focusMm.asStateFlow()

    /** [focusMm] corrected for this phone model's autofocus error; what the user is shown. */
    private val correction = FocusCorrection.forDevice(android.os.Build.MODEL)
    private val _trueMm = MutableStateFlow<Double?>(null)
    val trueDistanceMm: StateFlow<Double?> = _trueMm.asStateFlow()

    /** Attached to the preview's repeating request; reads the focus distance the lens settled on. */
    val focusCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val diopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: return
            // Diopters are 1 / metres; below 0.33 dpt (> 3 m) the estimate is meaningless for measuring.
            _focusMm.value = if (diopters > 0.33f) Math.round(1000.0 / diopters).toDouble() else null
            _trueMm.value = _focusMm.value?.let { correction.trueDistanceMm(it) }
        }
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastAnalysis = 0L
    private var frameCount = 0

    /** CameraX analyzer: runs the fast detector on the luma plane, ~12 fps. */
    fun analyze(image: ImageProxy) {
        image.use {
            val now = System.currentTimeMillis()
            if (now - lastAnalysis < 80) return
            lastAnalysis = now
            val plane = image.planes[0]
            val shapes = ShapeDetector.detectLuma(plane.buffer, image.width, image.height, plane.rowStride)
            val quality = if (frameCount++ % 4 == 0) ShapeDetector.quality(plane.buffer, image.width, image.height, plane.rowStride) else _live.value.quality
            val rot = image.imageInfo.rotationDegrees
            val (w, h) = if (rot == 90 || rot == 270) image.height to image.width else image.width to image.height
            val upright = shapes.map { s -> s.copy(points = s.points.map { rotate(it, rot, image.width, image.height) }) }
            // One object per frame: the outline nearest the centre, matching what the editor measures.
            _live.value = LiveFrame(listOfNotNull(upright.primary(w, h)), w, h, quality)
        }
    }

    /**
     * Stores the back camera's focal length relative to its sensor's long side, which the
     * fixed-distance calibration needs (focal length in px = value * image long side).
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun saveIntrinsics(camera: Camera) {
        val info = Camera2CameraInfo.from(camera.cameraInfo)
        val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: return
        val phys = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return
        val pixels = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return
        val active = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        // Physical size covers the full pixel array; captures use the active area.
        val wMm = phys.width * active.width() / pixels.width.toDouble()
        val hMm = phys.height * active.height() / pixels.height.toDouble()
        val longMm = maxOf(wMm, hMm)
        if (focal <= 0f || longMm <= 0) return
        viewModelScope.launch { settingsRepository.setFocalNorm(focal / longMm) }
    }

    fun setBusy(busy: Boolean) = _busy.update { busy }

    /** Saves the photo with the autofocus distance at the moment of capture in its EXIF data. */
    suspend fun persistCapture(bitmap: Bitmap, focusMm: Double?): String {
        val path = BitmapIO.writeCache(context, bitmap)
        if (focusMm != null) runCatching {
            ExifInterface(path).apply {
                setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE, "${focusMm.toLong()}/1000")
                saveAttributes()
            }
        }
        return path
    }

    suspend fun persistImport(uri: android.net.Uri): String? = BitmapIO.importToCache(context, uri)

    override fun onCleared() {
        analysisExecutor.shutdown()
    }

    private fun rotate(p: Point2, degrees: Int, w: Int, h: Int): Point2 = when (degrees) {
        90 -> Point2(h - p.y, p.x)
        180 -> Point2(w - p.x, h - p.y)
        270 -> Point2(p.y, w - p.x)
        else -> p
    }
}
