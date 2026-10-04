package com.example.system

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Penemu port ADB nirkabel lewat mDNS (NsdManager).
 *
 * Ini yang membuat app bisa "minta kode pairing"-nya saja seperti Shizuku:
 * port pairing & port koneksi ditemukan otomatis dari layanan yang di-advertise adbd,
 * sehingga user tidak perlu menyalin angka port secara manual.
 *
 *  - `_adb-tls-pairing._tcp`  -> aktif HANYA saat dialog "Pair device with pairing code" terbuka.
 *  - `_adb-tls-connect._tcp`  -> aktif saat "Wireless debugging" menyala (port koneksi).
 */
object AdbMdnsDiscovery {

    const val TYPE_PAIRING = "_adb-tls-pairing._tcp"
    const val TYPE_CONNECT = "_adb-tls-connect._tcp"

    fun discoverPairingPort(context: Context, timeoutMs: Long = 8000L): Int? =
        discover(context, TYPE_PAIRING, timeoutMs)

    fun discoverConnectPort(context: Context, timeoutMs: Long = 8000L): Int? =
        discover(context, TYPE_CONNECT, timeoutMs)

    private fun discover(context: Context, serviceType: String, timeoutMs: Long): Int? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val latch = CountDownLatch(1)
        val foundPort = AtomicInteger(-1)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                try {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            if (serviceInfo.port > 0 && foundPort.get() <= 0) {
                                foundPort.set(serviceInfo.port)
                                latch.countDown()
                            }
                        }
                    })
                } catch (_: Exception) {
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                latch.countDown()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                latch.countDown()
            }
        }

        val multicastLock = acquireMulticastLock(context)
        return try {
            nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            val p = foundPort.get()
            if (p > 0) p else null
        } catch (_: Exception) {
            null
        } finally {
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
            releaseMulticastLock(multicastLock)
        }
    }

    private fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wm?.createMulticastLock("INS::AdbMdns")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (_: Exception) {
        null
    }

    private fun releaseMulticastLock(lock: WifiManager.MulticastLock?) {
        try {
            if (lock?.isHeld == true) lock.release()
        } catch (_: Exception) {
        }
    }
}
