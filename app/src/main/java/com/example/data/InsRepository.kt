package com.example.data

import com.example.data.dao.InsDao
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

class InsRepository(private val insDao: InsDao) {
    val historyList: Flow<List<TerminalHistoryEntity>> = insDao.getAllHistory()
    val presetList: Flow<List<SystemTweakPresetEntity>> = insDao.getAllPresets()
    val npmPackages: Flow<List<NpmPackageEntity>> = insDao.getAllNpmPackages()
    val symlinks: Flow<List<SymlinkEntity>> = insDao.getAllSymlinks()

    suspend fun logCommand(cmd: String, out: String, exitCode: Int, timeMs: Long): Long {
        return insDao.insertHistory(
            TerminalHistoryEntity(
                command = cmd,
                output = out,
                exitCode = exitCode,
                executionTimeMs = timeMs
            )
        )
    }

    suspend fun clearHistory() = insDao.clearAllHistory()
    suspend fun deleteHistoryItem(id: Long) = insDao.deleteHistory(id)

    suspend fun savePreset(preset: SystemTweakPresetEntity) = insDao.insertPreset(preset)
    suspend fun deletePreset(preset: SystemTweakPresetEntity) = insDao.deletePreset(preset)
    suspend fun getPreset(id: String) = insDao.getPresetById(id)

    suspend fun installNpmPackage(pkg: NpmPackageEntity) = insDao.insertNpmPackage(pkg)
    suspend fun removeNpmPackage(name: String) = insDao.deleteNpmPackage(name)

    suspend fun createSymlink(symlink: SymlinkEntity) = insDao.insertSymlink(symlink)
    suspend fun updateSymlink(symlink: SymlinkEntity) = insDao.updateSymlink(symlink)
    suspend fun deleteSymlink(symlink: SymlinkEntity) = insDao.deleteSymlink(symlink)

    suspend fun getStockSnapshot() = insDao.getStockSnapshot()
    suspend fun saveStockSnapshot(snapshot: StockSnapshotEntity) = insDao.saveStockSnapshot(snapshot)
}
