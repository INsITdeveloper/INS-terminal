package com.example.system

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Base64

class CertificateAuthorityEngine(private val context: Context) {

    companion object {
        private const val MODULE_ID = "ins_httpcanary_ca"
        private const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
        private const val SYS_CACERTS = "/system/etc/security/cacerts"
        private const val APEX_CACERTS = "/apex/com.android.conscrypt/cacerts"

        private val CANARY_PACKAGES = listOf(
            "com.guoshi.httpcanary",
            "com.guoshi.httpcanary.premium",
            "com.guoshi.httpcanary.cert"
        )
    }

    data class InstallResult(
        val success: Boolean,
        val certHash: String?,
        val methodsTried: List<String>,
        val report: String
    )

    private fun priv() = PrivilegeExecutionEngine.getInstance(context)


    private data class Tlv(val tag: Int, val start: Int, val contentStart: Int, val contentEnd: Int)

    private fun readLength(b: ByteArray, i: Int): Pair<Int, Int> {
        var idx = i
        var len = b[idx].toInt() and 0xFF
        idx++
        if (len and 0x80 != 0) {
            val n = len and 0x7F
            if (n <= 0 || n > 4 || idx + n > b.size) return Pair(-1, idx)
            len = 0
            for (k in 0 until n) {
                len = (len shl 8) or (b[idx].toInt() and 0xFF)
                idx++
            }
        }
        return Pair(len, idx)
    }

    private fun readTlv(b: ByteArray, i: Int): Tlv? {
        if (i + 2 > b.size) return null
        val tag = b[i].toInt() and 0xFF
        val (len, contentStart) = readLength(b, i + 1)
        if (len < 0) return null
        val contentEnd = contentStart + len
        if (contentEnd > b.size) return null
        return Tlv(tag, i, contentStart, contentEnd)
    }

    private fun children(b: ByteArray, from: Int, to: Int): List<Tlv> {
        val out = mutableListOf<Tlv>()
        var i = from
        while (i < to) {
            val tlv = readTlv(b, i) ?: break
            out.add(tlv)
            i = tlv.contentEnd
        }
        return out
    }

