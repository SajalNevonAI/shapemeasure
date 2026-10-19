package com.nevonai.smartshape.feature.editor

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nevonai.smartshape.cv.MarkerDetector
import com.nevonai.smartshape.cv.ShapeDetector
import com.nevonai.smartshape.data.ScanDraft
import com.nevonai.smartshape.data.ScanRepository
import com.nevonai.smartshape.data.SettingsRepository
import com.nevonai.smartshape.model.BoxF
import com.nevonai.smartshape.model.Calibration
import com.nevonai.smartshape.model.CalibrationSource
import com.nevonai.smartshape.model.DetectedShape
import com.nevonai.smartshape.model.Formats
import com.nevonai.smartshape.model.Homography
import com.nevonai.smartshape.model.LengthUnit
import com.nevonai.smartshape.model.MeasurementEngine
import com.nevonai.smartshape.model.OcrResult
import com.nevonai.smartshape.model.Point2
import com.nevonai.smartshape.model.Polygons
import com.nevonai.smartshape.model.ShapeMeasurement
import com.nevonai.smartshape.model.ShapeEditing
import com.nevonai.smartshape.model.ShapeType
import com.nevonai.smartshape.model.TextMatch
import com.nevonai.smartshape.model.TextSearch
import com.nevonai.smartshape.model.primary
import com.nevonai.smartshape.ocr.TextRecognizer
import com.nevonai.smartshape.ui.navigation.Editor
import com.nevonai.smartshape.util.BitmapIO
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.nevonai.smartshape.model.FocusCorrection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class EditorTab { MEASURE, TEXT }

data class EditorState(
    val loading: Boolean = true,
    val error: String? = null,
    val image: Bitmap? = null,
    val scanId: Long? = null,
    val title: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val shapes: List<DetectedShape> = emptyList(),
    val selectedId: Int? = null,
    val calibration: Calibration = Calibration.None,
    val referenceShapeId: Int? = null,
    val referenceSizeMm: Pair<Double, Double>? = null,
    val markerCorners: List<Point2>? = null,
    val markerCalibration: Calibration? = null,
    val unit: LengthUnit = LengthUnit.MM,
    val ocr: OcrResult? = null,
    val ocrRunning: Boolean = false,
    val tab: EditorTab = EditorTab.MEASURE,
    val query: String = "",
    val matches: List<TextMatch> = emptyList(),
    val activeMatch: Int = 0,
    val saving: Boolean = false,
    /** User is drawing a box around the sticker on the image. */
    val marking: Boolean = false,
    /** Manual marking draws a circle/ellipse inside the dragged box instead of a rectangle. */
    val markRound: Boolean = false,
    /** OCR line the user tapped; the photo zooms to and highlights it. */
    val focusBox: BoxF? = null,
    val dirty: Boolean = false,
) {
    val measurements: Map<Int, ShapeMeasurement> = shapes.associate { it.id to MeasurementEngine.measure(it, calibration) }
    val selected: DetectedShape? get() = shapes.firstOrNull { it.id == selectedId }
}

sealed interface EditorEvent {
    data class Saved(val id: Long) : EditorEvent
    data class Message(val text: String) : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val scans: ScanRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Editor>()
    private val _state = MutableStateFlow(EditorState(tab = if (route.startOnText) EditorTab.TEXT else EditorTab.MEASURE))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val recognizer = TextRecognizer()

    init {
        viewModelScope.launch {
            val unit = settings.settings.first().unit
            _state.update { it.copy(unit = unit) }
            if (route.scanId != null) loadSaved(route.scanId) else loadNew(route.imagePath)
        }
    }

    private suspend fun loadSaved(id: Long) {
        val scan = scans.get(id) ?: return fail("This scan no longer exists.")
        val image = BitmapIO.load(scan.imagePath) ?: return fail("The scan image could not be opened.")
        _state.update {
            it.copy(
                loading = false, image = image, scanId = id, title = scan.title, notes = scan.notes, tags = scan.tags,
                shapes = scan.shapes, calibration = scan.calibration, ocr = scan.ocr,
            )
        }
        if (scan.ocr == null) runOcr()
        if (_state.value.query.isNotBlank()) search(_state.value.query)
    }

