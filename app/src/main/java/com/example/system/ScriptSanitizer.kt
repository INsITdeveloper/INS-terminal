package com.example.system

/**
 * 🛡️ ScriptSanitizer — pengaman wajib untuk SEMUA script yang dijalankan lewat shell/root.
 *
 * Latar belakang (BUG PENTING):
 * Versi lama app ini menulis banyak `setprop debug.*` "tweak" (mis.
 * `debug.hwui.renderer skiavk`, `debug.composition.type gpu`,
 * `debug.egl.swapinterval 0`, `debug.sf.latch_unsignaled 1`) serta
 * `service call SurfaceFlinger ...` dan `wm density ...`.
 *
 * Prop-prop itu BUKAN tweak performa — itu flag DEBUG internal Android.
 * Menyalakannya membuat compositor/HWUI rusak: layar jadi hitam dengan garis-garis
 * aneh saat membuka game, WhatsApp, dll. Karena diset ulang tiap 45 detik oleh
 * PersistentBoosterService, kerusakannya terus kembali.
 *
 * Sanitizer ini membuang semua baris berbahaya dari script APA PUN sebelum
 * dieksekusi, jadi tidak ada jalur kode lama yang bisa merusak layar lagi.
 */
object ScriptSanitizer {

    /** Pola substring yang menandakan baris berbahaya (harus dibuang). */
    private val BLOCKED_PATTERNS = listOf(
        // Renderer / compositor / GPU debug flags
        "debug.hwui.renderer",
        "debug.renderengine.backend",
        "debug.composition.type",
        "persist.sys.composition.type",
        "debug.hwui.render_dirty_regions",
        "debug.hwui.disable_draw_defer",
        "debug.hwui.fps_divisor",
        // VSync / SurfaceFlinger debug flags
        "debug.egl.swapinterval",
        "debug.gr.swapinterval",
        "debug.sf.",
        "ro.surface_flinger.",
        // Anti-aliasing debug flags (tidak berpengaruh nyata, malah bikin artefak)
        "debug.egl.force_msaa",
        "debug.egl.force_fxaa",
        "persist.sys.force_msaa",
        // Thermal / touch / perf debug flags
        "persist.sys.thermal.mitigation",
        "debug.thermal.throttle",
        "persist.vendor.touch.sampling_rate",
        "debug.performance.tuning",
        // Transaksi SurfaceFlinger mentah & override resolusi/density (bikin touch ngaco)
        "service call SurfaceFlinger",
        "wm density",
        "wm size",
    )

    fun sanitize(script: String): String {
        if (script.isBlank()) return script
        return script.lineSequence()
            .filterNot { line -> BLOCKED_PATTERNS.any { line.contains(it) } }
            .joinToString("\n")
    }

    fun isBlocked(line: String): Boolean = BLOCKED_PATTERNS.any { line.contains(it) }
}