    fun computeSubjectHashOld(der: ByteArray): String? {
        return try {
            val outer = readTlv(der, 0) ?: return null
            if (outer.tag != 0x30) return null
            val top = children(der, outer.contentStart, outer.contentEnd)
            val tbs = top.firstOrNull() ?: return null
            if (tbs.tag != 0x30) return null

            val kids = children(der, tbs.contentStart, tbs.contentEnd)
            var idx = 0
            if (kids.isNotEmpty() && kids[0].tag == 0xA0) idx++
            idx++
            idx++
            idx++
            idx++
            val subject = kids.getOrNull(idx) ?: return null

            val subjectDer = der.copyOfRange(subject.start, subject.contentEnd)
            val md5 = MessageDigest.getInstance("MD5").digest(subjectDer)
            val first4 = md5.copyOfRange(0, 4).reversedArray()
            first4.joinToString("") { String.format("%02x", it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun pemToDer(pem: String): ByteArray? {
        return try {
            val b64 = pem.lineSequence()
                .filterNot { it.startsWith("-----") || it.isBlank() }
                .joinToString("")
                .replace("\\s".toRegex(), "")
            if (b64.isEmpty()) return null
            Base64.getDecoder().decode(b64)
        } catch (_: Exception) {
            null
        }
    }


    suspend fun findHttpCanaryCert(): List<String> = withContext(Dispatchers.IO) {
        val script = buildString {
            appendLine("for p in ${CANARY_PACKAGES.joinToString(" ")}; do")
            appendLine("  find /data/data/\$p -maxdepth 4 -type f \\( -iname '*.pem' -o -iname '*.crt' -o -iname '*.cer' \\) 2>/dev/null")
            appendLine("done")
            appendLine("find /sdcard /storage/emulated/0 -maxdepth 4 -type f -iname '*httpcanary*.pem' 2>/dev/null")
            appendLine("find /sdcard /storage/emulated/0/Download -maxdepth 3 -type f -iname '*.pem' 2>/dev/null")
        }
        val res = priv().runBest(script, timeoutMs = 25_000L)
        res.output.lines()
            .map { it.trim() }
            .filter { it.startsWith("/") && it.endsWith(".pem", true) || it.startsWith("/") && it.endsWith(".crt", true) }
            .distinct()
    }

    private suspend fun readCertText(path: String): String? = withContext(Dispatchers.IO) {
        val direct = try {
            val f = File(path)
            if (f.exists() && f.canRead()) f.readText() else null
        } catch (_: Exception) {
            null
        }
        if (direct != null && direct.contains("BEGIN CERTIFICATE")) {
            direct
        } else {
            val res = priv().runBest("base64 '$path' 2>/dev/null", timeoutMs = 15_000L)
            if (!res.success || res.output.isBlank()) {
                null
            } else {
                try {
                    val decoded = String(Base64.getDecoder().decode(res.output.replace("\\s".toRegex(), "")))
                    if (decoded.contains("BEGIN CERTIFICATE")) decoded else null
                } catch (_: Exception) {
                    null
                }
            }
        }
    }


    suspend fun installHttpCanaryCa(certPath: String? = null): InstallResult = withContext(Dispatchers.IO) {
        val methods = mutableListOf<String>()
        val log = StringBuilder()

        log.appendLine("┌──────────────────────────────────────────────────────────┐")
        log.appendLine("│   🔐 SYSTEM CA INSTALLER (HTTP Canary / MITM Proxy)      │")
        log.appendLine("└──────────────────────────────────────────────────────────┘")

        val priv = priv()
        val status = priv.getPrivilegeStatus()

        if (!status.isRooted) {
            return@withContext InstallResult(
                success = false,
                certHash = null,
                methodsTried = emptyList(),
                report = buildString {
                    appendLine(log)
                    appendLine("✗ GAGAL: pemasangan CA ke system store WAJIB ROOT.")
                    appendLine()
                    appendLine("Kenapa: sejak Android 7, aplikasi hanya mempercayai sertifikat yang ada di")
                    appendLine("/system/etc/security/cacerts (atau APEX conscrypt). Menulis ke sana butuh root.")
                    appendLine()
                    appendLine("Tanpa root, cara satu-satunya: aktifkan 'Trust user certificates' di aplikasi")
                    appendLine("target (mis. lewat LSPosed/TrustMeAlready) — itu pun butuh root/LSPosed.")
                    appendLine()
                    appendLine("Root yang terdeteksi: ${status.rootManager}")
                }
            )
        }

        val path = certPath ?: findHttpCanaryCert().firstOrNull()
        if (path == null) {
            return@withContext InstallResult(
                success = false,
                certHash = null,
                methodsTried = emptyList(),
                report = buildString {
                    appendLine(log)
                    appendLine("✗ Sertifikat HTTP Canary tidak ditemukan.")
                    appendLine()
                    appendLine("Langkah yang perlu Anda lakukan:")
                    appendLine("  1. Buka aplikasi HTTP Canary.")
                    appendLine("  2. Masuk Settings → 'Install Certificate' / 'Export Certificate'.")
                    appendLine("  3. Simpan file .pem (biasanya ke /sdcard/HttpCanary.pem).")
                    appendLine("  4. Jalankan lagi:  install-ca")
                    appendLine()
                    appendLine("Atau sebutkan lokasinya:  install-ca /sdcard/HttpCanary.pem")
                }
            )
        }

        log.appendLine(" • Sumber sertifikat : $path")

        val pem = readCertText(path)
        if (pem == null) {
            return@withContext InstallResult(
                false, null, emptyList(),
                "$log\n✗ Tidak bisa membaca isi sertifikat di $path"
            )
        }

        val der = pemToDer(pem)
        if (der == null) {
            return@withContext InstallResult(
                false, null, emptyList(),
                "$log\n✗ Format sertifikat tidak dikenali (harus PEM/DER X.509)."
            )
        }

        val hash = computeSubjectHashOld(der)
        if (hash == null) {
            return@withContext InstallResult(
                false, null, emptyList(),
                "$log\n✗ Gagal menghitung subject hash sertifikat (struktur X.509 tidak terbaca)."
            )
        }

        log.appendLine(" • Subject hash      : $hash")
        log.appendLine(" • Nama file target  : $hash.0")
        log.appendLine()

        val stagingDir = File(context.cacheDir, "ca").apply { mkdirs() }
        val pemFile = File(stagingDir, "$hash.0")
        val b64File = File(stagingDir, "ca.b64")
        try {
            pemFile.writeText(pem)
            b64File.writeText(Base64.getEncoder().encodeToString(pem.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            return@withContext InstallResult(
                false, hash, emptyList(),
                "$log\n✗ Gagal menyiapkan file sementara: ${e.message}"
            )
        }

        val b64Path = b64File.absolutePath

        val moduleScript = """
            set -e
            MOD="$MODULE_DIR"
            mkdir -p "${'$'}MOD/system/etc/security/cacerts"
            base64 -d "$b64Path" > "${'$'}MOD/system/etc/security/cacerts/$hash.0"
            chmod 644 "${'$'}MOD/system/etc/security/cacerts/$hash.0"
            chown 0:0 "${'$'}MOD/system/etc/security/cacerts/$hash.0" 2>/dev/null || true
            printf 'id=%s\nname=%s\nauthor=%s\nversion=%s\ndescription=%s\n' \
              "$MODULE_ID" "INS HTTP Canary CA" "INS Terminal" "1.0" "System CA untuk HTTP Canary / MITM proxy" > "${'$'}MOD/module.prop"
            test -f "${'$'}MOD/system/etc/security/cacerts/$hash.0" && echo "MODULE_OK"
        """.trimIndent()

        val moduleRes = priv.runRoot(moduleScript, timeoutMs = 25_000L)
        if (moduleRes.success && moduleRes.output.contains("MODULE_OK")) {
            methods += "Modul Magisk/KernelSU: $MODULE_DIR (aktif setelah reboot)"
            log.appendLine("✓ Metode 1 BERHASIL: modul $MODULE_ID dibuat.")
            log.appendLine("   → Permanen, tidak menyentuh /system. Aktif setelah reboot.")
        } else {
            log.appendLine("✗ Metode 1 gagal: ${moduleRes.error ?: moduleRes.output.take(160)}")
        }

        val directScript = """
            TARGET="$SYS_CACERTS/$hash.0"
            if [ -d "$SYS_CACERTS" ]; then
              mount -o rw,remount /system 2>/dev/null || mount -o rw,remount / 2>/dev/null || true
              base64 -d "$b64Path" > "${'$'}TARGET" 2>/dev/null && chmod 644 "${'$'}TARGET" 2>/dev/null
              chown 0:0 "${'$'}TARGET" 2>/dev/null || true
              chcon u:object_r:system_file:s0 "${'$'}TARGET" 2>/dev/null || true
              if [ -f "${'$'}TARGET" ]; then echo "DIRECT_OK"; fi
            else
              echo "NO_SYSTEM_DIR"
            fi
        """.trimIndent()

        val directRes = priv.runRoot(directScript, timeoutMs = 25_000L)
        if (directRes.output.contains("DIRECT_OK")) {
            methods += "Tulis langsung ke $SYS_CACERTS (langsung berlaku)"
            log.appendLine("✓ Metode 2 BERHASIL: file ditulis ke $SYS_CACERTS/$hash.0")
        } else {
            log.appendLine("✗ Metode 2 gagal (normal di Android 10+ karena /system read-only): " +
                (directRes.output.lines().firstOrNull { it.isNotBlank() } ?: "tidak bisa menulis"))
        }

        val apexScript = """
            if [ -d "$APEX_CACERTS" ]; then
              WORK=/data/local/tmp/ins_ca_work
              rm -rf "${'$'}WORK"; mkdir -p "${'$'}WORK"
              cp "$APEX_CACERTS"/* "${'$'}WORK"/ 2>/dev/null || true
              base64 -d "$b64Path" > "${'$'}WORK/$hash.0" 2>/dev/null
              chmod 644 "${'$'}WORK"/* 2>/dev/null || true
              chown 0:0 "${'$'}WORK"/* 2>/dev/null || true
              chcon u:object_r:system_file:s0 "${'$'}WORK"/* 2>/dev/null || true
              if mount --bind "${'$'}WORK" "$APEX_CACERTS" 2>/dev/null; then
                echo "APEX_OK"
              else
                echo "APEX_MOUNT_FAILED"
              fi
            else
              echo "NO_APEX"
            fi
        """.trimIndent()

        val apexRes = priv.runRoot(apexScript, timeoutMs = 30_000L)
        when {
            apexRes.output.contains("APEX_OK") -> {
                methods += "Bind-mount APEX conscrypt (Android 14+, berlaku setelah app di-restart)"
                log.appendLine("✓ Metode 3 BERHASIL: bind-mount ke $APEX_CACERTS")
                log.appendLine("   → Restart aplikasi target (atau reboot) agar mount ini terbaca.")
            }
            apexRes.output.contains("NO_APEX") ->
                log.appendLine("• Metode 3 dilewati: tidak ada $APEX_CACERTS (Android < 14).")
            else ->
                log.appendLine("✗ Metode 3 gagal: ${apexRes.error ?: apexRes.output.take(160)}")
        }

        val verify = priv.runRoot(
            "echo \"MODULE=\$(ls $MODULE_DIR/system/etc/security/cacerts/$hash.0 2>/dev/null)\"; " +
                "echo \"SYSTEM=\$(ls $SYS_CACERTS/$hash.0 2>/dev/null)\"; " +
                "echo \"APEX=\$(ls $APEX_CACERTS/$hash.0 2>/dev/null)\"",
            timeoutMs = 12_000L
        )

        log.appendLine()
        log.appendLine("── VERIFIKASI (dibaca ulang dari sistem) ──")
        log.appendLine(verify.output.ifBlank { "(tidak ada output verifikasi)" })

        val success = methods.isNotEmpty()

        if (success) {
            log.appendLine()
            log.appendLine("✓ SELESAI. Sertifikat terpasang lewat ${methods.size} metode:")
            methods.forEach { log.appendLine("   • $it") }
            log.appendLine()
            log.appendLine("Langkah pakai HTTP Canary:")
            log.appendLine("  1. Reboot HP (kalau hanya metode modul yang berhasil).")
            log.appendLine("  2. Buka HTTP Canary → mulai capture.")
            log.appendLine("  3. Buka aplikasi target. HTTPS-nya sekarang bisa dibaca.")
            log.appendLine()
            log.appendLine("Kalau ada app yang masih menolak (certificate pinning), CA system saja")
            log.appendLine("tidak cukup — app itu perlu di-bypass lewat LSPosed/SSL unpinning module.")
        } else {
            log.appendLine()
            log.appendLine("✗ Semua metode gagal. Kemungkinan penyebab:")
            log.appendLine("   • Root terdeteksi tapi izin su ditolak untuk aksi tulis sistem.")
            log.appendLine("   • Perangkat memakai verified boot / /system terkunci total.")
            log.appendLine("   • Manajer root bukan Magisk/KernelSU/APatch (tidak ada folder /data/adb/modules).")
        }

        InstallResult(success, hash, methods, log.toString().trimEnd())
    }

    suspend fun uninstall(hash: String?): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (hash.isNullOrBlank()) {
            return@withContext Pair(false, "Sebutkan hash sertifikat yang mau dihapus, mis: uninstall-ca c021b38d")
        }
        val script = """
            rm -f "$MODULE_DIR/system/etc/security/cacerts/$hash.0" 2>/dev/null
            rm -rf "$MODULE_DIR" 2>/dev/null
            mount -o rw,remount /system 2>/dev/null || true
            rm -f "$SYS_CACERTS/$hash.0" 2>/dev/null
            rm -f "$APEX_CACERTS/$hash.0" 2>/dev/null
            echo "MODULE=\$(ls $MODULE_DIR 2>/dev/null | head -1)"
            echo "SYSTEM=\$(ls $SYS_CACERTS/$hash.0 2>/dev/null)"
            echo "SELESAI"
        """.trimIndent()
        val res = priv().runRoot(script, timeoutMs = 20_000L)
        val ok = res.success && !res.output.contains("SYSTEM=/")
        Pair(
            ok,
            if (ok) "Sertifikat $hash.0 dihapus. Reboot untuk memastikan perubahan berlaku."
            else "Gagal menghapus: ${res.error ?: res.output.take(200)}"
        )
    }

    suspend fun status(): String = withContext(Dispatchers.IO) {
        val priv = priv()
        val res = priv.runRoot(
            "echo \"MODULES=\$(ls -d /data/adb/modules/* 2>/dev/null | wc -l)\"; " +
                "echo \"SYS_COUNT=\$(ls $SYS_CACERTS 2>/dev/null | wc -l)\"; " +
                "echo \"APEX_COUNT=\$(ls $APEX_CACERTS 2>/dev/null | wc -l)\"; " +
                "echo \"INS_CA=\$(ls -d $MODULE_DIR 2>/dev/null)\"",
            timeoutMs = 12_000L
        )
        buildString {
            appendLine("┌──────────────────────────────────────────────────────────┐")
            appendLine("│   🔐 STATUS SYSTEM CA                                     │")
            appendLine("└──────────────────────────────────────────────────────────┘")
            if (!priv.isDeviceRooted()) {
                appendLine(" • Root: TIDAK ADA — pemasangan CA ke system store tidak mungkin.")
            } else {
                appendLine(" • Root: ${priv.getPrivilegeStatus().rootManager}")
            }
            appendLine(res.output.ifBlank { "(gagal membaca status)" })
        }
    }
}
