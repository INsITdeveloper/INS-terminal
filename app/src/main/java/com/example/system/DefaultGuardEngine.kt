package com.example.system

import android.os.Build
import com.example.data.entity.StockSnapshotEntity

data class DefaultGuardState(
    val isVolatileSessionActive: Boolean = true,
    val isStockDefaultsApplied: Boolean = false,
    val lastResetTimestamp: Long = 0L,
    val lastSnapshotTakenTimestamp: Long = System.currentTimeMillis(),
    val stockSnapshot: StockSnapshotEntity = StockSnapshotEntity(
        snapshotKey = "OEM_DEFAULT",
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = "Android 16.0 (API ${Build.VERSION.SDK_INT})",
        defaultDns = "Carrier DHCP Stock",
        defaultTcpCongestion = "cubic",
        defaultAnimationScale = 1.0f,
        defaultMtu = 1500,
        defaultRefreshRate = 60,
        defaultSwappiness = 60
    )
)

class DefaultGuardEngine {

    fun generateStockResetScript(snapshot: StockSnapshotEntity): String {
        return """
            # ========================================================
            # 🔄 INS STOCK DEFAULT RESTORATION ENGINE (Android 16)
            # ========================================================
            # Reverting all TCP, Network, Display & Governor tweaks

            # 1. Reset Animation Scales to OEM 1.0x
            settings put global window_animation_scale ${snapshot.defaultAnimationScale}
            settings put global transition_animation_scale ${snapshot.defaultAnimationScale}
            settings put global animator_duration_scale ${snapshot.defaultAnimationScale}

            # 2. Reset Refresh Rate to Auto / OEM Default
            settings delete system peak_refresh_rate
            settings delete system min_refresh_rate

            # 3. Reset TCP Congestion & Buffers
            echo ${snapshot.defaultTcpCongestion} > /proc/sys/net/ipv4/tcp_congestion_control
            echo 0 > /proc/sys/net/ipv4/tcp_tw_reuse

            # 4. Reset VM Swappiness & Thermal
            echo ${snapshot.defaultSwappiness} > /proc/sys/vm/swappiness
            echo 100 > /proc/sys/vm/vfs_cache_pressure

            # 5. Flush INS Transient Virtual Aliases
            rm -rf /data/local/tmp/ins_volatile_*

            # [SUCCESS] System restored to OEM factory parameters.
        """.trimIndent()
    }
}
