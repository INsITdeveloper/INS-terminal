package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CacheCleanResult(
    val freedBytes: Long,
    val freedMb: Long,
    val filesDeletedCount: Int,
    val details: String,
    val systemRamFreedMb: Long = 0L,
    val kernelDropCachesApplied: Boolean = false,
    val errors: List<String> = emptyList()
)

object CacheCleanerEngine {

    suspend fun cleanCaches(context: Context): CacheCleanResult = withContext(Dispatchers.IO) {
        var deletedFiles = 0
        var totalDeletedBytes = 0L
        val errors = mutableListOf<String>()

        fun purge(dir: File?, label: String) {
            if (dir == null || !dir.exists()) return
            try {
                val (count, bytes) = deleteDirectoryContents(dir)
                deletedFiles += count
                totalDeletedBytes += bytes
            } catch (e: Exception) {
                errors += "$label: ${e.message}"
            }
        }

        purge(context.cacheDir, "cacheDir")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            purge(context.codeCacheDir, "codeCacheDir")
        }
        purge(context.externalCacheDir, "externalCacheDir")
        purge(File(context.filesDir, "tmp"), "files/tmp")
        purge(File(context.noBackupFilesDir, "tmp"), "noBackup/tmp")

        try {
            val javaTmp = File(System.getProperty("java.io.tmpdir") ?: "")
            if (javaTmp.exists() && javaTmp.canWrite() && javaTmp.absolutePath.contains(context.packageName)) {
                purge(javaTmp, "java.io.tmpdir")
            }
        } catch (_: Exception) {
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        fun availableRamMb(): Long {
            val mi = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            return mi.availMem / (1024 * 1024)
        }

        val ramBefore = availableRamMb()

        System.gc()
        Thread.sleep(150)

        var kernelDrop = false
        val priv = PrivilegeExecutionEngine.getInstance(context)
        if (priv.isDeviceRooted()) {
            val res = priv.runRoot(
                "sync; [ -e /proc/sys/vm/drop_caches ] && echo 3 > /proc/sys/vm/drop_caches; " +
                    "echo DONE_DROP",
                timeoutMs = 15_000L
            )
            kernelDrop = res.success && res.output.contains("DONE_DROP")
            if (!kernelDrop) errors += "drop_caches: ${res.error ?: "exit ${res.exitCode}"}"
        } else {
            try {
                priv.runShell("sync", timeoutMs = 5_000L)
            } catch (_: Exception) {
            }
            errors += "drop_caches kernel butuh root (dilewati, bukan gagal)."
        }

        Thread.sleep(250)
        val ramAfter = availableRamMb()

        val systemRamFreed = (ramAfter - ramBefore).coerceAtLeast(0L)

        val freedMb = totalDeletedBytes / (1024 * 1024)

        val detailText = buildString {
            append("Dihapus $deletedFiles file (${freedMb} MB dari penyimpanan)")
            if (systemRamFreed > 0) append(" • RAM sistem bebas bertambah ${systemRamFreed} MB")
            if (kernelDrop) append(" • kernel drop_caches dijalankan")
        }

        CacheCleanResult(
            freedBytes = totalDeletedBytes,
            freedMb = freedMb,
            filesDeletedCount = deletedFiles,
            details = detailText,
            systemRamFreedMb = systemRamFreed,
            kernelDropCachesApplied = kernelDrop,
            errors = errors
        )
    }

    fun getStorageStats(): Triple<Long, Long, Long> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            Triple(total, free, total - free)
        } catch (_: Exception) {
            Triple(0L, 0L, 0L)
        }
    }

    private fun deleteDirectoryContents(dir: File): Pair<Int, Long> {
        var count = 0
        var bytes = 0L
        if (!dir.exists()) return Pair(0, 0L)

        val files = dir.listFiles() ?: return Pair(0, 0L)
        for (file in files) {
            if (file.isDirectory) {
                val sub = deleteDirectoryContents(file)
                count += sub.first
                bytes += sub.second
                try {
                    if (file.delete()) count++
                } catch (_: Exception) {
                }
            } else {
                val len = file.length()
                if (file.delete()) {
                    count++
                    bytes += len
                }
            }
        }
        return Pair(count, bytes)
    }
}
