package com.nevonai.smartshape.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Grid3x3
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nevonai.smartshape.ui.theme.overlayColor
import com.nevonai.smartshape.util.BitmapIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ScannerScreen(onCaptured: (String) -> Unit, onClose: () -> Unit, vm: ScannerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        asked = true
    }
    val scope = rememberCoroutineScope()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            vm.setBusy(true)
            val path = vm.persistImport(uri)
            vm.setBusy(false)
            path?.let(onCaptured)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraContent(vm, onCaptured, onPickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
        } else {
            PermissionPrompt(
                deniedBefore = asked,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onImport = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
        FilledTonalIconButton(
            onClick = onClose,
            modifier = Modifier.safeDrawingPadding().padding(12.dp).align(Alignment.TopStart),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
        ) { Icon(Icons.Rounded.Close, contentDescription = "Close scanner") }

        if (busy) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun CameraContent(vm: ScannerViewModel, onCaptured: (String) -> Unit, onPickImage: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()

    var showGrid by remember(settings.showGrid) { mutableStateOf(settings.showGrid) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val ratio = remember {
        ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(ratio.build())
            .build()
    }
    LaunchedEffect(flashMode) { imageCapture.flashMode = flashMode }

    DisposableEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        val job = scope.launch {
            val p = ProcessCameraProvider.awaitInstance(context)
            provider = p
            val preview = Preview.Builder().setResolutionSelector(ratio.build()).build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(vm.analysisExecutor, vm::analyze) }
            p.unbindAll()
            camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
        }
        onDispose {
            job.cancel()
            provider?.unbindAll()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView({ previewView }, Modifier.fillMaxSize())
            LiveOverlay(live, Modifier.fillMaxSize())
            if (showGrid) GridOverlay(Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures { o ->
                            val cam = camera ?: return@detectTapGestures
                            val point = previewView.meteringPointFactory.createPoint(o.x, o.y)
                            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                            focusPoint = o
                        }
                    },
            )
            focusPoint?.let { FocusRing(it) { focusPoint = null } }
            HintChip(live, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 16.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleControl(onClick = onPickImage) { Icon(Icons.Rounded.PhotoLibrary, "Import from gallery", tint = Color.White) }
            ShutterButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.setBusy(true)
                imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = image.use { BitmapIO.rotate(it.toBitmap(), it.imageInfo.rotationDegrees) }
                        scope.launch {
                            val path = vm.persistCapture(bitmap)
                            vm.setBusy(false)
                            onCaptured(path)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        vm.setBusy(false)
                    }
                })
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleControl(onClick = { showGrid = !showGrid }) {
                    Icon(Icons.Rounded.Grid3x3, "Toggle grid", tint = if (showGrid) MaterialTheme.colorScheme.secondary else Color.White)
                }
                CircleControl(onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }) {
                    val icon = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                        ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                        else -> Icons.Rounded.FlashOff
                    }
                    Icon(icon, "Flash mode", tint = Color.White)
                }
            }
        }
    }
}

/** Live outlines in analysis-frame coordinates, mapped like PreviewView's FILL_CENTER. */
@Composable
private fun LiveOverlay(live: LiveFrame, modifier: Modifier) {
    val phase by rememberInfiniteTransition(label = "ants").animateFloat(0f, 40f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        if (live.width == 0) return@Canvas
        val s = max(size.width / live.width, size.height / live.height)
        val dx = (size.width - live.width * s) / 2
        val dy = (size.height - live.height * s) / 2
        live.shapes.forEach { shape ->
            val path = Path().apply {
                shape.points.forEachIndexed { i, p ->
                    val x = p.x.toFloat() * s + dx
                    val y = p.y.toFloat() * s + dy
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            val color = shape.type.overlayColor()
            drawPath(path, color.copy(alpha = 0.12f))
            drawPath(path, color, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 18f), phase)))
        }
    }
}

@Composable
private fun GridOverlay(modifier: Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = 0.22f)
        val w = 1.dp.toPx()
        for (i in 1..2) {
            drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), w)
            drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), w)
        }
    }
}

@Composable
private fun FocusRing(at: Offset, onDone: () -> Unit) {
    LaunchedEffect(at) {
        delay(900)
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Color.White, 34.dp.toPx(), at, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color(0xFFFFD54F), 4.dp.toPx(), at)
    }
}

@Composable
private fun HintChip(live: LiveFrame, modifier: Modifier) {
    val q = live.quality
    val (text, warn) = when {
        q?.isDark == true -> "More light needed" to true
        q?.isOverexposed == true -> "Too bright – reduce glare" to true
        q?.isBlurry == true -> "Hold steady" to true
        live.shapes.isEmpty() -> "Point at an object on a flat surface" to false
        live.shapes.size == 1 -> "${live.shapes[0].type.label} detected" to false
        else -> "${live.shapes.size} shapes detected" to false
    }
    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(shape = CircleShape, color = if (warn) Color(0xE6B45309) else Color.Black.copy(alpha = 0.55f)) {
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .background(Color.White, CircleShape)
            .clickable(onClickLabel = "Capture photo", onClick = onClick),
    )
}

@Composable
private fun CircleControl(onClick: () -> Unit, content: @Composable () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.14f)),
    ) { content() }
}

@Composable
private fun PermissionPrompt(deniedBefore: Boolean, onRequest: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).aspectRatio(1f).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PhotoCamera, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("Camera access", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            if (deniedBefore) "Camera permission was denied. You can allow it in system settings, or import a photo instead."
            else "SmartShape uses the camera to detect shapes and measure objects. Photos stay on your device.",
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Allow camera") }
        TextButton(onClick = onImport) { Text("Import a photo instead") }
    }
}
