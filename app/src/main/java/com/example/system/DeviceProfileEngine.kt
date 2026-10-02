package com.example.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

enum class DeviceBrand(
    val displayName: String,
    val thermalServices: List<String>,
    val powerServices: List<String>
) {
    XIAOMI(
        displayName = "Xiaomi / Redmi / POCO (MIUI / HyperOS)",
        thermalServices = listOf("thermal-engine", "mi_thermald", "thermald", "vendor.thermal-hal-2-0"),
        powerServices = listOf("miui.powerkeeper", "miui.analytics", "com.miui.powerkeeper")
    ),
    OPPO_FAMILY(
        displayName = "OPPO / Realme / OnePlus (ColorOS / realme UI / OxygenOS)",
        thermalServices = listOf("thermal-engine", "thermald", "vendor.thermal-hal-2-0", "oplus_charger"),
        powerServices = listOf("com.coloros.oppoguardelf", "com.oplus.athena", "com.coloros.phonemanager")
    ),
    VIVO(
        displayName = "vivo / iQOO (Funtouch OS / OriginOS)",
        thermalServices = listOf("thermal-engine", "thermald", "vendor.thermal-hal-2-0"),
        powerServices = listOf("com.vivo.pem", "com.vivo.pemstandby", "com.iqoo.powersaving")
    ),
    SAMSUNG(
        displayName = "Samsung (One UI)",
        thermalServices = listOf("thermal-engine", "sec-thermal", "vendor.thermal-hal-2-0"),
        powerServices = listOf("com.samsung.android.lool", "com.samsung.android.sm.devicesecurity")
    ),
    HUAWEI_HONOR(
        displayName = "Huawei / Honor (EMUI / MagicOS)",
        thermalServices = listOf("thermal-engine", "hw-thermal", "vendor.thermal-hal-2-0"),
        powerServices = listOf("com.huawei.powergenie", "com.huawei.android.hwaps")
    ),
    TRANSSION(
        displayName = "Tecno / Infinix / itel (HiOS / XOS)",
        thermalServices = listOf("thermal-engine", "thermald", "mtk-thermal"),
        powerServices = listOf("com.transsion.phonemaster", "com.transsion.powercenter")
    ),
    ASUS(
        displayName = "ASUS (ZenUI / ROG UI)",
        thermalServices = listOf("thermal-engine", "asus-thermal", "thermald"),
        powerServices = listOf("com.asus.mobilemanager", "com.asus.ia.asusapp")
    ),
    MOTOROLA(
        displayName = "Motorola / Lenovo (My UX)",
        thermalServices = listOf("thermal-engine", "thermald", "vendor.thermal-hal-2-0"),
        powerServices = listOf("com.motorola.motocare")
    ),
    GOOGLE_PIXEL(
        displayName = "Google Pixel (Stock Android)",
        thermalServices = listOf("thermal-engine", "thermald", "vendor.thermal-hal-2-0"),
        powerServices = emptyList()
    ),
    OTHER(
        displayName = "Android Generic (Universal)",
        thermalServices = listOf("thermal-engine", "thermald", "vendor.thermal-hal-2-0"),
        powerServices = emptyList()
    )
}

data class DeviceProfile(
    val brand: DeviceBrand,
    val manufacturer: String,
    val model: String,
    val socHardware: String,
    val socVendor: String,
    val gpuFamily: String,
    val androidRelease: String,
    val sdkInt: Int,
    val is64Bit: Boolean,
    val cpuCoreCount: Int,
    val totalRamMb: Long,
    val hasZram: Boolean,
    val kernelRelease: String
)

class DeviceProfileEngine(private val context: Context) {

    companion object {
        @Volatile
        private var cachedProfile: DeviceProfile? = null

        fun detect(context: Context): DeviceProfile {
            val cached = cachedProfile
            if (cached != null) return cached
            val p = DeviceProfileEngine(context).build(context)
            cachedProfile = p
            return p
        }
    }


