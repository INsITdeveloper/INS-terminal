package com.example.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis

data class NpmRegistryPackage(
    val name: String,
    val version: String,
    val description: String,
    val size: String,
    val weeklyDownloads: String,
    val sampleCode: String
)

data class NpmRunResult(
    val output: String,
    val executionTimeMs: Long,
    val isSuccess: Boolean
)

class NpmPackageManager {

    val registryCatalog = listOf(
        NpmRegistryPackage(
            name = "axios",
            version = "1.7.9",
            description = "Promise based HTTP client for the browser and node.js",
            size = "450 kB",
            weeklyDownloads = "48,200,000",
            sampleCode = "const axios = require('axios');\nconst res = await axios.get('https://api.github.com');\nconsole.log('Status:', res.status);"
        ),
        NpmRegistryPackage(
            name = "chalk",
            version = "5.4.1",
            description = "Terminal string styling done right with 24-bit TrueColor",
            size = "85 kB",
            weeklyDownloads = "120,500,000",
            sampleCode = "const chalk = require('chalk');\nconsole.log(chalk.cyan.bold('⚡ INS Terminal Online!'));"
        ),
        NpmRegistryPackage(
            name = "express",
            version = "4.21.2",
            description = "Fast, unopinionated, minimalist web framework for node",
            size = "2.1 MB",
            weeklyDownloads = "32,400,000",
            sampleCode = "const express = require('express');\nconst app = express();\napp.get('/', (req, res) => res.send('INS UI 16 API'));\nconsole.log('App configured');"
        ),
        NpmRegistryPackage(
            name = "lodash",
            version = "4.17.21",
            description = "Lodash modular utilities delivering consistency & performance",
            size = "1.4 MB",
            weeklyDownloads = "55,000,000",
            sampleCode = "const _ = require('lodash');\nconst arr = [1, 2, [3, [4]]];\nconsole.log('Flattened:', _.flattenDeep(arr));"
        ),
        NpmRegistryPackage(
            name = "dotenv",
            version = "16.4.7",
            description = "Loads environment variables from .env file",
            size = "48 kB",
            weeklyDownloads = "41,000,000",
            sampleCode = "require('dotenv').config();\nconsole.log('NODE_ENV:', process.env.NODE_ENV || 'production');"
        ),
        NpmRegistryPackage(
            name = "ws",
            version = "8.18.0",
            description = "Simple to use, blazing fast WebSocket client & server",
            size = "320 kB",
            weeklyDownloads = "82,000,000",
            sampleCode = "const WebSocket = require('ws');\nconst ws = new WebSocket('wss://echo.websocket.org');\nconsole.log('WebSocket Client Init');"
        ),
        NpmRegistryPackage(
            name = "pm2",
            version = "5.4.3",
            description = "Production Process Manager for Node.js with built-in Load Balancer",
            size = "14.2 MB",
            weeklyDownloads = "3,200,000",
            sampleCode = "pm2 start server.js -i max --name 'ins-backend'\npm2 list"
        ),
        NpmRegistryPackage(
            name = "typescript",
            version = "5.7.3",
            description = "TypeScript is a language for application-scale JavaScript",
            size = "34.5 MB",
            weeklyDownloads = "45,000,000",
            sampleCode = "interface InsConfig { mode: string; turbo: boolean }\nconst cfg: InsConfig = { mode: 'active', turbo: true };\nconsole.log(cfg);"
        )
    )

    suspend fun executeJsScript(code: String, installedPackages: List<String>): NpmRunResult = withContext(Dispatchers.Default) {
        val outputLines = mutableListOf<String>()
        val elapsed = measureTimeMillis {
            outputLines.add("[$] node --experimental-ins-vm script.js")
            outputLines.add("[INS V8 Sandbox Runtime v22.12.0]")

            val lines = code.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("console.log(") && trimmed.endsWith(");")) {
                    val content = trimmed.removePrefix("console.log(").removeSuffix(");")
                    outputLines.add(cleanJsOutput(content))
                } else if (trimmed.startsWith("require(") || trimmed.contains("require(")) {
                    val pkgName = trimmed.substringAfter("require('").substringBefore("'")
                        .ifEmpty { trimmed.substringAfter("require(\"").substringBefore("\"") }
                    if (pkgName.isNotEmpty()) {
                        val isInstalled = installedPackages.any { it.equals(pkgName, ignoreCase = true) }
                        if (isInstalled) {
                            outputLines.add("[module] Loaded '$pkgName' from node_modules")
                        } else {
                            outputLines.add("[module] Loaded '$pkgName' (built-in fallback runtime)")
                        }
                    }
                } else if (trimmed.startsWith("const ") || trimmed.startsWith("let ") || trimmed.startsWith("var ")) {
                    outputLines.add("  > Evaluated: ${trimmed.take(45)}...")
                }
            }

            if (outputLines.size <= 2) {
                outputLines.add("[INS JavaScript Engine] Executed cleanly without exceptions.")
                outputLines.add("Result: undefined")
            }
            outputLines.add("[Process completed with exit code 0]")
        }

        NpmRunResult(
            output = outputLines.joinToString("\n"),
            executionTimeMs = elapsed,
            isSuccess = true
        )
    }

    private fun cleanJsOutput(raw: String): String {
        return raw.replace("'", "")
            .replace("\"", "")
            .replace("chalk.cyan.bold(", "")
            .replace(")", "")
    }
}
