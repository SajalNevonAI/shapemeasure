package com.nevonai.smartshape.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.nevonai.smartshape.model.BoxF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Zoom and pan for an image-sized child. The child is centred in the container and scaled about
 * its centre, so [offset] is the shift of that centre in container pixels.
 */
@Stable
class ZoomState internal constructor(private val scope: CoroutineScope) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    internal var container by mutableStateOf(IntSize.Zero)
    internal var child by mutableStateOf(IntSize.Zero)
    private var animation: Job? = null

    val isZoomed get() = scale > 1.01f

    private val centre get() = Offset(container.width / 2f, container.height / 2f)

    /** Keeps the scaled child covering the container where it can, so it never drifts off screen. */
    private fun clamp(s: Float, o: Offset): Offset {
        val mx = max(0f, (child.width * s - container.width) / 2f)
        val my = max(0f, (child.height * s - container.height) / 2f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    /** Offset that keeps the content under [anchor] (container px) fixed while scaling to [s]. */
    private fun anchored(anchor: Offset, s: Float): Offset {
        val a = anchor - centre
        return a - (a - offset) * (s / scale)
    }

    private fun animateTo(s: Float, o: Offset) {
        val target = clamp(s, o)
        val fromS = scale
        val fromO = offset
        animation?.cancel()
        animation = scope.launch {
            animate(0f, 1f, animationSpec = tween(280)) { t, _ ->
                scale = fromS + (s - fromS) * t
                offset = fromO + (target - fromO) * t
            }
        }
    }

    internal fun gesture(centroid: Offset, pan: Offset, zoom: Float) {
        animation?.cancel()
        val s = (scale * zoom).coerceIn(MIN, MAX)
        val o = clamp(s, anchored(centroid, s) + pan)
        scale = s
        offset = o
    }

    fun zoomBy(factor: Float, anchor: Offset = centre) {
        val s = (scale * factor).coerceIn(MIN, MAX)
        animateTo(s, anchored(anchor, s))
    }

    fun reset() = animateTo(MIN, Offset.Zero)

    /** Zooms so [box] (image px) fills most of the view, centred. */
    fun focusOn(box: BoxF, imageWidth: Int) {
        if (child.width == 0 || imageWidth <= 0) return
        val k = child.width / imageWidth.toFloat()
        val bw = max(1f, box.width * k)
        val bh = max(1f, box.height * k)
        val s = min(MAX, max(1.5f, min(container.width * 0.8f / bw, container.height * 0.5f / bh)))
        val cx = (box.left + box.width / 2) * k - child.width / 2f
        val cy = (box.top + box.height / 2) * k - child.height / 2f
        animateTo(s, Offset(-cx * s, -cy * s))
    }

    private companion object {
        const val MIN = 1f
        const val MAX = 6f
    }
}

@Composable
fun rememberZoomState(): ZoomState {
    val scope = rememberCoroutineScope()
    return remember { ZoomState(scope) }
}

/**
 * Pinch to zoom, drag to pan and double-tap to zoom in / reset, while [enabled]. When disabled the
 * view springs back to fit so other gestures on the child (e.g. corner dragging) work unscaled.
 */
@Composable
fun ZoomableContent(
    state: ZoomState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(enabled) { if (!enabled) state.reset() }
    Box(
        modifier
            .onSizeChanged { state.container = it }
            .clipToBounds()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onDoubleTap = { p -> if (state.isZoomed) state.reset() else state.zoomBy(2.5f, p) })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTransformGestures { centroid, pan, zoom, _ -> state.gesture(centroid, pan, zoom) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .onSizeChanged { state.child = it }
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                },
        ) { content() }
    }
}
