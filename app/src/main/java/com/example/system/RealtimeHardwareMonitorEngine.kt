package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

data class RealtimeCoreTelemetry(
    val coreIndex: Int,
    val name: String,
    val frequencyGhz: String,
    val loadPercent: Int
)

data class RealtimeHardwareData(
    val cpuUsagePercent: Int,
    val cpuCores: List<RealtimeCoreTelemetry>,
    val cpuTempC: Float,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val ramUsagePercent: Int,
    val zramUsedMb: Long,
    val zramTotalMb: Long,
    val batteryPercent: Int,
    val batteryVoltageMv: Int,
    val batteryTempC: Float,
    val batteryCurrentMa: Int,
    val isCharging: Boolean,
    val networkRxKbps: Float,
    val networkTxKbps: Float,
    val maxDisplayRefreshRateHz: Int,
    val activeFps: Int,
    val deviceModel: String,
    val hardwareSoc: String,
    val androidVersion: String,
    val kernelRelease: String,
    val uptimeFormatted: String,
    val isTelemetryReal: Boolean = true,
    val telemetryNotes: List<String> = emptyList()
)

class RealtimeHardwareMonitorEngine(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    private var lastTotalRxBytes: Long = 0L
    private var lastTotalTxBytes: Long = 0L
    private var lastNetworkSampleTimeMs: Long = 0L

    private val lastCoreTimes = HashMap<Int, Pair<Long, Long>>()
    private var lastAggregateTimes: Pair<Long, Long>? = null

    private var procStatProbed = false
    private var procStatOk = false
    private var sysCpuFreqProbed = false
    private var sysCpuFreqOk = false
    private var thermalProbed = false
    private var thermalOk = false
    private var meminfoProbed = false
    private var meminfoOk = false

    private var cachedMaxRefreshRate: Int = 60

    init {
        lastTotalRxBytes = TrafficStats.getTotalRxBytes()
        lastTotalTxBytes = TrafficStats.getTotalTxBytes()
        lastNetworkSampleTimeMs = SystemClock.elapsedRealtime()
        cachedMaxRefreshRate = readDisplayMaxRefreshRate()
    }

    suspend fun sampleTelemetry(
        activeGovernorMode: GovernorMode,
        configuredRefreshRateHz: Int = 0,
        isFpsLockBypassActive: Boolean = true
    ): RealtimeHardwareData = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        val notes = mutableListOf<String>()

        val cpuUsage = readCpuUsagePercent(notes)
        val cores = readCpuCores(notes)

        val battery = readBatteryStatus()
        if (!battery.second) notes += "Suhu/tegangan baterai tidak dilaporkan oleh driver perangkat."

        val cpuTemp = readCpuThermal()
        if (cpuTemp == null) notes += "Sensor suhu CPU tidak terekspos di /sys/class/thermal."

        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).coerceAtLeast(1L)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0L)
        val ramUsagePct = ((usedRamMb.toFloat() / totalRamMb) * 100).roundToInt().coerceIn(0, 100)

        val (zramUsed, zramTotal) = readZramStats()
        if (zramTotal == 0L) notes += "ZRAM tidak aktif / tidak terdeteksi."

        val curRx = TrafficStats.getTotalRxBytes()
        val curTx = TrafficStats.getTotalTxBytes()
        val deltaMs = (now - lastNetworkSampleTimeMs).coerceAtLeast(500L)

        val rxKbps = if (lastTotalRxBytes > 0 && curRx >= lastTotalRxBytes) {
            ((curRx - lastTotalRxBytes) * 1000f) / (deltaMs * 1024f)
        } else 0f
        val txKbps = if (lastTotalTxBytes > 0 && curTx >= lastTotalTxBytes) {
            ((curTx - lastTotalTxBytes) * 1000f) / (deltaMs * 1024f)
        } else 0f

        lastTotalRxBytes = curRx
        lastTotalTxBytes = curTx
        lastNetworkSampleTimeMs = now

        val panelMax = readDisplayMaxRefreshRate()
        cachedMaxRefreshRate = panelMax

        val currentRefresh = readCurrentRefreshRate()
        val activeFps = currentRefresh

        val uptimeSec = SystemClock.elapsedRealtime() / 1000
        val h = uptimeSec / 3600
        val m = (uptimeSec % 3600) / 60
        val s = uptimeSec % 60
        val uptimeStr = String.format("%02d:%02d:%02d", h, m, s)

        val profile = DeviceProfileEngine.detect(context)

        RealtimeHardwareData(
            cpuUsagePercent = cpuUsage,
            cpuCores = cores,
            cpuTempC = cpuTemp ?: -1f,
            ramUsedMb = usedRamMb,
            ramTotalMb = totalRamMb,
            ramUsagePercent = ramUsagePct,
            zramUsedMb = zramUsed,
            zramTotalMb = zramTotal,
            batteryPercent = battery.first,
            batteryVoltageMv = battery.third,
            batteryTempC = battery.fourth,
            batteryCurrentMa = battery.fifth,
            isCharging = battery.sixth,
            networkRxKbps = rxKbps.coerceAtLeast(0f),
            networkTxKbps = txKbps.coerceAtLeast(0f),
            maxDisplayRefreshRateHz = panelMax,
            activeFps = activeFps,
            deviceModel = "${profile.manufacturer} ${profile.model}",
            hardwareSoc = profile.socHardware,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            kernelRelease = profile.kernelRelease,
            uptimeFormatted = uptimeStr,
            isTelemetryReal = notes.isEmpty(),
            telemetryNotes = notes
        )
    }


    private fun probeProcStat(): Boolean {
        if (!procStatProbed) {
            procStatProbed = true
            procStatOk = try {
                val f = File("/proc/stat")
                f.exists() && f.canRead() && f.length() > 0
            } catch (_: Exception) {
                false
            }
        }
        return procStatOk
    }

    private fun parseCpuLine(tokens: List<String>): Pair<Long, Long>? {
        if (tokens.size < 5) return null
        val user = tokens[1].toLongOrNull() ?: return null
        val nice = tokens[2].toLongOrNull() ?: 0L
        val system = tokens[3].toLongOrNull() ?: 0L
        val idle = tokens[4].toLongOrNull() ?: 0L
        val iowait = tokens.getOrNull(5)?.toLongOrNull() ?: 0L
        val irq = tokens.getOrNull(6)?.toLongOrNull() ?: 0L
        val softirq = tokens.getOrNull(7)?.toLongOrNull() ?: 0L
        val steal = tokens.getOrNull(8)?.toLongOrNull() ?: 0L
        val total = user + nice + system + idle + iowait + irq + softirq + steal
        val idleAll = idle + iowait
        return Pair(total, idleAll)
    }

    private fun readCpuUsagePercent(notes: MutableList<String>): Int {
        if (!probeProcStat()) {
            notes += "Akses /proc/stat diblokir — beban CPU tidak bisa dibaca (Android 10+ membatasi /proc untuk app)."
            return -1
        }
        return try {
            val lines = File("/proc/stat").readLines()
            val aggregate = lines.firstOrNull { it.startsWith("cpu ") }?.let {
                parseCpuLine(it.split("\\s+".toRegex()))
            } ?: return -1

            val prev = lastAggregateTimes
            lastAggregateTimes = aggregate
            if (prev == null) return -1

            val dTotal = aggregate.first - prev.first
            val dIdle = aggregate.second - prev.second
            if (dTotal <= 0) return -1

            val usage = (1.0f - (dIdle.toFloat() / dTotal.toFloat())) * 100f
            usage.roundToInt().coerceIn(0, 100)
        } catch (_: Exception) {
            notes += "Gagal membaca /proc/stat."
            -1
        }
    }

    private fun readCpuCores(notes: MutableList<String>): List<RealtimeCoreTelemetry> {
        val coreCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 32)
        val list = mutableListOf<RealtimeCoreTelemetry>()

        val perCoreUsage = HashMap<Int, Int>()
        if (probeProcStat()) {
            try {
                File("/proc/stat").readLines().forEach { line ->
                    val tokens = line.split("\\s+".toRegex())
                    val name = tokens.firstOrNull() ?: return@forEach
                    if (!name.startsWith("cpu") || name == "cpu") return@forEach
                    val idx = name.removePrefix("cpu").toIntOrNull() ?: return@forEach
                    val parsed = parseCpuLine(tokens) ?: return@forEach
                    val prev = lastCoreTimes[idx]
                    lastCoreTimes[idx] = parsed
                    if (prev != null) {
                        val dTotal = parsed.first - prev.first
                        val dIdle = parsed.second - prev.second
                        if (dTotal > 0) {
                            perCoreUsage[idx] =
                                ((1.0f - dIdle.toFloat() / dTotal.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        val maxFreqByCore = HashMap<Int, Long>()
        for (i in 0 until coreCount) {
            val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (f.exists() && f.canRead()) {
                f.readText().trim().toLongOrNull()?.let { maxFreqByCore[i] = it }
            }
        }
        val distinctFreqs = maxFreqByCore.values.distinct().sorted()

        for (i in 0 until coreCount) {
            val curFreqKhz = readCurrentFreqKhz(i)
            val maxKhz = maxFreqByCore[i]

            val clusterLabel = when {
                distinctFreqs.size <= 1 || maxKhz == null -> "CORE"
                maxKhz == distinctFreqs.first() -> "LITTLE"
                maxKhz == distinctFreqs.last() -> "PRIME"
                else -> "MID"
            }

            val freqStr = when {
                curFreqKhz > 0 -> String.format("%.2f GHz", curFreqKhz / 1_000_000.0)
                maxKhz != null -> String.format("%.2f GHz (max)", maxKhz / 1_000_000.0)
                else -> "—"
            }

            list.add(
                RealtimeCoreTelemetry(
                    coreIndex = i,
                    name = "$clusterLabel ($i)",
                    frequencyGhz = freqStr,
                    loadPercent = perCoreUsage[i] ?: -1
                )
            )
        }

        if (maxFreqByCore.isEmpty()) {
            notes += "Frekuensi CPU tidak bisa dibaca dari /sys (butuh root atau dibatasi kernel)."
        }
        return list
    }

    private fun readCurrentFreqKhz(coreIndex: Int): Long {
        if (!sysCpuFreqProbed) {
            sysCpuFreqProbed = true
            sysCpuFreqOk = File("/sys/devices/system/cpu/cpu0/cpufreq").let { it.exists() && it.canRead() }
        }
        if (!sysCpuFreqOk) return 0L
        return try {
            val f = File("/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_cur_freq")
            if (f.exists() && f.canRead()) f.readText().trim().toLongOrNull() ?: 0L else 0L
        } catch (_: Exception) {
            0L
        }
    }


    private fun readBatteryStatus(): Sextuple<Int, Boolean, Int, Float, Int, Boolean> {
        var pct = -1
        var voltageMv = 0
        var tempC = -1f
        var hasTemp = false
        var charging = false

        try {
            val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent != null) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) pct = (level * 100f / scale).roundToInt()

                voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

                val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                if (rawTemp != Int.MIN_VALUE) {
                    tempC = rawTemp / 10f
                    hasTemp = true
                }

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) {
        }

        if (pct < 0) {
            pct = try {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: -1
            } catch (_: Exception) {
                -1
            }
        }
        if (!charging) {
            charging = try {
                batteryManager?.isCharging ?: false
            } catch (_: Exception) {
                false
            }
        }

        val currentUa = try {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        } catch (_: Exception) {
            0
        }
        val currentMa = currentUa / 1000

        return Sextuple(pct.coerceIn(0, 100), hasTemp, voltageMv, tempC, currentMa, charging)
    }


    private fun readCpuThermal(): Float? {
        if (!thermalProbed) {
            thermalProbed = true
            val dir = File("/sys/class/thermal")
            thermalOk = dir.exists() && dir.isDirectory && dir.canRead()
        }
        if (!thermalOk) return null

        return try {
            val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
                ?: return null

            var best: Float? = null
            for (z in zones) {
                val tempFile = File(z, "temp")
                if (!tempFile.exists() || !tempFile.canRead()) continue
                val raw = tempFile.readText().trim().toFloatOrNull() ?: continue
                val typeFile = File(z, "type")
                val type = if (typeFile.exists() && typeFile.canRead()) typeFile.readText().trim() else ""

                var t = raw
                if (t > 1000f) t /= 1000f
                else if (t > 200f) t /= 10f
                if (t !in 5f..125f) continue

                val isCpuZone = type.contains("cpu", true) || type.contains("soc", true) ||
                    type.contains("tsens", true) || type.contains("ap", true) ||
                    type.contains("cluster", true) || type.contains("mtk", true)

                if (isCpuZone && (best == null || t > best!!)) best = t
            }
            best
        } catch (_: Exception) {
            null
        }
    }


    private fun readZramStats(): Pair<Long, Long> {
        if (!meminfoProbed) {
            meminfoProbed = true
            val f = File("/proc/meminfo")
            meminfoOk = f.exists() && f.canRead()
        }
        if (!meminfoOk) return Pair(0L, 0L)

        return try {
            var swapTotalKb = 0L
            var swapFreeKb = 0L
            File("/proc/meminfo").forEachLine { line ->
                when {
                    line.startsWith("SwapTotal:") ->
                        swapTotalKb = line.substringAfter(":").trim().substringBefore(" ").toLongOrNull() ?: 0L
                    line.startsWith("SwapFree:") ->
                        swapFreeKb = line.substringAfter(":").trim().substringBefore(" ").toLongOrNull() ?: 0L
                }
            }
            if (swapTotalKb <= 0) {
                Pair(0L, 0L)
            } else {
                val total = swapTotalKb / 1024
                val used = ((swapTotalKb - swapFreeKb) / 1024).coerceAtLeast(0L)
                Pair(used, total)
            }
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }


    private fun resolveDisplay(): android.view.Display? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay
        }
    } catch (_: Exception) {
        null
    }

    private fun readDisplayMaxRefreshRate(): Int = try {
        val display = resolveDisplay()
        display?.supportedModes?.maxOfOrNull { it.refreshRate.roundToInt() }
            ?: display?.mode?.refreshRate?.roundToInt()
            ?: 60
    } catch (_: Exception) {
        60
    }

    private fun readCurrentRefreshRate(): Int = try {
        val display = resolveDisplay()
        val modeRate = display?.mode?.refreshRate?.roundToInt() ?: 0
        if (modeRate > 0) modeRate else cachedMaxRefreshRate
    } catch (_: Exception) {
        cachedMaxRefreshRate
    }
}

data class Sextuple<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)