    private suspend fun loadNew(path: String?) {
        val image = path?.let { BitmapIO.load(it) } ?: return fail("The image could not be opened.")
        val (shapes, markers) = withContext(Dispatchers.Default) {
            val markers = MarkerDetector.detect(image)
            // One photo = one measured object: the outline nearest the centre, where the user aimed.
            val shapes = MarkerDetector.withoutMarkers(ShapeDetector.detect(image), markers)
                .primary(image.width, image.height)
                ?.let { listOf(it.copy(id = 1)) }
                .orEmpty()
            shapes to markers
        }
        val markerCal = MarkerDetector.calibrationFrom(markers)
        val s = settings.settings.first()
        // Prefer the autofocus distance saved with the photo; fall back to the fixed distance setting.
        val focusMm = withContext(Dispatchers.IO) {
            runCatching { ExifInterface(path!!).getAttributeDouble(ExifInterface.TAG_SUBJECT_DISTANCE, -1.0) * 1000 }.getOrDefault(-1.0)
        }.takeIf { it in 50.0..3000.0 }?.let { FocusCorrection.forDevice(Build.MODEL).trueDistanceMm(it) }
        val distanceCal = s.focalNorm?.let { f ->
            if (focusMm != null) MeasurementEngine.fromCameraDistance(focusMm, f, maxOf(image.width, image.height), "away (autofocus)")
            else MeasurementEngine.fromCameraDistance(s.cameraDistanceMm, f, maxOf(image.width, image.height))
        }
        _state.update {
            it.copy(
                loading = false, image = image, shapes = shapes, selectedId = shapes.firstOrNull()?.id,
                markerCorners = markers.maxByOrNull { m -> Polygons.area(m.corners) }?.corners,
                markerCalibration = markerCal, calibration = markerCal ?: distanceCal ?: Calibration.None, dirty = true,
            )
        }
        if (markerCal != null) _events.send(EditorEvent.Message("Calibration marker found – perspective corrected"))
        runOcr()
    }

    private fun fail(message: String) = _state.update { it.copy(loading = false, error = message) }

    // Marking only applies to the Measure tab; leaving it cancels an unfinished mark.
    fun selectTab(tab: EditorTab) = _state.update { it.copy(tab = tab, marking = it.marking && tab == EditorTab.MEASURE) }
    fun select(id: Int?) = _state.update { it.copy(selectedId = id) }
    fun setUnit(unit: LengthUnit) = _state.update { it.copy(unit = unit) }
    fun setTitle(title: String) = _state.update { it.copy(title = title, dirty = true) }
    fun setNotes(notes: String) = _state.update { it.copy(notes = notes, dirty = true) }
    fun setTags(tags: List<String>) = _state.update { it.copy(tags = tags, dirty = true) }

    /** Hand edit of an outline (corner, side, resize or move); the shape is re-labelled to match its new form. */
    fun editShape(shapeId: Int, points: List<Point2>) = _state.update { s ->
        val shapes = s.shapes.map { shape ->
            if (shape.id != shapeId) shape else shape.copy(points = points, type = ShapeEditing.retype(shape.type, points))
        }
        val calibration = if (shapeId == s.referenceShapeId) recalibrateReference(s, shapes.first { it.id == shapeId }) ?: s.calibration else s.calibration
        s.copy(shapes = shapes, calibration = calibration, dirty = true)
    }

    fun deleteShape(id: Int) = _state.update { s ->
        val wasReference = s.referenceShapeId == id
        s.copy(
            shapes = s.shapes.filterNot { it.id == id },
            selectedId = null,
            referenceShapeId = if (wasReference) null else s.referenceShapeId,
            calibration = if (wasReference) Calibration.None else s.calibration,
            dirty = true,
        )
    }

    fun focusText(box: BoxF?) = _state.update { it.copy(focusBox = box) }

    fun startMarking() = _state.update { it.copy(marking = true, tab = EditorTab.MEASURE) }
    fun cancelMarking() = _state.update { it.copy(marking = false) }
    fun setMarkRound(round: Boolean) = _state.update { it.copy(markRound = round) }

    /**
     * Replaces the detected outline with the sticker the user marked (one object per photo). The
     * calibration is kept: it maps the whole image, not one shape.
     */
    fun markSticker(corners: List<Point2>) = _state.update { s ->
        val e = Polygons.edges(corners)
        val ratio = minOf(e[0], e[1]) / maxOf(e[0], e[1])
        val id = (s.shapes.maxOfOrNull { it.id } ?: 0) + 1
        val shape = if (s.markRound) {
            // Ellipse inscribed in the dragged box, sampled like detected curves.
            val cx = (corners[0].x + corners[2].x) / 2
            val cy = (corners[0].y + corners[2].y) / 2
            val rx = e[0] / 2
            val ry = e[1] / 2
            val pts = ShapeEditing.ellipseIn(cx - rx, cy - ry, cx + rx, cy + ry)
            DetectedShape(id, if (ratio > 0.92) ShapeType.CIRCLE else ShapeType.ELLIPSE, 1f, pts)
        } else {
            DetectedShape(id, if (ratio > 0.95) ShapeType.SQUARE else ShapeType.RECTANGLE, 1f, corners)
        }
        s.copy(shapes = listOf(shape), selectedId = id, referenceShapeId = null, marking = false, dirty = true)
    }

    fun useMarker() = _state.update { s -> s.markerCalibration?.let { s.copy(calibration = it, referenceShapeId = null, dirty = true) } ?: s }

    fun clearCalibration() = _state.update { it.copy(calibration = Calibration.None, referenceShapeId = null, dirty = true) }