    private fun detectBrand(): DeviceBrand {
        val fingerprint = (
            Build.MANUFACTURER + " " + Build.BRAND + " " + Build.PRODUCT + " " +
                Build.DEVICE + " " + Build.HARDWARE
            ).lowercase(Locale.ROOT)

        return when {
            fingerprint.contains("xiaomi") || fingerprint.contains("redmi") ||
                fingerprint.contains("poco") || fingerprint.contains("mi ") ||
                fingerprint.startsWith("mi") && fingerprint.contains("hyper") -> DeviceBrand.XIAOMI

            fingerprint.contains("oppo") || fingerprint.contains("realme") ||
                fingerprint.contains("oneplus") || fingerprint.contains("oplus") ||
                fingerprint.contains("coloros") -> DeviceBrand.OPPO_FAMILY

            fingerprint.contains("vivo") || fingerprint.contains("iqoo") ||
                fingerprint.contains("funtouch") -> DeviceBrand.VIVO

            fingerprint.contains("samsung") || fingerprint.contains("sec") -> DeviceBrand.SAMSUNG

            fingerprint.contains("huawei") || fingerprint.contains("honor") ||
                fingerprint.contains("hihonor") -> DeviceBrand.HUAWEI_HONOR

            fingerprint.contains("tecno") || fingerprint.contains("infinix") ||
                fingerprint.contains("itel") || fingerprint.contains("transsion") -> DeviceBrand.TRANSSION

            fingerprint.contains("asus") -> DeviceBrand.ASUS

            fingerprint.contains("motorola") || fingerprint.contains("moto ") ||
                fingerprint.contains("lenovo") -> DeviceBrand.MOTOROLA

            fingerprint.contains("google") || fingerprint.contains("pixel") -> DeviceBrand.GOOGLE_PIXEL

            else -> DeviceBrand.OTHER
        }
    }


    private fun detectSocVendor(): String {
        val hw = (Build.HARDWARE + " " + Build.BOARD + " " + Build.PRODUCT).lowercase(Locale.ROOT)
        return when {
            hw.contains("qcom") || hw.contains("msm") || hw.contains("sm8") ||
                hw.contains("sm7") || hw.contains("sdm") || hw.contains("kona") ||
                hw.contains("kalama") || hw.contains("waipio") || hw.contains("pineapple") -> "Qualcomm Snapdragon"
            hw.contains("mt") || hw.contains("mediatek") || hw.contains("helio") ||
                hw.contains("dimensity") -> "MediaTek"
            hw.contains("exynos") || hw.contains("universal") || hw.contains("s5e") -> "Samsung Exynos"
            hw.contains("kirin") || hw.contains("hi3") || hw.contains("hi6") -> "HiSilicon Kirin"
            hw.contains("tensor") || hw.contains("gs1") || hw.contains("zuma") ||
                hw.contains("ripcurrent") -> "Google Tensor"
            hw.contains("rk3") || hw.contains("rockchip") -> "Rockchip"
            hw.contains("ums") || hw.contains("unisoc") || hw.contains("sp9863") -> "Unisoc"
            else -> "ARM SoC"
        }
    }

    private fun detectGpuFamily(socVendor: String): String {
        return when (socVendor) {
            "Qualcomm Snapdragon" -> "Adreno"
            "MediaTek", "Samsung Exynos", "HiSilicon Kirin", "Google Tensor" -> "Mali"
            else -> "Mali"
        }
    }

    private fun build(context: Context): DeviceProfile {
        val brand = detectBrand()
        val socVendor = detectSocVendor()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val ramMb = (mi.totalMem / (1024L * 1024L)).coerceAtLeast(512L)

        val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 32)
        val is64 = android.os.Process.is64Bit()
        val kernel = System.getProperty("os.version") ?: Build.VERSION.RELEASE

