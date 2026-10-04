package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput

/**
 * Menerima kode pairing yang diketik user langsung dari notifikasi (inline reply),
 * lalu meneruskannya ke [AdbPairingService] untuk diproses.
 */
class AdbPairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AdbPairingService.KEY_CODE)
            ?.toString()
            ?.trim()
            .orEmpty()

        AdbPairingService.submitCode(context, code)
    }
}
