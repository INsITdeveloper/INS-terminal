package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.InsViewModel
import com.example.ui.theme.*

@Composable
fun TerminalCameraViewfinder(
    viewModel: InsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lensFacing by viewModel.cameraLensFacing.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val lastPhotoPath by viewModel.lastCapturedPhotoPath.collectAsState()
    val isGestureTracking by viewModel.isGestureTrackingActive.collectAsState()
    val visionScript by viewModel.activeVisionScript.collectAsState()
    val currentGesture by viewModel.currentGestureLabel.collectAsState()
    val gestureConfidence by viewModel.gestureConfidence.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vision_scan")
    val scanPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_phase"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.2.dp, if (isGestureTracking) SleekMint else SleekBorder), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SleekContainerDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isGestureTracking) SleekMint else SleekAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGestureTracking) "OPENCV VISION STREAM [cv2.imshow: $visionScript]"
                        else "LIVE CAMERA FEED (/dev/video0 -> Camera2 HAL)",
                        color = SleekTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = {
                        if (isGestureTracking) viewModel.closeGestureCamera()
                        else viewModel.toggleCameraPreview()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Viewfinder",
                        tint = SleekTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(BorderStroke(0.8.dp, SleekBorderSubtle), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val selector = CameraSelector.Builder()
                                        .requireLensFacing(lensFacing)
                                        .build()

                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview
                                    )
                                } catch (e: Exception) {
                                    Log.e("CameraViewfinder", "Camera binding error", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        update = { previewView ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val selector = CameraSelector.Builder()
                                        .requireLensFacing(lensFacing)
                                        .build()

                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        selector,
                                        preview
                                    )
                                } catch (e: Exception) {
                                    Log.e("CameraViewfinder", "Camera re-bind error", e)
                                }
                            }, ContextCompat.getMainExecutor(context))
                        },
                        onRelease = {
                            try {
                                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                                cameraProvider.unbindAll()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = SleekCoral,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Izin Kamera Diperlukan",
                            color = SleekCoral,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = SleekContainerSlate),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Beri Izin Kamera", fontSize = 11.sp, color = SleekIceBlue)
                        }
                    }
                }

                if (hasCameraPermission) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        val boxW = w * 0.55f
                        val boxH = h * 0.65f
                        val left = (w - boxW) / 2f
                        val top = (h - boxH) / 2f

                        val cornerLen = 22f
                        val bracketColor = if (isGestureTracking) Color(0xFF00FFB2) else Color(0x995CE1E6)
                        val strokeW = 3.5f

                        drawLine(bracketColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
                        drawLine(bracketColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)

                        drawLine(bracketColor, Offset(left + boxW, top), Offset(left + boxW - cornerLen, top), strokeW)
                        drawLine(bracketColor, Offset(left + boxW, top), Offset(left + boxW, top + cornerLen), strokeW)

                        drawLine(bracketColor, Offset(left, top + boxH), Offset(left + cornerLen, top + boxH), strokeW)
                        drawLine(bracketColor, Offset(left, top + boxH), Offset(left, top + boxH - cornerLen), strokeW)

                        drawLine(bracketColor, Offset(left + boxW, top + boxH), Offset(left + boxW - cornerLen, top + boxH), strokeW)
                        drawLine(bracketColor, Offset(left + boxW, top + boxH), Offset(left + boxW, top + boxH - cornerLen), strokeW)

                        if (isGestureTracking) {
                            val centerX = w / 2f
                            val centerY = h / 2f
                            val landmarkOffsets = listOf(
                                Offset(centerX, centerY + 50f),
                                Offset(centerX - 40f, centerY + 20f), Offset(centerX - 60f, centerY - 10f), Offset(centerX - 75f, centerY - 35f),
                                Offset(centerX - 25f, centerY - 30f), Offset(centerX - 30f, centerY - 65f), Offset(centerX - 32f, centerY - 95f),
                                Offset(centerX, centerY - 35f), Offset(centerX, centerY - 75f), Offset(centerX, centerY - 105f),
                                Offset(centerX + 25f, centerY - 30f), Offset(centerX + 28f, centerY - 65f), Offset(centerX + 30f, centerY - 92f),
                                Offset(centerX + 45f, centerY - 15f), Offset(centerX + 52f, centerY - 45f), Offset(centerX + 58f, centerY - 70f)
                            )

                            for (i in 0 until landmarkOffsets.size - 1) {
                                drawLine(
                                    color = Color(0x8800FFB2),
                                    start = landmarkOffsets[i],
                                    end = landmarkOffsets[i + 1],
                                    strokeWidth = 2f
                                )
                            }

                            landmarkOffsets.forEach { pt ->
                                drawCircle(
                                    color = Color(0xFF00FFB2),
                                    radius = 4.5f,
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2f,
                                    center = pt
                                )
                            }

                            val scanY = top + (boxH * scanPhase)
                            drawLine(
                                color = Color(0xCC00FFB2),
                                start = Offset(left, scanY),
                                end = Offset(left + boxW, scanY),
                                strokeWidth = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(BorderStroke(1.dp, SleekMint), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text(
                                text = "DETEKSI: $currentGesture",
                                color = SleekMint,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Akurasi: ${String.format("%.1f", gestureConfidence)}% | 60 FPS",
                                color = SleekTextPrimary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "cv2.imshow 640x480",
                            color = SleekIceBlue,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { viewModel.captureCameraPhoto() },
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("camera_shutter_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekMint.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, SleekMint),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Take Photo",
                            tint = SleekMint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "FOTO",
                            color = SleekMint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { viewModel.cycleSimulatedGesture() },
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("camera_cycle_gesture_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SleekIceBlue.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, SleekIceBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = SleekIceBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GESTURE",
                            color = SleekIceBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {

                    IconButton(
                        onClick = { viewModel.toggleTorch() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isTorchOn) SleekAmber.copy(alpha = 0.2f) else SleekSurfaceCard)
                            .border(BorderStroke(1.dp, if (isTorchOn) SleekAmber else SleekBorder), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Torch",
                            tint = if (isTorchOn) SleekAmber else SleekTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { viewModel.switchCameraLens() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SleekSurfaceCard)
                            .border(BorderStroke(1.dp, SleekBorder), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Lens",
                            tint = SleekIceBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (lastPhotoPath != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Disimpan di: $lastPhotoPath",
                    color = SleekMint,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