        val profile = DeviceProfile(
            brand = brand,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            socHardware = if (Build.HARDWARE.isNotBlank() && Build.HARDWARE != "unknown") Build.HARDWARE else Build.BOARD,
            socVendor = socVendor,
            gpuFamily = detectGpuFamily(socVendor),
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            is64Bit = is64,
            cpuCoreCount = cores,
            totalRamMb = ramMb,
            hasZram = java.io.File("/sys/block/zram0").exists() || java.io.File("/dev/block/zram0").exists(),
            kernelRelease = kernel
        )
        return profile
    }


    private fun start(component: ComponentName): Boolean = try {
        val intent = Intent().apply {
            this.component = component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    private fun startAny(candidates: List<ComponentName>): Boolean {
        for (c in candidates) if (start(c)) return true
        return false
    }

    fun openAutoStartSettings(): Pair<Boolean, String> {
        val brand = detectBrand()
        val opened = when (brand) {
            DeviceBrand.XIAOMI -> startAny(
                listOf(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            )

            DeviceBrand.OPPO_FAMILY -> startAny(
                listOf(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    ),
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    ),
                    ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.startupapp.StartupAppListActivity"
                    ),
                    ComponentName(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                    ),
                    ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                )
            )

            DeviceBrand.VIVO -> startAny(
                listOf(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    ),
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    ),
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                    )
                )
            )

            DeviceBrand.SAMSUNG -> startAny(
                listOf(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    ),
                    ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                )
            )

            DeviceBrand.HUAWEI_HONOR -> startAny(
                listOf(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    ),
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                )
            )

            DeviceBrand.ASUS -> startAny(
                listOf(
                    ComponentName(
                        "com.asus.mobilemanager",
                        "com.asus.mobilemanager.autostart.AutoStartActivity"
                    )
                )
            )

            DeviceBrand.TRANSSION -> startAny(
                listOf(
                    ComponentName(
                        "com.transsion.phonemaster",
                        "com.itel.autostartmanager.AutoStartActivity"
                    ),
                    ComponentName(
                        "com.transsion.phonemaster",
                        "com.transsion.phonemaster.ui.autostart.AutoStartActivity"
                    )
                )
            )

            else -> false
        }

        if (opened) {
            return Pair(
                true,
                "Membuka pengaturan Autostart ${brand.displayName}.\n" +
                    "Aktifkan INS Terminal di daftar tersebut agar daemon optimizer tidak dibunuh sistem."
            )
        }

        return try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Pair(
                false,
                "HP ini tidak punya halaman autostart khusus (${brand.displayName}).\n" +
                    "Buka Settings > Apps > INS Terminal, lalu izinkan 'Autostart' / 'Background activity' secara manual."
            )
        } catch (_: Exception) {
            Pair(false, "Tidak dapat membuka pengaturan autostart otomatis pada perangkat ini.")
        }
    }

    fun requestIgnoreBatteryOptimizations(): Pair<Boolean, String> {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val pkg = context.packageName
        if (pm?.isIgnoringBatteryOptimizations(pkg) == true) {
            return Pair(true, "INS Terminal SUDAH dikecualikan dari optimasi baterai. Daemon aman berjalan.")
        }
        return try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Dialog izin baterai dibuka. Pilih 'Allow / Izinkan' agar optimizer tidak dimatikan sistem.")
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                Pair(true, "Membuka daftar pengecualian baterai. Pilih INS Terminal > 'Don't optimize'.")
            } catch (_: Exception) {
                Pair(false, "Perangkat menolak dialog baterai. Atur manual di Settings > Battery.")
            }
        }
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun describe(): String {
        val p = detect(context)
        return buildString {
            appendLine("┌──────────────────────────────────────────────────────────┐")
            appendLine("│            🔎 PROFIL PERANGKAT TERDETEKSI                 │")
            appendLine("└──────────────────────────────────────────────────────────┘")
            appendLine(" • Merek        : ${p.manufacturer} ${p.model}")
            appendLine(" • Varian OEM   : ${p.brand.displayName}")
            appendLine(" • Chipset      : ${p.socVendor} (${p.socHardware})")
            appendLine(" • GPU Family   : ${p.gpuFamily}")
            appendLine(" • Android      : ${p.androidRelease} (API ${p.sdkInt}) ${if (p.is64Bit) "64-bit" else "32-bit"}")
            appendLine(" • CPU Cores    : ${p.cpuCoreCount}")
            appendLine(" • RAM Total    : ${p.totalRamMb} MB")
            appendLine(" • ZRAM         : ${if (p.hasZram) "Tersedia" else "Tidak terdeteksi"}")
            appendLine(" • Kernel       : ${p.kernelRelease}")
            appendLine(" • Battery Opt  : ${if (isBatteryOptimizationIgnored()) "Dikecualikan (bagus)" else "MASIH DIIZINKAN sistem mematikan app"}")
        }
    }
}
