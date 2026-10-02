package com.example.system

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class ShellKind { ROOT, SHIZUKU, ADB, APP, CLOSED }

class InteractiveShellEngine(private val context: Context) {

    interface Listener {
        fun onOutput(line: String)

        fun onCommandFinished(command: String, exitCode: Int, cwd: String, durationMs: Long)

        fun onClosed(reason: String)
    }

    @Volatile
    private var listener: Listener? = null

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerThread: Thread? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var isRootShell: Boolean = false
        private set

    @Volatile
    private var shellPid: Long = -1L

    @Volatile
    private var pidGroupCapable = false

    private val _cwd = MutableStateFlow("/")
    val cwd: StateFlow<String> = _cwd.asStateFlow()

    private val _kind = MutableStateFlow(ShellKind.CLOSED)
    val kind: StateFlow<ShellKind> = _kind.asStateFlow()

    private val tagCounter = AtomicLong(0)

    @Volatile
    private var pendingTag: String? = null
    @Volatile
    private var pendingCommand: String = ""
    @Volatile
    private var pendingStartedAt: Long = 0L

    private val readyLatch = CountDownLatch(1)
    private val pidLatch = CountDownLatch(1)

    @Volatile
    private var readyUid: String? = null

    private val privateDir = File(context.filesDir, "shell").apply { mkdirs() }

    fun setListener(l: Listener?) {
        listener = l
    }


    suspend fun start(useRoot: Boolean): Pair<Boolean, String> =
        start(if (useRoot) ShellKind.ROOT else ShellKind.APP)

    suspend fun startShizuku(): Pair<Boolean, String> = start(ShellKind.SHIZUKU)

    suspend fun startAdb(): Pair<Boolean, String> = start(ShellKind.ADB)

    private suspend fun start(kind: ShellKind): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        closeInternal(notify = false)

        val priv = PrivilegeExecutionEngine.getInstance(context)
        val shPath = findSystemShell()

        val baseArgs: List<String>
        val rootMode = kind == ShellKind.ROOT
        val shizukuMode = kind == ShellKind.SHIZUKU
        val adbMode = kind == ShellKind.ADB
        if (rootMode) {
            val su = priv.resolveSuBinary()
                ?: return@withContext Pair(
                    false,
                    "Root tidak tersedia di perangkat ini.\n" +
                        "INS Terminal tidak bisa memberi root sendiri — root harus datang dari Magisk / KernelSU / APatch.\n" +
                        "Terminal tetap bisa dipakai sebagai shell biasa."
                )
            baseArgs = listOf(su)
        } else {
            baseArgs = listOf(shPath)
        }

        if (shizukuMode) {
            val shz = ShizukuBridgeEngine.getInstance(context)
            if (!shz.isRunning()) {
                return@withContext Pair(false, "Shizuku tidak berjalan. Buka aplikasi Shizuku lalu mulai layanannya, kemudian jalankan: shell shizuku")
            }
            if (!shz.hasPermission()) {
                return@withContext Pair(false, "Izin Shizuku belum diberikan. Jalankan perintah: shizuku")
            }
        }

        if (adbMode) {
            val adbEngine = AdbShellEngine.getInstance(context)
            if (!adbEngine.isSupported()) {
                return@withContext Pair(false, "ADB nirkabel butuh Android 11 ke atas.")
            }
            if (!adbEngine.isConnected()) {
                return@withContext Pair(false, "ADB belum terhubung. Jalankan: adb pair <port> <kode> lalu adb auto")
            }
        }

        val setsid = listOf("/system/bin/setsid", "/system/xbin/setsid", "/vendor/bin/setsid")
            .firstOrNull { File(it).exists() }
        val args = if (setsid != null) listOf(setsid, "-w") + baseArgs else baseArgs
        pidGroupCapable = setsid != null

        val env = HashMap<String, String>()
        env["PATH"] = buildString {
            append("/system/bin:/system/xbin:/vendor/bin:/product/bin")
            append(":/data/adb/ksu/bin:/data/adb/ap/bin:/data/local/bin:/data/local/xbin")
            append(":/data/data/com.termux/files/usr/bin")
        }
        env["HOME"] = if (rootMode) "/data/adb" else context.filesDir.absolutePath
        env["TERM"] = "xterm-256color"
        env["LANG"] = "en_US.UTF-8"
        env["LC_ALL"] = "C"
        env["TMPDIR"] = context.cacheDir.absolutePath
        env["PS1"] = ""
        env["ENV"] = ""

