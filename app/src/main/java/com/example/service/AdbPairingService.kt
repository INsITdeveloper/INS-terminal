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
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.system.AdbMdnsDiscovery
import com.example.system.AdbShellEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Asisten pairing ADB nirkabel ala Shizuku.
 *
 * Alur:
 *  1. User menekan "PAIR OTOMATIS" di app.
 *  2. Service mencari port pairing otomatis via mDNS (`_adb-tls-pairing._tcp`).
 *  3. Begitu ketemu, muncul notifikasi yang MEMINTA KODE PAIRING (inline reply) —
 *     jadi user tidak perlu bolak-balik antara layar Setelan dan app.
 *  4. Kode dikirim -> app otomatis pair lalu auto-connect.
 */
class AdbPairingService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var workJob: Job? = null

    companion object {
        const val CHANNEL_ID = "channel_adb_pairing"
        const val NOTIFICATION_ID = 20263
        const val KEY_CODE = "adb_pair_code"
        const val ACTION_SUBMIT_CODE = "com.example.action.ADB_SUBMIT_CODE"
        const val ACTION_STOP = "com.example.action.ADB_PAIRING_STOP"
        const val EXTRA_CODE = "extra_code"

        @Volatile
        var pairingPort: Int = -1
            private set

        fun start(context: Context) {
            val intent = Intent(context, AdbPairingService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun submitCode(context: Context, code: String) {
            val intent = Intent(context, AdbPairingService::class.java).apply {
                action = ACTION_SUBMIT_CODE
                putExtra(EXTRA_CODE, code)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AdbPairingService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundSafe(
            buildNotification(
                "Menyiapkan pairing ADB",
                "Mencari layanan pairing di jaringan lokal...",
                withInput = false
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SUBMIT_CODE -> {
                val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
                handleSubmittedCode(code)
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startSearch()
        }
        return START_STICKY
    }

    private fun startSearch() {
        if (workJob?.isActive == true) return
        workJob = scope.launch {
            notifyStatus(
                "Mencari pairing service...",
                "Buka Setelan → Opsi pengembang → Penelusuran nirkabel → \"Pair device with pairing code\". " +
                    "Biarkan dialog itu terbuka, port akan terdeteksi otomatis.",
                withInput = false
            )

            var found: Int? = null
            val deadline = System.currentTimeMillis() + 180_000L
            while (isActive && System.currentTimeMillis() < deadline) {
                val p = AdbMdnsDiscovery.discoverPairingPort(this@AdbPairingService, 5000L)
                if (p != null && p > 0) {
                    found = p
                    break
                }
                delay(800)
            }

            if (found != null) {
                pairingPort = found
                notifyStatus(
                    "Masukkan kode pairing",
                    "Port $found ditemukan. Tarik notifikasi ini, ketik 6 digit kode pairing, lalu tekan KIRIM KODE.",
                    withInput = true
                )
            } else {
                notifyStatus(
                    "Port pairing tidak ditemukan",
                    "Cara manual: buka INS Terminal → Tweaks → ADB Nirkabel, lalu masukkan Port + Kode dari dialog pairing.",
                    withInput = false
                )
            }
        }
    }

    private fun handleSubmittedCode(code: String) {
        workJob?.cancel()
        workJob = scope.launch {
            val port = pairingPort
            if (code.isBlank()) {
                notifyStatus("Kode kosong", "Tarik notifikasi, masukkan 6 digit kode, lalu kirim lagi.", withInput = true)
                return@launch
            }
            if (port <= 0) {
                notifyStatus(
                    "Port pairing belum terdeteksi",
                    "Buka dialog \"Pair device with pairing code\" dulu, tunggu port terdeteksi, lalu kirim kode.",
                    withInput = false
                )
                return@launch
            }

            notifyStatus("Memproses pairing...", "Menghubungkan ke port $port ...", withInput = false)

            val engine = AdbShellEngine.getInstance(applicationContext)
            val (ok, msg) = engine.pair(port, code)
            if (!ok) {
                notifyStatus(
                    "Pairing gagal",
                    "$msg\nBuka lagi dialog pairing (kode baru) lalu kirim kode yang baru.",
                    withInput = true
                )
                return@launch
            }

            val (autoOk, autoMsg) = engine.autoConnect(10_000L)
            if (autoOk) {
                notifyStatus("✅ ADB tersambung", "$autoMsg\nBuka INS Terminal untuk memakai tweak.", withInput = false)
                delay(4000)
                stopSelf()
                return@launch
            }

            val connectPort = AdbMdnsDiscovery.discoverConnectPort(applicationContext, 8000L)
            if (connectPort != null && connectPort > 0) {
                val (cOk, cMsg) = engine.connect(connectPort)
                if (cOk) {
                    notifyStatus("✅ ADB tersambung", "$cMsg\nBuka INS Terminal untuk memakai tweak.", withInput = false)
                    delay(4000)
                    stopSelf()
                } else {
                    notifyStatus("Pairing berhasil, koneksi gagal", "$cMsg\nBuka INS Terminal → ADB Nirkabel → AUTO CONNECT.", withInput = false)
                }
            } else {
                notifyStatus("Pairing berhasil", "Buka INS Terminal → Tweaks → ADB Nirkabel → AUTO CONNECT.", withInput = false)
            }
        }
    }

    private fun notifyStatus(title: String, text: String, withInput: Boolean) {
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification(title, text, withInput))
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(title: String, text: String, withInput: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_stat_bolt)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (withInput) {
            val replyIntent = Intent(this, AdbPairingCodeReceiver::class.java).apply {
                action = ACTION_SUBMIT_CODE
            }
            val pi = PendingIntent.getBroadcast(
                this, 1, replyIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val remoteInput = RemoteInput.Builder(KEY_CODE)
                .setLabel("Kode pairing 6 digit")
                .build()
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "KIRIM KODE",
                    pi
                ).addRemoteInput(remoteInput).build()
            )
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ADB Pairing Assistant",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Membantu pairing ADB nirkabel tanpa PC (Shizuku-style)"
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundSafe(notification: Notification) {
        try {
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
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        workJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
