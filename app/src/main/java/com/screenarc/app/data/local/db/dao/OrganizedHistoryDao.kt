package com.screenarc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.screenarc.app.data.local.db.entity.OrganizedHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrganizedHistoryDao {

    @Query("SELECT * FROM organized_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<OrganizedHistoryEntity>>

    @Query("SELECT * FROM organized_history ORDER BY timestamp DESC")
    suspend fun getAllHistory(): List<OrganizedHistoryEntity>

    @Query("SELECT COUNT(*) FROM organized_history")
    suspend fun getHistoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: OrganizedHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<OrganizedHistoryEntity>)

    @Query("DELETE FROM organized_history WHERE id = :id")
    suspend fun deleteRecordById(id: String)

    @Query("DELETE FROM organized_history")
    suspend fun deleteAllHistory()

    /**
     * Efficient database-side pruning of old history records.
     * Keeps the most recent [keepCount] entries and deletes older rows.
     */
    @Query("""
        DELETE FROM organized_history
        WHERE id NOT IN (
            SELECT id FROM organized_history
            ORDER BY timestamp DESC
            LIMIT :keepCount
        )
    """)
    suspend fun pruneOldRecords(keepCount: Int = 1000)

    @Query("UPDATE organized_history SET categoryName = :newName WHERE categoryName = :oldName")
    suspend fun updateCategoryName(oldName: String, newName: String)
}
