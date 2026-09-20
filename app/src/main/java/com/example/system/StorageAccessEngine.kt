package com.example.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

object StorageAccessEngine {

    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            read && write
        }
    }

    fun hasMediaAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val img = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val vid = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val aud = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            img || vid || aud
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun openAllFilesAccessSettings(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                } catch (_: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    true
                }
            } else {
                openAppDetailsSettings(context)
            }
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun requestStorageAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openAllFilesAccessSettings(context)
        } else {
            openAppDetailsSettings(context)
        }
    }

    fun getExternalStorageRoot(): String {
        return try {
            Environment.getExternalStorageDirectory().absolutePath
        } catch (_: Exception) {
            "/storage/emulated/0"
        }
    }

    fun resolvePath(inputPath: String, currentDir: String, context: Context): File {
        val rootExternal = getExternalStorageRoot()
        val appHome = context.filesDir.absolutePath

        val trimmed = inputPath.trim()
        val expanded = when {
            trimmed.startsWith("~") -> trimmed.replaceFirst("~", appHome)
            trimmed.startsWith("/sdcard") -> trimmed.replaceFirst("/sdcard", rootExternal)
            else -> trimmed
        }

        val file = if (expanded.startsWith("/")) {
            File(expanded)
        } else {
            val baseExpanded = when {
                currentDir.startsWith("~") -> currentDir.replaceFirst("~", appHome)
                currentDir.startsWith("/sdcard") -> currentDir.replaceFirst("/sdcard", rootExternal)
                else -> currentDir
            }
            File(File(baseExpanded), expanded)
        }

        return try {
            file.canonicalFile
        } catch (_: Exception) {
            file.absoluteFile
        }
    }
}
