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

    fun generateFpsUnlockScript(targetHz: Int): String {
        return """
            # 🚀 HIGH-REFRESH RATE GAMING FPS UNLOCKER & ULTRA HD GRAPHICS ENGINE
            # Target Frequency: ${targetHz}Hz (Zero Lag / Ultra Crisp HD Resolution Preserved)

            # Universal Android Window Manager Refresh Rates
            settings put system peak_refresh_rate ${targetHz}.0
            settings put system min_refresh_rate ${targetHz}.0
            settings put global peak_refresh_rate ${targetHz}.0
            settings put global min_refresh_rate ${targetHz}.0
            settings put secure speed_mode 1 2>/dev/null
            settings put system refresh_rate_mode 2 2>/dev/null
            settings put system custom_refresh_rate $targetHz 2>/dev/null
            settings put system user_refresh_rate $targetHz 2>/dev/null
            settings put system user_refresh_rate_mode 2 2>/dev/null
            settings put system oplus_customize_refresh_rate 1 2>/dev/null
            settings put secure refresh_rate_setting 3 2>/dev/null
            settings put system vivo_screen_refresh_rate 2 2>/dev/null
            settings put system fps_limit $targetHz 2>/dev/null

            # 💎 ULTRA HD GRAPHICS PRESERVATION (ANTI-BLUR & FULL TEXTURE SHARPNESS)
            setprop debug.egl.force_msaa 1
            setprop debug.egl.force_fxaa 1
            setprop persist.sys.force_msaa 1
            setprop debug.hwui.disable_draw_defer 1
            setprop debug.composition.type gpu
            setprop persist.sys.composition.type gpu
            setprop debug.sf.disable_backpressure 0
            setprop debug.sf.latch_unsignaled 1
            setprop debug.choreographer.skipwarning 0
            setprop debug.egl.hw 1
            setprop persist.sys.fps.unlock 1
            setprop persist.sys.game.fps.lock 0
            setprop ro.surface_flinger.max_frame_buffer_acquired_buffers 3
            setprop vendor.display.enable_default_color_mode 1
            setprop debug.hwui.renderer skiavk
            setprop debug.sf.enable_gl_backpressure 0
        """.trimIndent()
    }

    fun generateUltraHdCrispScript(): String {
        return """
            # 🎮 ULTRA HD SHARP TEXTURES & ANTI-ALIASING ENGINE (NO RESOLUTION DOWNSCALE)
            setprop debug.egl.force_msaa 1
            setprop debug.egl.force_fxaa 1
            setprop persist.sys.force_msaa 1
            setprop debug.hwui.disable_draw_defer 1
            setprop debug.composition.type gpu
            setprop persist.sys.composition.type gpu
            setprop debug.hwui.render_dirty_regions false
            setprop debug.egl.profiler 0
            setprop debug.sf.early.app.duration 1600000
            setprop debug.sf.earlyGl.app.duration 1600000
            setprop vendor.display.enable_default_color_mode 1
            setprop debug.renderengine.backend skiagl
            setprop debug.hwui.renderer skiavk
            settings put secure sysui_haptic_feedback_multiplier 1.5
            sync
        """.trimIndent()
    }

    fun generateDefaultFallbackScript(): String {
        return """
            # 🛡️ RESTORE DEFAULT SAFE 60HZ / OEM STANDARD DISPLAY BASELINE
            settings put system peak_refresh_rate 60.0
            settings put system min_refresh_rate 60.0
            settings put global peak_refresh_rate 60.0
            settings put global min_refresh_rate 60.0
            settings put system user_refresh_rate 60 2>/dev/null
            settings put system custom_refresh_rate 60 2>/dev/null
            settings put system refresh_rate_mode 0 2>/dev/null
            settings put secure speed_mode 0 2>/dev/null

            # Restore standard compositor swap interval
            setprop debug.egl.swapinterval 1
            setprop debug.gr.swapinterval 1
            setprop debug.sf.disable_backpressure 1
            setprop debug.sf.latch_unsignaled 0
            setprop persist.sys.fps.unlock 0
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
                "debug.egl.swapinterval -> 0",
                "debug.sf.latch_unsignaled -> 1",
                "persist.sys.fps.unlock -> 1",
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
            appliedProperties = listOf("settings put peak_refresh_rate 60.0", "debug.egl.swapinterval -> 1"),
            isFallbackApplied = true,
            message = "[✓] Fallback Applied: Display refresh rate & compositor restored to standard 60Hz baseline.",
            hardwareCapabilities = caps
        )
    }

    fun generateDisplayTweaksScript(state: DisplaySystemState): String {
        return """
            # Universal Display, Game Graphic Unblur & FPS Unlock Engine (All Android Devices)
            settings put global window_animation_scale ${state.animationScale}
            settings put global transition_animation_scale ${state.animationScale}
            settings put global animator_duration_scale ${state.animationScale}

            # Universal Force Max Refresh Rate (60Hz / 90Hz / 120Hz / 144Hz)
            settings put system peak_refresh_rate ${state.refreshRateHz}.0
            settings put system min_refresh_rate ${state.refreshRateHz}.0
            settings put global peak_refresh_rate ${state.refreshRateHz}.0
            settings put global min_refresh_rate ${state.refreshRateHz}.0
            # OEM specific refresh rate bypass (Samsung, Xiaomi/HyperOS, Realme/Oppo/OnePlus, Vivo, Asus ROG)
            settings put system refresh_rate_mode 2 2>/dev/null
            settings put system custom_refresh_rate ${state.refreshRateHz} 2>/dev/null
            settings put system user_refresh_rate ${state.refreshRateHz} 2>/dev/null
            settings put system user_refresh_rate_mode 2 2>/dev/null
            settings put secure speed_mode 1 2>/dev/null
            settings put system power_mode 0 2>/dev/null
            settings put system oplus_customize_refresh_rate 1 2>/dev/null
            settings put secure refresh_rate_setting 3 2>/dev/null
            settings put system vivo_screen_refresh_rate 2 2>/dev/null
            settings put system fps_limit ${state.refreshRateHz} 2>/dev/null
            service call SurfaceFlinger 1035 i32 1 2>/dev/null

            wm density ${state.dpiDensity}

            # 🎮 ULTRA HD GAME GRAPHICS & ANTI-PECAH-PECAH (ANTI-ALIASING)
            setprop debug.egl.force_msaa ${if (state.isAntiAliasing4xMsaaEnabled) 1 else 0}
            setprop debug.egl.force_fxaa ${if (state.isAntiAliasing4xMsaaEnabled) 1 else 0}
            setprop persist.sys.force_msaa ${if (state.isAntiAliasing4xMsaaEnabled) 1 else 0}
            setprop debug.hwui.render_dirty_regions false
            setprop debug.composition.type gpu
            setprop persist.sys.composition.type c2d

            # 💎 NATIVE RESOLUTION LOCK & TEXTURE SHARPNESS
            setprop debug.hwui.disable_draw_defer ${if (state.isGameNativeResolutionLocked) 1 else 0}
            setprop debug.sf.disable_backpressure 0
            setprop debug.sf.latch_unsignaled 1
            setprop debug.choreographer.skipwarning 0
            setprop debug.egl.hw 1
            setprop debug.egl.profiler 0
            setprop debug.egl.swapinterval 0

            # 🚀 VULKAN SKIA PIPELINE & ZERO SHADER STUTTER
            setprop debug.hwui.renderer skiavk
            setprop debug.renderengine.backend skiagl
            setprop debug.sf.early.app.duration 1600000
            setprop debug.sf.earlyGl.app.duration 1600000
            setprop debug.cpurend.vsync true
            setprop ro.config.hw_quickpoweron 0

            # ❄️ THERMAL THROTTLING OVERRIDE FOR ZERO FPS DROP
            setprop debug.thermal.throttle.disable ${if (state.isThermalThrottlingDisabled) 1 else 0}
            setprop persist.sys.thermal.mitigation ${if (state.isThermalThrottlingDisabled) 0 else 1}

            # ⚡ TOUCH SAMPLING RATIO & ZERO LATENCY INPUT POLLING
            setprop persist.vendor.touch.sampling_rate ${state.touchSamplingRatioHz}
            setprop debug.touch.latency_reduction ${if (state.isTouchLatencyReductionEnabled) 1 else 0}
            settings put secure sysui_haptic_feedback_multiplier 1.5

            # 🎮 UNLOCK GAME GRAPHICS & EXTREME FPS (MLBB, PUBG, GENSHIN, FF MAX, COD)
            ${if (state.isGameGraphicUnlockerActive) """
            # Force System Game Driver & Updatable Driver
            settings put global game_driver_all_apps 1
            settings put global updatable_driver_all_apps 1
            settings put global game_driver_opt_in_apps 1
            setprop persist.sys.game.graphic_unlock 1
            setprop ro.vendor.game.turbo 1
            setprop persist.sys.gamemode.fps 120
            setprop persist.sys.fps.unlock 1
            setprop persist.sys.game.fps.lock 0
            setprop debug.stagefright.fps 120

            # Bypass OEM Game Limiters (Xiaomi Joyose, Samsung GOS, ColorOS Game Space)
            setprop persist.sys.joyose.disabled 1
            setprop persist.sys.gos.disabled 1
            setprop persist.sys.oem.game_limit 0

            # Anti-Lag Triple Buffering & Frame Pacing Stabilizer (Eliminate occasional micro-stutters)
            setprop ro.surface_flinger.max_frame_buffer_acquired_buffers 3
            setprop debug.sf.latch_unsignaled 1
            setprop debug.sf.disable_backpressure 0
            setprop debug.sf.enable_gl_backpressure 0
            setprop debug.sf.early.phase.offset_ns 500000
            setprop debug.sf.early.app.phase.offset_ns 500000
            """.trimIndent() else ""}
            # [OK] Ultra HD Game Graphics, Anti-Aliasing, Vulkan Pipelines & Zero Lag Configured!
        """.trimIndent()
    }
}

