package com.nevonai.smartshape.model

/**
 * Corrects the autofocus distance a phone reports, which is only "approximate" on most devices.
 * Focus works in diopters (1 / metres), and the error is close to linear there:
 * 1 / trueDistance = [scale] / reportedDistance + [offsetDiopters].
 */
data class FocusCorrection(val scale: Double, val offsetDiopters: Double) {

    fun trueDistanceMm(reportedMm: Double): Double? {
        if (reportedMm <= 0) return null
        val diopters = scale * 1000.0 / reportedMm + offsetDiopters
        return if (diopters > 0) 1000.0 / diopters else null
    }

    companion object {
        val Identity = FocusCorrection(1.0, 0.0)

        /**
         * Per-model fits from photos of an object of known size at several distances.
         * Galaxy S23 Ultra (SM-S918x) main camera: 75 mm box at 15–31 cm, residuals within ±3.5 %.
         */
        fun forDevice(model: String): FocusCorrection = when {
            model.startsWith("SM-S918") -> FocusCorrection(1.1254, -1.5771)
            else -> Identity
        }
    }
}
