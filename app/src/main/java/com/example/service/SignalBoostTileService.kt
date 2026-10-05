package com.example.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.system.DisplaySystemEngine
import com.example.system.NetworkOptimizerEngine
import com.example.system.NetworkTweakState
import com.example.system.PrivilegeExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile "INS Boost".
 *
 * Tujuan: supaya penguat sinyal + carrier bisa dinyalakan SATU TAP langsung dari panel
 * notifikasi — tanpa harus buka app dan tekan tombol "AKTIFKAN SEMUA PENGUAT SINYAL &
 * CARRIER". Sangat berguna saat sedang main game (layar penuh, tidak bisa keluar app).
 *
 * Tile ini menjalankan profil boost yang sama dengan tombol di dalam app
 * (NetworkTweakState() default = semua penguat sinyal/carrier aktif).
 */
class SignalBoostTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "INS Boost"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Sinyal + Carrier"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Mengaktifkan..."
            updateTile()
        }

        scope.launch {
            val ok = runCatching {
                val engine = NetworkOptimizerEngine()
                val script = engine.generateUltraSignalAndStreamingScript(NetworkTweakState())
                val netOk = PrivilegeExecutionEngine.getInstance(applicationContext)
                    .runBest(script, 20000L)
                    .success

                // Sekalian paksa layar ke refresh rate tertinggi yang didukung HP,
                // supaya game bisa naik FPS (mis. 60 -> 90/120 Hz bila panelnya mampu).
                runCatching { DisplaySystemEngine().unlockFps(applicationContext, null) }

                netOk
            }.getOrDefault(false)

            withContext(Dispatchers.Main) {
                val text = if (ok) {
                    "✓ Penguat sinyal & carrier aktif"
                } else {
                    "✗ Butuh akses shell (ADB/Shizuku/root)"
                }
                Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
                qsTile?.apply {
                    state = if (ok) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Sinyal + Carrier"
                    updateTile()
                }
            }
        }
    }
}
