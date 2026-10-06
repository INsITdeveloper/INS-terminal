package com.example.system

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class ShellResult(
    val success: Boolean,
    val viaRoot: Boolean,
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
    val error: String? = null
)

data class PrivilegeStatus(
    val isRooted: Boolean,
    val hasWriteSecureSettings: Boolean,
    val hasWriteSettings: Boolean,
    val isShizukuAvailable: Boolean,
    val activeExecutionMode: String,
    val adbGrantCommand: String = "adb shell pm grant com.ins.terminal android.permission.WRITE_SECURE_SETTINGS",
    val suBinary: String? = null,
    val rootManager: String = "None",
    val hasResetProp: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isShizukuInstalled: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val shizukuVersion: Int = -1,
    val isAdbSupported: Boolean = false,
    val isAdbConnected: Boolean = false,
    val adbPort: Int = -1
)

class PrivilegeExecutionEngine private constructor(private val context: Context) {

    private var wifiLock: WifiManager.WifiLock? = null

    private var rootCacheTime = 0L
    private var rootCacheValue = false
    private var suBinaryCache: String? = null
    private var resetpropCache: Boolean? = null

    companion object {
        private const val TAG = "PrivilegeExecutionEngine"
        private const val ROOT_CACHE_TTL_MS = 60_000L
        private const val DEFAULT_TIMEOUT_MS = 20_000L

        private val SU_CANDIDATES = listOf(
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/magisk/.core/bin/su"
        )

        @Volatile
        private var INSTANCE: PrivilegeExecutionEngine? = null

        fun getInstance(context: Context): PrivilegeExecutionEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrivilegeExecutionEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }


    fun isDeviceRooted(): Boolean {
        val now = System.currentTimeMillis()
        if (now - rootCacheTime < ROOT_CACHE_TTL_MS) return rootCacheValue

        val su = resolveSuBinary()
        val rooted = if (su != null) {
            val res = runProcess(listOf(su, "-c", "id -u"), asRoot = true, timeoutMs = 12_000L)
            res.success && res.output.trim().lines().lastOrNull()?.trim() == "0"
        } else {
            false
        }

        rootCacheValue = rooted
        rootCacheTime = now
        if (!rooted) suBinaryCache = null
        return rooted
    }

    fun invalidateRootCache() {
        rootCacheTime = 0L
        rootCacheValue = false
        suBinaryCache = null
        resetpropCache = null
    }

