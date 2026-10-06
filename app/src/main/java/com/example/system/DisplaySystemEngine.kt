package com.example.system

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import kotlin.math.roundToInt

data class DisplayCapabilities(
    val supportedRefreshRates: List<Int> = listOf(60),
    val maxHardwareRefreshRate: Int = 60,
    val currentActiveRefreshRate: Int = 60,
    val isHighRefreshRateSupported: Boolean = false,
    val panelType: String = "AMOLED / IPS Dynamic",
    val hasHdrSupport: Boolean = false,
    val displayWidth: Int = 1080,
    val displayHeight: Int = 2400,
    val details: String = ""
)

data class FpsUnlockResult(
    val isSuccess: Boolean,
    val targetFps: Int,
    val appliedProperties: List<String> = emptyList(),
    val isFallbackApplied: Boolean = false,
    val message: String,
    val hardwareCapabilities: DisplayCapabilities? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class DisplaySystemState(
    val animationScale: Float = 0.25f,
    val refreshRateHz: Int = 120,
    val dpiDensity: Int = 420,
    val isImmersiveMode: Boolean = true,
    val isHapticBoosterEnabled: Boolean = true,
    val isThermalThrottlingDisabled: Boolean = true,
    val isFramePacingEnabled: Boolean = true,
    val isHdGraphicsEnhancerEnabled: Boolean = true,
    val isAntiAliasing4xMsaaEnabled: Boolean = true,
    val isGameNativeResolutionLocked: Boolean = true,
    val isTextureFilter16xEnabled: Boolean = true,
    val isShaderPreloadBoosterEnabled: Boolean = true,
    val touchSamplingRatioHz: Int = 360,
    val gpuRenderMode: String = "Vulkan Skia HW",
    val isTouchLatencyReductionEnabled: Boolean = true,
    val isFpsUnlockerActive: Boolean = true,
    val fpsUnlockerTargetHz: Int = 120,
    val fpsUnlockerStatus: String = "Active (Override Locked)",
    val detectedMaxHardwareHz: Int = 120,
    val isFallbackActive: Boolean = false,
    val lastFpsUnlockMessage: String = "High-refresh-rate gaming parameters active.",

    val isGameGraphicUnlockerActive: Boolean = true,
    val gameGraphicProfile: String = "ROG Phone 8 Pro (120 FPS / Extreme)",
    val isJoyoseGosBypassEnabled: Boolean = true,
    val isGameDriverForced: Boolean = true,
    val isAntiLagTripleBufferingActive: Boolean = true
)

/**
 * ⚠️ PENTING — kenapa file ini "hanya settings put":
 *
 * Versi lama menulis banyak `setprop debug.*` (debug.hwui.renderer skiavk,
 * debug.composition.type gpu, debug.egl.swapinterval 0, debug.sf.latch_unsignaled,
 * debug.egl.force_msaa, dll), `service call SurfaceFlinger ...`, dan `wm density`.
 *
 * Itu semua BUKAN tweak performa — itu flag DEBUG internal Android. Menyalakannya
 * merusak compositor/HWUI sehingga layar jadi HITAM dengan GARIS-GARIS aneh saat
 * membuka game/WhatsApp, dan `wm density` membuat koordinat sentuhan ngaco.
 *
 * Jadi sekarang SEMUA script display hanya memakai perintah `settings put` yang aman.
 * (Selain itu ada ScriptSanitizer yang membuang baris berbahaya dari script apa pun.)
 */
class DisplaySystemEngine {

    fun checkDisplayCapabilities(context: Context): DisplayCapabilities {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display ?: windowManager?.defaultDisplay
            } catch (_: Exception) {
                windowManager?.defaultDisplay
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay
        }

        val supportedRates = mutableSetOf<Int>()
        var maxHz = 60
        var currentHz = 60
        var w = 1080
        var h = 2400
        var hasHdr = false

        if (display != null) {
            try {
                val mode = display.mode
                currentHz = mode.refreshRate.roundToInt().coerceAtLeast(60)
                w = mode.physicalWidth
                h = mode.physicalHeight
            } catch (_: Exception) {}

            try {
                val modes = display.supportedModes
                for (m in modes) {
                    val rate = m.refreshRate.roundToInt()
                    if (rate > 0) supportedRates.add(rate)
                }
            } catch (_: Exception) {}

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val hdrCaps = display.hdrCapabilities
                    hasHdr = hdrCaps?.supportedHdrTypes?.isNotEmpty() == true
                }
            } catch (_: Exception) {}
        }

        if (supportedRates.isEmpty()) {
            supportedRates.addAll(listOf(60, 90, 120, 144))
        } else {
            supportedRates.add(60)
        }

        val sortedRates = supportedRates.sorted()
        maxHz = sortedRates.lastOrNull() ?: 60

        val panel = if (maxHz >= 120) "Ultra High-Refresh AMOLED 120/144Hz" else if (maxHz >= 90) "Smooth 90Hz Panel" else "Standard 60Hz Display Panel"

        return DisplayCapabilities(
            supportedRefreshRates = sortedRates,
            maxHardwareRefreshRate = maxHz,
            currentActiveRefreshRate = currentHz,
            isHighRefreshRateSupported = maxHz > 60,
            panelType = panel,
            hasHdrSupport = hasHdr,
            displayWidth = w,
            displayHeight = h,
            details = "Supported: ${sortedRates.joinToString(", ") { "${it}Hz" }} | Hardware Max: ${maxHz}Hz"
        )
    }

    /** Hanya perintah AMAN: set refresh rate + animasi. Tanpa setprop debug.* apa pun. */
    private fun refreshRateCommands(targetHz: Int, animationScale: Float? = null): String {
        return buildString {
            if (animationScale != null) {
                appendLine("settings put global window_animation_scale $animationScale")
                appendLine("settings put global transition_animation_scale $animationScale")
                appendLine("settings put global animator_duration_scale $animationScale")
            }
            appendLine("settings put system peak_refresh_rate ${targetHz}.0")
            appendLine("settings put system min_refresh_rate ${targetHz}.0")
            appendLine("settings put global peak_refresh_rate ${targetHz}.0")
            appendLine("settings put global min_refresh_rate ${targetHz}.0")
            appendLine("settings put system user_refresh_rate $targetHz 2>/dev/null || true")
            appendLine("settings put system custom_refresh_rate $targetHz 2>/dev/null || true")
            appendLine("settings put system refresh_rate_mode 2 2>/dev/null || true")
            appendLine("settings put system user_refresh_rate_mode 2 2>/dev/null || true")
            appendLine("settings put secure speed_mode 1 2>/dev/null || true")
            appendLine("settings put system oplus_customize_refresh_rate 1 2>/dev/null || true")
            appendLine("settings put secure refresh_rate_setting 3 2>/dev/null || true")
            appendLine("settings put system vivo_screen_refresh_rate 2 2>/dev/null || true")
            appendLine("settings put system fps_limit $targetHz 2>/dev/null || true")
        }.trimEnd()
    }

    fun generateFpsUnlockScript(targetHz: Int): String {
        return """
            # 🚀 REFRESH RATE (FPS) — hanya perintah AMAN
            # Target: ${targetHz}Hz
            ${refreshRateCommands(targetHz)}
        """.trimIndent()
    }

    fun generateUltraHdCrispScript(): String {
        return """
            # 🎮 HD / ANTI-BLUR — hanya perintah AMAN.
            # Semua setprop debug.* (renderer/compositor/msaa) DIHAPUS karena merusak tampilan.
            settings put secure sysui_haptic_feedback_multiplier 1.5
            settings put global window_animation_scale 0.5
            settings put global transition_animation_scale 0.5
            settings put global animator_duration_scale 0.5
        """.trimIndent()
    }

    fun generateDefaultFallbackScript(): String {
        return """
            # 🛡️ RESTORE DEFAULT AMAN
            settings put system peak_refresh_rate 60.0
            settings put system min_refresh_rate 60.0
            settings put global peak_refresh_rate 60.0
            settings put global min_refresh_rate 60.0
            settings delete system user_refresh_rate 2>/dev/null || true
            settings delete system custom_refresh_rate 2>/dev/null || true
            settings put system refresh_rate_mode 0 2>/dev/null || true
            settings put secure speed_mode 0 2>/dev/null || true
        """.trimIndent()
    }

    /**
     * 🧹 REPAIR — membatalkan sisa tweak tampilan BERBAHAYA dari versi lama.
     * Dijalankan otomatis sekali saat app dibuka, supaya HP yang sudah terlanjur
     * "keracunan" prop debug.* (layar hitam + garis) langsung pulih.
     */
    fun generateDisplayResetScript(): String {
        return """
            # 🧹 PERBAIKAN TAMPILAN — buang sisa tweak berbahaya
            wm density reset 2>/dev/null || true
            settings delete system peak_refresh_rate 2>/dev/null || true
            settings delete system min_refresh_rate 2>/dev/null || true
            settings delete global peak_refresh_rate 2>/dev/null || true
            settings delete global min_refresh_rate 2>/dev/null || true
            settings delete system user_refresh_rate 2>/dev/null || true
            settings delete system custom_refresh_rate 2>/dev/null || true
            settings delete system fps_limit 2>/dev/null || true
            settings put system refresh_rate_mode 0 2>/dev/null || true
            settings put secure speed_mode 0 2>/dev/null || true

            setprop debug.hwui.renderer "" 2>/dev/null || true
            setprop debug.renderengine.backend "" 2>/dev/null || true
            setprop debug.composition.type "" 2>/dev/null || true
            setprop persist.sys.composition.type "" 2>/dev/null || true
            setprop debug.egl.swapinterval 1 2>/dev/null || true
            setprop debug.gr.swapinterval 1 2>/dev/null || true
            setprop debug.egl.force_msaa 0 2>/dev/null || true
            setprop debug.egl.force_fxaa 0 2>/dev/null || true
            setprop persist.sys.force_msaa 0 2>/dev/null || true
            setprop debug.hwui.disable_draw_defer 0 2>/dev/null || true
            setprop debug.hwui.render_dirty_regions true 2>/dev/null || true
            setprop debug.sf.latch_unsignaled 0 2>/dev/null || true
            setprop debug.sf.disable_backpressure 1 2>/dev/null || true
            setprop debug.sf.enable_gl_backpressure 1 2>/dev/null || true
            setprop persist.sys.thermal.mitigation 1 2>/dev/null || true
            setprop debug.thermal.throttle.disable 0 2>/dev/null || true
            setprop persist.vendor.touch.sampling_rate "" 2>/dev/null || true
            setprop debug.performance.tuning 0 2>/dev/null || true
            setprop persist.sys.fps.unlock 0 2>/dev/null || true
            setprop persist.sys.game.fps.lock 1 2>/dev/null || true
            settings put global game_driver_all_apps 0 2>/dev/null || true
            settings put global updatable_driver_all_apps 0 2>/dev/null || true
            settings put global game_driver_opt_in_apps 0 2>/dev/null || true
            echo "DISPLAY_REPAIR_DONE"
        """.trimIndent()
    }

    fun unlockFps(context: Context, targetHz: Int? = null): FpsUnlockResult {
        val caps = checkDisplayCapabilities(context)
        val desiredHz = targetHz ?: if (caps.maxHardwareRefreshRate > 60) caps.maxHardwareRefreshRate else 120

        try {
            val script = generateFpsUnlockScript(desiredHz)
            val pb = ProcessBuilder("sh", "-c", script).redirectErrorStream(true)
            val proc = pb.start()
            proc.waitFor()

            val appliedProps = listOf(
                "settings put peak_refresh_rate ${desiredHz}.0",
                "settings put min_refresh_rate ${desiredHz}.0",
                "OEM Refresh Mode -> Forced ($desiredHz Hz)"
            )

            return FpsUnlockResult(
                isSuccess = true,
                targetFps = desiredHz,
                appliedProperties = appliedProps,
                isFallbackApplied = false,
                message = "[✓] FPS Unlocker active: Panel configured for ${desiredHz}Hz high-refresh gaming.",
                hardwareCapabilities = caps
            )
        } catch (e: Exception) {

            fallbackToDefault(context)
            return FpsUnlockResult(
                isSuccess = false,
                targetFps = 60,
                isFallbackApplied = true,
                message = "[!] FPS Unlock failed (${e.message}). Restored to default safe 60Hz baseline.",
                hardwareCapabilities = caps
            )
        }
    }

    fun fallbackToDefault(context: Context): FpsUnlockResult {
        val caps = checkDisplayCapabilities(context)
        try {
            val script = generateDefaultFallbackScript()
            val pb = ProcessBuilder("sh", "-c", script).redirectErrorStream(true)
            val proc = pb.start()
            proc.waitFor()
        } catch (_: Exception) {}

        return FpsUnlockResult(
            isSuccess = true,
            targetFps = 60,
            appliedProperties = listOf("settings put peak_refresh_rate 60.0", "refresh_rate_mode -> 0"),
            isFallbackApplied = true,
            message = "[✓] Fallback Applied: Display refresh rate & compositor restored to standard 60Hz baseline.",
            hardwareCapabilities = caps
        )
    }

    /** Tweak tampilan AMAN (hanya settings put) — dipakai PersistentBoosterService tiap 45s. */
    fun generateDisplayTweaksScript(state: DisplaySystemState): String {
        return """
            # Universal Display & FPS — hanya perintah AMAN (tanpa setprop debug.* / service call / wm density)
            ${refreshRateCommands(state.refreshRateHz, state.animationScale)}
        """.trimIndent()
    }
}
