package com.example.system

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

data class DnsServerInfo(
    val id: String,
    val name: String,
    val primaryIp: String,
    val secondaryIp: String,
    val dohUrl: String,
    val description: String,
    val latencyMs: Long = -1L,
    val isSecureDoH: Boolean = true
)

/**
 * Mode penguat sinyal. Ini yang menyelesaikan masalah "kalau dua-duanya diidupin jadi aneh":
 * alih-alih menembak setting Wi-Fi DAN seluler secara bersamaan (yang saling bertabrakan),
 * engine memilih SATU profil koheren berdasarkan transport yang benar-benar aktif.
 */
enum class SignalBoostMode(val label: String) {
    AUTO("Auto (Transport Aktif)"),
    WIFI_ONLY("Wi-Fi Only"),
    CELLULAR_ONLY("Seluler Only"),
    DUAL_CHANNEL("Dual-Channel Wi-Fi + Seluler")
}

data class NetworkTweakState(
    val activeDnsId: String = "cloudflare",
    val activeDnsName: String = "Cloudflare 1.1.1.1",
    val primaryIp: String = "1.1.1.1",
    val secondaryIp: String = "1.0.0.1",
    val tcpAlgorithm: String = "bbr",
    val mtuSize: Int = 1500,
    val isFastHandoverEnabled: Boolean = true,
    val isTls13BoosterEnabled: Boolean = true,
    val isBufferbloatMitigationEnabled: Boolean = true,
    val isSignalBoosterEnabled: Boolean = true,
    val is4g5gAggregationBoostEnabled: Boolean = true,
    val isWifiAntiJitterEnabled: Boolean = true,
    val isVideoStreamingBoosterActive: Boolean = true,
    val isHardwareCodecAccelerationEnabled: Boolean = true,
    val isHdrVideoEnhancerEnabled: Boolean = true,
    val isCinemaVocalClarityEnabled: Boolean = true,
    val isChromeSuperFastBoosterEnabled: Boolean = true,
    val isGpuRasterizationEnabled: Boolean = true,
    val isParallelDownloadEnabled: Boolean = true,
    val isQuicHttp3ProtocolEnabled: Boolean = true,
    val isWebViewHardwareAccelerationEnabled: Boolean = true,
    val isUniversalBrowserMediaBoosterEnabled: Boolean = true,
    val isImageWebpAvifHardwareDecodeEnabled: Boolean = true,
    val isDualChannelSignalBoostEnabled: Boolean = false,
    val isWifiSignalBoosterActive: Boolean = true,
    val isCellularSignalBoosterActive: Boolean = false,
    val measuredPingMs: Long = -1L,
    val packetLossPercent: Int = 0,
    val lastOptimizedTime: Long = System.currentTimeMillis(),
    val lastExecutionDetails: String? = null
) {
    /**
     * Tentukan profil koheren dari kombinasi toggle. Inilah "otak" anti-konflik.
     * - Dua toggle ON tanpa mode dual-channel -> AUTO (ikut transport aktif), bukan tembak dua-duanya.
     * - Dua toggle ON + dual-channel -> DUAL_CHANNEL (hanya berguna kalau perangkat memang
     *   mendukung koneksi Wi-Fi + seluler bersamaan; kalau tidak, script akan jatuh ke AUTO).
     */
    fun resolveBoostMode(): SignalBoostMode = when {
        isWifiSignalBoosterActive && isCellularSignalBoosterActive ->
            if (isDualChannelSignalBoostEnabled) SignalBoostMode.DUAL_CHANNEL else SignalBoostMode.AUTO
        isWifiSignalBoosterActive -> SignalBoostMode.WIFI_ONLY
        isCellularSignalBoosterActive -> SignalBoostMode.CELLULAR_ONLY
        else -> SignalBoostMode.AUTO
    }
}

data class TweakExecutionLog(
    val title: String,
    val details: String,
    val isSuccess: Boolean,
    val executionTimeMs: Long
)

class NetworkOptimizerEngine {

