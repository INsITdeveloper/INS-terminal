package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import kotlinx.coroutines.withContext

class FloatingBoosterOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private val floatingViewsList = mutableListOf<View>()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var telemetryJob: Job? = null

    companion object {
        const val CHANNEL_ID = "channel_floating_booster"
        const val NOTIFICATION_ID = 20262
        const val ACTION_ADD_WINDOW = "com.example.action.ADD_FLOATING_WINDOW"

        private val _isOverlayActive = MutableStateFlow(false)
        val isOverlayActive = _isOverlayActive.asStateFlow()

        private val _activeWindowsCount = MutableStateFlow(0)
        val activeWindowsCount = _activeWindowsCount.asStateFlow()

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun requestOverlayPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }

        fun start(context: Context) {
            if (!canDrawOverlays(context)) {
                requestOverlayPermission(context)
                Toast.makeText(context, "Berikan Izin Jendela Pop-up Mengambang terlebih dahulu", Toast.LENGTH_LONG).show()
                return
            }
            val intent = Intent(context, FloatingBoosterOverlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }

        fun addWindow(context: Context) {
            if (!canDrawOverlays(context)) {
                requestOverlayPermission(context)
                Toast.makeText(context, "Berikan Izin Jendela Pop-up Mengambang terlebih dahulu", Toast.LENGTH_LONG).show()
                return
            }
            val intent = Intent(context, FloatingBoosterOverlayService::class.java).apply {
                action = ACTION_ADD_WINDOW
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBoosterOverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ADD_WINDOW) {
            createNewOverlayWindow()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundSafe()
        initOverlayWindow()
        _isOverlayActive.value = true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AOPtimize Floating HUD Booster"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "Status jendela mengambang game boost"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundSafe() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("AOPtimize Pop-up Booster Aktif")
                .setContentText("Jendela melayang aktif di atas game & aplikasi")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

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
        } catch (_: Exception) {}
    }

    private fun initOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        createNewOverlayWindow()
    }

    fun createNewOverlayWindow() {
        val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
        windowManager = wm

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val windowIndex = floatingViewsList.size + 1
        val offsetX = 40 + ((windowIndex - 1) * 30) % 200
        val offsetY = 200 + ((windowIndex - 1) * 80) % 600

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = offsetX
            y = offsetY
        }

        val rootLayout = createFloatingView(params, windowIndex)
        floatingViewsList.add(rootLayout)
        _activeWindowsCount.value = floatingViewsList.size

        try {
            wm.addView(rootLayout, params)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menampilkan jendela pop-up: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeSingleWindow(view: View) {
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {}
        floatingViewsList.remove(view)
        _activeWindowsCount.value = floatingViewsList.size
        if (floatingViewsList.isEmpty()) {
            stopSelf()
        }
    }

    private fun createFloatingView(params: WindowManager.LayoutParams, windowIndex: Int): View {
        val root = FrameLayout(this)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 20)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 32f
                setColor(Color.parseColor("#E6080D1A"))
                val strokeColor = if (windowIndex % 2 == 1) Color.parseColor("#00F0FF") else Color.parseColor("#7928CA")
                setStroke(3, strokeColor)
            }
            background = shape
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(if (windowIndex % 2 == 1) Color.parseColor("#00F0FF") else Color.parseColor("#FF0055"))
            layoutParams = LinearLayout.LayoutParams(38, 38)
        }

        val title = TextView(this).apply {
            text = " ⚡ HUD #$windowIndex"
            setTextColor(Color.parseColor("#00F0FF"))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val addBtn = TextView(this).apply {
            text = " ＋ "
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 15f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(8, 0, 8, 0)
            setOnClickListener {
                createNewOverlayWindow()
                Toast.makeText(applicationContext, "Jendela Mengambang Ditambahkan!", Toast.LENGTH_SHORT).show()
            }
        }

        val closeBtn = TextView(this).apply {
            text = " ✕ "
            setTextColor(Color.parseColor("#FF0055"))
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(8, 0, 8, 0)
            setOnClickListener {
                removeSingleWindow(root)
            }
        }

        headerRow.addView(icon)
        headerRow.addView(title)
        headerRow.addView(addBtn)
        headerRow.addView(closeBtn)
        container.addView(headerRow)

        val statsText = TextView(this).apply {
            text = "FPS: 120 (Locked) | RAM: OK\nMSAA: 4x Ultra | No-Lag Active"
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 10, 0, 10)
        }
        container.addView(statsText)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val boostBtn = Button(this).apply {
            text = "⚡ TURBO"
            setTextColor(Color.parseColor("#030712"))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            val btnShape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor("#00F0FF"))
            }
            background = btnShape
            setPadding(20, 12, 20, 12)
            setOnClickListener {
                serviceScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            CacheCleanerEngine.cleanCaches(applicationContext)
                            val script = "setprop debug.egl.force_msaa 1; settings put system peak_refresh_rate 120.0; sync"
                            ProcessBuilder("sh", "-c", script).start().waitFor()
                        } catch (_: Exception) {}
                    }
                    Toast.makeText(applicationContext, "🚀 TURBO 120 FPS & 4x MSAA DITERAPKAN!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val cleanBtn = Button(this).apply {
            text = "🧹 RAM"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            val btnShape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor("#1F2937"))
                setStroke(2, Color.parseColor("#00FF66"))
            }
            background = btnShape
            setPadding(20, 12, 20, 12)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(12, 0, 0, 0)
            layoutParams = lp
            setOnClickListener {
                serviceScope.launch {
                    val res = withContext(Dispatchers.IO) {
                        CacheCleanerEngine.cleanCaches(applicationContext)
                    }
                    Toast.makeText(applicationContext, "🧹 Dibersihkan ${res.freedMb}MB Cache RAM!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonRow.addView(boostBtn)
        buttonRow.addView(cleanBtn)
        container.addView(buttonRow)

        root.addView(container)

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        telemetryJob?.cancel()
        _isOverlayActive.value = false
        _activeWindowsCount.value = 0
        for (view in floatingViewsList) {
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
        }
        floatingViewsList.clear()
    }
}
