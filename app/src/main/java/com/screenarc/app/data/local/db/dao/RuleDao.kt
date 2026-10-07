package com.screenarc.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.screenarc.app.data.local.db.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY priority ASC")
    fun getAllRulesFlow(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY priority ASC")
    suspend fun getAllRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: String): RuleEntity?

    @Query("SELECT COUNT(*) FROM rules")
    suspend fun getRuleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RuleEntity>)

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("DELETE FROM rules")
    suspend fun deleteAllRules()

    @Query("UPDATE rules SET targetCategoryName = :newName WHERE targetCategoryId = :categoryId")
    suspend fun updateCategoryNameForRules(categoryId: Long, newName: String)
}