    val availableDnsServers = listOf(
        DnsServerInfo(
            id = "cloudflare",
            name = "Cloudflare DNS",
            primaryIp = "1.1.1.1",
            secondaryIp = "1.0.0.1",
            dohUrl = "https://cloudflare-dns.com/dns-query",
            description = "Latency rendah global, tanpa log"
        ),
        DnsServerInfo(
            id = "google",
            name = "Google Public DNS",
            primaryIp = "8.8.8.8",
            secondaryIp = "8.8.4.4",
            dohUrl = "https://dns.google/dns-query",
            description = "Throughput global tinggi, anycast"
        ),
        DnsServerInfo(
            id = "quad9",
            name = "Quad9 Threat Shield",
            primaryIp = "9.9.9.9",
            secondaryIp = "149.112.112.112",
            dohUrl = "https://dns.quad9.net/dns-query",
            description = "Blokir domain berbahaya di level jaringan"
        ),
        DnsServerInfo(
            id = "adguard",
            name = "AdGuard Clean DNS",
            primaryIp = "94.140.14.14",
            secondaryIp = "94.140.15.15",
            dohUrl = "https://dns.adguard-dns.com/dns-query",
            description = "Blokir iklan & tracker sistem-wide"
        ),
        DnsServerInfo(
            id = "nextdns",
            name = "NextDNS Engine",
            primaryIp = "45.90.28.0",
            secondaryIp = "45.90.30.0",
            dohUrl = "https://dns.nextdns.io",
            description = "DNS terenkripsi yang bisa dikustom"
        ),
        DnsServerInfo(
            id = "stock",
            name = "OEM System Default (ISP / APN)",
            primaryIp = "1.1.1.1",
            secondaryIp = "8.8.8.8",
            dohUrl = "System Default",
            description = "Profil DNS standar operator tanpa modifikasi",
            isSecureDoH = false
        )
    )

    suspend fun pingHost(host: String, port: Int = 443, timeoutMs: Int = 2000): Long = withContext(Dispatchers.IO) {
        val target = when {
            host.isBlank() || host == "DHCP/Default" -> "1.1.1.1"
            else -> host
        }
        try {
            var latency = -1L
            measureTimeMillis {
                Socket().use { s ->
                    // TCP_NODELAY + IP_TOS low-delay: hilangkan antrean Nagle & minta jalur cepat.
                    s.tcpNoDelay = true
                    try {
                        s.trafficClass = IP_TOS_LOW_DELAY
                    } catch (_: Exception) {}
                    s.connect(InetSocketAddress(target, port), timeoutMs)
                }
            }.let { latency = it }
            latency
        } catch (_: Exception) {
            try {
                val start = System.currentTimeMillis()
                val addr = InetAddress.getByName(target)
                val reached = addr.isReachable(timeoutMs)
                val delta = System.currentTimeMillis() - start
                if (reached && delta > 0) delta else -1L
            } catch (_: Exception) {
                -1L
            }
        }
    }

    suspend fun benchmarkAllDns(): List<Pair<DnsServerInfo, Long>> = withContext(Dispatchers.IO) {
        availableDnsServers.map { server ->
            val ping = pingHost(server.primaryIp)
            server.copy(latencyMs = ping) to ping
        }.sortedWith(compareBy({ it.second < 0 }, { it.second }))
    }

    suspend fun applyNetworkTweaks(context: Context, state: NetworkTweakState): TweakExecutionLog =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val priv = PrivilegeExecutionEngine.getInstance(context)
            val mode = state.resolveBoostMode()
            val script = generateUltraSignalAndStreamingScript(state, mode)
            val (success, report) = priv.executePrivilegedScript(script)

            val pingText = if (state.measuredPingMs < 0) "N/A (host tidak terjangkau)"
            else "${state.measuredPingMs} ms"

            val header = buildString {
                appendLine("Target DNS : ${state.activeDnsName} (${state.primaryIp})")
                appendLine("TCP algo   : ${state.tcpAlgorithm}")
                appendLine("MTU target : ${state.mtuSize}")
                appendLine("Mode sinyal: ${mode.label}")
                appendLine("Ping nyata : $pingText")
                appendLine()
            }

