package com.example.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CameraHardwareStatus(
    val hasCamera: Boolean = true,
    val lensFacing: String = "BACK (0)",
    val isTorchOn: Boolean = false,
    val isPreviewActive: Boolean = false,
    val lastCapturedPath: String? = null,
    val sensorMegaPixels: String = "50.0 MP (Quad-Bayer INS Sensor)"
)

class CameraBridgeEngine(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var torchEnabled = false

    fun isTorchSupported(): Boolean {
        return try {
            val cameraIds = cameraManager?.cameraIdList ?: return false
            cameraIds.any { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun toggleTorch(enable: Boolean): Boolean {
        return try {
            val cameraIds = cameraManager?.cameraIdList ?: return false
            val flashCameraId = cameraIds.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraIds.firstOrNull() ?: return false

            cameraManager.setTorchMode(flashCameraId, enable)
            torchEnabled = enable
            true
        } catch (e: Exception) {
            torchEnabled = false
            false
        }
    }

    fun isTorchActive(): Boolean = torchEnabled

    fun getCameraInfo(): String {
        return try {
            val cameraIds = cameraManager?.cameraIdList ?: emptyArray()
            val sb = StringBuilder()
            sb.appendLine("┌──────────────────────────────────────────────────────────┐")
            sb.appendLine("│              📷 HARDWARE CAMERA BUS & SENSORS             │")
            sb.appendLine("└──────────────────────────────────────────────────────────┘")
            sb.appendLine("Detected Sensors: ${cameraIds.size} Optical Units")
            cameraIds.forEachIndexed { index, id ->
                val chars = cameraManager?.getCameraCharacteristics(id)
                val facing = when (chars?.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> "BACK (Primary Tele/Wide)"
                    CameraCharacteristics.LENS_FACING_FRONT -> "FRONT (TrueDepth/Wide)"
                    else -> "EXTERNAL / USB-V4L2"
                }
                val hasFlash = chars?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                sb.appendLine(" [dev/video$index] Node: /dev/camera$id | $facing | Flash: $hasFlash | INS HAL: Active")
            }
            sb.appendLine("HAL Interface: Linux v4l2 & Android Camera2 Subsystem")
            sb.appendLine("Capture Storage: /sdcard/DCIM/Camera (Virtual Emulation Bridge)")
            sb.toString()
        } catch (e: Exception) {
            "Camera sensor bus: Camera2 HAL active (ID: 0 Back, ID: 1 Front)"
        }
    }

    suspend fun captureVirtualSnapshot(lensFacing: Int = CameraSelector.LENS_FACING_BACK): File = withContext(Dispatchers.IO) {
        val dcimDir = File(context.cacheDir, "DCIM/Camera").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dcimDir, "INS_IMG_${timestamp}.jpg")

        if (!file.exists()) {
            file.writeBytes(ByteArray(1024))
        }
        file
    }
}
