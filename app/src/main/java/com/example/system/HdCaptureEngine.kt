package com.example.system

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.os.Build
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HdCaptureResult(
    val isSuccess: Boolean,
    val message: String,
    val savedPath: String? = null,
    val nativeWidth: Int = 0,
    val nativeHeight: Int = 0,
    val isRecording: Boolean = false
)

/**
 * HD (native-resolution) screen capture.
 *
 * Tujuan: screenshot/recording TIDAK diturunkan resolusinya dan TIDAK dikonversi ke JPEG
 * yang bikin pecah saat di-zoom. Kita pakai `screencap -p` (lossless PNG, resolusi panel penuh)
 * dan `screenrecord` dengan bitrate tinggi untuk video.
 *
 * Capture dijalankan lewat jalur privilege yang sudah ada (root / Shizuku / ADB wireless),
 * karena perintah shell `screencap`/`screenrecord` butuh UID shell.
 */
class HdCaptureEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val privilege = PrivilegeExecutionEngine.getInstance(context)

    @Volatile
    private var recordingJob: Job? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    /** Direktori output di penyimpanan bersama (dibaca shell, tampil di Galeri). */
    private val screenshotDir = "/sdcard/Pictures/INS_HQ_Screenshots"
    private val recordingDir = "/sdcard/Movies/INS_HQ_Recordings"

    private val stamp: String
        get() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Resolusi native panel (bukan resolusi window yang bisa lebih kecil). */
    fun nativeResolution(): Pair<Int, Int> {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            } ?: dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)

            val mode = display?.mode
            val w = mode?.physicalWidth ?: 0
            val h = mode?.physicalHeight ?: 0
            if (w > 0 && h > 0) w to h else 1080 to 2400
        } catch (_: Exception) {
            1080 to 2400
        }
    }

    /** Ambil screenshot PNG resolusi penuh (lossless). */
    suspend fun captureHdScreenshot(): HdCaptureResult = withContext(Dispatchers.IO) {
        val (w, h) = nativeResolution()
        val path = "$screenshotDir/INS_HD_${stamp}.png"

        val script = """
            mkdir -p "$screenshotDir" 2>/dev/null
            # -p = PNG lossless, tanpa kompresi JPEG. Resolusi mengikuti panel native ($w x $h).
            screencap -p "$path"
            if [ -f "$path" ]; then
              ls -l "$path"
              echo "INS_CAPTURE_OK"
            else
              echo "INS_CAPTURE_FAILED"
            fi
        """.trimIndent()

        val res = privilege.runBest(script, timeoutMs = 20_000L)
        val ok = res.output.contains("INS_CAPTURE_OK") || fileExists(path)

        if (ok) {
            scanToGallery(path)
            HdCaptureResult(
                isSuccess = true,
                message = "✓ Screenshot HD tersimpan (PNG lossless, ${w}x${h}, tanpa downscale):\n$path",
                savedPath = path,
                nativeWidth = w,
                nativeHeight = h
            )
        } else {
            HdCaptureResult(
                isSuccess = false,
                message = buildString {
                    appendLine("✗ Gagal mengambil screenshot HD.")
                    appendLine("  Alasan: jalur shell (root/Shizuku/ADB) belum aktif — perintah `screencap` butuh UID shell.")
                    appendLine("  Solusi: aktifkan Shizuku / ADB nirkabel, atau root.")
                    if (res.output.isNotBlank()) appendLine("  Output: ${res.output.take(300)}")
                },
                nativeWidth = w,
                nativeHeight = h
            )
        }
    }

    /**
     * Mulai rekaman layar HD (resolusi native, bitrate tinggi).
     * @param bitrateMbps bitrate target dalam Mbps (default 24 Mbps = kualitas HD/near-lossless).
     * @param timeLimitSec batas durasi (screenrecord maksimum 180 detik).
     */
    fun startHdRecording(bitrateMbps: Int = 24, timeLimitSec: Int = 180): HdCaptureResult {
        if (isRecording) {
            return HdCaptureResult(false, "Rekaman sudah berjalan.", isRecording = true)
        }
        val (w, h) = nativeResolution()
        val bitrate = (bitrateMbps.coerceIn(4, 80) * 1_000_000)
        val limit = timeLimitSec.coerceIn(5, 180)
        val path = "$recordingDir/INS_HD_REC_${stamp}.mp4"

        isRecording = true
        recordingJob = scope.launch {
            val script = """
                mkdir -p "$recordingDir" 2>/dev/null
                # Resolusi native penuh, bitrate tinggi, H.264 level tinggi.
                screenrecord --bit-rate $bitrate --time-limit $limit --verbose "$path"
                echo "INS_RECORD_DONE"
            """.trimIndent()
            val res = privilege.runBest(script, timeoutMs = (limit + 20) * 1000L)
            isRecording = false
            if (fileExists(path)) scanToGallery(path)
            android.util.Log.d("HdCaptureEngine", "record finished: ${res.output.takeLast(200)}")
        }

        return HdCaptureResult(
            isSuccess = true,
            message = "● Rekaman HD dimulai (${w}x${h}, ${bitrateMbps.coerceIn(4, 80)} Mbps, maks ${limit}s).\n$path",
            savedPath = path,
            nativeWidth = w,
            nativeHeight = h,
            isRecording = true
        )
    }

    /** Hentikan rekaman lebih awal (file tetap tersimpan). */
    suspend fun stopHdRecording(): HdCaptureResult = withContext(Dispatchers.IO) {
        if (!isRecording) {
            return@withContext HdCaptureResult(false, "Tidak ada rekaman yang berjalan.")
        }
        privilege.runBest("pkill -INT -f screenrecord 2>/dev/null; echo INS_REC_STOP", timeoutMs = 8_000L)
        recordingJob?.cancel()
        recordingJob = null
        isRecording = false
        HdCaptureResult(true, "■ Rekaman dihentikan. File tersimpan di $recordingDir.")
    }

    private fun fileExists(path: String): Boolean {
        return try {
            if (File(path).exists()) return true
            privilege.runBest("ls '$path' >/dev/null 2>&1 && echo INS_FILE_YES", timeoutMs = 6_000L)
                .output.contains("INS_FILE_YES")
        } catch (_: Exception) {
            false
        }
    }

    private fun scanToGallery(path: String) {
        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (_: Exception) {}
    }

    fun describe(): String {
        val (w, h) = nativeResolution()
        val status = privilege.getPrivilegeStatus()
        return buildString {
            appendLine(" • Resolusi native panel : ${w}x${h}")
            appendLine(" • Mode akses shell      : ${status.activeExecutionMode}")
            appendLine(" • Folder screenshot     : $screenshotDir")
            appendLine(" • Folder rekaman        : $recordingDir")
            appendLine(" • Format                : PNG lossless (screenshot) / MP4 H.264 high-bitrate (rekaman)")
        }
    }
}
