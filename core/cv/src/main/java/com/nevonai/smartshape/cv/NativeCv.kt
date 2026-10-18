package com.nevonai.smartshape.cv

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Raw JNI surface of libsmartshape_cv. Prefer [ShapeDetector] / [MarkerDetector].
 *
 * Shape layout:  [count, (type, confidence, nPoints, x0, y0, … xn, yn) * count]
 * Marker layout: [count, (id, x0, y0, x1, y1, x2, y2, x3, y3) * count]   corners TL, TR, BR, BL
 * Quality:       [sharpness, brightness]
 */
internal object NativeCv {
    init {
        System.loadLibrary("smartshape_cv")
    }

    external fun openCvVersion(): String

    /** [bitmap] must be ARGB_8888. */
    external fun detectShapesBitmap(bitmap: Bitmap, maxDim: Int, refine: Boolean): FloatArray

    /** [buffer] is a direct buffer holding an 8-bit luma plane (e.g. CameraX Y plane). */
    external fun detectShapesLuma(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, maxDim: Int): FloatArray

    external fun detectMarkersBitmap(bitmap: Bitmap): FloatArray

    external fun qualityLuma(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int): FloatArray
}
