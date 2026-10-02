package com.example.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.util.Locale

class HardwareBridgeEngine(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    fun triggerVibration(durationMs: Long = 200L): Boolean {
        return try {
            val v = vibrator ?: return false
            if (!v.hasVibrator()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun pluggedLabel(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        else -> "UNPLUGGED"
    }

    private fun healthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }

    fun getBatteryInfo(): String {
        return try {
            val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return "Baterai: data tidak tersedia pada perangkat ini."

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else -1

            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val tempC = if (tempRaw != Int.MIN_VALUE) tempRaw / 10f else null

            val statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val status = when (statusInt) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            }
            val plugged = pluggedLabel(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
            val health = healthLabel(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN))

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val currentUa = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } catch (_: Exception) {
                null
            }
            val chargeCounterUah = try {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            } catch (_: Exception) {
                null
            }

            val currentJson = currentUa?.let { "$it" } ?: "null"
            val capacityJson = chargeCounterUah?.let { "$it" } ?: "null"

            """
                {
                  "health": "$health",
                  "percentage": ${if (pct >= 0) pct else "null"},
                  "plugged": "$plugged",
                  "status": "$status",
                  "temperature": ${tempC ?: "null"},
                  "voltage": ${if (voltageMv > 0) voltageMv else "null"},
                  "current_now_ua": $currentJson,
                  "charge_counter_uah": $capacityJson
                }
            """.trimIndent()
        } catch (e: Exception) {
            "Baterai: gagal membaca status (${e.message})."
        }
    }

    fun getSensorList(): String {
        val sensors = try {
            sensorManager?.getSensorList(Sensor.TYPE_ALL) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        if (sensors.isEmpty()) {
            return "Tidak ada sensor yang dilaporkan oleh SensorManager pada perangkat ini."
        }
        val sb = StringBuilder()
        sb.appendLine("┌──────────────────────────────────────────────────────────┐")
        sb.appendLine("│               📡 HARDWARE SENSORS (${sensors.size} terdeteksi)".padEnd(59) + "│")
        sb.appendLine("└──────────────────────────────────────────────────────────┘")
        sensors.take(20).forEach { sensor ->
            val type = sensor.stringType.substringAfterLast(".")
            sb.appendLine(" • [$type] ${sensor.name}")
            sb.appendLine("     vendor=${sensor.vendor}  power=${sensor.power}mA  maxRange=${sensor.maximumRange}")
        }
        if (sensors.size > 20) {
            sb.appendLine(" • ... dan ${sensors.size - 20} sensor lain.")
        }
        return sb.toString()
    }

    suspend fun getNetworkInterfaces(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            var any = false
            for (nif in interfaces) {
                try {
                    if (!nif.isUp) continue
                    val addrs = nif.inetAddresses.toList().filterIsInstance<Inet4Address>()
                    val mac = try {
                        nif.hardwareAddress?.joinToString(":") { String.format("%02x", it) }
                    } catch (_: Exception) {
                        null
                    }

                    sb.appendLine("${nif.name}: <${if (nif.isUp) "UP" else "DOWN"}> mtu ${nif.mtu}")

                    val ifaceAddrs: List<InterfaceAddress> = try {
                        nif.interfaceAddresses
                    } catch (_: Exception) {
                        emptyList()
                    }

                    if (addrs.isEmpty()) {
                        sb.appendLine("        (tidak ada alamat IPv4)")
                    } else {
                        for (a in addrs) {
                            val match = ifaceAddrs.firstOrNull { it.address == a }
                            val prefix = match?.networkPrefixLength?.toInt()
                            val mask = prefix?.let { p ->
                                try {
                                    InetAddress.getByAddress(
                                        ByteArray(4) { i ->
                                            if (i < p / 8) 0xFF.toByte()
                                            else if (i == p / 8) ((0xFF shl (8 - p % 8)) and 0xFF).toByte()
                                            else 0
                                        }
                                    ).hostAddress
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            val bcast = match?.broadcast?.hostAddress
                            sb.appendLine(
                                "        inet ${a.hostAddress}" +
                                    (prefix?.let { "/$it" } ?: "") +
                                    (mask?.let { "  netmask $it" } ?: "") +
                                    (bcast?.let { "  broadcast $it" } ?: "")
                            )
                        }
                    }
                    mac?.let { sb.appendLine("        ether $it") }
                    any = true
                } catch (_: Exception) {
                }
            }
            if (!any) {
                "Tidak ada interface jaringan aktif yang bisa dibaca (izin NETWORK terbatas)."
            } else {
                sb.toString().trimEnd()
            }
        } catch (e: Exception) {
            "Gagal membaca interface jaringan: ${e.message}"
        }
    }

    fun getRadioSummary(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                ?: return "Telephony: tidak tersedia"
            val operator = tm.networkOperatorName.ifBlank { tm.simOperatorName }.ifBlank { "Tidak diketahui" }
            val sim = tm.simState.toString()
            String.format(Locale.US, "Operator: %s | SIM state: %s", operator, sim)
        } catch (e: Exception) {
            "Telephony: ${e.javaClass.simpleName}"
        }
    }
}
