package com.example.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.system.HdCaptureEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile "HD Screenshot".
 *
 * Supaya rasanya seperti tombol screenshot bawaan sistem: tarik panel notifikasi →
 * tap tile → screenshot PNG lossless resolusi native langsung tersimpan.
 *
 * (Kualitas screenshot/recording BAWAAN sistem tidak bisa diubah oleh app pihak ketiga —
 * lihat penjelasan di chat. Tile ini menjadikan capture milik app praktis satu-tap.)
 */
class HdCaptureTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "HD Screenshot"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Native PNG"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Mengambil..."
            updateTile()
        }

        scope.launch {
            val res = HdCaptureEngine(applicationContext).captureHdScreenshot()
            withContext(Dispatchers.Main) {
                val text = if (res.isSuccess) {
                    "✓ Screenshot HD tersimpan"
                } else {
                    "✗ " + res.message.lineSequence().firstOrNull().orEmpty()
                }
                Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
                qsTile?.apply {
                    state = Tile.STATE_INACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Native PNG"
                    updateTile()
                }
            }
        }
    }
}