        val started = try {
            val p: Process = if (adbMode) {
                val session = AdbShellEngine.getInstance(context).openSession()
                    ?: throw IllegalStateException("Gagal membuka shell ADB")
                AdbProcess(session)
            } else if (shizukuMode) {
                val envArray = env.entries.map { "${it.key}=${it.value}" }.toTypedArray()
                ShizukuBridgeEngine.getInstance(context)
                    .openProcess(arrayOf(shPath), envArray, privateDir.absolutePath)
                    ?: throw IllegalStateException("Shizuku gagal membuka proses shell")
            } else {
                val pb = ProcessBuilder(args)
                    .directory(privateDir)
                    .redirectErrorStream(true)
                pb.environment().putAll(env)
                pb.start()
            }
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
            isRunning = true
            isRootShell = rootMode
            _kind.value = kind
            startReaderThread(p)
            true
        } catch (e: Exception) {
            closeInternal(notify = false)
            return@withContext Pair(false, "Gagal membuka shell: ${e.message}")
        }

        if (!started) return@withContext Pair(false, "Gagal membuka shell.")

        writeRaw("printf '\\n__INS_READY__%s__\\n' \"\$(id -u)\"\n")
        val ready = readyLatch.await(12, TimeUnit.SECONDS)

        if (!ready) {
            val alive = process?.isAlive == true
            closeInternal(notify = false)
            val msg = if (rootMode) {
                "Shell root tidak merespons dalam 12 detik. " +
                    "Kalau dialog izin root muncul, setujui lalu coba lagi."
            } else {
                "Shell sistem tidak merespons dalam 12 detik."
            }
            return@withContext Pair(false, msg + if (alive) "" else " (proses langsung mati)")
        }

        writeRaw("echo __INS_PID__\$\$__\n")
        pidLatch.await(5, TimeUnit.SECONDS)

        writeRaw("printf '\\n__INS_READY2__%s__\\n' \"\$(pwd)\"\n")

        val uid = readyUid ?: "?"
        val modeText = if (rootMode && uid == "0") "ROOT (UID 0)" else "APP (UID $uid)"
        isRootShell = rootMode && uid == "0"

