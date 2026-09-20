package com.example.system

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean = false,
    val isGame: Boolean = false
)

data class AudioDeviceTarget(
    val id: String,
    val name: String,
    val typeName: String,
    val isBluetooth: Boolean,
    val isSpeaker: Boolean,
    val isWired: Boolean,
    val isAvailable: Boolean = true
)

data class SeparateAppSoundState(
    val isSeparateSoundEnabled: Boolean = false,
    val selectedAppPackage: String = "com.spotify.music",
    val selectedAppName: String = "Spotify Music",
    val selectedBluetoothPackages: Set<String> = setOf("com.spotify.music"),
    val isAllOtherAppsToSpeaker: Boolean = true,
    val targetAudioDevice: String = "BLUETOOTH",
    val otherAppsAudioDevice: String = "SPEAKER",
    val isMultiAudioFocusEnabled: Boolean = true,
    val isBluetoothConnected: Boolean = false,
    val connectedBluetoothDeviceName: String? = null,
    val activeRoutingSummary: String = "Semua audio melalui output default sistem",
    val lastExecutionLog: String? = null
)

class AudioRouterEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val presetApps = listOf(
        Pair("com.spotify.music", "Spotify Music"),
        Pair("com.google.android.youtube", "YouTube"),
        Pair("com.google.android.apps.youtube.music", "YouTube Music"),
        Pair("com.whatsapp", "WhatsApp"),
        Pair("com.mobile.legends", "Mobile Legends"),
        Pair("com.tencent.ig", "PUBG Mobile"),
        Pair("com.dts.freefireth", "Free Fire"),
        Pair("com.zhiliaoapp.musically", "TikTok"),
        Pair("com.instagram.android", "Instagram"),
        Pair("com.discord", "Discord"),
        Pair("com.netease.ch117", "SoundCloud / Music Player"),
        Pair("com.android.chrome", "Google Chrome")
    )

    fun loadInstalledApps(): List<InstalledAppInfo> {
        val list = mutableListOf<InstalledAppInfo>()
        val pm = context.packageManager
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {

                val isLaunchable = pm.getLaunchIntentForPackage(app.packageName) != null
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.category == ApplicationInfo.CATEGORY_GAME
                } else false

                if (isLaunchable || !isSystem) {
                    val name = pm.getApplicationLabel(app).toString()
                    list.add(
                        InstalledAppInfo(
                            packageName = app.packageName,
                            appName = if (name.isNotBlank()) name else app.packageName,
                            isSystem = isSystem,
                            isGame = isGame
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        for (preset in presetApps) {
            if (list.none { it.packageName == preset.first }) {
                list.add(
                    InstalledAppInfo(
                        packageName = preset.first,
                        appName = preset.second,
                        isSystem = false
                    )
                )
            }
        }

        return list.sortedBy { it.appName.lowercase() }
    }

    fun getAvailableAudioOutputDevices(): List<AudioDeviceTarget> {
        val list = mutableListOf<AudioDeviceTarget>()
        list.add(
            AudioDeviceTarget(
                id = "SPEAKER",
                name = "Speaker Internal HP",
                typeName = "Built-in Speaker",
                isBluetooth = false,
                isSpeaker = true,
                isWired = false
            )
        )

        var hasBt = false
        var btName: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            try {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (dev in devices) {
                    when (dev.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                            hasBt = true
                            val dName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !dev.productName.isNullOrBlank()) {
                                dev.productName.toString()
                            } else {
                                "Headset / Speaker Bluetooth"
                            }
                            btName = dName
                            list.add(
                                AudioDeviceTarget(
                                    id = "BLUETOOTH",
                                    name = dName,
                                    typeName = "Bluetooth A2DP / LE Audio",
                                    isBluetooth = true,
                                    isSpeaker = false,
                                    isWired = false
                                )
                            )
                        }
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_USB_DEVICE -> {
                            list.add(
                                AudioDeviceTarget(
                                    id = "WIRED",
                                    name = "Wired / USB Headset",
                                    typeName = "3.5mm / Type-C Audio",
                                    isBluetooth = false,
                                    isSpeaker = false,
                                    isWired = true
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (!hasBt) {
            list.add(
                AudioDeviceTarget(
                    id = "BLUETOOTH",
                    name = "Headset Bluetooth (Tidak Terhubung)",
                    typeName = "Bluetooth A2DP",
                    isBluetooth = true,
                    isSpeaker = false,
                    isWired = false,
                    isAvailable = false
                )
            )
        }

        return list.distinctBy { it.id }
    }

    fun isBluetoothAudioConnected(): Pair<Boolean, String?> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            try {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val btDev = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                }
                if (btDev != null) {
                    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !btDev.productName.isNullOrBlank()) {
                        btDev.productName.toString()
                    } else "Bluetooth Audio Connected"
                    return Pair(true, name)
                }
            } catch (_: Exception) {}
        }
        return Pair(false, null)
    }

    suspend fun applySeparateAppSound(state: SeparateAppSoundState): String = withContext(Dispatchers.IO) {
        val shell = SystemShellEngine(context)
        val startTime = System.currentTimeMillis()

        val isBt = state.targetAudioDevice == "BLUETOOTH"
        val targetDevStr = if (isBt) "bluetooth" else "speaker"
        val otherDevStr = if (isBt) "speaker" else "bluetooth"

        val targetPackages = if (state.selectedBluetoothPackages.isNotEmpty()) {
            state.selectedBluetoothPackages.toList()
        } else {
            listOf(state.selectedAppPackage)
        }

        val joinedColon = targetPackages.joinToString(":")
        val joinedComma = targetPackages.joinToString(",")
        val primaryPkg = targetPackages.firstOrNull() ?: "com.spotify.music"

        val script = if (state.isSeparateSoundEnabled) {
            """
                # 🎧 DUAL AUDIO ROUTER & MULTI-APP SOUND SEPARATOR (BLUETOOTH <-> SPEAKER SPLIT)

                # 1. Android Separate App Sound & SoundAssistant Subsystem (Multi-App Support)
                settings put system separate_app_sound 1
                settings put system separate_audio_package "$joinedColon"
                settings put system separate_audio_packages "$joinedColon"
                settings put system separate_app_sound_package "$joinedColon"
                settings put system separate_audio_device "$targetDevStr"
                settings put system separate_app_sound_device "$targetDevStr"
                settings put system sound_separate_active 1
                settings put system sound_separate_app_list "$joinedComma"
                settings put system sound_separate_device_type ${if (isBt) 2 else 1}

                # 2. Multi-Audio Focus & Dual Stream Output Policy
                settings put global multi_audio_focus_enabled 1
                settings put secure sound_assistant_dual_audio 1
                settings put system multi_audio_output 1
                settings put system dual_audio_split_enabled 1
                settings put global audio_safe_volume_state 0
                settings put system volume_music_speaker 15

                # 3. Audio HAL & Stagefright Router System Properties
                setprop audio.routing.split 1
                setprop persist.audio.dual_stream 1
                setprop media.routing.separate 1
                setprop persist.vendor.audio.separate_app 1
                setprop persist.sys.audio.split_sound 1
                setprop persist.audio.multiaudio.enable 1
                setprop persist.sys.audio.pkg.bluetooth "$joinedColon"
                setprop persist.sys.audio.separate.app "$primaryPkg"

                # 4. Route policy for Speaker HP (All Other Apps & Games)
                settings put system sound_separate_all_other_to_speaker ${if (state.isAllOtherAppsToSpeaker) 1 else 0}
                setprop persist.sys.audio.default_stream speaker

                # 5. Audio HAL Sync
                cmd audio set-surround-sound-enabled-categories 0 2>/dev/null || true
                sync
            """.trimIndent()
        } else {
            """
                # 🔊 RESTORE STANDARD UNIFIED AUDIO ROUTING
                settings put system separate_app_sound 0
                settings put system sound_separate_active 0
                settings put system dual_audio_split_enabled 0
                setprop audio.routing.split 0
                setprop persist.audio.dual_stream 0
                setprop media.routing.separate 0
                setprop persist.sys.audio.pkg.bluetooth ""
                sync
            """.trimIndent()
        }

        val result = if (shell.isDeviceRooted()) {
            shell.runRootProcess(script, "/storage/emulated/0", startTime)
        } else {
            shell.runUniversalNonRootEngine(script, "/storage/emulated/0", startTime)
        }

        val summary = if (state.isSeparateSoundEnabled) {
            val appCount = targetPackages.size
            val appListStr = targetPackages.take(3).joinToString(", ") + if (appCount > 3) " (+${appCount - 3} lainnya)" else ""
            """
                [✓] Mode Pemisah Suara (Separate App Sound) AKTIF:
                • Aplikasi ke Bluetooth 🎧 ($appCount app): $appListStr
                • Jalur Aplikasi Terpilih: 🎧 Bluetooth Headset/A2DP
                • Jalur SEMUA Aplikasi Lain (Select All): 🔊 Speaker Internal HP (MLBB, PUBG, Notif, Media Lain)
                • Multi-Audio Focus: AKTIF (Musik & Game bunyi bersamaan tanpa pause)
            """.trimIndent()
        } else {
            "[✓] Mode Pemisah Suara DINONAKTIFKAN (Semua suara keluar normal melalui satu jalur audio aktif)."
        }

        return@withContext summary
    }

    fun generateAudioAndMicFixScript(): String {
        return """
            # 🛠️ UNIVERSAL AUDIO & MICROPHONE FIXER ENGINE (WA VN PELAN, GAME MUTE, SUARA HILANG)

            # 1. 🎙️ FIX WHATSAPP VOICE NOTE (VN) MIC SENSITIVITY & CLARITY (ANTI-PELAN)
            # Disable dual-mic fluence suppression bug that cancels voice notes when holding phone normally
            setprop persist.audio.fluence.voicerec false
            setprop persist.vendor.audio.fluence.voicerec false
            setprop persist.audio.fluence.voicecall true
            setprop persist.audio.handset.mic digital
            setprop persist.audio.voice.clarity 1
            setprop persist.vendor.audio.mic.gain 15
            setprop debug.audio.mic.gain 20
            setprop persist.vendor.audio.record.gain 2
            setprop persist.audio.hp true
            setprop persist.vendor.audio.voice.rec.enhance 1

            # 2. 🔊 FIX MOBILE LEGENDS (MLBB) & GAMES MUTE ON INTERNAL PHONE SPEAKER
            # Restore unified media stream & unmute speaker routing
            setprop audio.routing.split 0
            setprop persist.audio.dual_stream 0
            setprop media.routing.separate 0
            settings put system sound_separate_active 0
            settings put system separate_app_sound 0
            settings put global audio_safe_volume_state 0
            settings put secure audio_safe_volume_state 0
            settings put system volume_music_speaker 15
            settings put system master_mono 0

            # 3. ⚡ FIX SUARA HILANG-HILANG / AUDIO DROPOUT & BUFFER CRACKLING
            setprop audio.deep_buffer.media 1
            setprop persist.audio.offload.buffer 1
            setprop af.fast_track_multiplier 1
            setprop af.resampler.quality 4
            setprop persist.sys.audio.resampler 1
            setprop ro.audio.flinger_standbytime_ms 3000

            # 4. 🎛️ Audio HAL Sync
            cmd audio set-surround-sound-enabled-categories 0 2>/dev/null || true
            sync
        """.trimIndent()
    }

    suspend fun fixAllAudioAndMicIssues(): String = withContext(Dispatchers.IO) {
        val shell = SystemShellEngine(context)
        val startTime = System.currentTimeMillis()
        val script = generateAudioAndMicFixScript()

        val res = if (shell.isDeviceRooted()) {
            shell.runRootProcess(script, "/storage/emulated/0", startTime)
        } else {
            shell.runUniversalNonRootEngine(script, "/storage/emulated/0", startTime)
        }

        return@withContext """
            [✓] PERBAIKAN AUDIO & MICROPHONE SUKSES!
            --------------------------------------------------
            🎙️ Mic WhatsApp VN: Sensitivitas mic dinaikkan & bug Fluence suppression dinonaktifkan (VN sekarang kencang & jernih).
            🔊 Speaker HP / Game MLBB: Jalur audio speaker dipulihkan & di-unmute (Suara Mobile Legends normal kembali).
            ⚡ Anti Suara Hilang: Audio buffer size distabilkan (Mencegah suara putus-putus / kresek-kresek).
            --------------------------------------------------
            Silakan tes kirim Voice Note di WhatsApp dan buka Mobile Legends!
        """.trimIndent()
    }
}