    fun resolveSuBinary(): String? {
        suBinaryCache?.let { if (File(it).exists() || it == "su") return it }

        for (candidate in SU_CANDIDATES) {
            try {
                if (candidate == "su") {
                    val p = ProcessBuilder("sh", "-c", "command -v su").redirectErrorStream(true).start()
                    val out = p.inputStream.bufferedReader().readText().trim()
                    p.waitFor(4, TimeUnit.SECONDS)
                    if (out.isNotEmpty() && File(out).exists()) {
                        suBinaryCache = out
                        return out
                    }
                } else {
                    val f = File(candidate)
                    if (f.exists() && f.canExecute()) {
                        suBinaryCache = candidate
                        return candidate
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun detectRootManager(): String {
        return when {
            File("/data/adb/ksu").exists() -> "KernelSU"
            File("/data/adb/ap").exists() -> "APatch"
            File("/data/adb/magisk").exists() -> "Magisk"
            File("/system/xbin/su").exists() -> "SuperSU / Legacy"
            else -> if (suBinaryCache != null) "Unknown su" else "None"
        }
    }

    fun isShizukuActive(): Boolean = try {
        shizuku().canExecute()
    } catch (_: Throwable) {
        false
    }

    fun isAdbActive(): Boolean = try {
        adb().canExecute()
    } catch (_: Throwable) {
        false
    }

    private fun shizuku(): ShizukuBridgeEngine = ShizukuBridgeEngine.getInstance(context)

    private fun adb(): AdbShellEngine = AdbShellEngine.getInstance(context)

    fun isShizukuInstalled(): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (_: Exception) {
        false
    }

    fun hasResetProp(): Boolean {
        resetpropCache?.let { return it }
        val found = try {
            val p = ProcessBuilder("sh", "-c", "command -v resetprop || ls /data/adb/magisk/resetprop /data/adb/ksu/bin/resetprop 2>/dev/null")
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor(4, TimeUnit.SECONDS)
            out.isNotEmpty()
        } catch (_: Exception) {
            false
        }
        resetpropCache = found
        return found
    }


    fun runShell(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult =
        runProcess(listOf("sh", "-c", ScriptSanitizer.sanitize(script)), asRoot = false, timeoutMs = timeoutMs)

    fun runRoot(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult {
        val script = ScriptSanitizer.sanitize(script)
        val su = resolveSuBinary()
            ?: return ShellResult(
                success = false, viaRoot = true, exitCode = -1, output = "",
                error = "Binary su tidak ditemukan di perangkat ini (perangkat belum di-root)."
            )
        val res = runProcess(listOf(su, "-c", script), asRoot = true, timeoutMs = timeoutMs)
        if (!res.success && !res.timedOut) invalidateRootCache()
        return res
    }

    fun runBest(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult {
        val script = ScriptSanitizer.sanitize(script)
        if (isDeviceRooted()) return runRoot(script, timeoutMs)
        val shz = shizuku()
        if (shz.canExecute()) {
            val res = shz.runShell(script, timeoutMs)
            if (res.success || res.output.isNotBlank()) return res
        }
        val adbEngine = adb()
        if (adbEngine.canExecute()) {
            val res = adbEngine.runShell(script, timeoutMs)
            if (res.success || res.output.isNotBlank()) return res
        }
        return runShell(script, timeoutMs)
    }

    fun runShizuku(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult =
        shizuku().runShell(ScriptSanitizer.sanitize(script), timeoutMs)

    fun runAdb(script: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult =
        adb().runShell(ScriptSanitizer.sanitize(script), timeoutMs)

    private fun runProcess(args: List<String>, asRoot: Boolean, timeoutMs: Long): ShellResult {
        var process: Process? = null
        return try {
            val pb = ProcessBuilder(args)
                .directory(context.filesDir)
                .redirectErrorStream(true)

            pb.environment().apply {
                val extra = "/system/bin:/system/xbin:/vendor/bin:/data/adb/ksu/bin:/data/adb/ap/bin:/data/local/tmp"
                this["PATH"] = extra + ":" + (this["PATH"] ?: "")
                this["HOME"] = context.filesDir.absolutePath
                this["TMPDIR"] = context.cacheDir.absolutePath
            }

            process = pb.start()
            try {
                process.outputStream.close()
            } catch (_: Exception) {
            }

            val buffer = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        if (buffer.length < 200_000) buffer.append(line).append('\n')
                    }
                } catch (_: Exception) {
                }
            }
            reader.isDaemon = true
            reader.start()

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                try {
                    process.destroy()
                    if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                } catch (_: Exception) {
                }
            }
            reader.join(600)

            val code = if (finished) process.exitValue() else -1
            val text = buffer.toString().trimEnd()
            ShellResult(
                success = finished && code == 0,
                viaRoot = asRoot,
                exitCode = code,
                output = text,
                timedOut = !finished,
                error = when {
                    !finished -> "Perintah melebihi batas waktu ${timeoutMs / 1000} detik dan dihentikan."
                    else -> null
                }
            )
        } catch (e: Exception) {
            ShellResult(false, asRoot, -1, "", false, e.message ?: e.javaClass.simpleName)
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }


    fun applySettingViaContentResolver(namespace: String, key: String, value: String): Boolean {
        return try {
            val cr = context.contentResolver
            when (namespace.lowercase()) {
                "system" -> Settings.System.putString(cr, key, value)
                "secure" -> Settings.Secure.putString(cr, key, value)
                "global" -> Settings.Global.putString(cr, key, value)
                else -> Settings.Global.putString(cr, key, value)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Tidak bisa menulis setting $namespace.$key: ${e.message}")
            false
        }
    }

    fun readSettingViaContentResolver(namespace: String, key: String): String? = try {
        val cr = context.contentResolver
        when (namespace.lowercase()) {
            "system" -> Settings.System.getString(cr, key)
            "secure" -> Settings.Secure.getString(cr, key)
            else -> Settings.Global.getString(cr, key)
        }
    } catch (_: Exception) {
        null
    }

    fun applySystemPropertyReflection(key: String, value: String): Boolean {
        if (!isDeviceRooted()) return false
        val cmd = if (key.startsWith("ro.") && hasResetProp()) {
            "resetprop -n '$key' '$value'"
        } else {
            "setprop '$key' '$value'"
        }
        val res = runRoot(cmd, timeoutMs = 6_000L)
        if (!res.success) return false
        val readBack = readSystemProperty(key)
        return readBack != null && readBack == value
    }

    fun readSystemProperty(key: String): String? {
        val res = runShell("getprop '$key'", timeoutMs = 5_000L)
        val v = res.output.trim()
        return v.ifEmpty { null }
    }

    fun readSettingValue(namespace: String, key: String): String? {
        val res = runShell("settings get $namespace '$key'", timeoutMs = 5_000L)
        val v = res.output.trim()
        return if (v.isEmpty() || v == "null") null else v
    }


    fun getPrivilegeStatus(): PrivilegeStatus {
        val rooted = isDeviceRooted()
        val hasSecure = try {
            context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        val hasWrite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Settings.System.canWrite(context)
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }
        val shz = shizuku()
        val shizuku = try { shz.canExecute() } catch (_: Throwable) { false }
        val adbConnected = try { adb().isConnected() } catch (_: Throwable) { false }

        val mode = when {
            rooted && hasSecure -> "Root Superuser + WriteSecureSettings (Akses Penuh)"
            rooted -> "Root Superuser (Full Direct HW)"
            hasSecure -> "WriteSecureSettings (Otoritas Sistem via ADB/Shizuku)"
            shizuku -> "SHIZUKU (UID shell 2000) - non-root, tweak sistem aktif"
            adbConnected -> "ADB NIRKABEL (UID shell 2000) - non-root, tanpa Shizuku"
            hasWrite -> "System Settings API (Non-Root, Terbatas)"
            else -> "Non-Root (Terbatas — tweak kernel butuh root/ADB)"
        }

        return PrivilegeStatus(
            isRooted = rooted,
            hasWriteSecureSettings = hasSecure,
            hasWriteSettings = hasWrite,
            isShizukuAvailable = shizuku,
            activeExecutionMode = mode,
            suBinary = suBinaryCache,
            rootManager = detectRootManager(),
            hasResetProp = hasResetProp(),
            isBatteryOptimizationIgnored = try {
                (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
            } catch (_: Exception) {
                false
            },
            adbGrantCommand = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
            isShizukuInstalled = try { shz.isInstalled() } catch (_: Throwable) { false },
            isShizukuRunning = try { shz.isRunning() } catch (_: Throwable) { false },
            shizukuVersion = try { shz.version() } catch (_: Throwable) { -1 },
            isAdbSupported = try { adb().isSupported() } catch (_: Throwable) { false },
            isAdbConnected = try { adb().isConnected() } catch (_: Throwable) { false },
            adbPort = try { adb().connectedPort() } catch (_: Throwable) { -1 }
        )
    }


    fun executePrivilegedScript(script: String): Pair<Boolean, String> {
        val lines = script.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty()) return Pair(true, "Tidak ada perintah untuk dijalankan.")

        val status = getPrivilegeStatus()

        if (status.isRooted) {
            val res = runRoot(script, timeoutMs = 30_000L)
            val header = buildString {
                appendLine("┌──────────────────────────────────────────────────────────┐")
                appendLine("│   👑 EKSEKUSI ROOT (${status.rootManager})".padEnd(59) + "│")
                appendLine("└──────────────────────────────────────────────────────────┘")
                appendLine(" • Binary su     : ${status.suBinary ?: "su"}")
                appendLine(" • Exit code     : ${res.exitCode}${if (res.timedOut) " (timeout)" else ""}")
                appendLine(" • resetprop     : ${if (status.hasResetProp) "Tersedia (key ro.* bisa diubah)" else "Tidak ada (key ro.* read-only)"}")
            }
            val body = res.output.ifBlank {
                if (res.success) "Semua ${lines.size} perintah dieksekusi tanpa error." else "(tanpa output)"
            }
            val ok = res.success
            val verdict = if (ok) {
                "✓ ${lines.size} perintah dieksekusi sebagai root (exit 0)."
            } else {
                "✗ Eksekusi root GAGAL. ${res.error ?: "Sebagian perintah mengembalikan error di bawah ini."}"
            }
            return Pair(ok, "$header\n$verdict\n\n$body")
        }

        if (status.isShizukuAvailable) {
            val res = shizuku().runShell(script, timeoutMs = 30_000L)
            val header = buildString {
                appendLine("┌──────────────────────────────────────────────────────────┐")
                appendLine("│   🟢 EKSEKUSI SHIZUKU (UID shell 2000, TANPA ROOT)".padEnd(59) + "│")
                appendLine("└──────────────────────────────────────────────────────────┘")
                appendLine(" • Versi Shizuku : ${status.shizukuVersion}")
                appendLine(" • Exit code     : ${res.exitCode}${if (res.timedOut) " (timeout)" else ""}")
                appendLine(" • Batasan       : tweak kernel (/proc, /sys) dan key ro.* tetap butuh root")
            }
            val body = res.output.ifBlank {
                if (res.success) "Semua ${lines.size} perintah dieksekusi lewat Shizuku." else "(tanpa output)"
            }
            val verdict = if (res.success) {
                "✓ ${lines.size} perintah dijalankan sebagai UID shell (exit 0)."
            } else {
                "✗ Sebagian perintah gagal. ${res.error ?: "Lihat output di bawah."}"
            }
            return Pair(res.success, "$header\n$verdict\n\n$body")
        }

        if (status.isAdbConnected) {
            val res = adb().runShell(script, timeoutMs = 30_000L)
            val header = buildString {
                appendLine("┌──────────────────────────────────────────────────────────┐")
                appendLine("│   🔌 EKSEKUSI ADB NIRKABEL (UID shell 2000, TANPA ROOT)".padEnd(59) + "│")
                appendLine("└──────────────────────────────────────────────────────────┘")
                appendLine(" • Port ADB      : ${status.adbPort}")
                appendLine(" • Exit code     : ${res.exitCode}${if (res.timedOut) " (timeout)" else ""}")
                appendLine(" • Batasan       : tweak kernel (/proc, /sys) dan key ro.* tetap butuh root")
            }
            val body = res.output.ifBlank {
                if (res.success) "Semua ${lines.size} perintah dieksekusi lewat ADB." else "(tanpa output)"
            }
            val verdict = if (res.success) {
                "✓ ${lines.size} perintah dijalankan sebagai UID shell lewat ADB (exit 0)."
            } else {
                "✗ Sebagian perintah gagal. ${res.error ?: "Lihat output di bawah."}"
            }
            return Pair(res.success, "$header\n$verdict\n\n$body")
        }

        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val passthrough = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("settings put") -> {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        val ns = parts[2].lowercase()
                        val key = parts[3]
                        val value = parts.drop(4).joinToString(" ")
                            .removeSurrounding("\"").removeSurrounding("'")
                        val wrote = applySettingViaContentResolver(ns, key, value)
                        val readBack = readSettingValue(ns, key)
                        if (wrote && readBack == value) {
                            applied += "settings $ns.$key = $value  ✓ terverifikasi"
                        } else if (wrote) {
                            applied += "settings $ns.$key = $value  ✓ ditulis (nilai terbaca: $readBack)"
                        } else {
                            failed += "settings $ns.$key  ✗ DITOLAK (butuh WRITE_SECURE_SETTINGS / root)"
                        }
                    } else {
                        failed += "$line  ✗ format tidak dikenal"
                    }
                }

                line.startsWith("setprop") || line.startsWith("resetprop") -> {
                    val parts = line.split("\\s+".toRegex())
                    val key = parts.getOrNull(1) ?: "?"
                    failed += "setprop $key  ✗ BUTUH ROOT (system property tidak bisa diubah dari app biasa)"
                }

                line.startsWith("echo") && line.contains(">") -> {
                    failed += "$line  ✗ BUTUH ROOT (tulis ke /proc & /sys)"
                }

                else -> passthrough += line
            }
        }

        val shellRes = if (passthrough.isNotEmpty()) {
            runShell(passthrough.joinToString("\n"), timeoutMs = 20_000L)
        } else {
            null
        }
        shellRes?.let {
            if (it.output.isNotBlank()) {
                val clean = it.output.lines()
                    .filterNot { l ->
                        l.contains("Permission Denial") || l.contains("Failed to set property") ||
                            l.contains("Operation not permitted") || l.contains("Permission denied")
                    }.joinToString("\n").trim()
                if (clean.isNotEmpty()) applied += "[shell] $clean"
            }
        }

        val report = buildString {
            appendLine("┌──────────────────────────────────────────────────────────┐")
            appendLine("│   ⚠️  MODE NON-ROOT — LAPORAN JUJUR                      │")
            appendLine("└──────────────────────────────────────────────────────────┘")
            appendLine(" • Mode aktif : ${status.activeExecutionMode}")
            appendLine(" • Root       : TIDAK ADA")
            appendLine(" • Secure Set : ${if (status.hasWriteSecureSettings) "GRANTED" else "BELUM DIBERIKAN"}")
            appendLine()
            if (applied.isNotEmpty()) {
                appendLine("✓ BERHASIL DITERAPKAN (${applied.size}):")
                applied.take(12).forEach { appendLine("   • $it") }
                appendLine()
            }
            if (failed.isNotEmpty()) {
                appendLine("✗ TIDAK DITERAPKAN (${failed.size}) — perlu root/ADB:")
                failed.take(12).forEach { appendLine("   • $it") }
                if (failed.size > 12) appendLine("   • ... dan ${failed.size - 12} lagi")
                appendLine()
            }
            if (applied.isEmpty() && failed.isEmpty()) {
                appendLine("Tidak ada direktif yang bisa dijalankan pada mode ini.")
                appendLine()
            }
            appendLine("Cara mengaktifkan semuanya:")
            appendLine("  1) Root HP (Magisk / KernelSU / APatch), ATAU")
            appendLine("  2) Sambungkan ke PC lalu jalankan sekali:")
            appendLine("     ${status.adbGrantCommand}")
            appendLine("     (aktifkan juga Shizuku agar bisa grant tanpa PC)")
        }

        return Pair(applied.isNotEmpty(), report)
    }


    private fun deviceTweakPreamble(): String {
        val p = DeviceProfileEngine.detect(context)
        val thermalStops = p.brand.thermalServices.joinToString("\n") { "stop $it 2>/dev/null || true" }
        return """
            # Profil perangkat: ${p.brand.displayName}
            # Chipset: ${p.socVendor} (${p.socHardware}) | GPU: ${p.gpuFamily}
            # Android ${p.androidRelease} (API ${p.sdkInt}) | ${p.cpuCoreCount} core | ${p.totalRamMb} MB RAM
        """.trimIndent()
    }

    suspend fun applySuperLowSignalOptimizer(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val actions = mutableListOf<String>()
        val failures = mutableListOf<String>()

        var wifiLocked = false
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                try {
                    if (wifiLock?.isHeld == true) wifiLock?.release()
                } catch (_: Exception) {
                }
                wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "INSLowLatencySignalLock")
                } else {
                    @Suppress("DEPRECATION")
                    wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "INSHighPerfSignalLock")
                }
                wifiLock?.setReferenceCounted(false)
                wifiLock?.acquire()
                wifiLocked = wifiLock?.isHeld == true
            }
        } catch (e: Exception) {
            failures += "Wi-Fi lock: ${e.message}"
        }
        if (wifiLocked) {
            actions += "Wi-Fi radio LOW_LATENCY lock aktif (radio tidak tidur saat Wi-Fi dipakai)"
        } else {
            failures += "Wi-Fi lock gagal diambil"
        }

        val settingTargets = listOf(
            Triple("global", "wifi_scan_always_enabled", "0"),
            Triple("global", "wifi_wakeup_enabled", "0"),
            Triple("global", "wifi_cellular_data_fallback", "0"),
            Triple("global", "mobile_data_always_on", "1"),
            Triple("global", "private_dns_mode", "hostname"),
            Triple("global", "private_dns_specifier", "one.one.one.one")
        )
        for ((ns, key, value) in settingTargets) {
            val wrote = applySettingViaContentResolver(ns, key, value)
            val back = readSettingValue(ns, key)
            if (wrote && back == value) {
                actions += "$ns.$key = $value ✓"
            } else {
                failures += "$ns.$key (butuh WRITE_SECURE_SETTINGS)"
            }
        }

        val script = """
            ${deviceTweakPreamble()}

            # --- Radio / RIL tuning (aman: semua dibungkus cek ketersediaan file) ---
            [ -e /proc/sys/net/ipv4/tcp_congestion_control ] && echo bbr > /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_tw_reuse ] && echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_fastopen ] && echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_slow_start_after_idle ] && echo 0 > /proc/sys/net/ipv4/tcp_slow_start_after_idle 2>/dev/null || true
            [ -e /proc/sys/net/core/rmem_max ] && echo 8388608 > /proc/sys/net/core/rmem_max 2>/dev/null || true
            [ -e /proc/sys/net/core/wmem_max ] && echo 8388608 > /proc/sys/net/core/wmem_max 2>/dev/null || true

            # --- Pastikan Wi-Fi tidak dimatikan saat layar mati ---
            settings put global wifi_sleep_policy 2 2>/dev/null || true
            settings put global wifi_scan_always_enabled 0 2>/dev/null || true
            settings put global private_dns_mode hostname 2>/dev/null || true
            settings put global private_dns_specifier one.one.one.one 2>/dev/null || true

            # --- Verifikasi ---
            echo "TCP_CONGESTION=${'$'}(cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null)"
        """.trimIndent()

        val rootRes = if (isDeviceRooted()) runRoot(script, timeoutMs = 25_000L) else null
        if (rootRes != null) {
            if (rootRes.success) {
                actions += "Kernel TCP tuning diterapkan via root"
                rootRes.output.lines().filter { it.contains("TCP_CONGESTION=") }.forEach {
                    actions += it.trim()
                }
            } else {
                failures += "Kernel TCP tuning gagal: ${rootRes.error ?: rootRes.output.take(200)}"
            }
        } else {
            failures += "Kernel TCP/radio tuning (butuh root — setprop & /proc tidak bisa diakses app biasa)"
        }

        val ok = actions.isNotEmpty()
        val report = buildString {
            appendLine("┌──────────────────────────────────────────────────────────┐")
            appendLine("│   📶 SIGNAL & NETWORK OPTIMIZER — LAPORAN NYATA          │")
            appendLine("└──────────────────────────────────────────────────────────┘")
            appendLine(deviceTweakPreamble().lines().joinToString("\n") { "   $it" })
            appendLine()
            appendLine("✓ BERHASIL (${actions.size}):")
            actions.forEach { appendLine("   • $it") }
            if (failures.isNotEmpty()) {
                appendLine()
                appendLine("✗ GAGAL / BUTUH IZIN (${failures.size}):")
                failures.forEach { appendLine("   • $it") }
            }
            appendLine()
            appendLine("Catatan jujur: kekuatan sinyal (dBm) tidak bisa \"dipaksa naik\" oleh aplikasi.")
            appendLine("Yang bisa dilakukan: mencegah radio tidur, mempercepat handover, dan")
            appendLine("memperbaiki TCP/DNS. Untuk paksa band/LTE-only, pakai menu Radio Info.")
        }
        Pair(ok, report)
    }

