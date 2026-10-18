package com.nevonai.smartshape.data

import com.nevonai.smartshape.model.Calibration
import com.nevonai.smartshape.model.DetectedShape
import com.nevonai.smartshape.model.MeasurementEngine
import com.nevonai.smartshape.model.OcrResult
import com.nevonai.smartshape.model.ShapeMeasurement

data class Scan(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val imagePath: String,
    val thumbnailPath: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val favorite: Boolean,
    val notes: String,
    val tags: List<String>,
    val collection: String?,
    val calibration: Calibration,
    val shapes: List<DetectedShape>,
    val ocr: OcrResult?,
    val deletedAt: Long?,
) {
    /** Measurements are always recomputed from outlines + calibration, so unit/edit changes stay consistent. */
    val measurements: List<ShapeMeasurement> get() = shapes.map { MeasurementEngine.measure(it, calibration) }
}

/** What the editor hands to the repository when the user taps Save. */
data class ScanDraft(
    val title: String,
    val calibration: Calibration,
    val shapes: List<DetectedShape>,
    val ocr: OcrResult?,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val collection: String? = null,
)

enum class ScanSort(val label: String) {
    RECENT("Most recent"),
    OLDEST("Oldest"),
    TITLE("Title"),
    SHAPE("Shape"),
}

data class ScanFilter(
    val query: String = "",
    val favoritesOnly: Boolean = false,
    val collection: String? = null,
    val sort: ScanSort = ScanSort.RECENT,
)