    /** Uses a detected 4-corner shape as a reference of known physical size (perspective-correcting). */
    fun useReference(shapeId: Int, widthMm: Double, heightMm: Double, label: String) {
        val shape = _state.value.shapes.firstOrNull { it.id == shapeId } ?: return
        if (shape.points.size != 4) {
            viewModelScope.launch { _events.send(EditorEvent.Message("Pick a four-cornered shape as the reference")) }
            return
        }
        val h = Homography.fromReferenceRect(shape.points, widthMm, heightMm) ?: return
        val note = "$label · ${Formats.number(widthMm, 1)} × ${Formats.number(heightMm, 1)} mm"
        _state.update {
            it.copy(
                calibration = Calibration.of(CalibrationSource.REFERENCE_OBJECT, h, note),
                referenceShapeId = shapeId, referenceSizeMm = widthMm to heightMm, dirty = true,
            )
        }
    }

    /** Scale-only calibration from one known dimension of the selected shape (the first dimension listed). */
    fun useKnownDimension(shapeId: Int, valueInUnit: Double) {
        val shape = _state.value.shapes.firstOrNull { it.id == shapeId } ?: return
        val px = MeasurementEngine.measure(shape, Calibration.None).dimensions.firstOrNull()?.value ?: return
        val mm = _state.value.unit.toMm(valueInUnit)
        if (px < 1 || mm <= 0) return
        val first = MeasurementEngine.measure(shape, Calibration.None).dimensions.first().label
        val note = "${shape.type.label} $first = ${Formats.number(valueInUnit, _state.value.unit.decimals)} ${_state.value.unit.symbol}"
        _state.update { it.copy(calibration = Calibration.of(CalibrationSource.KNOWN_DIMENSION, Homography.scale(mm / px), note), referenceShapeId = null, dirty = true) }
    }

    /** Scale-only calibration from the image resolution (cm = px * 2.54 / ppi). */
    fun usePpi(ppi: Double) {
        val calibration = MeasurementEngine.fromPpi(ppi) ?: return
        _state.update { it.copy(calibration = calibration, referenceShapeId = null, dirty = true) }
    }

    /** Reference-free calibration from the lens-to-object distance; also saved as the new default. */
    fun useCameraDistance(distanceCm: Double) {
        val image = _state.value.image ?: return
        viewModelScope.launch {
            val focal = settings.settings.first().focalNorm
            if (focal == null) {
                _events.send(EditorEvent.Message("Open the camera once so the app can read its lens data"))
                return@launch
            }
            val calibration = MeasurementEngine.fromCameraDistance(distanceCm * 10, focal, maxOf(image.width, image.height)) ?: return@launch
            settings.setCameraDistance(distanceCm * 10)
            _state.update { it.copy(calibration = calibration, referenceShapeId = null, dirty = true) }
        }
    }

    /** Refits the reference homography after the user moves one of the reference's corners. */
    private fun recalibrateReference(s: EditorState, shape: DetectedShape): Calibration? {
        val (w, h) = s.referenceSizeMm ?: return null
        val hm = Homography.fromReferenceRect(shape.points, w, h) ?: return null
        return Calibration.of(CalibrationSource.REFERENCE_OBJECT, hm, s.calibration.note)
    }

    fun runOcr() {
        val image = _state.value.image ?: return
        if (_state.value.ocrRunning) return
        _state.update { it.copy(ocrRunning = true) }
        viewModelScope.launch {
            val result = runCatching { recognizer.recognize(image) }
            _state.update { it.copy(ocrRunning = false, ocr = result.getOrNull() ?: it.ocr, dirty = it.dirty || result.isSuccess) }
            result.exceptionOrNull()?.let { _events.send(EditorEvent.Message("Text recognition failed: ${it.message}")) }
            search(_state.value.query)
        }
    }

    fun search(query: String) = _state.update { s ->
        val matches = s.ocr?.let { TextSearch.find(it, query) } ?: emptyList()
        s.copy(query = query, matches = matches, activeMatch = 0, focusBox = null)
    }

    fun nextMatch(step: Int) = _state.update { s ->
        if (s.matches.isEmpty()) s else s.copy(activeMatch = (s.activeMatch + step).mod(s.matches.size), focusBox = null)
    }

    fun save() {
        val s = _state.value
        val image = s.image ?: return
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val draft = ScanDraft(s.title, s.calibration, s.shapes, s.ocr, s.notes, s.tags)
            val id = runCatching {
                if (s.scanId != null) s.scanId.also { scans.update(it, draft) } else scans.create(draft, image)
            }
            _state.update { it.copy(saving = false, dirty = id.isFailure) }
            id.onSuccess { _events.send(EditorEvent.Saved(it)) }
                .onFailure { _events.send(EditorEvent.Message("Could not save: ${it.message}")) }
        }
    }

    override fun onCleared() {
        recognizer.close()
    }
}
