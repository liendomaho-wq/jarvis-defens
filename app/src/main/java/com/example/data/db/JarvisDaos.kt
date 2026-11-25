package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 100")
    fun getRecentCommands(): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CommandHistoryEntity): Long

    @Query("DELETE FROM command_history")
    suspend fun clearAll()
}

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules WHERE isEnabled = 1")
    suspend fun getActiveRulesList(): List<AutomationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AutomationRuleEntity): Long

    @Update
    suspend fun update(rule: AutomationRuleEntity)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM automation_rules WHERE name LIKE :name")
    suspend fun deleteByName(name: String): Int
}

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY label ASC")
    fun getAllLocations(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations WHERE LOWER(label) LIKE '%' || LOWER(:labelQuery) || '%' LIMIT 1")
    suspend fun findByLabel(labelQuery: String): SavedLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loc: SavedLocationEntity): Long

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MemoryNoteDao {
    @Query("SELECT * FROM memory_notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<MemoryNoteEntity>>

    @Query("SELECT * FROM memory_notes ORDER BY createdAt DESC")
    suspend fun getAllNotesList(): List<MemoryNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: MemoryNoteEntity): Long

    @Query("DELETE FROM memory_notes")
    suspend fun clearAll(): Int
}

@Dao
interface LearnedMacroDao {
    @Query("SELECT * FROM learned_macros ORDER BY id DESC")
    fun getAllMacros(): Flow<List<LearnedMacroEntity>>

    @Query("SELECT * FROM learned_macros")
    suspend fun getAllMacrosList(): List<LearnedMacroEntity>

    @Query("SELECT * FROM learned_macros WHERE LOWER(phrase) = LOWER(:phrase) LIMIT 1")
    suspend fun findByPhrase(phrase: String): LearnedMacroEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(macro: LearnedMacroEntity): Long

    @Query("DELETE FROM learned_macros WHERE id = :id")
    suspend fun deleteById(id: Long)
}