        val msg = buildString {
            appendLine("Shell interaktif aktif — mode $modeText")
            appendLine("PID shell: ${if (shellPid > 0) shellPid else "tidak diketahui"}")
            appendLine("Shell: ${baseArgs.last()}  |  Ctrl+C: ${if (pidGroupCapable) "mematikan proses grup" else "terbatas"}")
            appendLine()
            appendLine("Semua perintah shell asli sekarang jalan: pipe (|), redirect (> >>), globbing (*),")
            appendLine("variabel, &&/||, for/while, fungsi, serta cd yang menetap antar perintah.")
        }
        Pair(true, msg)
    }

    private fun findSystemShell(): String {
        val candidates = listOf(
            "/system/bin/sh",
            "/system/xbin/sh",
            "/vendor/bin/sh",
            "/system/bin/bash",
            "/data/data/com.termux/files/usr/bin/bash"
        )
        return candidates.firstOrNull { File(it).exists() } ?: "/system/bin/sh"
    }


    private fun startReaderThread(p: Process) {
        val t = Thread {
            try {
                val br = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
                while (true) {
                    val line = br.readLine() ?: break
                    handleLine(line)
                }
            } catch (_: Exception) {
            } finally {
                val wasRunning = isRunning
                isRunning = false
                _kind.value = ShellKind.CLOSED
                if (wasRunning) {
                    listener?.onClosed("Shell berhenti (mungkin Anda mengetik 'exit').")
                }
            }
        }
        t.isDaemon = true
        t.name = "ins-shell-reader"
        readerThread = t
        t.start()
    }

    private fun handleLine(line: String) {
        if (line.contains("__INS_READY__")) {
            val uid = line.substringAfter("__INS_READY__").substringBefore("__").trim()
            if (uid.isNotEmpty()) readyUid = uid
            readyLatch.countDown()
            return
        }

        if (line.contains("__INS_PID__")) {
            val pid = line.substringAfter("__INS_PID__").substringBefore("__").trim().toLongOrNull()
            if (pid != null && pid > 0) shellPid = pid
            pidLatch.countDown()
            return
        }

        if (line.contains("__INS_READY2__")) {
            val dir = line.substringAfter("__INS_READY2__").substringBefore("__").trim()
            if (dir.isNotEmpty()) _cwd.value = dir
            return
        }

        if (line.startsWith("__INS_BEGIN_")) {
            pendingStartedAt = System.currentTimeMillis()
            return
        }

        if (line.startsWith("__INS_END_")) {
            val body = line.removePrefix("__INS_END_")
            val parts = body.split("__")
            val tag = parts.getOrNull(0) ?: ""
            val rc = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: -1
            val cwd = parts.getOrNull(2)?.trim() ?: _cwd.value

            if (tag == pendingTag) {
                if (cwd.isNotEmpty()) _cwd.value = cwd
                val duration = System.currentTimeMillis() - pendingStartedAt
                val cmd = pendingCommand
                pendingTag = null
                listener?.onCommandFinished(cmd, rc, cwd, duration)
            }
            return
        }

        listener?.onOutput(line)
    }


    private fun writeRaw(text: String) {
        try {
            writer?.write(text)
            writer?.flush()
        } catch (_: Exception) {
        }
    }

    fun submit(command: String, silent: Boolean = false) {
        val w = writer ?: return
        val t = "c${tagCounter.incrementAndGet()}"
        pendingTag = if (silent) null else t
        pendingCommand = command
        pendingStartedAt = System.currentTimeMillis()

        val sb = StringBuilder()
        sb.append("printf '\\n__INS_BEGIN_").append(t).append("__\\n'\n")
        sb.append(command).append('\n')
        sb.append("__ins_rc=\$?\n")
        sb.append("printf '\\n__INS_END_").append(t)
            .append("__%s__%s__\\n' \"\$__ins_rc\" \"\$PWD\"\n")

        try {
            w.write(sb.toString())
            w.flush()
        } catch (_: Exception) {
        }
    }

    suspend fun sendInterrupt(): Boolean = withContext(Dispatchers.IO) {
        val pid = shellPid
        if (!isRunning) return@withContext false

        if (pid > 0 && pidGroupCapable) {
            val priv = PrivilegeExecutionEngine.getInstance(context)
            val cmd = "kill -INT -$pid 2>/dev/null || kill -INT $pid 2>/dev/null"
            val res = if (isRootShell) priv.runRoot(cmd, timeoutMs = 5_000L) else priv.runShell(cmd, timeoutMs = 5_000L)
            if (res.success) return@withContext true
        }

        if (pid > 0) {
            val priv = PrivilegeExecutionEngine.getInstance(context)
            val cmd = "pkill -INT -P $pid 2>/dev/null; kill -INT $pid 2>/dev/null"
            val res = if (isRootShell) priv.runRoot(cmd, timeoutMs = 5_000L) else priv.runShell(cmd, timeoutMs = 5_000L)
            if (res.success) return@withContext true
        }

        false
    }

    suspend fun restart(useRoot: Boolean, restoreCwd: String? = null): Pair<Boolean, String> {
        val dir = restoreCwd ?: _cwd.value
        val kind = when {
            useRoot -> ShellKind.ROOT
            _kind.value == ShellKind.SHIZUKU -> ShellKind.SHIZUKU
            _kind.value == ShellKind.ADB -> ShellKind.ADB
            else -> ShellKind.APP
        }
        val res = start(kind)
        if (res.first && !dir.isNullOrBlank() && dir != "/") {
            submit("cd \"$dir\" 2>/dev/null || true", silent = true)
        }
        return res
    }


    fun close() {
        closeInternal(notify = true)
    }

    private fun closeInternal(notify: Boolean) {
        try {
            writer?.write("exit\n")
            writer?.flush()
        } catch (_: Exception) {
        }
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        try {
            process?.destroy()
            if (!(process?.waitFor(800, TimeUnit.MILLISECONDS) ?: true)) {
                process?.destroyForcibly()
            }
        } catch (_: Exception) {
        }
        process = null
        writer = null
        readerThread = null
        isRunning = false
        isRootShell = false
        shellPid = -1L
        _kind.value = ShellKind.CLOSED
        pendingTag = null
        if (notify) listener?.onClosed("Shell ditutup.")
    }


    fun listAvailableBinaries(): List<String> {
        val dirs = listOf("/system/bin", "/system/xbin", "/vendor/bin", "/product/bin")
        val set = sortedSetOf<String>()
        dirs.forEach { d ->
            File(d).listFiles()?.forEach { f ->
                if (f.isFile) set.add(f.name)
            }
        }
        return set.toList()
    }
}
