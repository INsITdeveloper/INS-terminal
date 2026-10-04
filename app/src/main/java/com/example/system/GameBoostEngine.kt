package com.example.system

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GameBoostResult(
    val isSuccess: Boolean,
    val targetPackage: String?,
    val message: String
)

/**
 * Anti-stutter / anti-macet untuk game — dijalankan lewat jalur privilege yang ada
 * (root / Shizuku / ADB nirkabel), jadi **bisa bekerja tanpa root**.
 *
 * Yang dipakai adalah API resmi Android, bukan trik aneh:
 *  - Android 12+ **Game Mode API** (`cmd game set --mode performance <pkg>`) -> minta sistem
 *    memberi prioritas performa (bukan battery) untuk game tersebut.
 *  - **Battery whitelist** (`cmd deviceidle whitelist`) -> game tidak "dibekukan" saat layar
 *    sesaat tidak disentuh (penyebab klasik frame drop/macet).
 *  - **`am set-inactive false`** -> app tidak ditandai idle.
 *  - **Skala animasi 0.5x** -> jank saat transisi/UI berkurang, frame terasa lebih stabil.
 *  - Root-only (kalau ada): governor CPU `performance` + matikan thermal throttling.
 */
class GameBoostEngine(private val context: Context) {

    private val privilege = PrivilegeExecutionEngine.getInstance(context)

    /** Deteksi paket app yang sedang tampil di layar (game yang sedang dimainkan). */
    fun currentForegroundPackage(): String? {
        val probes = listOf(
            "dumpsys activity activities 2>/dev/null | grep -m1 -E 'mResumedActivity|topResumedActivity'",
            "dumpsys window 2>/dev/null | grep -m1 -E 'mCurrentFocus|mFocusedApp'",
            "dumpsys activity top 2>/dev/null | grep -m1 ACTIVITY"
        )
        for (probe in probes) {
            val out = privilege.runBest(probe, timeoutMs = 8000L).output
            val pkg = parsePackage(out)
            if (!pkg.isNullOrBlank() && pkg != "com.android.systemui") return pkg
        }
        return null
    }

    private fun parsePackage(text: String): String? {
        // Contoh: "mResumedActivity: ActivityRecord{... u0 com.miHoYo.GenshinImpact/com... }"
        val patterns = listOf(
            Regex("""\su\d+\s+([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/"""),
            Regex("""([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+){1,})/[a-zA-Z0-9_.$]+"""),
            Regex("""Activity\s+([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/""")
        )
        for (p in patterns) {
            val m = p.find(text)
            if (m != null) {
                val candidate = m.groupValues[1]
                if (!candidate.startsWith("android") && candidate.contains(".")) return candidate
            }
        }
        return null
    }

