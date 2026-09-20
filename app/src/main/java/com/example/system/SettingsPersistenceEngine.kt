package com.example.system

import android.content.Context
import android.content.SharedPreferences

class SettingsPersistenceEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ins_system_settings_prefs", Context.MODE_PRIVATE)

    companion object {

        private const val KEY_ANIMATION_SCALE = "disp_animation_scale"
        private const val KEY_REFRESH_RATE = "disp_refresh_rate"
        private const val KEY_DPI_DENSITY = "disp_dpi_density"
        private const val KEY_IMMERSIVE_MODE = "disp_immersive_mode"
        private const val KEY_HAPTIC_BOOST = "disp_haptic_boost"
        private const val KEY_THERMAL_THROTTLING_DISABLED = "disp_thermal_throttling_disabled"
        private const val KEY_FRAME_PACING = "disp_frame_pacing"
        private const val KEY_HD_GRAPHICS = "disp_hd_graphics"
        private const val KEY_ANTI_ALIASING_4X_MSAA = "disp_anti_aliasing_4x_msaa"
        private const val KEY_GAME_NATIVE_RES_LOCKED = "disp_game_native_res_locked"
        private const val KEY_TEXTURE_FILTER_16X = "disp_texture_filter_16x"
        private const val KEY_SHADER_PRELOAD = "disp_shader_preload"
        private const val KEY_TOUCH_SAMPLING_RATIO = "disp_touch_sampling_ratio"
        private const val KEY_GPU_RENDER_MODE = "disp_gpu_render_mode"
        private const val KEY_TOUCH_LATENCY_REDUCTION = "disp_touch_latency_reduction"
        private const val KEY_FPS_UNLOCKER_ACTIVE = "disp_fps_unlocker_active"
        private const val KEY_FPS_UNLOCKER_TARGET_HZ = "disp_fps_unlocker_target_hz"
        private const val KEY_FPS_UNLOCKER_STATUS = "disp_fps_unlocker_status"
        private const val KEY_FPS_FALLBACK_ACTIVE = "disp_fps_fallback_active"
        private const val KEY_GAME_GRAPHIC_UNLOCKER = "disp_game_graphic_unlocker"
        private const val KEY_GAME_GRAPHIC_PROFILE = "disp_game_graphic_profile"
        private const val KEY_JOYOSE_GOS_BYPASS = "disp_joyose_gos_bypass"
        private const val KEY_GAME_DRIVER_FORCED = "disp_game_driver_forced"
        private const val KEY_ANTI_LAG_TRIPLE_BUF = "disp_anti_lag_triple_buf"

        private const val KEY_ACTIVE_GOVERNOR = "perf_active_governor"
        private const val KEY_SWAPPINESS = "perf_swappiness"
        private const val KEY_TOUCH_BOOST = "perf_touch_boost"
        private const val KEY_ZRAM_COMPACTION = "perf_zram_compaction"
        private const val KEY_AUTO_CACHE_ACTIVE = "perf_auto_cache_active"
        private const val KEY_AUTO_CACHE_INTERVAL = "perf_auto_cache_interval"
        private const val KEY_UNIVERSAL_HYPER_BOOST = "perf_hyper_boost"
        private const val KEY_FPS_LOCK_BYPASS = "perf_fps_lock_bypass"
        private const val KEY_GPU_TURBO = "perf_gpu_turbo"

        private const val KEY_NET_DNS_ID = "net_dns_id"
        private const val KEY_NET_DNS_NAME = "net_dns_name"
        private const val KEY_NET_PRIMARY_IP = "net_primary_ip"
        private const val KEY_NET_SECONDARY_IP = "net_secondary_ip"
        private const val KEY_NET_TCP_ALGORITHM = "net_tcp_algorithm"
        private const val KEY_NET_MTU_SIZE = "net_mtu_size"
        private const val KEY_NET_FAST_HANDOVER = "net_fast_handover"
        private const val KEY_NET_TLS13 = "net_tls13"
        private const val KEY_NET_BUFFERBLOAT = "net_bufferbloat"
        private const val KEY_NET_VIDEO_STREAMING = "net_video_streaming"
        private const val KEY_NET_HW_CODEC = "net_hw_codec"
        private const val KEY_NET_HDR_ENHANCER = "net_hdr_enhancer"
        private const val KEY_NET_VOCAL_CLARITY = "net_vocal_clarity"
        private const val KEY_NET_CHROME_BOOSTER = "net_chrome_booster"
        private const val KEY_NET_GPU_RASTER = "net_gpu_raster"
        private const val KEY_NET_PARALLEL_DL = "net_parallel_dl"
        private const val KEY_NET_QUIC_HTTP3 = "net_quic_http3"
        private const val KEY_NET_WEBVIEW_HW = "net_webview_hw"
        private const val KEY_NET_UNIVERSAL_BROWSER_MEDIA = "net_universal_browser_media"
        private const val KEY_NET_IMAGE_HW_DECODE = "net_image_hw_decode"
        private const val KEY_NET_DUAL_CHANNEL_SIGNAL = "net_dual_channel_signal"
        private const val KEY_NET_WIFI_SIGNAL = "net_wifi_signal"
        private const val KEY_NET_CELL_SIGNAL = "net_cell_signal"

        private const val KEY_MEDIA_PREVIEW = "gen_media_preview"
        private const val KEY_SYMLINK_SUPPORT = "gen_symlink_support"
        private const val KEY_VOLATILE_SESSION = "gen_volatile_session"
        private const val KEY_CUSTOM_SCRIPT = "gen_custom_script"
        private const val KEY_JS_CODE = "gen_js_code"

        private const val KEY_AUDIO_SEPARATE_ENABLED = "audio_separate_enabled"
        private const val KEY_AUDIO_SELECTED_PKG = "audio_selected_pkg"
        private const val KEY_AUDIO_SELECTED_APP_NAME = "audio_selected_app_name"
        private const val KEY_AUDIO_SELECTED_PKGS = "audio_selected_pkgs"
        private const val KEY_AUDIO_ALL_OTHER_TO_SPEAKER = "audio_all_other_to_speaker"
        private const val KEY_AUDIO_TARGET_DEV = "audio_target_dev"
        private const val KEY_AUDIO_OTHER_DEV = "audio_other_dev"
        private const val KEY_AUDIO_MULTI_FOCUS = "audio_multi_focus"
    }

    fun saveAudioRouterState(state: SeparateAppSoundState) {
        prefs.edit().apply {
            putBoolean(KEY_AUDIO_SEPARATE_ENABLED, state.isSeparateSoundEnabled)
            putString(KEY_AUDIO_SELECTED_PKG, state.selectedAppPackage)
            putString(KEY_AUDIO_SELECTED_APP_NAME, state.selectedAppName)
            putStringSet(KEY_AUDIO_SELECTED_PKGS, state.selectedBluetoothPackages)
            putBoolean(KEY_AUDIO_ALL_OTHER_TO_SPEAKER, state.isAllOtherAppsToSpeaker)
            putString(KEY_AUDIO_TARGET_DEV, state.targetAudioDevice)
            putString(KEY_AUDIO_OTHER_DEV, state.otherAppsAudioDevice)
            putBoolean(KEY_AUDIO_MULTI_FOCUS, state.isMultiAudioFocusEnabled)
            apply()
        }
    }

    fun loadAudioRouterState(): SeparateAppSoundState {
        val default = SeparateAppSoundState()
        return SeparateAppSoundState(
            isSeparateSoundEnabled = prefs.getBoolean(KEY_AUDIO_SEPARATE_ENABLED, default.isSeparateSoundEnabled),
            selectedAppPackage = prefs.getString(KEY_AUDIO_SELECTED_PKG, default.selectedAppPackage) ?: default.selectedAppPackage,
            selectedAppName = prefs.getString(KEY_AUDIO_SELECTED_APP_NAME, default.selectedAppName) ?: default.selectedAppName,
            selectedBluetoothPackages = prefs.getStringSet(KEY_AUDIO_SELECTED_PKGS, default.selectedBluetoothPackages) ?: default.selectedBluetoothPackages,
            isAllOtherAppsToSpeaker = prefs.getBoolean(KEY_AUDIO_ALL_OTHER_TO_SPEAKER, default.isAllOtherAppsToSpeaker),
            targetAudioDevice = prefs.getString(KEY_AUDIO_TARGET_DEV, default.targetAudioDevice) ?: default.targetAudioDevice,
            otherAppsAudioDevice = prefs.getString(KEY_AUDIO_OTHER_DEV, default.otherAppsAudioDevice) ?: default.otherAppsAudioDevice,
            isMultiAudioFocusEnabled = prefs.getBoolean(KEY_AUDIO_MULTI_FOCUS, default.isMultiAudioFocusEnabled)
        )
    }

    fun saveDisplayState(state: DisplaySystemState) {
        prefs.edit().apply {
            putFloat(KEY_ANIMATION_SCALE, state.animationScale)
            putInt(KEY_REFRESH_RATE, state.refreshRateHz)
            putInt(KEY_DPI_DENSITY, state.dpiDensity)
            putBoolean(KEY_IMMERSIVE_MODE, state.isImmersiveMode)
            putBoolean(KEY_HAPTIC_BOOST, state.isHapticBoosterEnabled)
            putBoolean(KEY_THERMAL_THROTTLING_DISABLED, state.isThermalThrottlingDisabled)
            putBoolean(KEY_FRAME_PACING, state.isFramePacingEnabled)
            putBoolean(KEY_HD_GRAPHICS, state.isHdGraphicsEnhancerEnabled)
            putBoolean(KEY_ANTI_ALIASING_4X_MSAA, state.isAntiAliasing4xMsaaEnabled)
            putBoolean(KEY_GAME_NATIVE_RES_LOCKED, state.isGameNativeResolutionLocked)
            putBoolean(KEY_TEXTURE_FILTER_16X, state.isTextureFilter16xEnabled)
            putBoolean(KEY_SHADER_PRELOAD, state.isShaderPreloadBoosterEnabled)
            putInt(KEY_TOUCH_SAMPLING_RATIO, state.touchSamplingRatioHz)
            putString(KEY_GPU_RENDER_MODE, state.gpuRenderMode)
            putBoolean(KEY_TOUCH_LATENCY_REDUCTION, state.isTouchLatencyReductionEnabled)
            putBoolean(KEY_FPS_UNLOCKER_ACTIVE, state.isFpsUnlockerActive)
            putInt(KEY_FPS_UNLOCKER_TARGET_HZ, state.fpsUnlockerTargetHz)
            putString(KEY_FPS_UNLOCKER_STATUS, state.fpsUnlockerStatus)
            putBoolean(KEY_FPS_FALLBACK_ACTIVE, state.isFallbackActive)
            putBoolean(KEY_GAME_GRAPHIC_UNLOCKER, state.isGameGraphicUnlockerActive)
            putString(KEY_GAME_GRAPHIC_PROFILE, state.gameGraphicProfile)
            putBoolean(KEY_JOYOSE_GOS_BYPASS, state.isJoyoseGosBypassEnabled)
            putBoolean(KEY_GAME_DRIVER_FORCED, state.isGameDriverForced)
            putBoolean(KEY_ANTI_LAG_TRIPLE_BUF, state.isAntiLagTripleBufferingActive)
            apply()
        }
    }

    fun loadDisplayState(): DisplaySystemState {
        val default = DisplaySystemState()
        return DisplaySystemState(
            animationScale = prefs.getFloat(KEY_ANIMATION_SCALE, default.animationScale),
            refreshRateHz = prefs.getInt(KEY_REFRESH_RATE, default.refreshRateHz),
            dpiDensity = prefs.getInt(KEY_DPI_DENSITY, default.dpiDensity),
            isImmersiveMode = prefs.getBoolean(KEY_IMMERSIVE_MODE, default.isImmersiveMode),
            isHapticBoosterEnabled = prefs.getBoolean(KEY_HAPTIC_BOOST, default.isHapticBoosterEnabled),
            isThermalThrottlingDisabled = prefs.getBoolean(KEY_THERMAL_THROTTLING_DISABLED, default.isThermalThrottlingDisabled),
            isFramePacingEnabled = prefs.getBoolean(KEY_FRAME_PACING, default.isFramePacingEnabled),
            isHdGraphicsEnhancerEnabled = prefs.getBoolean(KEY_HD_GRAPHICS, default.isHdGraphicsEnhancerEnabled),
            isAntiAliasing4xMsaaEnabled = prefs.getBoolean(KEY_ANTI_ALIASING_4X_MSAA, default.isAntiAliasing4xMsaaEnabled),
            isGameNativeResolutionLocked = prefs.getBoolean(KEY_GAME_NATIVE_RES_LOCKED, default.isGameNativeResolutionLocked),
            isTextureFilter16xEnabled = prefs.getBoolean(KEY_TEXTURE_FILTER_16X, default.isTextureFilter16xEnabled),
            isShaderPreloadBoosterEnabled = prefs.getBoolean(KEY_SHADER_PRELOAD, default.isShaderPreloadBoosterEnabled),
            touchSamplingRatioHz = prefs.getInt(KEY_TOUCH_SAMPLING_RATIO, default.touchSamplingRatioHz),
            gpuRenderMode = prefs.getString(KEY_GPU_RENDER_MODE, default.gpuRenderMode) ?: default.gpuRenderMode,
            isTouchLatencyReductionEnabled = prefs.getBoolean(KEY_TOUCH_LATENCY_REDUCTION, default.isTouchLatencyReductionEnabled),
            isFpsUnlockerActive = prefs.getBoolean(KEY_FPS_UNLOCKER_ACTIVE, default.isFpsUnlockerActive),
            fpsUnlockerTargetHz = prefs.getInt(KEY_FPS_UNLOCKER_TARGET_HZ, default.fpsUnlockerTargetHz),
            fpsUnlockerStatus = prefs.getString(KEY_FPS_UNLOCKER_STATUS, default.fpsUnlockerStatus) ?: default.fpsUnlockerStatus,
            isFallbackActive = prefs.getBoolean(KEY_FPS_FALLBACK_ACTIVE, default.isFallbackActive),
            isGameGraphicUnlockerActive = prefs.getBoolean(KEY_GAME_GRAPHIC_UNLOCKER, default.isGameGraphicUnlockerActive),
            gameGraphicProfile = prefs.getString(KEY_GAME_GRAPHIC_PROFILE, default.gameGraphicProfile) ?: default.gameGraphicProfile,
            isJoyoseGosBypassEnabled = prefs.getBoolean(KEY_JOYOSE_GOS_BYPASS, default.isJoyoseGosBypassEnabled),
            isGameDriverForced = prefs.getBoolean(KEY_GAME_DRIVER_FORCED, default.isGameDriverForced),
            isAntiLagTripleBufferingActive = prefs.getBoolean(KEY_ANTI_LAG_TRIPLE_BUF, default.isAntiLagTripleBufferingActive)
        )
    }

    fun savePerformanceState(state: PerformanceState) {
        prefs.edit().apply {
            putString(KEY_ACTIVE_GOVERNOR, state.activeGovernor.name)
            putInt(KEY_SWAPPINESS, state.swappiness)
            putBoolean(KEY_TOUCH_BOOST, state.isTouchBoostEnabled)
            putBoolean(KEY_ZRAM_COMPACTION, state.isZramCompactionEnabled)
            putBoolean(KEY_AUTO_CACHE_ACTIVE, state.isAutoCacheClearDaemonActive)
            putInt(KEY_AUTO_CACHE_INTERVAL, state.autoCacheClearIntervalSec)
            putBoolean(KEY_UNIVERSAL_HYPER_BOOST, state.isUniversalHyperBoostActive)
            putBoolean(KEY_FPS_LOCK_BYPASS, state.isFpsLockBypassActive)
            putBoolean(KEY_GPU_TURBO, state.isGpuTurboActive)
            apply()
        }
    }

    fun loadPerformanceState(): PerformanceState {
        val default = PerformanceState()
        val govName = prefs.getString(KEY_ACTIVE_GOVERNOR, default.activeGovernor.name)
        val governor = try {
            GovernorMode.valueOf(govName ?: GovernorMode.TURBO.name)
        } catch (_: Exception) {
            GovernorMode.TURBO
        }

        return default.copy(
            activeGovernor = governor,
            swappiness = prefs.getInt(KEY_SWAPPINESS, default.swappiness),
            isTouchBoostEnabled = prefs.getBoolean(KEY_TOUCH_BOOST, default.isTouchBoostEnabled),
            isZramCompactionEnabled = prefs.getBoolean(KEY_ZRAM_COMPACTION, default.isZramCompactionEnabled),
            isAutoCacheClearDaemonActive = prefs.getBoolean(KEY_AUTO_CACHE_ACTIVE, default.isAutoCacheClearDaemonActive),
            autoCacheClearIntervalSec = prefs.getInt(KEY_AUTO_CACHE_INTERVAL, default.autoCacheClearIntervalSec),
            isUniversalHyperBoostActive = prefs.getBoolean(KEY_UNIVERSAL_HYPER_BOOST, default.isUniversalHyperBoostActive),
            isFpsLockBypassActive = prefs.getBoolean(KEY_FPS_LOCK_BYPASS, default.isFpsLockBypassActive),
            isGpuTurboActive = prefs.getBoolean(KEY_GPU_TURBO, default.isGpuTurboActive)
        )
    }

    fun saveNetworkState(state: NetworkTweakState) {
        prefs.edit().apply {
            putString(KEY_NET_DNS_ID, state.activeDnsId)
            putString(KEY_NET_DNS_NAME, state.activeDnsName)
            putString(KEY_NET_PRIMARY_IP, state.primaryIp)
            putString(KEY_NET_SECONDARY_IP, state.secondaryIp)
            putString(KEY_NET_TCP_ALGORITHM, state.tcpAlgorithm)
            putInt(KEY_NET_MTU_SIZE, state.mtuSize)
            putBoolean(KEY_NET_FAST_HANDOVER, state.isFastHandoverEnabled)
            putBoolean(KEY_NET_TLS13, state.isTls13BoosterEnabled)
            putBoolean(KEY_NET_BUFFERBLOAT, state.isBufferbloatMitigationEnabled)
            putBoolean(KEY_NET_VIDEO_STREAMING, state.isVideoStreamingBoosterActive)
            putBoolean(KEY_NET_HW_CODEC, state.isHardwareCodecAccelerationEnabled)
            putBoolean(KEY_NET_HDR_ENHANCER, state.isHdrVideoEnhancerEnabled)
            putBoolean(KEY_NET_VOCAL_CLARITY, state.isCinemaVocalClarityEnabled)
            putBoolean(KEY_NET_CHROME_BOOSTER, state.isChromeSuperFastBoosterEnabled)
            putBoolean(KEY_NET_GPU_RASTER, state.isGpuRasterizationEnabled)
            putBoolean(KEY_NET_PARALLEL_DL, state.isParallelDownloadEnabled)
            putBoolean(KEY_NET_QUIC_HTTP3, state.isQuicHttp3ProtocolEnabled)
            putBoolean(KEY_NET_WEBVIEW_HW, state.isWebViewHardwareAccelerationEnabled)
            putBoolean(KEY_NET_UNIVERSAL_BROWSER_MEDIA, state.isUniversalBrowserMediaBoosterEnabled)
            putBoolean(KEY_NET_IMAGE_HW_DECODE, state.isImageWebpAvifHardwareDecodeEnabled)
            putBoolean(KEY_NET_DUAL_CHANNEL_SIGNAL, state.isDualChannelSignalBoostEnabled)
            putBoolean(KEY_NET_WIFI_SIGNAL, state.isWifiSignalBoosterActive)
            putBoolean(KEY_NET_CELL_SIGNAL, state.isCellularSignalBoosterActive)
            apply()
        }
    }

    fun loadNetworkState(): NetworkTweakState {
        val default = NetworkTweakState()
        return default.copy(
            activeDnsId = prefs.getString(KEY_NET_DNS_ID, default.activeDnsId) ?: default.activeDnsId,
            activeDnsName = prefs.getString(KEY_NET_DNS_NAME, default.activeDnsName) ?: default.activeDnsName,
            primaryIp = prefs.getString(KEY_NET_PRIMARY_IP, default.primaryIp) ?: default.primaryIp,
            secondaryIp = prefs.getString(KEY_NET_SECONDARY_IP, default.secondaryIp) ?: default.secondaryIp,
            tcpAlgorithm = prefs.getString(KEY_NET_TCP_ALGORITHM, default.tcpAlgorithm) ?: default.tcpAlgorithm,
            mtuSize = prefs.getInt(KEY_NET_MTU_SIZE, default.mtuSize),
            isFastHandoverEnabled = prefs.getBoolean(KEY_NET_FAST_HANDOVER, default.isFastHandoverEnabled),
            isTls13BoosterEnabled = prefs.getBoolean(KEY_NET_TLS13, default.isTls13BoosterEnabled),
            isBufferbloatMitigationEnabled = prefs.getBoolean(KEY_NET_BUFFERBLOAT, default.isBufferbloatMitigationEnabled),
            isVideoStreamingBoosterActive = prefs.getBoolean(KEY_NET_VIDEO_STREAMING, default.isVideoStreamingBoosterActive),
            isHardwareCodecAccelerationEnabled = prefs.getBoolean(KEY_NET_HW_CODEC, default.isHardwareCodecAccelerationEnabled),
            isHdrVideoEnhancerEnabled = prefs.getBoolean(KEY_NET_HDR_ENHANCER, default.isHdrVideoEnhancerEnabled),
            isCinemaVocalClarityEnabled = prefs.getBoolean(KEY_NET_VOCAL_CLARITY, default.isCinemaVocalClarityEnabled),
            isChromeSuperFastBoosterEnabled = prefs.getBoolean(KEY_NET_CHROME_BOOSTER, default.isChromeSuperFastBoosterEnabled),
            isGpuRasterizationEnabled = prefs.getBoolean(KEY_NET_GPU_RASTER, default.isGpuRasterizationEnabled),
            isParallelDownloadEnabled = prefs.getBoolean(KEY_NET_PARALLEL_DL, default.isParallelDownloadEnabled),
            isQuicHttp3ProtocolEnabled = prefs.getBoolean(KEY_NET_QUIC_HTTP3, default.isQuicHttp3ProtocolEnabled),
            isWebViewHardwareAccelerationEnabled = prefs.getBoolean(KEY_NET_WEBVIEW_HW, default.isWebViewHardwareAccelerationEnabled),
            isUniversalBrowserMediaBoosterEnabled = prefs.getBoolean(KEY_NET_UNIVERSAL_BROWSER_MEDIA, default.isUniversalBrowserMediaBoosterEnabled),
            isImageWebpAvifHardwareDecodeEnabled = prefs.getBoolean(KEY_NET_IMAGE_HW_DECODE, default.isImageWebpAvifHardwareDecodeEnabled),
            isDualChannelSignalBoostEnabled = prefs.getBoolean(KEY_NET_DUAL_CHANNEL_SIGNAL, default.isDualChannelSignalBoostEnabled),
            isWifiSignalBoosterActive = prefs.getBoolean(KEY_NET_WIFI_SIGNAL, default.isWifiSignalBoosterActive),
            isCellularSignalBoosterActive = prefs.getBoolean(KEY_NET_CELL_SIGNAL, default.isCellularSignalBoosterActive)
        )
    }

    fun saveCustomScript(script: String) {
        prefs.edit().putString(KEY_CUSTOM_SCRIPT, script).apply()
    }

    fun loadCustomScript(): String {
        val defaultScript = """
            # 🚀 Universal Ultra Game HD & 120 FPS Script
            setprop debug.egl.force_msaa 1
            setprop debug.hwui.renderer skiavk
            settings put system peak_refresh_rate 120.0
            settings put global peak_refresh_rate 120.0
            sync
        """.trimIndent()
        return prefs.getString(KEY_CUSTOM_SCRIPT, defaultScript) ?: defaultScript
    }

    fun saveJsCode(code: String) {
        prefs.edit().putString(KEY_JS_CODE, code).apply()
    }

    fun loadJsCode(): String {
        val defaultCode = "const chalk = require('chalk');\nconst axios = require('axios');\n\nconsole.log('⚡ INS Android 16 Node Runtime');\nconst latency = 14.2;\nconsole.log(`Ping latency: \${latency}ms [OPTIMIZED]`);"
        return prefs.getString(KEY_JS_CODE, defaultCode) ?: defaultCode
    }

    fun saveMediaPreview(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEDIA_PREVIEW, enabled).apply()
    }

    fun loadMediaPreview(): Boolean {
        return prefs.getBoolean(KEY_MEDIA_PREVIEW, true)
    }

    fun saveSymlinkSupport(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SYMLINK_SUPPORT, enabled).apply()
    }

    fun loadSymlinkSupport(): Boolean {
        return prefs.getBoolean(KEY_SYMLINK_SUPPORT, true)
    }

    fun saveVolatileSession(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLATILE_SESSION, enabled).apply()
    }

    fun loadVolatileSession(): Boolean {
        return prefs.getBoolean(KEY_VOLATILE_SESSION, false)
    }
}
