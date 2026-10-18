package com.nevonai.smartshape.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nevonai.smartshape.model.BoxF
import com.nevonai.smartshape.model.OcrBlock
import com.nevonai.smartshape.model.OcrLine
import com.nevonai.smartshape.model.OcrResult
import com.nevonai.smartshape.model.OcrWord
import kotlinx.coroutines.tasks.await

/**
 * On-device printed-text recognition (Latin script) using the bundled ML Kit model, so it works
 * offline from first launch. Boxes are in the pixel space of the bitmap passed in.
 */
class TextRecognizer : AutoCloseable {

    private val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        val text = client.process(InputImage.fromBitmap(bitmap, 0)).await()
        return text.toResult(bitmap.width, bitmap.height)
    }

    override fun close() = client.close()

    private fun Text.toResult(width: Int, height: Int) = OcrResult(
        text = text,
        blocks = textBlocks.map { b ->
            OcrBlock(
                text = b.text,
                box = b.boundingBox.toBox(),
                lines = b.lines.map { l ->
                    OcrLine(
                        text = l.text,
                        box = l.boundingBox.toBox(),
                        words = l.elements.map { e -> OcrWord(e.text, e.boundingBox.toBox()) },
                    )
                },
            )
        },
        imageWidth = width,
        imageHeight = height,
    )

    private fun Rect?.toBox() = this?.let { BoxF(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) } ?: BoxF(0f, 0f, 0f, 0f)
}