    suspend fun applyRealFpsUnlock(targetHz: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val hzStr = "$targetHz.0"
        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()

        val targets = listOf(
            Triple("system", "peak_refresh_rate", hzStr),
            Triple("system", "min_refresh_rate", hzStr),
            Triple("system", "user_refresh_rate", targetHz.toString())
        )
        for ((ns, key, value) in targets) {
            if (applySettingViaContentResolver(ns, key, value)) {
                applied += "$ns.$key = $value"
            } else {
                failed += "$ns.$key (butuh root/WRITE_SECURE_SETTINGS)"
            }
        }

        val script = """
            settings put system peak_refresh_rate $hzStr
            settings put system min_refresh_rate $hzStr
            settings put system user_refresh_rate $targetHz
            settings put global peak_refresh_rate $hzStr
            settings put global min_refresh_rate $hzStr
        """.trimIndent()

        val rootRes = if (isDeviceRooted()) runRoot(script, timeoutMs = 15_000L) else null
        if (rootRes != null && rootRes.success) {
            applied += "settings global + system ditulis via root"
        } else if (rootRes != null) {
            failed += "penulisan via root gagal: ${rootRes.error ?: "exit ${rootRes.exitCode}"}"
        }

        val verified = readSettingValue("system", "peak_refresh_rate")
        val displayMax = maxSupportedRefreshRate()

        val report = buildString {
            appendLine("┌──────────────────────────────────────────────────────────┐")
            appendLine("│   🚀 REFRESH RATE — HASIL NYATA                          │")
            appendLine("└──────────────────────────────────────────────────────────┘")
            appendLine(" • Target           : ${targetHz} Hz")
            appendLine(" • Panel maksimum   : ${displayMax} Hz (batas hardware, tidak bisa dilewati)")
            appendLine(" • peak_refresh_rate: ${verified ?: "tidak terbaca"}")
            appendLine()
            if (applied.isNotEmpty()) {
                appendLine("✓ Diterapkan:")
                applied.forEach { appendLine("   • $it") }
            }
            if (failed.isNotEmpty()) {
                appendLine("✗ Gagal:")
                failed.forEach { appendLine("   • $it") }
            }
            if (targetHz > displayMax) {
                appendLine()
                appendLine("⚠️  Target ${targetHz}Hz melebihi panel ${displayMax}Hz. Nilai setting akan ditulis,")
                appendLine("   tetapi panel tetap mentok di ${displayMax}Hz — itu batas fisik layar.")
            }
        }
        Pair(applied.isNotEmpty(), report)
    }

