package com.example.system

import android.content.Context
import android.content.pm.PackageManager
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

class ShizukuBridgeEngine private constructor(private val context: Context) {

    companion object {
        const val REQUEST_CODE = 4213
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

        @Volatile
        private var INSTANCE: ShizukuBridgeEngine? = null

        @Volatile
        private var newProcessMethod: Method? = null

        fun getInstance(context: Context): ShizukuBridgeEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShizukuBridgeEngine(context.applicationContext).also { INSTANCE = it }
            }
    }


    fun isInstalled(): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun version(): Int = try {
        Shizuku.getVersion()
    } catch (_: Throwable) {
        -1
    }

    fun isPreV11(): Boolean = try {
        Shizuku.isPreV11()
    } catch (_: Throwable) {
        true
    }

    fun hasPermission(): Boolean = try {
        if (!isRunning() || isPreV11()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun shouldShowRationale(): Boolean = try {
        Shizuku.shouldShowRequestPermissionRationale()
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(onResult: ((Boolean) -> Unit)? = null) {
        try {
            if (onResult != null) {
                val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                    onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                }
                Shizuku.addRequestPermissionResultListener(listener)
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
    }

    fun addBinderReceivedListener(onReceived: () -> Unit) {
        try {
            Shizuku.addBinderReceivedListenerSticky { onReceived() }
        } catch (_: Throwable) {
        }
    }


    fun canExecute(): Boolean = isRunning() && hasPermission()

    fun openProcess(args: Array<String>, env: Array<String>? = null, dir: String? = null): Process? {
        if (!canExecute()) return null
        return try {
            val m = resolveNewProcess()
                ?: return null
            m.invoke(null, args, env, dir) as? Process
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveNewProcess(): Method? {
        newProcessMethod?.let { return it }
        return synchronized(this) {
            newProcessMethod ?: try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                m.isAccessible = true
                newProcessMethod = m
                m
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun runShell(command: String, timeoutMs: Long = 20_000L): ShellResult {
        if (!isRunning()) {
            return ShellResult(
                success = false, viaRoot = false, exitCode = -1, output = "",
                error = "Shizuku tidak berjalan. Buka aplikasi Shizuku lalu mulai layanannya."
            )
        }
        if (!hasPermission()) {
            return ShellResult(
                success = false, viaRoot = false, exitCode = -1, output = "",
                error = "Izin Shizuku belum diberikan. Jalankan perintah `shizuku` untuk memintanya."
            )
        }

        val process = openProcess(arrayOf("sh", "-c", command))
            ?: return ShellResult(
                success = false, viaRoot = false, exitCode = -1, output = "",
                error = "Gagal membuka proses lewat Shizuku."
            )

        return try {
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
            ShellResult(
                success = finished && code == 0,
                viaRoot = false,
                exitCode = code,
                output = buffer.toString().trimEnd(),
                timedOut = !finished,
                error = if (!finished) "Perintah melewati batas ${timeoutMs / 1000} detik." else null
            )
        } catch (e: Exception) {
            ShellResult(false, false, -1, "", false, e.message)
        } finally {
            try {
                process.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun describe(): String = buildString {
        appendLine(" • Shizuku terpasang : ${if (isInstalled()) "YA" else "TIDAK"}")
        appendLine(" • Layanan berjalan  : ${if (isRunning()) "YA" else "TIDAK"}")
        appendLine(" • Versi Shizuku     : ${version()}")
        appendLine(" • Izin diberikan    : ${if (hasPermission()) "YA" else "BELUM"}")
    }

    fun adbGrantCommand(): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
}
