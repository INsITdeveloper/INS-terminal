package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "terminal_history")
data class TerminalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val output: String,
    val exitCode: Int = 0,
    val executionTimeMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

@Entity(tableName = "system_tweak_presets")
data class SystemTweakPresetEntity(
    @PrimaryKey val presetId: String,
    val name: String,
    val description: String,
    val performanceGovernor: String,
    val dnsProvider: String,
    val tcpAlgorithm: String,
    val animationScale: Float,
    val refreshRateHz: Int,
    val zramSwappiness: Int,
    val touchResponseBoost: Boolean,
    val mtuTuning: Int,
    val isVolatileSession: Boolean = true,
    val isStock: Boolean = false,
    val lastAppliedTimestamp: Long = 0L
)

@Entity(tableName = "npm_packages")
data class NpmPackageEntity(
    @PrimaryKey val packageName: String,
    val version: String,
    val description: String,
    val license: String = "MIT",
    val installedTimestamp: Long = System.currentTimeMillis(),
    val isGlobal: Boolean = true,
    val sampleScript: String = "",
    val binAlias: String = ""
)

@Entity(tableName = "symlinks")
data class SymlinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val linkName: String,
    val sourceTarget: String,
    val destinationPath: String,
    val linkType: String = "SOFT",
    val isEnabled: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val isValid: Boolean = true,
    val notes: String = ""
)

@Entity(tableName = "stock_snapshot")
data class StockSnapshotEntity(
    @PrimaryKey val snapshotKey: String = "OEM_DEFAULT",
    val deviceModel: String,
    val androidVersion: String,
    val defaultDns: String,
    val defaultTcpCongestion: String,
    val defaultAnimationScale: Float,
    val defaultMtu: Int,
    val defaultRefreshRate: Int,
    val defaultSwappiness: Int,
    val createdAt: Long = System.currentTimeMillis()
)
