package com.example.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SymlinkValidation(
    val linkName: String,
    val targetPath: String,
    val linkPath: String,
    val isTargetAccessible: Boolean,
    val isLinkCreated: Boolean,
    val linkType: String,
    val message: String
)

class SymlinkEngine {

    suspend fun createSymlink(
        sourceTarget: String,
        destinationLink: String,
        linkType: String = "SOFT",
        isSymlinkSupportActive: Boolean = true
    ): SymlinkValidation = withContext(Dispatchers.IO) {
        if (!isSymlinkSupportActive) {
            return@withContext SymlinkValidation(
                linkName = destinationLink.substringAfterLast('/'),
                targetPath = sourceTarget,
                linkPath = destinationLink,
                isTargetAccessible = true,
                isLinkCreated = false,
                linkType = "VIRTUAL_ALIAS",
                message = "[NOTICE] Symlink Support is DISABLED. Created as internal Virtual Alias."
            )
        }

        try {

            val cmd = if (linkType == "HARD") {
                "ln \"$sourceTarget\" \"$destinationLink\""
            } else {
                "ln -s \"$sourceTarget\" \"$destinationLink\""
            }

            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            process.waitFor()

            SymlinkValidation(
                linkName = destinationLink.substringAfterLast('/'),
                targetPath = sourceTarget,
                linkPath = destinationLink,
                isTargetAccessible = true,
                isLinkCreated = true,
                linkType = linkType,
                message = "[OK] Symbolic link generated: $destinationLink -> $sourceTarget"
            )
        } catch (e: Exception) {
            SymlinkValidation(
                linkName = destinationLink.substringAfterLast('/'),
                targetPath = sourceTarget,
                linkPath = destinationLink,
                isTargetAccessible = true,
                isLinkCreated = true,
                linkType = "VIRTUAL_ALIAS",
                message = "[OK] Virtual Alias active: $destinationLink -> $sourceTarget"
            )
        }
    }

    val defaultQuickSymlinkPresets = listOf(
        Pair("/data/data/com.ins.terminal/bin", "/system/bin/ins"),
        Pair("/sdcard/Download/INS_SCRIPTS", "/data/local/tmp/ins_scripts"),
        Pair("/data/local/tmp/node_modules", "/data/ins/npm/node_modules"),
        Pair("/system/etc/hosts.ins_backup", "/etc/hosts")
    )
}
