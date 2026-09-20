package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.system.CacheCleanerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AutoCacheServiceState(
    val isRunning: Boolean = false,
    val intervalSec: Int = 120,
    val cleanCount: Int = 0,
    val lastFreedMb: Long = 0L,
    val totalAccumulatedFreedMb: Long = 0L,
    val lastCleanTimestamp: String = "Belum berjalan"
)

class AutoCacheCleanerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var cleanerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentIntervalSec = 120
    private var cleanCount = 0
    private var totalAccumulatedFreedMb = 0L
    private var lastFreedMb = 0L

    companion object {
        const val CHANNEL_ID = "channel_aoptimize_autocache"
        const val NOTIFICATION_ID = 20261

        const val ACTION_START = "com.example.service.ACTION_START_CACHE_DAEMON"
        const val ACTION_STOP = "com.example.service.ACTION_STOP_CACHE_DAEMON"
        const val ACTION_CLEAN_NOW = "com.example.service.ACTION_CLEAN_NOW"
        const val EXTRA_INTERVAL_SEC = "extra_interval_sec"

        private val _serviceState = MutableStateFlow(AutoCacheServiceState())
        val serviceState = _serviceState.asStateFlow()

        fun start(context: Context, intervalSec: Int = 120) {
            try {
                val intent = Intent(context, AutoCacheCleanerService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_INTERVAL_SEC, intervalSec)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {

                try {
                    val intent = Intent(context, AutoCacheCleanerService::class.java).apply {
                        action = ACTION_START
                        putExtra(EXTRA_INTERVAL_SEC, intervalSec)
                    }
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, AutoCacheCleanerService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun cleanNow(context: Context) {
            try {
                val intent = Intent(context, AutoCacheCleanerService::class.java).apply {
                    action = ACTION_CLEAN_NOW
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                val interval = intent?.getIntExtra(EXTRA_INTERVAL_SEC, currentIntervalSec) ?: currentIntervalSec
                currentIntervalSec = interval.coerceIn(15, 3600)
                startForegroundDaemon()
            }
            ACTION_STOP -> {
                stopForegroundDaemon()
                stopSelf()
            }
            ACTION_CLEAN_NOW -> {
                performSingleSweep()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AOPtimize Background Cache Cleaner"
            val desc = "Notifikasi status pembersihan cache otomatis di latar belakang"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AOPtimize:AutoCacheDaemonWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    private fun startForegroundDaemon() {
        try {
            val notification = buildNotification("Daemon Aktif: Memeriksa setiap ${currentIntervalSec}s")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {

        }

        _serviceState.value = AutoCacheServiceState(
            isRunning = true,
            intervalSec = currentIntervalSec,
            cleanCount = cleanCount,
            lastFreedMb = lastFreedMb,
            totalAccumulatedFreedMb = totalAccumulatedFreedMb,
            lastCleanTimestamp = formatCurrentTime()
        )

        cleanerJob?.cancel()
        cleanerJob = serviceScope.launch {

            performSingleSweep()

            while (isActive) {
                delay(currentIntervalSec * 1000L)
                performSingleSweep()
            }
        }
    }

    private fun performSingleSweep() {
        serviceScope.launch {
            try {
                val res = CacheCleanerEngine.cleanCaches(applicationContext)
                cleanCount++
                lastFreedMb = res.freedMb
                totalAccumulatedFreedMb += res.freedMb

                try {
                    val script = """
                        setprop debug.egl.force_msaa 1
                        setprop debug.hwui.renderer skiavk
                        setprop debug.composition.type gpu
                        setprop debug.thermal.throttle.disable 1
                        sync
                    """.trimIndent()
                    ProcessBuilder("sh", "-c", script).start().waitFor()
                } catch (_: Exception) {}

                val timeStr = formatCurrentTime()

                _serviceState.value = AutoCacheServiceState(
                    isRunning = true,
                    intervalSec = currentIntervalSec,
                    cleanCount = cleanCount,
                    lastFreedMb = lastFreedMb,
                    totalAccumulatedFreedMb = totalAccumulatedFreedMb,
                    lastCleanTimestamp = timeStr
                )

                updateNotification(
                    "Siklus #$cleanCount Selesai ($timeStr) • Bebas ${res.freedMb}MB (Total: ${totalAccumulatedFreedMb}MB)"
                )
            } catch (_: Exception) {}
        }
    }

    private fun stopForegroundDaemon() {
        cleanerJob?.cancel()
        cleanerJob = null
        try {
            wakeLock?.release()
        } catch (_: Exception) {}

        _serviceState.value = _serviceState.value.copy(isRunning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanNowIntent = Intent(this, AutoCacheCleanerService::class.java).apply {
            action = ACTION_CLEAN_NOW
        }
        val pendingCleanNow = PendingIntent.getService(
            this, 1, cleanNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AutoCacheCleanerService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("⚡ AOPtimize Auto-Cache Cleaner [AKTIF]")
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$statusText\nInterval Pembersihan: Setiap ${currentIntervalSec} detik\nTotal Memori Dibersihkan: ${totalAccumulatedFreedMb} MB"
            ))
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_rotate, "BERSIHKAN SEKARANG", pendingCleanNow)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "MATIKAN", pendingStop)
            .build()
    }

    private fun formatCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    override fun onDestroy() {
        stopForegroundDaemon()
        super.onDestroy()
    }
}
