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
import com.example.system.DisplaySystemEngine
import com.example.system.PerformanceGovernorEngine
import com.example.system.SettingsPersistenceEngine
import com.example.system.SystemShellEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PersistentBoosterState(
    val isRunning: Boolean = false,
    val activeGovernor: String = "Extreme Turbo Gaming",
    val lockedRefreshRate: Int = 120,
    val reapplyCount: Int = 0,
    val lastAppliedTime: Long = 0L,
    val totalCleanedMb: Long = 0L
)

class PersistentBoosterService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var daemonJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var persistenceEngine: SettingsPersistenceEngine
    private lateinit var shellEngine: SystemShellEngine
    private val displayEngine = DisplaySystemEngine()
    private val governorEngine = PerformanceGovernorEngine()

    private var reapplyCount = 0
    private var totalCleanedMb = 0L

    companion object {
        const val CHANNEL_ID = "channel_aoptimize_persistent_booster"
        const val NOTIFICATION_ID = 20263

        const val ACTION_START = "com.example.service.ACTION_START_PERSISTENT_BOOSTER"
        const val ACTION_STOP = "com.example.service.ACTION_STOP_PERSISTENT_BOOSTER"
        const val ACTION_QUICK_BOOST = "com.example.service.ACTION_QUICK_BOOST"
        const val ACTION_TOGGLE_HUD = "com.example.service.ACTION_TOGGLE_HUD"

        private val _boosterState = MutableStateFlow(PersistentBoosterState())
        val boosterState = _boosterState.asStateFlow()

        fun start(context: Context) {
            try {
                val intent = Intent(context, PersistentBoosterService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                try {
                    val intent = Intent(context, PersistentBoosterService::class.java).apply {
                        action = ACTION_START
                    }
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, PersistentBoosterService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        persistenceEngine = SettingsPersistenceEngine(this)
        shellEngine = SystemShellEngine(this)
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                startForegroundBooster()
            }
            ACTION_STOP -> {
                stopForegroundBooster()
                stopSelf()
            }
            ACTION_QUICK_BOOST -> {
                performQuickClean()
            }
            ACTION_TOGGLE_HUD -> {
                toggleOverlayHUD()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AOPtimize Game Booster Daemon"
            val desc = "Menjaga performa CPU, GPU, Refresh Rate & RAM tetap terkunci aktif saat bermain game"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AOPtimize::PersistentBoosterLock")?.apply {
                    setReferenceCounted(false)
                    acquire(12 * 60 * 60 * 1000L)
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (_: Exception) {}
    }

    private fun startForegroundBooster() {
        try {
            val notification = buildBoosterNotification("⚡ Tweak Gaming Aktif di Latar Belakang")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {}

        applyActiveTweaks()

        daemonJob?.cancel()
        daemonJob = serviceScope.launch {
            _boosterState.value = _boosterState.value.copy(
                isRunning = true,
                lastAppliedTime = System.currentTimeMillis()
            )

            while (isActive) {
                delay(45_000L)
                applyActiveTweaks()
            }
        }
    }

    private fun applyActiveTweaks() {
        serviceScope.launch {
            try {
                val perfState = persistenceEngine.loadPerformanceState()
                val displayState = persistenceEngine.loadDisplayState()

                reapplyCount++

                val displayScript = displayEngine.generateDisplayTweaksScript(displayState)
                val govScript = governorEngine.generateGovernorScript(perfState.activeGovernor, perfState.swappiness)

                shellEngine.executeCommand("sh -c \"$displayScript\n$govScript\"", "/storage/emulated/0")

                _boosterState.value = _boosterState.value.copy(
                    isRunning = true,
                    activeGovernor = perfState.activeGovernor.title,
                    lockedRefreshRate = displayState.refreshRateHz,
                    reapplyCount = reapplyCount,
                    lastAppliedTime = System.currentTimeMillis()
                )

                updateNotification("⚡ ${perfState.activeGovernor.title} | ${displayState.refreshRateHz}Hz Locked")
            } catch (_: Exception) {}
        }
    }

    private fun performQuickClean() {
        serviceScope.launch {
            val res = CacheCleanerEngine.cleanCaches(applicationContext)
            totalCleanedMb += res.freedMb
            _boosterState.value = _boosterState.value.copy(
                totalCleanedMb = totalCleanedMb
            )
            updateNotification("🚀 RAM Dibersihkan +${res.freedMb}MB | CPU & GPU Boost Locked")
        }
    }

    private fun toggleOverlayHUD() {
        if (FloatingBoosterOverlayService.canDrawOverlays(this)) {
            if (FloatingBoosterOverlayService.isOverlayActive.value) {
                FloatingBoosterOverlayService.stop(this)
            } else {
                FloatingBoosterOverlayService.start(this)
            }
        }
    }

    private fun buildBoosterNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val cleanIntent = Intent(this, PersistentBoosterService::class.java).apply {
            action = ACTION_QUICK_BOOST
        }
        val pendingClean = PendingIntent.getService(
            this,
            1,
            cleanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val hudIntent = Intent(this, PersistentBoosterService::class.java).apply {
            action = ACTION_TOGGLE_HUD
        }
        val pendingHud = PendingIntent.getService(
            this,
            2,
            hudIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle("⚡ AOPtimize Gaming Daemon Active")
            .setContentText(statusText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$statusText\n✓ CPU Governor & Vulkan GPU Pipeline Locked\n✓ Touch Polling 360Hz & Zero-Lag Shaders\n✓ Tweak tetap aktif saat aplikasi keluar / membuka game"
                )
            )
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stat_clean, "Clean RAM", pendingClean)
            .addAction(android.R.drawable.ic_menu_manage, "HUD Overlay", pendingHud)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildBoosterNotification(statusText))
    }

    private fun stopForegroundBooster() {
        daemonJob?.cancel()
        daemonJob = null
        _boosterState.value = _boosterState.value.copy(isRunning = false)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopForegroundBooster()
        super.onDestroy()
    }
}
