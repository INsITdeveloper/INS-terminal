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

    /**
     * Daftar host yang dicoba saat pairing/connect, berurutan.
     *
     * PENTING: pada banyak perangkat (ColorOS/Xiaomi/Samsung) server pairing adbd TIDAK
     * listen di 127.0.0.1, hanya di alamat IP Wi-Fi perangkat. Karena itu kita coba
     * loopback dulu, lalu semua alamat IPv4 lokal perangkat (termasuk IP Wi-Fi).
     */
    fun candidateHosts(): List<String> {
        val hosts = mutableListOf<String>()
        hosts += "127.0.0.1"
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        addr.hostAddress?.let { if (!hosts.contains(it)) hosts += it }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return hosts
    }

    /** Alamat IPv4 Wi-Fi utama (dipakai untuk info ke user). */
    fun primaryLanAddress(): String? = candidateHosts().firstOrNull { it != "127.0.0.1" }

    fun pair(port: Int, code: String, preferredHost: String? = null): Pair<Boolean, String> {
        if (!isSupported()) {
            return false to "Pairing ADB nirkabel hanya tersedia di Android 11 ke atas."
        }
        if (port <= 0) return false to "Port pairing tidak valid."
        val clean = code.trim()
        if (clean.length < 6) return false to "Kode pairing harus 6 digit."

        val hosts = buildList {
            if (!preferredHost.isNullOrBlank()) add(preferredHost)
            addAll(candidateHosts())
        }.distinct()

        var lastMsg = "Pairing gagal."
        for (host in hosts) {
            // adbd kadang belum siap menerima pairing; coba 3x per host dengan jeda.
            for (attempt in 1..3) {
                try {
                    manager.setHostAddress(host)
                    manager.setApi(Build.VERSION.SDK_INT)
                    val ok = manager.pair(port, clean)
                    if (ok) {
                        pairedFlag = true
                        lastErrorValue = null
                        return true to "Pairing berhasil (via $host:$port)."
                    }
                    lastMsg = "Pairing ditolak perangkat (via $host:$port)."
                } catch (e: Throwable) {
                    lastErrorValue = e.message
                    val raw = e.message ?: e.javaClass.simpleName
                    lastMsg = if (raw.contains("NoSuchMethod") || raw.contains("Conscrypt", ignoreCase = true) || raw.contains("exportKeyingMaterial")) {
                        "Pairing gagal: Android versi ini menutup API Conscrypt yang dipakai library ADB " +
                            "(NoSuchMethod: exportKeyingMaterial). Ini bug library pada Android terbaru, bukan izin Anda.\n" +
                            "Solusi: pakai Shizuku (start dari PC/root) atau root — keduanya tidak butuh pairing."
                    } else if (raw.contains("ECONNREFUSED", ignoreCase = true) || raw.contains("Connection refused", ignoreCase = true)) {
                        "Pairing gagal via $host:$port: koneksi ditolak. PASTIKAN dialog \"Pair device with pairing code\" MASIH TERBUKA " +
                            "(kalau ditutup, server pairing langsung mati) dan port+kode diambil dari dialog yang SAMA."
                    } else {
                        "Pairing gagal via $host:$port: $raw"
                    }
                }
                try { Thread.sleep(700L) } catch (_: InterruptedException) {}
            }
        }
        pairedFlag = false
        return false to lastMsg
    }

    fun connect(port: Int, preferredHost: String? = null): Pair<Boolean, String> {
        if (!isSupported()) return false to "ADB nirkabel butuh Android 11 ke atas."
        if (port <= 0) return false to "Port tidak valid."

        val hosts = buildList {
            if (!preferredHost.isNullOrBlank()) add(preferredHost)
            addAll(candidateHosts())
        }.distinct()

        var lastMsg = "Koneksi ke port $port ditolak."
        // Setelah pairing, adbd butuh beberapa saat untuk membuka port koneksi.
        // Jadi kita coba beberapa kali dengan jeda sebelum menyerah.
        for (round in 1..3) {
            for (host in hosts) {
                try {
                    manager.setHostAddress(host)
                    manager.setApi(Build.VERSION.SDK_INT)
                    val ok = manager.connect(port)
                    if (ok) {
                        connectedPortValue = port
                        lastErrorValue = null
                        rememberPort(port)
                        return true to "Terhubung ke ADB $host:$port sebagai user shell (UID 2000)."
                    }
                    lastMsg = "Koneksi ke $host:$port ditolak."
                } catch (e: AdbPairingRequiredException) {
                    lastErrorValue = e.message
                    lastMsg = "Perangkat belum dipasangkan (via $host:$port)."
                } catch (e: Throwable) {
                    lastErrorValue = e.message
                    lastMsg = "Gagal konek $host:$port: ${e.message ?: e.javaClass.simpleName}"
                }
            }
            if (round < 3) { try { Thread.sleep(1200L) } catch (_: InterruptedException) {} }
        }
        return false to lastMsg
    }

    // ── Simpan port koneksi terakhir supaya bisa dicoba lagi tanpa mDNS ──
    private val prefs by lazy {
        context.getSharedPreferences("ins_adb", Context.MODE_PRIVATE)
    }

    private fun rememberPort(port: Int) {
        runCatching { prefs.edit().putInt("last_connect_port", port).apply() }
    }

    fun lastKnownPort(): Int = runCatching { prefs.getInt("last_connect_port", -1) }.getOrDefault(-1)

    fun autoConnect(timeoutMs: Long = 8000L): Pair<Boolean, String> {
        if (!isSupported()) return false to "ADB nirkabel butuh Android 11 ke atas."
        // 1) Coba lewat mDNS (paling andal saat wireless debugging baru dinyalakan).
        try {
            manager.setHostAddress("127.0.0.1")
            manager.setApi(Build.VERSION.SDK_INT)
            if (manager.autoConnect(context, timeoutMs)) {
                lastErrorValue = null
                return true to "Terhubung otomatis lewat penemuan mDNS."
            }
        } catch (e: AdbPairingRequiredException) {
            lastErrorValue = e.message
        } catch (e: Throwable) {
            lastErrorValue = e.message
        }

        // 2) Fallback: coba port koneksi yang terakhir berhasil.
        val known = lastKnownPort()
        if (known > 0) {
            val (ok, msg) = connect(known)
            if (ok) return ok to "$msg (port terakhir yang tersimpan)"
        }

        // 3) Fallback terakhir: cari endpoint connect via mDNS lalu sambung langsung.
        val ep = AdbMdnsDiscovery.discoverConnectEndpoint(context, timeoutMs)
        if (ep != null && ep.port > 0) {
            val (ok, msg) = connect(ep.port, ep.host)
            if (ok) return ok to msg
        }

        return false to "ADB tidak ditemukan otomatis. Pastikan 'Penelusuran nirkabel' MENYALA " +
            "(perangkat harus Wi-Fi), lalu PAIR OTOMATIS lagi (port & kode baru)."
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
