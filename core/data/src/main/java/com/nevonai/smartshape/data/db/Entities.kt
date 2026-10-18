package com.nevonai.smartshape.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * One saved scan. Shapes, measurements, calibration and OCR output are stored as JSON documents;
 * the denormalised text columns feed the full-text index.
 */
@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String,
    @ColumnInfo(name = "image_width") val imageWidth: Int,
    @ColumnInfo(name = "image_height") val imageHeight: Int,
    val favorite: Boolean = false,
    val notes: String = "",
    /** Space-separated, lower-case tags. */
    val tags: String = "",
    val collection: String? = null,
    /** Space-separated shape type names, e.g. "RECTANGLE CIRCLE". */
    @ColumnInfo(name = "shape_types") val shapeTypes: String = "",
    @ColumnInfo(name = "ocr_text") val ocrText: String = "",
    @ColumnInfo(name = "calibration_json") val calibrationJson: String,
    @ColumnInfo(name = "shapes_json") val shapesJson: String,
    @ColumnInfo(name = "ocr_json") val ocrJson: String? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Fts4(contentEntity = ScanEntity::class)
@Entity(tableName = "scans_fts")
data class ScanFts(
    val title: String,
    val notes: String,
    val tags: String,
    @ColumnInfo(name = "shape_types") val shapeTypes: String,
    @ColumnInfo(name = "ocr_text") val ocrText: String,
)
