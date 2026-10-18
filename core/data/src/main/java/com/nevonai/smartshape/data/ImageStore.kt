package com.nevonai.smartshape.data

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** Scan images live in app-private storage; only paths go into the database. */
@Singleton
class ImageStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir get() = File(context.filesDir, "scans").apply { mkdirs() }

    data class Stored(val imagePath: String, val thumbnailPath: String)

    suspend fun save(bitmap: Bitmap): Stored = withContext(Dispatchers.IO) {
        val name = UUID.randomUUID().toString()
        val image = File(dir, "$name.jpg")
        val thumb = File(dir, "${name}_thumb.jpg")
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val s = THUMB_SIZE.toFloat() / max(bitmap.width, bitmap.height)
        val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).roundToInt().coerceAtLeast(1), (bitmap.height * s).roundToInt().coerceAtLeast(1), true)
        thumb.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (small !== bitmap) small.recycle()
        Stored(image.absolutePath, thumb.absolutePath)
    }

    suspend fun copy(stored: Stored): Stored = withContext(Dispatchers.IO) {
        val name = UUID.randomUUID().toString()
        val image = File(stored.imagePath).copyTo(File(dir, "$name.jpg"))
        val thumb = File(stored.thumbnailPath).copyTo(File(dir, "${name}_thumb.jpg"))
        Stored(image.absolutePath, thumb.absolutePath)
    }

    suspend fun delete(vararg paths: String) = withContext(Dispatchers.IO) {
        paths.forEach { File(it).delete() }
    }

    private companion object {
        const val THUMB_SIZE = 480
    }
}
