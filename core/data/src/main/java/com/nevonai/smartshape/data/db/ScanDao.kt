package com.nevonai.smartshape.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Query("SELECT * FROM scans WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeActive(): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun observeDeleted(): Flow<List<ScanEntity>>

    @Query(
        """
        SELECT scans.* FROM scans
        JOIN scans_fts ON scans.rowid = scans_fts.rowid
        WHERE scans_fts MATCH :match AND scans.deleted_at IS NULL
        ORDER BY scans.created_at DESC
        """,
    )
    fun search(match: String): Flow<List<ScanEntity>>

    @Query("SELECT * FROM scans WHERE id = :id")
    fun observe(id: Long): Flow<ScanEntity?>

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun get(id: Long): ScanEntity?

    @Insert
    suspend fun insert(scan: ScanEntity): Long

    @Update
    suspend fun update(scan: ScanEntity)

    @Query("UPDATE scans SET favorite = :favorite, updated_at = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE scans SET title = :title, updated_at = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long)

    @Query("UPDATE scans SET deleted_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE scans SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM scans WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun deletedBefore(before: Long): List<ScanEntity>

    @Query("DELETE FROM scans WHERE id = :id")
    suspend fun purge(id: Long)
}
