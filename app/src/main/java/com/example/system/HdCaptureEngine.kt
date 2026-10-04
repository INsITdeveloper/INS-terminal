package com.example.system

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HdCaptureResult(
    val isSuccess: Boolean,
    val message: String,
    val savedPath: String? = null,
    val galleryUri: String? = null,
    val nativeWidth: Int = 0,
    val nativeHeight: Int = 0,
    val isRecording: Boolean = false
)

/**
 * HD (native-resolution) screen capture.
 *
 * Perbaikan v1.5:
 *  - Output ditulis ke folder milik app sendiri (`Android/data/<pkg>/files/...`) yang PASTI
 *    bisa ditulis oleh shell, lalu disalin ke Galeri via MediaStore. Sebelumnya ditulis ke
 *    `/sdcard/Pictures/...` yang di sebagian perangkat gagal -> file 0 byte.
 *  - Ukuran file diverifikasi (`-s` / `wc -c`). Kalau 0 byte, dicoba metode capture alternatif.
 *  - Tidak lagi melaporkan "berhasil" untuk file kosong.
 */
class HdCaptureEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val privilege = PrivilegeExecutionEngine.getInstance(context)

    @Volatile
    private var recordingJob: Job? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    private val stamp: String
        get() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun shotDir(): String {
        val dir = context.getExternalFilesDir("INS_HQ_Screenshots")
        return (dir ?: File(context.filesDir, "INS_HQ_Screenshots")).absolutePath
    }

    private fun recDir(): String {
        val dir = context.getExternalFilesDir("INS_HQ_Recordings")
        return (dir ?: File(context.filesDir, "INS_HQ_Recordings")).absolutePath
    }

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
        val dir = shotDir()
        val name = "INS_HD_${stamp}.png"
        val out = "$dir/$name"

        val script = """
            mkdir -p "$dir" 2>/dev/null
            rm -f "$out" 2>/dev/null

            # Strategi 1: screencap ke file langsung (paling umum)
            screencap -p "$out" >/dev/null 2>&1
            # Strategi 2: screencap ke stdout lalu redirect
            if [ ! -s "$out" ]; then screencap -p > "$out" 2>/dev/null; fi
            # Strategi 3: eksplisit display 0
            if [ ! -s "$out" ]; then screencap -p -d 0 > "$out" 2>/dev/null; fi

            SIZE=0
            if [ -s "$out" ]; then SIZE=${'$'}(wc -c < "$out" 2>/dev/null); fi
            echo "INS_SIZE=${'$'}SIZE"
            if [ "${'$'}SIZE" -gt 1000 ] 2>/dev/null; then
              echo "INS_CAPTURE_OK"
            else
              echo "INS_CAPTURE_FAILED"
              echo "SHELL_UID=${'$'}(id 2>/dev/null | head -c 60)"
            fi
        """.trimIndent()

        val res = privilege.runBest(script, timeoutMs = 30_000L)
        val size = Regex("INS_SIZE=(\\d+)").find(res.output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        if (res.output.contains("INS_CAPTURE_OK") && size > 1000) {
            val file = File(out)
            val uri = copyToMediaStore(file, name, "image/png", "Pictures/INS_HQ_Screenshots")
            scanToGallery(file.absolutePath)
            HdCaptureResult(
                isSuccess = true,
                message = buildString {
                    appendLine("✓ Screenshot HD tersimpan (PNG lossless, ${w}x${h}, ${size / 1024} KB, tanpa downscale).")
                    appendLine("  File  : $out")
                    if (uri != null) appendLine("  Galeri: OK (Pictures/INS_HQ_Screenshots)")
                }.trim(),
                savedPath = out,
                galleryUri = uri?.toString(),
                nativeWidth = w,
                nativeHeight = h
            )
        } else {
            HdCaptureResult(
                isSuccess = false,
                message = buildString {
                    appendLine("✗ Gagal mengambil screenshot HD (file ${size} byte / kosong).")
                    appendLine("  Penyebab paling umum: jalur shell (Shizuku / ADB nirkabel / root) belum aktif,")
                    appendLine("  sehingga `screencap` tidak punya izin menangkap layar.")
                    appendLine("  Solusi: sambungkan Shizuku atau ADB nirkabel dulu, lalu GRANT WRITE_SECURE_SETTINGS.")
                    val uid = Regex("SHELL_UID=(.+)").find(res.output)?.groupValues?.get(1)
                    if (!uid.isNullOrBlank()) appendLine("  Konteks proses: $uid")
                }.trim(),
                nativeWidth = w,
                nativeHeight = h
            )
        }
    }

    fun startHdRecording(bitrateMbps: Int = 24, timeLimitSec: Int = 180): HdCaptureResult {
        if (isRecording) return HdCaptureResult(false, "Rekaman sudah berjalan.", isRecording = true)
        val (w, h) = nativeResolution()
        val dir = recDir()
        val bitrate = bitrateMbps.coerceIn(4, 80) * 1_000_000
        val limit = timeLimitSec.coerceIn(5, 180)
        val name = "INS_HD_REC_${stamp}.mp4"
        val out = "$dir/$name"

        isRecording = true
        recordingJob = scope.launch {
            val script = """
                mkdir -p "$dir" 2>/dev/null
                screenrecord --bit-rate $bitrate --time-limit $limit "$out"
                echo "INS_REC_DONE"
                if [ -s "$out" ]; then echo "INS_REC_SIZE=${'$'}(wc -c < "$out")"; else echo "INS_REC_SIZE=0"; fi
            """.trimIndent()
            val res = privilege.runBest(script, timeoutMs = (limit + 25) * 1000L)
            isRecording = false
            val file = File(out)
            if (file.exists() && file.length() > 1000) {
                copyToMediaStore(file, name, "video/mp4", "Movies/INS_HQ_Recordings")
                scanToGallery(out)
            }
            android.util.Log.d("HdCaptureEngine", "record finished: ${res.output.takeLast(200)}")
        }

        return HdCaptureResult(
            isSuccess = true,
            message = "● Rekaman HD dimulai (${w}x${h}, ${bitrateMbps.coerceIn(4, 80)} Mbps, maks ${limit}s).\n$out",
            savedPath = out,
            nativeWidth = w,
            nativeHeight = h,
            isRecording = true
        )
    }

    suspend fun stopHdRecording(): HdCaptureResult = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext HdCaptureResult(false, "Tidak ada rekaman yang berjalan.")
        privilege.runBest("pkill -INT -f screenrecord 2>/dev/null; echo INS_REC_STOP", timeoutMs = 8_000L)
        recordingJob?.cancel()
        recordingJob = null
        isRecording = false
        HdCaptureResult(true, "■ Rekaman dihentikan. File tersimpan di folder INS_HQ_Recordings.")
    }

    private fun copyToMediaStore(file: File, displayName: String, mime: String, relativePath: String): Uri? {
        if (!file.exists() || file.length() <= 0) return null
        return try {
            val collection = if (mime.startsWith("image")) {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(collection, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    private fun scanToGallery(path: String) {
        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (_: Exception) {
        }
    }

    fun describe(): String {
        val (w, h) = nativeResolution()
        val status = privilege.getPrivilegeStatus()
        return buildString {
            appendLine(" • Resolusi native panel : ${w}x${h}")
            appendLine(" • Mode akses shell      : ${status.activeExecutionMode}")
            appendLine(" • Folder screenshot     : ${shotDir()}")
            appendLine(" • Folder rekaman        : ${recDir()}")
            appendLine(" • Salinan Galeri        : Pictures/INS_HQ_Screenshots & Movies/INS_HQ_Recordings")
            appendLine(" • Format                : PNG lossless (screenshot) / MP4 H.264 high-bitrate (rekaman)")
        }
    }
}