            TweakExecutionLog(
                title = "Signal & Network Optimization (${state.activeDnsName})",
                details = header + report,
                isSuccess = success,
                executionTimeMs = System.currentTimeMillis() - start
            )
        }

    fun generateTcpSysctlScript(state: NetworkTweakState): String =
        generateUltraSignalAndStreamingScript(state, state.resolveBoostMode())

    fun generateTcpSysctlScript(algorithm: String, mtu: Int): String =
        generateUltraSignalAndStreamingScript(
            NetworkTweakState(tcpAlgorithm = algorithm, mtuSize = mtu),
            SignalBoostMode.AUTO
        )

    /**
     * Script penguat sinyal terpadu. Menghasilkan SATU profil koheren sesuai [mode] sehingga
     * Wi-Fi dan seluler tidak lagi saling menimpa (bug "dua-duanya on jadi aneh").
     */
    fun generateUltraSignalAndStreamingScript(
        state: NetworkTweakState = NetworkTweakState(),
        mode: SignalBoostMode = state.resolveBoostMode()
    ): String {
        val effectiveMode = mode

        val commonStack = """
            # ── TCP / IP stack (aman & universal) ─────────────────────
            [ -e /proc/sys/net/ipv4/tcp_congestion_control ] && echo ${state.tcpAlgorithm} > /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_tw_reuse ] && echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_fastopen ] && echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_sack ] && echo 1 > /proc/sys/net/ipv4/tcp_sack 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_dsack ] && echo 1 > /proc/sys/net/ipv4/tcp_dsack 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_autocorking ] && echo 1 > /proc/sys/net/ipv4/tcp_autocorking 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_no_metrics_save ] && echo 1 > /proc/sys/net/ipv4/tcp_no_metrics_save 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_slow_start_after_idle ] && echo 0 > /proc/sys/net/ipv4/tcp_slow_start_after_idle 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_mtu_probing ] && echo 1 > /proc/sys/net/ipv4/tcp_mtu_probing 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_window_scaling ] && echo 1 > /proc/sys/net/ipv4/tcp_window_scaling 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_rfc1337 ] && echo 1 > /proc/sys/net/ipv4/tcp_rfc1337 2>/dev/null || true

            # ── Anti-bufferbloat: buffer kecil agar paket tidak antre ──
            [ -e /proc/sys/net/core/rmem_max ] && echo 16777216 > /proc/sys/net/core/rmem_max 2>/dev/null || true
            [ -e /proc/sys/net/core/wmem_max ] && echo 16777216 > /proc/sys/net/core/wmem_max 2>/dev/null || true
            [ -e /proc/sys/net/core/optmem_max ] && echo 1048576 > /proc/sys/net/core/optmem_max 2>/dev/null || true
            [ -e /proc/sys/net/core/netdev_max_backlog ] && echo 5000 > /proc/sys/net/core/netdev_max_backlog 2>/dev/null || true
            [ -e /proc/sys/net/ipv4/tcp_low_latency ] && echo 1 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null || true
        """.trimIndent()

        val wifiBlock = """
            # ── PROFIL WI-FI (radio tidak tidur, anti-jitter) ──────────
            settings put global wifi_scan_always_enabled 0 2>/dev/null || true
            settings put global wifi_wakeup_enabled 0 2>/dev/null || true
            settings put global wifi_sleep_policy 2 2>/dev/null || true
            # JANGAN paksa fallback seluler saat mode Wi-Fi: ini yang membuat Wi-Fi terasa "kureng"
            settings put global wifi_cellular_data_fallback 0 2>/dev/null || true
            # Power save Wi-Fi dimatikan bila node tersedia (butuh root)
            [ -e /sys/module/wlan/parameters/wifi_power_save ] && echo N > /sys/module/wlan/parameters/wifi_power_save 2>/dev/null || true
            [ -e /sys/module/wlan/parameters/force_sleep ] && echo 0 > /sys/module/wlan/parameters/force_sleep 2>/dev/null || true
            echo "profil: Wi-Fi aktif (radio low-latency, tanpa fallback paksa)"
        """.trimIndent()

        val cellularBlock = """
            # ── PROFIL SELULER (RIL tetap terjaga, anti dormancy) ──────
            settings put global mobile_data_always_on 1 2>/dev/null || true
            # Hanya paksa fallback bila memang mode seluler
            settings put global wifi_cellular_data_fallback 1 2>/dev/null || true
            if command -v resetprop >/dev/null 2>&1; then
              resetprop -n persist.radio.add_power_save 0 2>/dev/null || true
              resetprop -n persist.radio.apm_sim_not_pwdn 1 2>/dev/null || true
              echo "profil: seluler (resetprop RIL diterapkan)"
            else
              echo "profil: seluler (parameter ro.*/RIL dilewati — butuh Magisk/KernelSU)"
            fi
        """.trimIndent()

        val dualBlock = """
            # ── PROFIL DUAL-CHANNEL (Wi-Fi + seluler bersamaan) ────────
            # Hanya berguna bila perangkat benar-benar mendukung dua jalur data sekaligus.
            # Cek dukungan: presence interface seluler di samping wlan0.
            if ip link show wlan0 >/dev/null 2>&1 && (ip link show rmnet_data0 >/dev/null 2>&1 || ip link show rmnet0 >/dev/null 2>&1 || ip link show ccmni0 >/dev/null 2>&1); then
              settings put global mobile_data_always_on 1 2>/dev/null || true
              settings put global wifi_cellular_data_fallback 1 2>/dev/null || true
              settings put global wifi_sleep_policy 2 2>/dev/null || true
              settings put global wifi_scan_always_enabled 0 2>/dev/null || true
              if command -v resetprop >/dev/null 2>&1; then
                resetprop -n persist.radio.add_power_save 0 2>/dev/null || true
              fi
              echo "profil: DUAL-CHANNEL (Wi-Fi + seluler terdeteksi, keduanya dijaga aktif)"
            else
              # Perangkat tidak punya dua jalur -> jatuh ke AUTO, JANGAN tembak dua-duanya.
              settings put global wifi_scan_always_enabled 0 2>/dev/null || true
              settings put global wifi_sleep_policy 2 2>/dev/null || true
              settings put global mobile_data_always_on 1 2>/dev/null || true
              echo "profil: DUAL diminta tetapi perangkat tidak mendukung 2 jalur — dialihkan ke AUTO"
            fi
        """.trimIndent()

        val autoBlock = """
            # ── PROFIL AUTO (ikut transport yang benar-benar aktif) ────
            # Tidak memaksa Wi-Fi atau seluler secara bersamaan -> tidak ada setting bertabrakan.
            if ip route get 1.1.1.1 2>/dev/null | grep -q "dev wlan"; then
              settings put global wifi_scan_always_enabled 0 2>/dev/null || true
              settings put global wifi_sleep_policy 2 2>/dev/null || true
              settings put global wifi_cellular_data_fallback 0 2>/dev/null || true
              echo "profil: AUTO -> jalur aktif = Wi-Fi"
            else
              settings put global mobile_data_always_on 1 2>/dev/null || true
              settings put global wifi_cellular_data_fallback 1 2>/dev/null || true
              if command -v resetprop >/dev/null 2>&1; then
                resetprop -n persist.radio.add_power_save 0 2>/dev/null || true
              fi
              echo "profil: AUTO -> jalur aktif = seluler"
            fi
        """.trimIndent()

        val transportBlock = when (effectiveMode) {
            SignalBoostMode.WIFI_ONLY -> wifiBlock
            SignalBoostMode.CELLULAR_ONLY -> cellularBlock
            SignalBoostMode.DUAL_CHANNEL -> dualBlock
            SignalBoostMode.AUTO -> autoBlock
        }

        return """
            # ═══════════════════════════════════════════════════════════
            # INS NETWORK STACK TUNING
            # Mode sinyal : ${effectiveMode.label}
            # DNS         : ${state.activeDnsName} (${state.primaryIp} / ${state.secondaryIp})
            # ═══════════════════════════════════════════════════════════

            $commonStack

            # ── DNS privat (Cloudflare DoH) ───────────────────────────
            settings put global private_dns_mode hostname 2>/dev/null || true
            settings put global private_dns_specifier "one.one.one.one" 2>/dev/null || true
            settings put global captive_portal_mode 0 2>/dev/null || true

            $transportBlock

            # ── MTU per-interface (hanya interface yang benar-benar ada) ──
            for iface in wlan0 rmnet_data0 rmnet0 ccmni0 rndis0; do
              if ip link show "${'$'}iface" >/dev/null 2>&1; then
                ip link set "${'$'}iface" mtu ${state.mtuSize} 2>/dev/null && echo "mtu: ${'$'}iface = ${state.mtuSize}"
              fi
            done

            # ── Verifikasi ────────────────────────────────────────────
            echo "── VERIFIKASI ──"
            echo "mode=${effectiveMode.name}"
            echo "cc=${'$'}(cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null || echo n/a)"
            echo "rmem_max=${'$'}(cat /proc/sys/net/core/rmem_max 2>/dev/null || echo n/a)"
            echo "private_dns=${'$'}(settings get global private_dns_mode 2>/dev/null || echo n/a)"
            echo "── SELESAI ──"
        """.trimIndent()
    }

    companion object {
        /**
         * IP_TOS "minimize delay" + DSCP Expedited Forwarding (0xB8).
         * Menandai paket sebagai latency-sensitive agar router/operator memprioritaskan jalur cepat.
         * Catatan: ini mengoptimalkan paket MILIK KITA SENDIRI; tidak memengaruhi trafik pengguna lain.
         */
        const val IP_TOS_LOW_DELAY: Int = 0xB8

        /** Buka socket TCP low-latency (TCP_NODELAY + IP_TOS) untuk dipakai engine lain. */
        fun openLowLatencySocket(): Socket = Socket().apply {
            tcpNoDelay = true
            try {
                trafficClass = IP_TOS_LOW_DELAY
            } catch (_: Exception) {}
        }

        /** Buka socket UDP low-latency dengan timeout baca. */
        fun openLowLatencyDatagram(timeoutMs: Int = 400): DatagramSocket = DatagramSocket().apply {
            soTimeout = timeoutMs
            try {
                trafficClass = IP_TOS_LOW_DELAY
            } catch (_: Exception) {}
        }

        fun sendDnsProbe(socket: DatagramSocket, host: String, port: Int = 53): Long {
            val payload = byteArrayOf(
                0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x06, 0x67, 0x6f, 0x6f,
                0x67, 0x6c, 0x65, 0x03, 0x63, 0x6f, 0x6d, 0x00,
                0x00, 0x01, 0x00, 0x01
            )
            val start = System.currentTimeMillis()
            val target = InetAddress.getByName(host)
            socket.send(DatagramPacket(payload, payload.size, target, port))
            val buf = ByteArray(512)
            socket.receive(DatagramPacket(buf, buf.size))
            return System.currentTimeMillis() - start
        }
    }
}
