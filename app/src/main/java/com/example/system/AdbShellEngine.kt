package com.example.system

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import java.util.concurrent.TimeUnit

class AdbShellEngine private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: AdbShellEngine? = null

        fun getInstance(context: Context): AdbShellEngine =
            instance ?: synchronized(this) {
                instance ?: AdbShellEngine(context.applicationContext).also { instance = it }
            }
    }

    private val manager = AdbConnectionManagerImpl.getInstance(context)

    @Volatile
    private var pairedFlag = false

    @Volatile
    private var connectedPortValue = -1

    @Volatile
    private var lastErrorValue: String? = null

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isConnected(): Boolean = runCatching { manager.isConnected }.getOrDefault(false)

    fun isPaired(): Boolean = pairedFlag

    fun connectedPort(): Int = connectedPortValue

    fun lastError(): String? = lastErrorValue

    fun canExecute(): Boolean = isSupported() && isConnected()

    fun pair(port: Int, code: String): Pair<Boolean, String> {
        if (!isSupported()) {
            return false to "Pairing ADB nirkabel hanya tersedia di Android 11 ke atas."
        }
        if (port <= 0) return false to "Port pairing tidak valid."
        val clean = code.trim()
        if (clean.length < 6) return false to "Kode pairing harus 6 digit."

        return try {
            manager.setHostAddress("127.0.0.1")
            manager.setApi(Build.VERSION.SDK_INT)
            val ok = manager.pair(port, clean)
            pairedFlag = ok
            if (ok) {
                lastErrorValue = null
                true to "Pairing berhasil. Sekarang jalankan: adb connect <port>"
            } else {
                false to "Pairing ditolak perangkat."
            }
        } catch (e: Throwable) {
            pairedFlag = false
            lastErrorValue = e.message
            false to "Pairing gagal: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun connect(port: Int): Pair<Boolean, String> {
        if (!isSupported()) return false to "ADB nirkabel butuh Android 11 ke atas."
        if (port <= 0) return false to "Port tidak valid."
        return try {
            manager.setHostAddress("127.0.0.1")
            manager.setApi(Build.VERSION.SDK_INT)
            val ok = manager.connect(port)
            if (ok) {
                connectedPortValue = port
                lastErrorValue = null
                true to "Terhubung ke ADB port $port sebagai user shell (UID 2000)."
            } else {
                false to "Koneksi ke port $port ditolak."
            }
        } catch (e: AdbPairingRequiredException) {
            lastErrorValue = e.message
            false to "Perangkat belum dipasangkan. Jalankan: adb pair <port> <kode>"
        } catch (e: Throwable) {
            lastErrorValue = e.message
            false to "Gagal konek port $port: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun autoConnect(timeoutMs: Long = 8000L): Pair<Boolean, String> {
        if (!isSupported()) return false to "ADB nirkabel butuh Android 11 ke atas."
        return try {
            manager.setHostAddress("127.0.0.1")
            manager.setApi(Build.VERSION.SDK_INT)
            val ok = manager.autoConnect(context, timeoutMs)
            if (ok) {
                lastErrorValue = null
                true to "Terhubung otomatis lewat penemuan mDNS."
            } else {
                false to "Perangkat tidak ditemukan lewat mDNS. Isi port secara manual."
            }
        } catch (e: AdbPairingRequiredException) {
            lastErrorValue = e.message
            false to "Perangkat ditemukan tetapi belum dipasangkan. Jalankan: adb pair <port> <kode>"
        } catch (e: Throwable) {
            lastErrorValue = e.message
            false to "Auto-connect gagal: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun disconnect(): Pair<Boolean, String> {
        return try {
            manager.disconnect()
            connectedPortValue = -1
            true to "Koneksi ADB ditutup."
        } catch (e: Throwable) {
            false to "Gagal menutup koneksi: ${e.message}"
        }
    }

    fun openSession(): AdbShellStream? {
        if (!isConnected()) return null
        return try {
            val stream: AdbStream = manager.openStream("shell,v2,raw:")
            AdbShellStream(stream)
        } catch (e: Throwable) {
            lastErrorValue = e.message
            null
        }
    }

    fun runShell(command: String, timeoutMs: Long = 20_000L): ShellResult {
        if (!isSupported()) {
            return ShellResult(false, false, -1, "", false, "ADB nirkabel butuh Android 11 ke atas.")
        }
        if (!isConnected()) {
            return ShellResult(
                false, false, -1, "", false,
                "ADB belum terhubung. Pasang lewat Wireless debugging lalu jalankan: adb pair <port> <kode>"
            )
        }

        val session = openSession()
            ?: return ShellResult(false, false, -1, "", false, "Gagal membuka shell ADB.")

        val collected = StringBuilder()
        return try {
            val out = session.outputStream
            out.write((command + "\n").toByteArray(Charsets.UTF_8))
            out.flush()

            val reader = Thread {
                try {
                    val input = session.inputStream
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            if (collected.length < 400_000) {
                                collected.append(String(buffer, 0, read, Charsets.UTF_8))
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            reader.isDaemon = true
            reader.start()

            val finished = session.awaitExitOrTimeout(timeoutMs)
            runCatching { reader.join(600) }

            val code = session.exitCode()
            ShellResult(
                success = finished && code == 0,
                viaRoot = false,
                exitCode = if (finished) code else -1,
                output = collected.toString().trimEnd(),
                timedOut = !finished,
                error = if (!finished) "Perintah melewati batas ${timeoutMs / 1000} detik." else null
            )
        } catch (e: Throwable) {
            ShellResult(false, false, -1, collected.toString().trimEnd(), false, e.message)
        } finally {
            session.close()
        }
    }

    fun grantSecureSettings(): Pair<Boolean, String> {
        val res = runShell("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        return if (res.success) {
            true to "Izin WRITE_SECURE_SETTINGS diberikan permanen ke aplikasi ini."
        } else {
            false to "Gagal memberi izin: ${res.output.ifBlank { res.error ?: "tidak diketahui" }}"
        }
    }

    fun describe(): String = buildString {
        appendLine(" • Didukung perangkat : ${if (isSupported()) "YA (Android ${Build.VERSION.RELEASE})" else "TIDAK (butuh Android 11+)"}")
        appendLine(" • Sudah dipasangkan  : ${if (isPaired()) "YA" else "BELUM"}")
        appendLine(" • Terhubung ke ADB   : ${if (isConnected()) "YA (port $connectedPortValue)" else "TIDAK"}")
        appendLine(" • Kunci ADB tersimpan: ${if (manager.hasKeys()) "YA" else "BELUM"}")
        lastError()?.let { appendLine(" • Error terakhir     : $it") }
    }

    fun pairingGuide(): String = buildString {
        appendLine("Cara menghubungkan tanpa root dan tanpa Shizuku:")
        appendLine("  1. Pengaturan → Tentang ponsel → ketuk 'Nomor bentukan' 7x")
        appendLine("  2. Opsi pengembang → aktifkan 'Penelusuran nirkabel' / Wireless debugging")
        appendLine("  3. Pastikan Wi-Fi tersambung, lalu buka 'Pair device with pairing code'")
        appendLine("  4. Catat PORT dan KODE 6 digit yang muncul")
        appendLine("  5. Kembali ke sini, jalankan:  adb pair <PORT> <KODE>")
        appendLine("  6. Lalu jalankan:  adb auto    atau    adb connect <PORT-KONEKSI>")
        appendLine()
        appendLine("Port pairing dan port koneksi BERBEDA. Port koneksi terlihat di layar")
        appendLine("'Wireless debugging' utama, dan biasanya bisa ditemukan otomatis oleh 'adb auto'.")
    }
}