    suspend fun applyGameBoost(targetPackage: String? = null): GameBoostResult = withContext(Dispatchers.IO) {
        val pkg = targetPackage?.trim()?.takeIf { it.isNotEmpty() } ?: currentForegroundPackage()

        val script = buildString {
            appendLine("echo INS_GAME_BOOST_START")
            if (!pkg.isNullOrBlank()) {
                appendLine("# ── Android 12+ Game Mode API: minta prioritas performa ──")
                appendLine("(cmd game set --mode performance \"$pkg\" >/dev/null 2>&1 || cmd game set --mode 2 \"$pkg\" >/dev/null 2>&1 || cmd game set --mode performance --user 0 \"$pkg\" >/dev/null 2>&1) && echo 'GAME_MODE=performance' || echo 'GAME_MODE=unsupported'")
                appendLine("# ── Jangan bekukan game (anti frame-drop) ──")
                appendLine("(cmd deviceidle whitelist +\"$pkg\" >/dev/null 2>&1) && echo 'BATTERY_WHITELIST=ok' || echo 'BATTERY_WHITELIST=fail'")
                appendLine("(am set-inactive \"$pkg\" false >/dev/null 2>&1) && echo 'SET_INACTIVE=ok' || echo 'SET_INACTIVE=fail'")
                appendLine("(cmd game set --mode performance \"$pkg\" >/dev/null 2>&1) && echo 'GAME_MODE_SET=ok' || true")
            } else {
                appendLine("echo 'TARGET=none (game tidak terdeteksi, boost sistem saja)'")
            }
            appendLine("# ── Kurangi jank animasi (frame terasa lebih stabil) ──")
            appendLine("settings put global window_animation_scale 0.5 >/dev/null 2>&1 && echo 'ANIM_WINDOW=0.5' || true")
            appendLine("settings put global transition_animation_scale 0.5 >/dev/null 2>&1 && echo 'ANIM_TRANSITION=0.5' || true")
            appendLine("settings put global animator_duration_scale 0.5 >/dev/null 2>&1 && echo 'ANIM_DURATION=0.5' || true")
            appendLine("# ── Root-only (dilewati kalau bukan root) ──")
            appendLine("for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \"\$g\" 2>/dev/null && echo \"GOV=\$g:performance\"; done")
            appendLine("for t in /sys/class/thermal/thermal_zone*/mode; do echo disabled > \"\$t\" 2>/dev/null && echo \"THERMAL=\$t:disabled\"; done")
            appendLine("echo INS_GAME_BOOST_END")
        }

        val res = privilege.runBest(script, timeoutMs = 45_000L)
        val out = res.output
        val applied = out.contains("GAME_MODE=performance") || out.contains("GAME_MODE_SET=ok") ||
            out.contains("ANIM_WINDOW=0.5") || out.contains("BATTERY_WHITELIST=ok")

        val summary = buildString {
            appendLine(if (pkg.isNullOrBlank()) "🎮 Game Boost (sistem) diterapkan." else "🎮 Game Boost diterapkan untuk: $pkg")
            appendLine("• Game Mode performa : ${if (out.contains("GAME_MODE=performance")) "AKTIF" else "tidak didukung sistem (butuh Android 12+)"}")
            appendLine("• Battery whitelist  : ${if (out.contains("BATTERY_WHITELIST=ok")) "AKTIF (game tidak dibekukan)" else "GAGAL/butuh izin"}")
            appendLine("• Anti-idle app      : ${if (out.contains("SET_INACTIVE=ok")) "AKTIF" else "GAGAL/butuh izin"}")
            appendLine("• Skala animasi      : ${if (out.contains("ANIM_WINDOW=0.5")) "0.5x (jank berkurang)" else "tidak berubah"}")
            appendLine("• CPU governor       : ${if (out.contains(":performance")) "performance (root)" else "butuh root — dilewati"}")
            appendLine("• Thermal throttle   : ${if (out.contains(":disabled")) "dimatikan (root)" else "butuh root — dilewati"}")
            if (!applied) {
                appendLine()
                appendLine("⚠️ Tidak ada perubahan yang berhasil. Pastikan Shizuku / ADB nirkabel aktif, lalu GRANT WRITE_SECURE_SETTINGS.")
            }
        }

        GameBoostResult(
            isSuccess = applied,
            targetPackage = pkg,
            message = summary.toString().trim()
        )
    }

    suspend fun revertGameBoost(): GameBoostResult = withContext(Dispatchers.IO) {
        val script = """
            settings put global window_animation_scale 1.0 >/dev/null 2>&1 || true
            settings put global transition_animation_scale 1.0 >/dev/null 2>&1 || true
            settings put global animator_duration_scale 1.0 >/dev/null 2>&1 || true
            echo INS_GAME_REVERT_OK
        """.trimIndent()
        val res = privilege.runBest(script, timeoutMs = 20_000L)
        GameBoostResult(
            isSuccess = res.output.contains("INS_GAME_REVERT_OK"),
            targetPackage = null,
            message = "♻️ Pengaturan game boost dikembalikan (skala animasi 1.0x)."
        )
    }
}
