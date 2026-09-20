package com.example.system

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val launchActivity: String?,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

data class FreeformStatus(
    val isFreeformSupported: Boolean,
    val isForceResizableEnabled: Boolean,
    val hasWriteSecureSettings: Boolean,
    val activeMode: String,
    val adbCommand: String = "adb shell settings put global enable_freeform_support 1 && adb shell settings put global force_resizable_activities 1"
)

class AppFloatingLauncherEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AppFloatingLauncher"

        @Volatile
        private var INSTANCE: AppFloatingLauncherEngine? = null

        fun getInstance(context: Context): AppFloatingLauncherEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppFloatingLauncherEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun checkFreeformStatus(): FreeformStatus {
        val cr = context.contentResolver
        val freeformVal = try {
            Settings.Global.getInt(cr, "enable_freeform_support", 0)
        } catch (_: Exception) { 0 }

        val forceResizable = try {
            Settings.Global.getInt(cr, "force_resizable_activities", 0)
        } catch (_: Exception) { 0 }

        val hasSecure = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

        val isSupported = freeformVal == 1 || forceResizable == 1 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

        val mode = when {
            freeformVal == 1 && forceResizable == 1 -> "Freeform Multi-Window AKTIF (Siap Buka Banyak Jendela)"
            hasSecure -> "WriteSecureSettings Tersedia (Dapat Diaktifkan Instan)"
            else -> "Memerlukan Izin Developer Options / ADB"
        }

        return FreeformStatus(
            isFreeformSupported = isSupported,
            isForceResizableEnabled = forceResizable == 1,
            hasWriteSecureSettings = hasSecure,
            activeMode = mode
        )
    }

    suspend fun enableFreeformSystemWide(): Boolean = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        var success = false

        try {
            Settings.Global.putInt(cr, "enable_freeform_support", 1)
            Settings.Global.putInt(cr, "force_resizable_activities", 1)
            Settings.Global.putInt(cr, "freeform_window_management", 1)
            success = true
        } catch (e: Exception) {
            Log.w(TAG, "Gagal melalui Settings.Global: ${e.message}")
        }

        if (!success) {
            val cmds = arrayOf(
                "settings put global enable_freeform_support 1",
                "settings put global force_resizable_activities 1",
                "settings put global freeform_window_management 1"
            )
            for (cmd in cmds) {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                    p.waitFor()
                    success = true
                } catch (_: Exception) {}
            }
        }

        success
    }

    suspend fun getLaunchableApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val result = mutableListOf<InstalledAppItem>()

        for (ri in resolveInfos) {
            try {
                val pkgName = ri.activityInfo.packageName
                if (pkgName == context.packageName) continue

                val appName = ri.loadLabel(pm).toString()
                val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
                val isSys = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val activityName = ri.activityInfo.name

                result.add(
                    InstalledAppItem(
                        appName = appName,
                        packageName = pkgName,
                        launchActivity = activityName,
                        icon = icon,
                        isSystemApp = isSys
                    )
                )
            } catch (_: Exception) {}
        }

        result.sortedBy { it.appName.lowercase() }
    }

    fun launchAppInFloatingWindow(
        packageName: String,
        launchActivity: String?,
        windowIndex: Int = 0
    ): Boolean {
        val pm = context.packageManager

        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        val windowW = (screenW * 0.72).toInt().coerceAtLeast(600)
        val windowH = (screenH * 0.55).toInt().coerceAtLeast(800)

        val offsetX = (60 + (windowIndex * 80)) % (screenW - windowW).coerceAtLeast(1)
        val offsetY = (150 + (windowIndex * 120)) % (screenH - windowH).coerceAtLeast(1)

        val bounds = Rect(offsetX, offsetY, offsetX + windowW, offsetY + windowH)

        val intent = if (launchActivity != null) {
            Intent().apply {
                setClassName(packageName, launchActivity)
            }
        } else {
            pm.getLaunchIntentForPackage(packageName)
        } ?: return false

        intent.apply {

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            addFlags(0x10000000)
            addFlags(0x00002000)

            putExtra("android.intent.extra.TASK_ID", -1)
            putExtra("is_freeform", true)
            putExtra("freeform_bounds", bounds)
        }

        val options = ActivityOptions.makeBasic()

        try {
            val setBoundsMethod = ActivityOptions::class.java.getMethod("setLaunchBounds", Rect::class.java)
            setBoundsMethod.invoke(options, bounds)
        } catch (e: Exception) {
            Log.w(TAG, "setLaunchBounds reflection: ${e.message}")
        }

        try {
            val setWindowingModeMethod = ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            setWindowingModeMethod.invoke(options, 5)
        } catch (e: Exception) {
            Log.w(TAG, "setLaunchWindowingMode reflection: ${e.message}")
        }

        val started = try {
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            Log.w(TAG, "startActivity standard failed: ${e.message}, mencoba fallback shell...")
            false
        }

        if (started) return true

        return launchViaShell(packageName, launchActivity, bounds)
    }

    private fun launchViaShell(pkg: String, act: String?, bounds: Rect): Boolean {
        val target = if (act != null) "$pkg/$act" else pkg

        val cmd = "am start -n $target -f 0x18000000 --windowingMode 5"
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            p.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}
