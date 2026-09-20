package com.example.data.dao

import androidx.room.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InsDao {

    @Query("SELECT * FROM terminal_history ORDER BY id DESC LIMIT 200")
    fun getAllHistory(): Flow<List<TerminalHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: TerminalHistoryEntity): Long

    @Query("DELETE FROM terminal_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM terminal_history")
    suspend fun clearAllHistory()

    @Query("SELECT * FROM system_tweak_presets ORDER BY name ASC")
    fun getAllPresets(): Flow<List<SystemTweakPresetEntity>>

    @Query("SELECT * FROM system_tweak_presets WHERE presetId = :id LIMIT 1")
    suspend fun getPresetById(id: String): SystemTweakPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: SystemTweakPresetEntity)

    @Delete
    suspend fun deletePreset(preset: SystemTweakPresetEntity)

    @Query("SELECT * FROM npm_packages ORDER BY packageName ASC")
    fun getAllNpmPackages(): Flow<List<NpmPackageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNpmPackage(pkg: NpmPackageEntity)

    @Query("DELETE FROM npm_packages WHERE packageName = :packageName")
    suspend fun deleteNpmPackage(packageName: String)

    @Query("SELECT * FROM symlinks ORDER BY id DESC")
    fun getAllSymlinks(): Flow<List<SymlinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSymlink(symlink: SymlinkEntity): Long

    @Update
    suspend fun updateSymlink(symlink: SymlinkEntity)

    @Delete
    suspend fun deleteSymlink(symlink: SymlinkEntity)

    @Query("SELECT * FROM stock_snapshot WHERE snapshotKey = 'OEM_DEFAULT' LIMIT 1")
    suspend fun getStockSnapshot(): StockSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStockSnapshot(snapshot: StockSnapshotEntity)
}