    fun maxSupportedRefreshRate(): Int {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                wm?.defaultDisplay
            }
            display?.supportedModes?.maxOfOrNull { it.refreshRate.toInt() } ?: 60
        } catch (_: Exception) {
            60
        }
    }

    suspend fun applyRealTouchOptimization(samplingHz: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()

        if (applySettingViaContentResolver("system", "pointer_speed", "7")) {
            applied += "pointer_speed = 7"
        } else {
            failed += "pointer_speed (butuh WRITE_SETTINGS)"
        }
        if (applySettingViaContentResolver("secure", "long_press_timeout", "150")) {
            applied += "long_press_timeout = 150ms"
        } else {
            failed += "long_press_timeout (butuh WRITE_SECURE_SETTINGS)"
        }

        val script = """
            ${deviceTweakPreamble()}
            # Touch IC yang berbeda-beda per merek — cek dulu, baru tulis
            for f in /sys/class/touch/touch_boost/touch_boost_enable \
                     /sys/class/touchscreen/*/game_switch_enable \
                     /proc/touchpanel/game_switch_enable \
                     /sys/module/msm_performance/parameters/touchboost; do
                [ -e "${'$'}f" ] && echo 1 > "${'$'}f" 2>/dev/null && echo "touch: ${'$'}f diaktifkan"
            done
            for f in /sys/class/touch/touch_boost/sampling_rate \
                     /sys/class/touchscreen/*/sampling_rate; do
                [ -e "${'$'}f" ] && echo $samplingHz > "${'$'}f" 2>/dev/null && echo "sampling: ${'$'}f = $samplingHz"
            done
            echo "SELESAI_TOUCH"
        """.trimIndent()

        val rootRes = if (isDeviceRooted()) runRoot(script, timeoutMs = 15_000L) else null
        if (rootRes != null) {
            val lines = rootRes.output.lines().filter { it.startsWith("touch:") || it.startsWith("sampling:") }
            if (lines.isNotEmpty()) {
                applied += lines
            } else {
                failed += "Node sysfs touch IC tidak ditemukan di ${DeviceProfileEngine.detect(context).socVendor} ini " +
                    "(normal — tidak semua HP mengekspos sampling rate ke sysfs)"
            }
        } else {
            failed += "Tuning touch IC via sysfs (butuh root)"
        }

        val report = buildString {
            appendLine("┌──────────────────────────────────────────────────────────┐")
            appendLine("│   🎯 TOUCH RESPONSE — HASIL NYATA                        │")
            appendLine("└──────────────────────────────────────────────────────────┘")
            if (applied.isNotEmpty()) {
                appendLine("✓ Diterapkan:")
                applied.forEach { appendLine("   • $it") }
            }
            if (failed.isNotEmpty()) {
                appendLine("✗ Tidak tersedia:")
                failed.forEach { appendLine("   • $it") }
            }
        }
        Pair(applied.isNotEmpty(), report)
    }

    suspend fun applyRealBufferAndSmoothness(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()

        val anims = listOf(
            "window_animation_scale" to "0.5",
            "transition_animation_scale" to "0.5",
            "animator_duration_scale" to "0.5"
        )
        for ((key, value) in anims) {
            if (applySettingViaContentResolver("global", key, value)) {
                applied += "$key = $value"
            } else {
                failed += "$key (butuh WRITE_SECURE_SETTINGS)"
            }
        }

        val script = """
            settings put global window_animation_scale 0.5
            settings put global transition_animation_scale 0.5
            settings put global animator_duration_scale 0.5
        """.trimIndent()

        val rootRes = if (isDeviceRooted()) runRoot(script, timeoutMs = 12_000L) else null
        if (rootRes != null && rootRes.success) {
            applied += "Animation scale 0.5x via root"
        } else if (rootRes != null) {
            failed += "Animation scale: ${rootRes.error ?: "exit ${rootRes.exitCode}"}"
        } else {
            failed += "Animation scale (butuh izin tulis settings)"
        }

        val report = buildString {
            appendLine("┌──────────────────────────────────────────────────────────┐")
            appendLine("│   ⚡ UI SMOOTHNESS — HASIL NYATA                         │")
            appendLine("└──────────────────────────────────────────────────────────┘")
            if (applied.isNotEmpty()) {
                appendLine("✓ Diterapkan:")
                applied.forEach { appendLine("   • $it") }
            }
            if (failed.isNotEmpty()) {
                appendLine("✗ Gagal / butuh izin:")
                failed.forEach { appendLine("   • $it") }
            }
        }
        Pair(applied.isNotEmpty(), report)
    }
}
