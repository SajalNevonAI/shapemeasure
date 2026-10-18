package com.nevonai.smartshape.data

import android.graphics.Bitmap
import com.nevonai.smartshape.data.db.ScanDao
import com.nevonai.smartshape.data.db.ScanEntity
import com.nevonai.smartshape.model.Calibration
import com.nevonai.smartshape.model.DetectedShape
import com.nevonai.smartshape.model.OcrResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val dao: ScanDao,
    private val images: ImageStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observe(filter: ScanFilter): Flow<List<Scan>> {
        val match = ftsQuery(filter.query)
        val source = if (match == null) dao.observeActive() else dao.search(match)
        return source.map { rows ->
            rows.asSequence()
                .filter { !filter.favoritesOnly || it.favorite }
                .filter { filter.collection == null || it.collection == filter.collection }
                .map(::toScan)
                .toList()
                .let { sort(it, filter.sort) }
        }
    }

    fun observeDeleted(): Flow<List<Scan>> = dao.observeDeleted().map { it.map(::toScan) }

    fun observe(id: Long): Flow<Scan?> = dao.observe(id).map { it?.let(::toScan) }

    suspend fun get(id: Long): Scan? = dao.get(id)?.let(::toScan)

    suspend fun create(draft: ScanDraft, image: Bitmap): Long {
        val stored = images.save(image)
        val now = System.currentTimeMillis()
        return dao.insert(
            ScanEntity(
                title = draft.title.ifBlank { defaultTitle(draft) },
                createdAt = now,
                updatedAt = now,
                imagePath = stored.imagePath,
                thumbnailPath = stored.thumbnailPath,
                imageWidth = image.width,
                imageHeight = image.height,
                notes = draft.notes,
                tags = normalizeTags(draft.tags),
                collection = draft.collection,
                shapeTypes = shapeTypes(draft.shapes),
                ocrText = draft.ocr?.text.orEmpty(),
                calibrationJson = json.encodeToString(draft.calibration),
                shapesJson = json.encodeToString(draft.shapes),
                ocrJson = draft.ocr?.let { json.encodeToString(it) },
            ),
        )
    }

    /** Saves edits to an existing scan (re-opened from history). */
    suspend fun update(id: Long, draft: ScanDraft) {
        val e = dao.get(id) ?: return
        dao.update(
            e.copy(
                title = draft.title.ifBlank { e.title },
                updatedAt = System.currentTimeMillis(),
                notes = draft.notes,
                tags = normalizeTags(draft.tags),
                collection = draft.collection,
                shapeTypes = shapeTypes(draft.shapes),
                ocrText = draft.ocr?.text.orEmpty(),
                calibrationJson = json.encodeToString(draft.calibration),
                shapesJson = json.encodeToString(draft.shapes),
                ocrJson = draft.ocr?.let { json.encodeToString(it) },
            ),
        )
    }

    suspend fun duplicate(id: Long): Long? {
        val e = dao.get(id) ?: return null
        val copy = images.copy(ImageStore.Stored(e.imagePath, e.thumbnailPath))
        val now = System.currentTimeMillis()
        return dao.insert(e.copy(id = 0, title = "${e.title} (copy)", createdAt = now, updatedAt = now, imagePath = copy.imagePath, thumbnailPath = copy.thumbnailPath, favorite = false))
    }

    suspend fun rename(id: Long, title: String) = dao.rename(id, title.trim(), System.currentTimeMillis())
    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite, System.currentTimeMillis())
    suspend fun delete(id: Long) = dao.softDelete(id, System.currentTimeMillis())
    suspend fun restore(id: Long) = dao.restore(id)

    /** Permanently removes scans that have been in Recently deleted longer than [retentionMs]. */
    suspend fun purgeDeleted(retentionMs: Long = 30L * 24 * 60 * 60 * 1000) {
        dao.deletedBefore(System.currentTimeMillis() - retentionMs).forEach {
            images.delete(it.imagePath, it.thumbnailPath)
            dao.purge(it.id)
        }
    }

    private fun toScan(e: ScanEntity) = Scan(
        id = e.id,
        title = e.title,
        createdAt = e.createdAt,
        updatedAt = e.updatedAt,
        imagePath = e.imagePath,
        thumbnailPath = e.thumbnailPath,
        imageWidth = e.imageWidth,
        imageHeight = e.imageHeight,
        favorite = e.favorite,
        notes = e.notes,
        tags = e.tags.split(' ').filter { it.isNotBlank() },
        collection = e.collection,
        calibration = runCatching { json.decodeFromString<Calibration>(e.calibrationJson) }.getOrDefault(Calibration.None),
        shapes = runCatching { json.decodeFromString<List<DetectedShape>>(e.shapesJson) }.getOrDefault(emptyList()),
        ocr = e.ocrJson?.let { runCatching { json.decodeFromString<OcrResult>(it) }.getOrNull() },
        deletedAt = e.deletedAt,
    )

    private fun sort(list: List<Scan>, sort: ScanSort) = when (sort) {
        ScanSort.RECENT -> list.sortedByDescending { it.createdAt }
        ScanSort.OLDEST -> list.sortedBy { it.createdAt }
        ScanSort.TITLE -> list.sortedBy { it.title.lowercase() }
        ScanSort.SHAPE -> list.sortedWith(compareBy<Scan> { it.shapes.firstOrNull()?.type?.ordinal ?: Int.MAX_VALUE }.thenByDescending { it.createdAt })
    }

    private fun defaultTitle(draft: ScanDraft): String {
        val first = draft.shapes.firstOrNull()?.type?.label
        val text = draft.ocr?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(40)
        return text ?: first?.let { "$it scan" } ?: "Scan"
    }

    companion object {
        /** Turns free text into an FTS4 prefix query ("blue lab" → "blue* lab*"); null when blank. */
        fun ftsQuery(query: String): String? {
            val tokens = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            return if (tokens.isEmpty()) null else tokens.joinToString(" ") { "$it*" }
        }

        fun normalizeTags(tags: List<String>) =
            tags.map { it.trim().lowercase().replace(Regex("\\s+"), "-") }.filter { it.isNotEmpty() }.distinct().joinToString(" ")

        private fun shapeTypes(shapes: List<DetectedShape>) = shapes.map { it.type.name.lowercase() }.distinct().joinToString(" ")
    }
}
