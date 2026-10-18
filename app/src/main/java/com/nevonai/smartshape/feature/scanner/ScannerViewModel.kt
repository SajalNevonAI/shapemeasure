package com.nevonai.smartshape.feature.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nevonai.smartshape.cv.FrameQuality
import com.nevonai.smartshape.cv.ShapeDetector
import com.nevonai.smartshape.data.AppSettings
import com.nevonai.smartshape.data.SettingsRepository
import com.nevonai.smartshape.model.DetectedShape
import com.nevonai.smartshape.model.Point2
import com.nevonai.smartshape.model.Polygons
import com.nevonai.smartshape.util.BitmapIO
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _live = MutableStateFlow(LiveFrame())
    val live: StateFlow<LiveFrame> = _live.asStateFlow()

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
            // One object per frame: show only the largest outline in the live overlay.
            val shapes = ShapeDetector.detectLuma(plane.buffer, image.width, image.height, plane.rowStride)
                .maxByOrNull { Polygons.area(it.points) }
                ?.let(::listOf)
                .orEmpty()
            val quality = if (frameCount++ % 4 == 0) ShapeDetector.quality(plane.buffer, image.width, image.height, plane.rowStride) else _live.value.quality
            val rot = image.imageInfo.rotationDegrees
            val (w, h) = if (rot == 90 || rot == 270) image.height to image.width else image.width to image.height
            _live.value = LiveFrame(shapes.map { s -> s.copy(points = s.points.map { rotate(it, rot, image.width, image.height) }) }, w, h, quality)
        }
    }

    fun setBusy(busy: Boolean) = _busy.update { busy }

    suspend fun persistCapture(bitmap: Bitmap): String = BitmapIO.writeCache(context, bitmap)

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
