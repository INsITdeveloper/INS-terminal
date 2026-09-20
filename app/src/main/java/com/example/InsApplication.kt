package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.util.Log

class InsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("InsApplication", "Caught background exception on ${thread.name}: ${throwable.message}", throwable)
            try {

                if (thread.name.contains("DefaultDispatcher") || thread.name.contains("IO") || thread.name.contains("AutoCache")) {
                    Log.w("InsApplication", "Suppressed non-fatal thread crash.")
                    return@setDefaultUncaughtExceptionHandler
                }
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {

                }
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
                ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {

                }
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {

                }
                else -> {

                }
            }
        } catch (e: Exception) {
            Log.w("InsApp", "onTrimMemory handled safely: ${e.message}")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onTerminate() {
        super.onTerminate()
    }
}
